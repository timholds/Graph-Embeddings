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
package org.neo4j.graphalgo.impl.pagerank;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.LongArrayList;
import org.neo4j.collection.primitive.PrimitiveLongIterator;
import org.neo4j.graphalgo.api.*;
import org.neo4j.graphalgo.core.utils.ParallelUtil;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.write.Exporter;
import org.neo4j.graphalgo.core.write.PropertyTranslator;
import org.neo4j.graphalgo.core.write.Translators;
import org.neo4j.graphalgo.impl.Algorithm;
import org.neo4j.graphalgo.impl.WeightedDegreeCentrality;
import org.neo4j.graphdb.Direction;
import org.neo4j.logging.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.LongStream;

import static org.neo4j.graphalgo.core.utils.ArrayUtil.binaryLookup;
import static org.neo4j.graphalgo.core.utils.paged.AllocationTracker.humanReadable;
import static org.neo4j.graphalgo.core.utils.paged.MemoryUsage.*;


/**
 * Partition based parallel PageRank based on
 * "An Efficient Partition-Based Parallel PageRank Algorithm" [1]
 * <p>
 * Each partition thread has its local array of only the nodes that it is responsible for,
 * not for all nodes. Combined, all partitions hold all page rank scores for every node once.
 * Instead of writing partition files and transferring them across the network
 * (as done in the paper since they were concerned with parallelising across multiple nodes),
 * we use integer arrays to write the results to.
 * The actual score is upscaled from a double to an integer by multiplying it with {@code 100_000}.
 * <p>
 * To avoid contention by writing to a shared array, we partition the result array.
 * During execution, the scores arrays
 * are shaped like this:
 * <pre>
 *     [ executing partition ] -> [ calculated partition ] -> [ local page rank scores ]
 * </pre>
 * Each single partition writes in a partitioned array, calculation the scores
 * for every receiving partition. A single partition only sees:
 * <pre>
 *     [ calculated partition ] -> [ local page rank scores ]
 * </pre>
 * The coordinating thread then builds the transpose of all written partitions from every partition:
 * <pre>
 *     [ calculated partition ] -> [ executing partition ] -> [ local page rank scores ]
 * </pre>
 * This step does not happen in parallel, but does not involve extensive copying.
 * The local page rank scores needn't be copied, only the partitioning arrays.
 * All in all, {@code concurrency^2} array element reads and assignments have to
 * be performed.
 * <p>
 * For the next iteration, every partition first updates its scores, in parallel.
 * A single partition now sees:
 * <pre>
 *     [ executing partition ] -> [ local page rank scores ]
 * </pre>
 * That is, a list of all calculated scores for it self, grouped by the partition that
 * calculated these scores.
 * This means, most of the synchronization happens in parallel, too.
 * <p>
 * Partitioning is not done by number of nodes but by the accumulated degree –
 * as described in "Fast Parallel PageRank: A Linear System Approach" [2].
 * Every partition should have about the same number of relationships to operate on.
 * This is done to avoid having one partition with super nodes and instead have
 * all partitions run in approximately equal time.
 * Smaller partitions are merged down until we have at most {@code concurrency} partitions,
 * in order to batch partitions and keep the number of threads in use predictable/configurable.
 * <p>
 * [1]: <a href="http://delab.csd.auth.gr/~dimitris/courses/ir_spring06/page_rank_computing/01531136.pdf">An Efficient Partition-Based Parallel PageRank Algorithm</a><br>
 * [2]: <a href="https://www.cs.purdue.edu/homes/dgleich/publications/gleich2004-parallel.pdf">Fast Parallel PageRank: A Linear System Approach</a>
 */
public class HugePageRank extends Algorithm<HugePageRank> implements PageRankAlgorithm {

    private final ExecutorService executor;
    private final int concurrency;
    private final int batchSize;
    private final AllocationTracker tracker;
    private final HugeIdMapping idMapping;
    private final HugeNodeIterator nodeIterator;
    private final HugeRelationshipIterator relationshipIterator;
    private final HugeDegrees degrees;
    private final double dampingFactor;
    private final HugeGraph graph;
    private final HugeRelationshipWeights relationshipWeights;
    private LongStream sourceNodeIds;
    private ComputeStepFactory computeStepFactory;

    private Log log;
    private ComputeSteps computeSteps;

    /**
     * Forces sequential use. If you want parallelism, prefer
     * {@link #HugePageRank(ExecutorService, int, int, AllocationTracker, HugeIdMapping, HugeNodeIterator, HugeRelationshipIterator, HugeDegrees, double)}
     */
    HugePageRank(
            AllocationTracker tracker,
            HugeGraph graph,
            double dampingFactor,
            LongStream sourceNodeIds,
            ComputeStepFactory computeStepFactory) {
        this(
                null,
                -1,
                ParallelUtil.DEFAULT_BATCH_SIZE,
                tracker,
                graph,
                dampingFactor,
                sourceNodeIds,
                computeStepFactory);
    }

