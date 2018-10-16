package org.neo4j.graphalgo.impl.pagerank;

import org.neo4j.graphalgo.api.*;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;

import java.util.concurrent.ExecutorService;


public interface ComputeStepFactory {
    ComputeStep createComputeStep(double dampingFactor, int[] sourceNodeIds, RelationshipIterator relationshipIterator, Degrees degrees, RelationshipWeights relationshipWeights, int partitionCount, int start, double[] aggregatedDegrees);

    HugeComputeStep createHugeComputeStep(double dampingFactor, long[] sourceNodeIds, HugeRelationshipIterator relationshipIterator, HugeDegrees degrees, HugeRelationshipWeights relationshipWeights, AllocationTracker tracker, int partitionCount, long start, double[] aggregatedDegrees);

    double[] degreeCentrality(Graph graph, ExecutorService executor, int concurrency);
}


