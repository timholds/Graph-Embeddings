package org.neo4j.graphalgo.impl.pagerank;

import org.neo4j.graphalgo.api.*;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.impl.DegreeCentrality;
import org.neo4j.graphalgo.impl.WeightedDegreeCentrality;
import org.neo4j.graphdb.Direction;

import java.util.concurrent.ExecutorService;

public class ArticleRankComputeStepFactory implements ComputeStepFactory {
    public ComputeStep createComputeStep(double dampingFactor, int[] sourceNodeIds, RelationshipIterator relationshipIterator, Degrees degrees, RelationshipWeights relationshipWeights, int partitionCount, int start, double[] aggregatedDegrees) {
        return new ArticleRankComputeStep(
                dampingFactor,
                sourceNodeIds,
                relationshipIterator,
                degrees,
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
        DegreeCentrality degreeCentrality = new DegreeCentrality(graph, executor, concurrency, Direction.OUTGOING);
        degreeCentrality.compute();
        return degreeCentrality.degrees();
    }
}
