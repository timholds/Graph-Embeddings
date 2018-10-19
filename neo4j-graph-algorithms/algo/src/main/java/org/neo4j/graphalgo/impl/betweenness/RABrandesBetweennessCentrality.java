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

import com.carrotsearch.hppc.*;
import com.carrotsearch.hppc.cursors.IntCursor;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.utils.AtomicDoubleArray;
import org.neo4j.graphalgo.core.utils.ParallelUtil;
import org.neo4j.graphalgo.impl.Algorithm;
import org.neo4j.graphdb.Direction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Randomized Approximate Brandes
 *
 * @author mknblch
 */
public class RABrandesBetweennessCentrality extends Algorithm<RABrandesBetweennessCentrality> {

    public interface SelectionStrategy {

        /**
         * tell if the node is part of the selection
         */
        boolean select(int nodeId);

        /**
         * total count of selectable nodes
         */
        int size();
    }

    // the graph
    private Graph graph;
    // AI counts up for every node until nodeCount is reached
    private volatile AtomicInteger nodeQueue = new AtomicInteger();
    // atomic double array which supports only atomic-add
    private AtomicDoubleArray centrality;
    // the node count
    private final int nodeCount;
    private final int expectedNodeCount;
    // global executor service
    private final ExecutorService executorService;
    // number of threads to spawn
    private final int concurrency;
    private SelectionStrategy selectionStrategy;
    private Direction direction = Direction.OUTGOING;
    private double divisor = 1.0;

    private int maxDepth = Integer.MAX_VALUE;

    /**
     * @param graph the graph iface
     * @param executorService the executor service
     * @param concurrency desired number of threads to spawn
     */
    public RABrandesBetweennessCentrality(Graph graph, ExecutorService executorService, int concurrency, SelectionStrategy selectionStrategy) {
        this.graph = graph;
        this.executorService = executorService;
        this.concurrency = concurrency;
        this.nodeCount = Math.toIntExact(graph.nodeCount());
        this.centrality = new AtomicDoubleArray(nodeCount);
        this.selectionStrategy = selectionStrategy;
        this.expectedNodeCount = selectionStrategy.size();
    }

    public RABrandesBetweennessCentrality withDirection(Direction direction) {
        this.direction = direction;
        this.divisor = direction == Direction.BOTH ? 2.0 : 1.0;
        return this;
    }

    public RABrandesBetweennessCentrality withMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
        return this;
    }

    /**
     * compute centrality
     *
     * @return itself for method chaining
     */
    public RABrandesBetweennessCentrality compute() {
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
    public RABrandesBetweennessCentrality me() {
        return this;
    }

    @Override
    public RABrandesBetweennessCentrality release() {
        graph = null;
        selectionStrategy = null;
        return this;
    }

    /**
     * a BCTask takes one element from the nodeQueue as long as
     * it is lower then nodeCount and calculates it's centrality
     */
    private class BCTask implements Runnable {

        private final IntObjectMap<IntArrayList> paths;
        private final IntStack stack;
        private final IntArrayDeque queue;
        private final IntDoubleMap delta;
        private final IntIntMap sigma;
        private final int[] distance;

        private BCTask() {
            this.paths = new IntObjectScatterMap<>(expectedNodeCount);
            this.stack = new IntStack();
            this.queue = new IntArrayDeque();
            this.sigma = new IntIntScatterMap(expectedNodeCount);
            this.delta = new IntDoubleScatterMap(expectedNodeCount);

            this.distance = new int[nodeCount];
        }

        @Override
        public void run() {
            final double f = (nodeCount * divisor) / selectionStrategy.size();
            for (;;) {
                final int startNodeId = nodeQueue.getAndIncrement();
                if (startNodeId >= nodeCount || !running()) {
                    return;
                }
                if (!selectionStrategy.select(startNodeId)) {
                    continue;
                }
                getProgressLogger().logProgress((double) startNodeId / (nodeCount - 1));
                Arrays.fill(distance, -1);
                sigma.clear();
                paths.clear();
                delta.clear();
                sigma.put(startNodeId, 1);
                distance[startNodeId] = 0;
                queue.addLast(startNodeId);
                queue.addLast(0);
                while (!queue.isEmpty()) {
                    int node = queue.removeFirst();
                    int nodeDepth = queue.removeFirst();
                    if (nodeDepth - 1 > maxDepth) {
                        continue;
                    }
                    stack.push(node);
                    graph.forEachRelationship(node, direction, (source, target, relationId) -> {
                        if (distance[target] < 0) {
                            queue.addLast(target);
                            queue.addLast(nodeDepth + 1);
                            distance[target] = distance[node] + 1;
                        }

                        if (distance[target] == distance[node] + 1) {
                            sigma.addTo(target, sigma.getOrDefault(node, 0));
                            append(target, node);
                        }
                        return true;
                    });
                }

                while (!stack.isEmpty()) {
                    int node = stack.pop();
                    final IntArrayList intCursors = paths.get(node);
                    if (null != intCursors) {
                        intCursors.forEach((Consumer<? super IntCursor>) c -> {
                            delta.addTo(c.value,
                                    (double) sigma.getOrDefault(c.value, 0) /
                                            (double) sigma.getOrDefault(node, 0) *
                                            (delta.getOrDefault(node, 0) + 1.0));
                        });
                    }
                    if (node != startNodeId) {
                        centrality.add(node, f * (delta.getOrDefault(node, 0)));
                    }
                }
            }
        }

        private void append(int target, int node) {
            IntArrayList intCursors = paths.get(target);
            if (null == intCursors) {
                intCursors = new IntArrayList();
                paths.put(target, intCursors);
            }
            intCursors.add(node);
        }
    }
}
