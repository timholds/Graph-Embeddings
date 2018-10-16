package org.neo4j.graphalgo.impl;

import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.utils.ParallelUtil;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.ProgressLogger;
import org.neo4j.graphalgo.impl.betweenness.BetweennessCentrality;
import org.neo4j.graphdb.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class DegreeCentrality extends Algorithm<DegreeCentrality> {
    private final int nodeCount;
    private Direction direction;
    private Graph graph;
    private final ExecutorService executor;
    private final int concurrency;
    private volatile AtomicInteger nodeQueue = new AtomicInteger();
    private double[] degrees;

    public DegreeCentrality(
            Graph graph,
            ExecutorService executor,
            int concurrency,
            Direction direction
    ) {
        if (concurrency <= 0) {
            concurrency = Pools.DEFAULT_QUEUE_SIZE;
        }

        this.graph = graph;
        this.executor = executor;
        this.concurrency = concurrency;
        nodeCount = Math.toIntExact(graph.nodeCount());
        this.direction = direction;
        degrees = new double[nodeCount];
    }

    public DegreeCentrality compute() {
        nodeQueue.set(0);

        List<DegreeTask> tasks = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
            tasks.add(new DegreeTask());
        }
        ParallelUtil.runWithConcurrency(concurrency, tasks, executor);

        return this;
    }

    @Override
    public DegreeCentrality me() {
        return this;
    }

    @Override
    public DegreeCentrality release() {
        graph = null;
        return null;
    }

    private class DegreeTask implements Runnable {
        @Override
        public void run() {
            for (; ; ) {
                final int nodeId = nodeQueue.getAndIncrement();
                if (nodeId >= nodeCount || !running()) {
                    return;
                }

                int degree = graph.degree(nodeId, direction);
                degrees[nodeId] = degree;

            }
        }
    }

    public double[] degrees() {
        return degrees;
    }

    public Stream<Result> resultStream() {
        return IntStream.range(0, nodeCount)
                .mapToObj(nodeId ->
                        new Result(graph.toOriginalNodeId(nodeId), degrees[nodeId]));
    }

    /**
     * Result class used for streaming
     */
    public static final class Result {

        public final long nodeId;
        public final double centrality;

        public Result(long nodeId, double centrality) {
            this.nodeId = nodeId;
            this.centrality = centrality;
        }

        @Override
        public String toString() {
            return "Result{" +
                    "nodeId=" + nodeId +
                    ", centrality=" + centrality +
                    '}';
        }
    }

}
