package org.neo4j.graphalgo.impl.pagerank;

import org.neo4j.graphalgo.api.HugeDegrees;
import org.neo4j.graphalgo.api.HugeRelationshipIterator;
import org.neo4j.graphalgo.api.HugeRelationshipWeights;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphdb.Direction;

import static org.neo4j.graphalgo.core.utils.ArrayUtil.binaryLookup;

public class HugeNonWeightedComputeStep extends HugeBaseComputeStep {
    HugeNonWeightedComputeStep(
            double dampingFactor,
            long[] sourceNodeIds,
            HugeRelationshipIterator relationshipIterator,
            HugeDegrees degrees,
            AllocationTracker tracker,
            int partitionSize,
            long startNode) {
        super(dampingFactor,
                sourceNodeIds,
                relationshipIterator,
                degrees,
                tracker,
                partitionSize,
                startNode);
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
                    int srcRankDelta = (int) (100_000 * (delta / degree));
                    rels.forEachRelationship(nodeId, Direction.OUTGOING, (sourceNodeId, targetNodeId) -> {
                        if (srcRankDelta != 0) {
                            int idx = binaryLookup(targetNodeId, starts);
                            nextScores[idx][(int) (targetNodeId - starts[idx])] += srcRankDelta;
                        }
                        return true;
                    });
                }
            }
        }
    }
}
