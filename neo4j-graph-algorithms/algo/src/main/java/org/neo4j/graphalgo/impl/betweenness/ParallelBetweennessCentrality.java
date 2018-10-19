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
package org.neo4j.graphalgo.impl.betweenness;

import com.carrotsearch.hppc.IntArrayDeque;
import com.carrotsearch.hppc.IntStack;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.utils.AtomicDoubleArray;
import org.neo4j.graphalgo.core.utils.ParallelUtil;
import org.neo4j.graphalgo.core.utils.container.Paths;
import org.neo4j.graphalgo.impl.Algorithm;
import org.neo4j.graphdb.Direction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Implements Betweenness Centrality for unweighted graphs
 * as specified in <a href="http://www.algo.uni-konstanz.de/publications/b-fabc-01.pdf">this paper</a>
 * using node-partitioning
 *
 * @author mknblch
 */
public class ParallelBetweennessCentrality extends Algorithm<ParallelBetweennessCentrality> {

    // the graph
    private Graph graph;
    // AI counts up for every node until nodeCount is reached
    private volatile AtomicInteger nodeQueue = new AtomicInteger();
    // atomic double array which supports only atomic-add
    private AtomicDoubleArray centrality;
    // the node count
    private final int nodeCount;
    // global executor service
    private final ExecutorService executorService;
    // number of threads to spawn
    private final int concurrency;
    private Direction direction = Direction.OUTGOING;
    private double divisor = 1.0;

    /**
     * constructs a parallel centrality solver
     *
     * @param graph the graph iface
     * @param executorService the executor service
     * @param concurrency desired number of threads to spawn
     */
    public ParallelBetweennessCentrality(Graph graph, ExecutorService executorService, int concurrency) {
        this.graph = graph;
        this.nodeCount = Math.toIntExact(graph.nodeCount());
        this.executorService = executorService;
        this.concurrency = concurrency;
        this.centrality = new AtomicDoubleArray(nodeCount);
    }

    public ParallelBetweennessCentrality withDirection(Direction direction) {
        this.direction = direction;
        this.divisor = direction == Direction.BOTH ? 2.0 : 1.0;
        return this;
    }

    /**
     * compute centrality
     *
     * @return itself for method chaining
     */
    public ParallelBetweennessCentrality compute() {
        nodeQueue.set(0);
        final ArrayList<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
            futures.add(executorService.submit(new BCTask()));
        }
        ParallelUtil.awaitTermination(futures);
        return this;
    }

    /**
     * get the centrality array
     *
     * @return array with centrality
     */
    public AtomicDoubleArray getCentrality() {
        return centrality;
    }

    /**
     * emit the result stream
     *
     * @return stream if Results
     */
    public Stream<BetweennessCentrality.Result> resultStream() {
        return IntStream.range(0, nodeCount)
                .mapToObj(nodeId ->
                        new BetweennessCentrality.Result(
                                graph.toOriginalNodeId(nodeId),
                                centrality.get(nodeId)));
    }

    @Override
    public ParallelBetweennessCentrality me() {
        return this;
    }

    @Override
    public ParallelBetweennessCentrality release() {
        graph = null;
        centrality = null;
        return null;
    }

    /**
     * a BCTask takes one element from the nodeQueue as long as
     * it is lower then nodeCount and calculates it's centrality
     */
    private class BCTask implements Runnable {

        private final Paths paths;
        private final IntStack stack;
        private final IntArrayDeque queue;
        private final double[] delta;
        private final int[] sigma;
        private final int[] distance;

        private BCTask() {
            this.paths = new Paths();
            this.stack = new IntStack();
            this.queue = new IntArrayDeque();
            this.sigma = new int[nodeCount];
            this.distance = new int[nodeCount];
            this.delta = new double[nodeCount];
        }

        @Override
        public void run() {
            for (;;) {
                reset();
                final int startNodeId = nodeQueue.getAndIncrement();
                if (startNodeId >= nodeCount || !running()) {
                    return;
                }
                getProgressLogger().logProgress((double) startNodeId / (nodeCount - 1));
                sigma[startNodeId] = 1;
                distance[startNodeId] = 0;
                queue.addLast(startNodeId);
                while (!queue.isEmpty()) {
                    int node = queue.removeFirst();
                    stack.push(node);
                    graph.forEachRelationship(node, direction, (source, target, relationId) -> {
                        if (distance[target] < 0) {
                            queue.addLast(target);
                            distance[target] = distance[node] + 1;
                        }
                        if (distance[target] == distance[node] + 1) {
                            sigma[target] += sigma[node];
                            paths.append(target, node);
                        }
                        return true;
                    });
                }

                while (!stack.isEmpty()) {
                    int node = stack.pop();
                    paths.forEach(node, v -> {
                        delta[v] += (double) sigma[v] / (double) sigma[node] * (delta[node] + 1.0);
                        return true;
                    });
                    if (node != startNodeId) {
                        centrality.add(node, delta[node] / divisor);
                    }
                }
            }
        }

        /**
         * reset local state
         */
        private void reset() {
            paths.clear();
            stack.clear();
            queue.clear();
            Arrays.fill(sigma, 0);
            Arrays.fill(delta, 0);
            Arrays.fill(distance, -1);
        }
    }
}
