/**
 * Copyright (c) 2017 "Neo4j, Inc." <http://neo4j.com>
 *
 * This file is part of Neo4j Graph Algorithms <http://github.com/neo4j-contrib/neo4j-graph-algorithms>.
 *
 * Neo4j Graph Algorithms is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphalgo.impl.louvain;

import com.carrotsearch.hppc.BitSet;
import com.carrotsearch.hppc.IntIntMap;
import com.carrotsearch.hppc.IntIntScatterMap;
import org.neo4j.collection.primitive.PrimitiveIntIterator;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.NodeIterator;
import org.neo4j.graphalgo.api.NodeWeights;
import org.neo4j.graphalgo.core.sources.ShuffledNodeIterator;
import org.neo4j.graphalgo.core.utils.ParallelUtil;
import org.neo4j.graphalgo.core.utils.Pointer;
import org.neo4j.graphalgo.core.utils.ProgressLogger;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.impl.Algorithm;
import org.neo4j.graphdb.Direction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * parallel weighted undirected modularity based community detection
 * (first phase of louvain algo). The algorithm assigns community ids to each
 * node in the graph. This is done by several threads in parallel. Each thread
 * performs a modularity optimization using a shuffled node iterator. The task
 * with the best (highest) modularity is selected and its community structure
 * is used as result
 *
 * @author mknblch
 */
public class ModularityOptimization extends Algorithm<ModularityOptimization> {

    private static final double MINIMUM_MODULARITY = -1.0;
    /**
     * only outgoing directions are visited since the graph itself must be loaded using {@code .asUndirected(true) } !
     */
    private static final Direction D = Direction.OUTGOING;
    private static final int NONE = -1;
    private final int nodeCount;
    private final int concurrency;
    private final AllocationTracker tracker;
    private final NodeWeights nodeWeights;
    private Graph graph;
    private ExecutorService pool;
    private NodeIterator nodeIterator;
    private double m2, m22;
    private int[] communities;
    private double[] ki;
    private int iterations;
    private double q = MINIMUM_MODULARITY;
    private AtomicInteger counter = new AtomicInteger(0);

    ModularityOptimization(Graph graph, NodeWeights nodeWeights, ExecutorService pool, int concurrency, AllocationTracker tracker) {
        this.graph = graph;
        this.nodeWeights = nodeWeights;
        nodeCount = Math.toIntExact(graph.nodeCount());
        this.pool = pool;
        this.concurrency = concurrency;
        this.tracker = tracker;
        this.nodeIterator = createNodeIterator(concurrency);

        ki = new double[nodeCount];
        communities = new int[nodeCount];
        // (1x double + 1x int) * N
        tracker.add(12 * nodeCount);
    }

    private NodeIterator createNodeIterator(int concurrency) {

        if (concurrency > 1) {
            return new ShuffledNodeIterator(nodeCount);
        }

        return new NodeIterator() {
            @Override
            public void forEachNode(IntPredicate consumer) {
                for (int i = 0; i < nodeCount; i++) {
                    if (!consumer.test(i)) {
                        return;
                    }
                }
            }

            @Override
            public PrimitiveIntIterator nodeIterator() {
                return new PrimitiveIntIterator() {

                    int offset = 0;

                    @Override
                    public boolean hasNext() {
                        return offset < nodeCount;
                    }

                    @Override
                    public int next() {
                        return offset++;
                    }
                };
            }
        };
    }

    /**
     * get the task with the best community distribution
     * (highest modularity value) of an array of tasks
     *
     * @return best task
     */
    private static Task best(Collection<Task> tasks) {
        Task best = null; // may stay null if no task improves the current q
        double q = MINIMUM_MODULARITY;
        for (Task task : tasks) {
            if (!task.improvement) {
                continue;
            }
            final double modularity = task.getModularity();
            if (modularity > q) {
                q = modularity;
                best = task;
            }
        }
        return best;
    }

    /**
     * normalize nodeToCommunity-Array. Maps community IDs
     * in a sequential order starting at 0.
     *
     * @param communities
     * @return number of communities
     */
    static int normalize(int[] communities) {
        final IntIntMap map = new IntIntScatterMap(communities.length);
        int c = 0;
        for (int i = 0; i < communities.length; i++) {
            int mapped, community = communities[i];
            if ((mapped = map.getOrDefault(community, -1)) != -1) {
                communities[i] = mapped;
            } else {
                map.put(community, c);
                communities[i] = c++;
            }
        }
        return c;
    }

    /**
     * init ki (sum of weights of node) & m
     */
    private void init() {
        m2 = .0;
        for (int node = 0; node < nodeCount; node++) {
            // since we use an undirected graph 2m is counted here
            graph.forEachRelationship(node, D, (s, t, r) -> {
                final double w = graph.weightOf(s, t);
                m2 += w;
                ki[s] += w / 2;
                ki[t] += w / 2;
                return true;
            });
        }
        m22 = Math.pow(m2, 2.0);
        Arrays.setAll(communities, i -> i);
    }