    /**
     * Parallel Page Rank implementation.
     * Whether the algorithm actually runs in parallel depends on the given
     * executor and batchSize.
     */
    HugePageRank(
            ExecutorService executor,
            int concurrency,
            int batchSize,
            AllocationTracker tracker,
            HugeGraph graph,
            double dampingFactor,
            LongStream sourceNodeIds,
            ComputeStepFactory computeStepFactory) {
        this.executor = executor;
        this.concurrency = concurrency;
        this.batchSize = batchSize;
        this.tracker = tracker;
        this.idMapping = graph;
        this.nodeIterator = graph;
        this.relationshipIterator = graph;
        this.degrees = graph;
        this.graph = graph;
        this.relationshipWeights = graph;
        this.dampingFactor = dampingFactor;
        this.sourceNodeIds = sourceNodeIds;
        this.computeStepFactory = computeStepFactory;
    }

    /**
     * compute pageRank for n iterations
     */
    @Override
    public HugePageRank compute(int iterations) {
        assert iterations >= 1;
        initializeSteps();
        computeSteps.run(iterations);
        return this;
    }

    @Override
    public PageRankResult result() {
        return computeSteps.getPageRank();
    }

    @Override
    public Algorithm<?> algorithm() {
        return this;
    }

    @Override
    public HugePageRank withLog(final Log log) {
        super.withLog(log);
        this.log = log;
        return this;
    }

    // we cannot do this in the constructor anymore since
    // we want to allow the user to provide a log instance
    private void initializeSteps() {
        if (computeSteps != null) {
            return;
        }
        List<Partition> partitions = partitionGraph(
                adjustBatchSize(batchSize),
                nodeIterator,
                degrees);
        ExecutorService executor = ParallelUtil.canRunInParallel(this.executor)
                ? this.executor : null;

        WeightedDegreeCentrality degreeCentrality = new WeightedDegreeCentrality(graph, executor, concurrency, Direction.OUTGOING);
        degreeCentrality.compute();

        computeSteps = createComputeSteps(
                concurrency,
                idMapping.nodeCount(),
                dampingFactor,
                sourceNodeIds.map(graph::toHugeMappedNodeId).filter(mappedId -> mappedId != -1L).toArray(),
                relationshipIterator,
                degrees,
                partitions,
                executor);
    }

    private int adjustBatchSize(int batchSize) {
        // multiply batchsize by 8 as a very rough estimate of an average
        // degree of 8 for nodes, so that every partition has approx
        // batchSize nodes.
        batchSize <<= 3;
        return batchSize > 0 ? batchSize : Integer.MAX_VALUE;
    }

    private List<Partition> partitionGraph(
            int batchSize,
            HugeNodeIterator nodeIterator,
            HugeDegrees degrees) {
        PrimitiveLongIterator nodes = nodeIterator.hugeNodeIterator();
        List<Partition> partitions = new ArrayList<>();
        long start = 0L;
        while (nodes.hasNext()) {
            Partition partition = new Partition(
                    nodes,
                    degrees,
                    start,
                    (long) batchSize);
            partitions.add(partition);
            start += ((long) partition.nodeCount);
        }
        return partitions;
    }

    private ComputeSteps createComputeSteps(
            int concurrency,
            long nodeCount,
            double dampingFactor,
            long[] sourceNodeIds,
            HugeRelationshipIterator relationshipIterator,
            HugeDegrees degrees,
            List<Partition> partitions,
            ExecutorService pool) {
        concurrency = findIdealConcurrency(nodeCount, partitions, concurrency, log);
        final int expectedParallelism = Math.min(
                concurrency,
                partitions.size());

        List<HugeComputeStep> computeSteps = new ArrayList<>(expectedParallelism);
        LongArrayList starts = new LongArrayList(expectedParallelism);
        IntArrayList lengths = new IntArrayList(expectedParallelism);
        int partitionsPerThread = ParallelUtil.threadSize(
                concurrency + 1,
                partitions.size());
        Iterator<Partition> parts = partitions.iterator();

        WeightedDegreeCentrality degreeCentrality = new WeightedDegreeCentrality(graph, executor, concurrency, Direction.OUTGOING);
        degreeCentrality.compute();

        while (parts.hasNext()) {
            Partition partition = parts.next();
            int partitionCount = partition.nodeCount;
            long start = partition.startNode;
            int i = 1;
            while (parts.hasNext()
                    && i < partitionsPerThread
                    && partition.fits(partitionCount)) {
                partition = parts.next();
                partitionCount += partition.nodeCount;
                ++i;
            }

            starts.add(start);
            lengths.add(partitionCount);

            computeSteps.add(computeStepFactory.createHugeComputeStep(dampingFactor,
                    sourceNodeIds,
                    relationshipIterator,
                    degrees,
                    relationshipWeights,
                    tracker,
                    partitionCount,
                    start,
                    degreeCentrality.degrees()));
        }

        long[] startArray = starts.toArray();
        int[] lengthArray = lengths.toArray();
        for (HugeComputeStep computeStep : computeSteps) {
            computeStep.setStarts(startArray, lengthArray);
        }
        return new ComputeSteps(tracker, computeSteps, concurrency, pool);
    }

