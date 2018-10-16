package org.neo4j.graphalgo.impl.pagerank;

import org.neo4j.graphalgo.api.*;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.impl.WeightedDegreeCentrality;
import org.neo4j.graphdb.Direction;

import java.util.concurrent.ExecutorService;

public class WeightedComputeStepFactory implements ComputeStepFactory {
    public ComputeStep createComputeStep(double dampingFactor, int[] sourceNodeIds, RelationshipIterator relationshipIterator, Degrees degrees, RelationshipWeights relationshipWeights, int partitionCount, int start, double[] aggregatedDegrees) {
        return new WeightedComputeStep(
                dampingFactor,
                sourceNodeIds,
                relationshipIterator,
                degrees,
                relationshipWeights,
                partitionCount,
                start,
                aggregatedDegrees
        );
    }

    @Override
    public HugeComputeStep createHugeComputeStep(double dampingFactor, long[] sourceNodeIds, HugeRelationshipIterator relationshipIterator, HugeDegrees degrees, HugeRelationshipWeights relationshipWeights, AllocationTracker tracker, int partitionCount, long start, double[] aggregatedDegrees) {
        return new HugeWeightedComputeStep(
                dampingFactor,
                sourceNodeIds,
                relationshipIterator,
                degrees,
                relationshipWeights,
                tracker,
                partitionCount,
                start,
                aggregatedDegrees
        );
    }

    @Override
    public double[] degreeCentrality(Graph graph, ExecutorService executor, int concurrency) {
        WeightedDegreeCentrality degreeCentrality = new WeightedDegreeCentrality(graph, executor, concurrency, Direction.OUTGOING);
        degreeCentrality.compute();
        return degreeCentrality.degrees();
    }
}