    /**
     * compute first phase louvain
     *
     * @param maxIterations
     * @return
     */
    public ModularityOptimization compute(int maxIterations) {
        // init helper values & initial community structure
        init();
        // create an array of tasks for parallel exec
        final ArrayList<Task> tasks = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
            tasks.add(new Task());
        }
        // (2x double + 1x int) * N * threads
        tracker.add(20 * nodeCount * concurrency);
        // as long as maxIterations is not reached
        for (iterations = 0; iterations < maxIterations; iterations++) {
            // reset node counter (for logging)
            counter.set(0);
            // run all tasks
            ParallelUtil.runWithConcurrency(concurrency, tasks, pool);
            // take the best candidate
            Task candidate = best(tasks);
            if (null == candidate || candidate.q <= this.q) {
                // best candidate's modularity did not improve
                break;
            }
            // save current modularity
            this.q = candidate.q;
            // sync all tasks with the best candidate for the next round
            sync(candidate, tasks);
        }
        tracker.remove(20 * nodeCount * concurrency);
        return this;
    }

    /**
     * sync parent Task with all other task except itself and
     * copy community structure to global community structure
     */
    private void sync(Task parent, Collection<Task> tasks) {
        for (Task task : tasks) {
            task.improvement = false;
            if (task == parent) {
                continue;
            }
            task.sync(parent);
        }
        System.arraycopy(parent.localCommunities, 0, communities, 0, nodeCount);
    }

    /**
     * get communities
     *
     * @return node-nodeId to localCommunities nodeId mapping
     */
    public int[] getCommunityIds() {
        return communities;
    }

    /**
     * number of iterations
     *
     * @return number of iterations
     */
    public int getIterations() {
        return iterations;
    }

    public double getModularity() {
        return q;
    }

    /**
     * @return this
     */
    @Override
    public ModularityOptimization me() {
        return this;
    }

    /**
     * release structures
     *
     * @return this
     */
    @Override
    public ModularityOptimization release() {
        this.graph = null;
        this.pool = null;
        this.communities = null;
        this.ki = null;
        tracker.remove(12 * nodeCount);
        return this;
    }

    /**
     * Restartable task to perform modularity optimization
     */
    private class Task implements Runnable {

        final double[] sTot, sIn;
        final int[] localCommunities;
        double bestGain, bestWeight, q = MINIMUM_MODULARITY;
        int bestCommunity;
        boolean improvement = false;

        /**
         * at creation the task copies the community-structure
         * and initializes its helper arrays
         */
        Task() {
            sTot = new double[nodeCount];
            System.arraycopy(ki, 0, sTot, 0, nodeCount); // ki -> sTot
            localCommunities = new int[nodeCount];
            System.arraycopy(communities, 0, localCommunities, 0, nodeCount);
            sIn = new double[nodeCount];
            Arrays.fill(sIn, 0.);
        }

        /**
         * copy community structure and helper arrays from parent
         * task into this task
         */
        void sync(Task parent) {
            System.arraycopy(parent.localCommunities, 0, localCommunities, 0, nodeCount);
            System.arraycopy(parent.sTot, 0, sTot, 0, nodeCount);
            System.arraycopy(parent.sIn, 0, sIn, 0, nodeCount);
            this.q = parent.q;
        }

        @Override
        public void run() {
            final ProgressLogger progressLogger = getProgressLogger();
            final int denominator = nodeCount * concurrency;
            improvement = false;
            nodeIterator.forEachNode(node -> {
                final boolean move = move(node);
                improvement |= move;
                progressLogger.logProgress(
                        counter.getAndIncrement(),
                        denominator,
                        () -> String.format("round %d", iterations + 1));
                return true;
            });
            this.q = calcModularity();
        }

        /**
         * get the graph modularity of the calculated community structure
         */
        double getModularity() {
            return q;
        }

        /**
         * calc modularity-gain for a node and move it into the best community
         *
         * @param node node nodeId
         * @return true if the node has been moved
         */
        private boolean move(int node) {
            final int currentCommunity = bestCommunity = localCommunities[node];
            final double w = weightIntoCom(node, currentCommunity);
            sTot[currentCommunity] -= ki[node];
            sIn[currentCommunity] -= 2 * (w + nodeWeights.weightOf(node));
            localCommunities[node] = NONE;
            bestGain = .0;
            bestWeight = w;
            forEachConnectedCommunity(node, c -> {
                final double wic = weightIntoCom(node, c);
                final double g = wic / m2 - sTot[c] * ki[node] / m22;
                if (g > bestGain) {
                    bestGain = g;
                    bestCommunity = c;
                    bestWeight = wic;
                }
            });
            sTot[bestCommunity] += ki[node];
            sIn[bestCommunity] += 2 * (bestWeight + nodeWeights.weightOf(node));
            localCommunities[node] = bestCommunity;
            return bestCommunity != currentCommunity;
        }

        /**
         * apply consumer to each connected community one time
         *
         * @param node     node nodeId
         * @param consumer community nodeId consumer
         */
        private void forEachConnectedCommunity(int node, IntConsumer consumer) {
            final BitSet visited = new BitSet(nodeCount);
            graph.forEachRelationship(node, D, (s, t, r) -> {
                final int c = localCommunities[t];
                if (c == NONE) {
                    return true;
                }
                if (visited.get(c)) {
                    return true;
                }
                visited.set(c);
                consumer.accept(c);
                return true;
            });
        }

        /**
         * sum weights from node into community c
         *
         * @param node node nodeId
         * @param c    community nodeId
         * @return sum of weights from node into community c
         */
        private double weightIntoCom(int node, int c) {
            final Pointer.DoublePointer p = Pointer.wrap(.0);
            graph.forEachRelationship(node, D, (s, t, r) -> {
                if (localCommunities[t] == c) {
                    p.map(v -> v + graph.weightOf(s, t));
                }
                return true;
            });
            return p.v;
        }

        private double calcModularity() {
            final Pointer.DoublePointer pointer = Pointer.wrap(.0);
            for (int node = 0; node < nodeCount; node++) {
                graph.forEachOutgoing(node, (s, t, r) -> {
                    if (localCommunities[s] != localCommunities[t]) {
                        return true;
                    }
                    pointer.map(v -> v + graph.weightOf(s, t) - (ki[s] * ki[t] / m2));
                    return true;
                });
            }
            return pointer.v / m2;
        }
    }
}