    private static int findIdealConcurrency(
            long nodeCount,
            List<Partition> partitions,
            int concurrency,
            Log log) {
        if (concurrency <= 0) {
            concurrency = partitions.size();
        }

        if (log != null && log.isDebugEnabled()) {
            log.debug(
                    "PageRank: nodes=%d, concurrency=%d, available memory=%s, estimated memory usage: %s",
                    nodeCount,
                    concurrency,
                    humanReadable(availableMemory()),
                    humanReadable(memoryUsageFor(concurrency, partitions))
            );
        }

        int maxConcurrency = maxConcurrencyByMemory(
                nodeCount,
                concurrency,
                availableMemory(),
                partitions);
        if (concurrency > maxConcurrency) {
            if (log != null) {
                long required = memoryUsageFor(concurrency, partitions);
                long newRequired = memoryUsageFor(maxConcurrency, partitions);
                long available = availableMemory();
                log.warn("Requested concurrency of %d would require %s Heap but only %s are available, PageRank will be throttled to a concurrency of %d to use only %s Heap.",
                        concurrency,
                        humanReadable(required),
                        humanReadable(available),
                        maxConcurrency,
                        humanReadable(newRequired)
                );
            }
            concurrency = maxConcurrency;
        }
        return concurrency;
    }

    private static int maxConcurrencyByMemory(
            long nodeCount,
            int concurrency,
            long availableBytes,
            List<Partition> partitions) {
        int newConcurrency = concurrency;

        long memoryUsage = memoryUsageFor(newConcurrency, partitions);
        while (memoryUsage > availableBytes) {
            long perThread = estimateMemoryUsagePerThread(nodeCount, concurrency);
            long overflow = memoryUsage - availableBytes;
            newConcurrency -= (int) Math.ceil((double) overflow / (double) perThread);

            memoryUsage = memoryUsageFor(newConcurrency, partitions);
        }
        return newConcurrency;
    }

    private static long availableMemory() {
        // TODO: run gc first to free up memory?
        Runtime rt = Runtime.getRuntime();

        long max = rt.maxMemory(); // max allocated
        long total = rt.totalMemory(); // currently allocated
        long free = rt.freeMemory(); // unused portion of currently allocated

        return max - total + free;
    }

    private static long estimateMemoryUsagePerThread(long nodeCount, int concurrency) {
        int nodesPerThread = (int) Math.ceil((double) nodeCount / (double) concurrency);
        long partitions = sizeOfIntArray(nodesPerThread) * (long) concurrency;
        return shallowSizeOfInstance(HugeBaseComputeStep.class) + partitions;
    }

    private static long memoryUsageFor(
            int concurrency,
            List<Partition> partitions) {
        long perThreadUsage = 0L;
        long sharedUsage = 0L;
        int stepSize = 0;
        int partitionsPerThread = ParallelUtil.threadSize(concurrency + 1, partitions.size());
        Iterator<Partition> parts = partitions.iterator();

        while (parts.hasNext()) {
            Partition partition = parts.next();
            int partitionCount = partition.nodeCount;
            int i = 1;
            while (parts.hasNext()
                    && i < partitionsPerThread
                    && partition.fits(partitionCount)) {
                partition = parts.next();
                partitionCount += partition.nodeCount;
                ++i;
            }
            stepSize++;
            sharedUsage += (sizeOfDoubleArray(partitionCount) << 1);
            perThreadUsage += sizeOfIntArray(partitionCount);
        }

        perThreadUsage *= stepSize;
        perThreadUsage += shallowSizeOfInstance(HugeBaseComputeStep.class);
        perThreadUsage += sizeOfObjectArray(stepSize);

        sharedUsage += shallowSizeOfInstance(ComputeSteps.class);
        sharedUsage += sizeOfLongArray(stepSize) << 1;

        return sharedUsage + perThreadUsage;
    }

    @Override
    public HugePageRank me() {
        return this;
    }

    @Override
    public HugePageRank release() {
        computeSteps.release();
        return this;
    }

    private static final class Partition {

        // rough estimate of what capacity would still yield acceptable performance
        // per thread
        private static final int MAX_NODE_COUNT = (Integer.MAX_VALUE - 32) >> 1;

