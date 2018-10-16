package org.neo4j.graphalgo.impl.pagerank;

import org.neo4j.graphalgo.api.*;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;

import java.util.concurrent.ExecutorService;

public class NonWeightedComputeStepFactory implements ComputeStepFactory {
    public ComputeStep createComputeStep(double dampingFactor, int[] sourceNodeIds, RelationshipIterator relationshipIterator, Degrees degrees, RelationshipWeights relationshipWeights, int partitionCount, int start, double[] aggregatedDegrees) {
        return new NonWeightedComputeStep(
                dampingFactor,
                sourceNodeIds,
                relationshipIterator,
                degrees,
                relationshipWeights,
                partitionCount,
                start
        );
    }

    @Override
    public HugeNonWeightedComputeStep createHugeComputeStep(double dampingFactor, long[] sourceNodeIds, HugeRelationshipIterator relationshipIterator, HugeDegrees degrees, HugeRelationshipWeights relationshipWeights, AllocationTracker tracker, int partitionCount, long start, double[] aggregatedDegrees) {
        return new HugeNonWeightedComputeStep(
                dampingFactor,
                sourceNodeIds,
                relationshipIterator,
                degrees,
                tracker,
                partitionCount,
                start
        );
    }

    @Override
    public double[] degreeCentrality(Graph graph, ExecutorService executor, int concurrency) {
        return new double[]{};
    }
}
