package org.neo4j.graphalgo.impl.pagerank;

import org.neo4j.graphalgo.api.HugeDegrees;
import org.neo4j.graphalgo.api.HugeRelationshipIterator;
import org.neo4j.graphalgo.api.HugeRelationshipWeights;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphdb.Direction;

import static org.neo4j.graphalgo.core.utils.ArrayUtil.binaryLookup;

public class HugeWeightedComputeStep extends HugeBaseComputeStep {
    private final HugeRelationshipWeights relationshipWeights;
    private final double[] aggregatedDegrees;

    HugeWeightedComputeStep(
            double dampingFactor,
            long[] sourceNodeIds,
            HugeRelationshipIterator relationshipIterator,
            HugeDegrees degrees,
            HugeRelationshipWeights relationshipWeights,
            AllocationTracker tracker,
            int partitionSize,
            long startNode, double[] aggregatedDegrees) {
        super(dampingFactor,
                sourceNodeIds,
                relationshipIterator,
                degrees,
                tracker,
                partitionSize,
                startNode);
        this.relationshipWeights = relationshipWeights;
        this.aggregatedDegrees = aggregatedDegrees;
    }

    void singleIteration() {
        long startNode = this.startNode;
        long endNode = this.endNode;
        HugeRelationshipIterator rels = this.relationshipIterator;
        for (long nodeId = startNode; nodeId < endNode; ++nodeId) {
            double delta = deltas[(int) (nodeId - startNode)];
            if (delta > 0) {
                int degree = degrees.degree(nodeId, Direction.OUTGOING);
                if (degree > 0) {
                    double sumOfWeights = aggregatedDegrees[(int) nodeId];

                    rels.forEachRelationship(nodeId, Direction.OUTGOING, (sourceNodeId, targetNodeId) -> {
                        double weight = relationshipWeights.weightOf(sourceNodeId, targetNodeId);

                        if (weight > 0) {
                            double proportion = weight / sumOfWeights;
                            int srcRankDelta = (int) (100_000 * (delta * proportion));
                            if (srcRankDelta != 0) {
                                int idx = binaryLookup(targetNodeId, starts);
                                nextScores[idx][(int) (targetNodeId - starts[idx])] += srcRankDelta;
                            }
                        }

                        return true;
                    });
                }
            }
        }
    }
}