        private final long startNode;
        private final int nodeCount;

        Partition(
                PrimitiveLongIterator nodes,
                HugeDegrees degrees,
                long startNode,
                long batchSize) {
            assert batchSize > 0L;
            int nodeCount = 0;
            long partitionSize = 0L;
            while (nodes.hasNext() && partitionSize < batchSize && nodeCount < MAX_NODE_COUNT) {
                long nodeId = nodes.next();
                ++nodeCount;
                partitionSize += ((long) degrees.degree(nodeId, Direction.OUTGOING));
            }
            this.startNode = startNode;
            this.nodeCount = nodeCount;
        }

        private boolean fits(int otherPartitionsCount) {
            return MAX_NODE_COUNT - otherPartitionsCount >= nodeCount;
        }
    }

    private final class ComputeSteps {
        private List<HugeComputeStep> steps;
        private final ExecutorService pool;
        private int[][][] scores;
        private final int concurrency;

        private ComputeSteps(
                AllocationTracker tracker,
                List<HugeComputeStep> steps,
                int concurrency,
                ExecutorService pool) {
            this.concurrency = concurrency;
            assert !steps.isEmpty();
            this.steps = steps;
            this.pool = pool;
            int stepSize = steps.size();
            scores = new int[stepSize][stepSize][];
            if (AllocationTracker.isTracking(tracker)) {
                tracker.add((stepSize + 1) * sizeOfObjectArray(stepSize));
            }
        }

        PageRankResult getPageRank() {
            HugeComputeStep firstStep = steps.get(0);
            if (steps.size() > 1) {
                double[][] results = new double[steps.size()][];
                int i = 0;
                for (HugeComputeStep step : steps) {
                    results[i++] = step.pageRank();
                }
                return new PartitionedDoubleArrayResult(results, firstStep.starts());
            } else {
                return new DoubleArrayResult(firstStep.pageRank());
            }
        }

        private void run(int iterations) {
            final int operations = (iterations << 1) + 1;
            int op = 0;
            ParallelUtil.runWithConcurrency(concurrency, steps, pool);
            getProgressLogger().logProgress(++op, operations, tracker);
            for (int i = 0; i < iterations && running(); i++) {
                // calculate scores
                ParallelUtil.runWithConcurrency(concurrency, steps, pool);
                getProgressLogger().logProgress(++op, operations, tracker);
                synchronizeScores();
                // sync scores
                ParallelUtil.runWithConcurrency(concurrency, steps, pool);
                getProgressLogger().logProgress(++op, operations, tracker);
            }
        }

        private void synchronizeScores() {
            int stepSize = steps.size();
            int[][][] scores = this.scores;
            int i;
            for (i = 0; i < stepSize; i++) {
                synchronizeScores(steps.get(i), i, scores);
            }
        }

        private void synchronizeScores(
                HugeComputeStep step,
                int idx,
                int[][][] scores) {
            step.prepareNextIteration(scores[idx]);
            int[][] nextScores = step.nextScores();
            for (int j = 0, len = nextScores.length; j < len; j++) {
                scores[j][idx] = nextScores[j];
            }
        }

        private void release() {
             if (AllocationTracker.isTracking(tracker)) {
                tracker.remove((scores.length + 1) * sizeOfObjectArray(scores.length));
            }
            steps.clear();
            steps = null;
            scores = null;
        }
    }

    private static final class PartitionedDoubleArrayResult implements PageRankResult, PropertyTranslator.OfDouble<double[][]> {
        private final double[][] partitions;
        private final long[] starts;

        private PartitionedDoubleArrayResult(
                double[][] partitions,
                long[] starts) {
            this.partitions = partitions;
            this.starts = starts;
        }

        @Override
        public void export(final String propertyName, final Exporter exporter) {
            exporter.write(propertyName, partitions, this);
        }

        @Override
        public double toDouble(final double[][] data, final long nodeId) {
            int idx = binaryLookup(nodeId, starts);
            return data[idx][(int) (nodeId - starts[idx])];
        }

        @Override
        public double score(final long nodeId) {
            return toDouble(partitions, nodeId);
        }

        @Override
        public double score(final int nodeId) {
            return score((long) nodeId);
        }
    }

    private static final class DoubleArrayResult implements PageRankResult {
        private final double[] result;

        private DoubleArrayResult(double[] result) {
            this.result = result;
        }

        @Override
        public void export(
                final String propertyName, final Exporter exporter) {
            exporter.write(
                    propertyName,
                    result,
                    Translators.DOUBLE_ARRAY_TRANSLATOR);
        }

        @Override
        public final double score(final long nodeId) {
            return result[(int) nodeId];
        }

        @Override
        public double score(final int nodeId) {
            return result[nodeId];
        }
    }
}
