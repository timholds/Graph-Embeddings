package org.neo4j.graphalgo.impl.pagerank;

import org.neo4j.graphalgo.api.HugeDegrees;
import org.neo4j.graphalgo.api.HugeRelationshipIterator;
import org.neo4j.graphalgo.api.HugeRelationshipWeights;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphdb.Direction;

import java.util.Arrays;
import java.util.stream.LongStream;

import static org.neo4j.graphalgo.core.utils.ArrayUtil.binaryLookup;
import static org.neo4j.graphalgo.core.utils.paged.MemoryUsage.sizeOfDoubleArray;
import static org.neo4j.graphalgo.core.utils.paged.MemoryUsage.sizeOfIntArray;

public abstract class HugeBaseComputeStep implements HugeComputeStep {
    private static final int S_INIT = 0;
    private static final int S_CALC = 1;
    private static final int S_SYNC = 2;

    private int state;

    long[] starts;
    private int[] lengths;
    private long[] sourceNodeIds;
    final HugeRelationshipIterator relationshipIterator;
    final HugeDegrees degrees;
    private final AllocationTracker tracker;

    private final double alpha;
    final double dampingFactor;

    double[] pageRank;
    double[] deltas;
    int[][] nextScores;
    int[][] prevScores;

    final long startNode;
    final long endNode;
    private final int partitionSize;

    HugeBaseComputeStep(
            double dampingFactor,
            long[] sourceNodeIds,
            HugeRelationshipIterator relationshipIterator,
            HugeDegrees degrees,
            AllocationTracker tracker,
            int partitionSize,
            long startNode) {
        this.dampingFactor = dampingFactor;
        this.alpha = 1.0 - dampingFactor;
        this.sourceNodeIds = sourceNodeIds;
        this.relationshipIterator = relationshipIterator.concurrentCopy();
        this.degrees = degrees;
        this.tracker = tracker;
        this.partitionSize = partitionSize;
        this.startNode = startNode;
        this.endNode = startNode + (long) partitionSize;
        state = S_INIT;
    }

    public void setStarts(long[] starts, int[] lengths) {
        this.starts = starts;
        this.lengths = lengths;
    }

    @Override
    public void run() {
        if (state == S_CALC) {
            singleIteration();
            state = S_SYNC;
        } else if (state == S_SYNC) {
            combineScores();
            state = S_CALC;
        } else if (state == S_INIT) {
            initialize();
            state = S_CALC;
        }
    }

    private void initialize() {
        this.nextScores = new int[starts.length][];
        Arrays.setAll(nextScores, i -> {
            int size = lengths[i];
            tracker.add(sizeOfIntArray(size));
            return new int[size];
        });

        tracker.add(sizeOfDoubleArray(partitionSize) << 1);

        double[] partitionRank = new double[partitionSize];
        if(sourceNodeIds.length == 0) {
            Arrays.fill(partitionRank, alpha);
        } else {
            Arrays.fill(partitionRank,0);

            long[] partitionSourceNodeIds = LongStream.of(sourceNodeIds)
                    .filter(sourceNodeId -> sourceNodeId >= startNode && sourceNodeId <= endNode)
                    .toArray();

            for (long sourceNodeId : partitionSourceNodeIds) {
                partitionRank[Math.toIntExact(sourceNodeId - this.startNode)] = alpha;
            }
        }

        this.pageRank = partitionRank;
        this.deltas = Arrays.copyOf(partitionRank, partitionSize);
    }

    abstract void singleIteration();

    public void prepareNextIteration(int[][] prevScores) {
        this.prevScores = prevScores;
    }

    private void combineScores() {
        assert prevScores != null;
        assert prevScores.length >= 1;

        int scoreDim = prevScores.length;
        int[][] prevScores = this.prevScores;

        int length = prevScores[0].length;
        for (int i = 0; i < length; i++) {
            int sum = 0;
            for (int j = 0; j < scoreDim; j++) {
                int[] scores = prevScores[j];
                sum += scores[i];
                scores[i] = 0;
            }
            double delta = dampingFactor * (sum / 100_000.0);
            pageRank[i] += delta;
            deltas[i] = delta;
        }
    }

    public int[][] nextScores() {
        return nextScores;
    }

    public double[] pageRank() {
        return pageRank;
    }

    public long[] starts() {
        return starts;
    }
}
