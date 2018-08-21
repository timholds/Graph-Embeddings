package algo.algo.pagerank;

import algo.algo.algorithms.AlgoUtils;
import algo.algo.algorithms.Algorithm;
import algo.algo.algorithms.AlgorithmInterface;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicIntegerArray;
import static algo.algo.pagerank.PageRankUtils.toFloat;
import static algo.algo.pagerank.PageRankUtils.toInt;
import static algo.algo.pagerank.NodeCounter.getSourceYearPublished;
import static algo.algo.pagerank.NodeCounter.getTargetYearPublished;

public class PageRankArrayStorageParallelCypher implements PageRank, AlgorithmInterface
{
    public static final int ONE_MINUS_ALPHA_INT = toInt( ONE_MINUS_ALPHA );
    public static final int WRITE_BATCH=100_100;
    public static final int INITIAL_ARRAY_SIZE=100_000;
    public final int BATCH_SIZE = 100_000 ;
    private final GraphDatabaseAPI db;
    private final Log log;
    private final ExecutorService pool;
    private int nodeCount;
    private int relCount;

    private PageRankStatistics stats = new PageRankStatistics();

    // Output arrays.
    int [] previousPageRanks;
    private AtomicIntegerArray pageRanksAtomic;

    private Algorithm algorithm;
    private String property;

    public PageRankArrayStorageParallelCypher(
            GraphDatabaseAPI db,
            ExecutorService pool, Log log) {
        this.pool = pool;
        this.db = db;
        this.log = log;
        algorithm = new Algorithm(db, pool, log);
    }

    @Override
    public long getMappedNode(int algoId) {
        return algorithm.getMappedNode(algoId);
    }

    private int getNodeIndex(int node) {
        return algorithm.getAlgoNodeId(node);
    }

    public boolean readNodeAndRelCypherData(String relCypher, String nodeCypher, Number weight, Number batchSize, int concurrency) {
        boolean success = algorithm.readNodeAndRelCypher(relCypher, nodeCypher, weight,batchSize,concurrency);
        this.nodeCount = algorithm.getNodeCount();
        this.relCount = algorithm.relCount;
        stats.readNodeMillis = algorithm.readNodeMillis;
        stats.readRelationshipMillis = algorithm.readRelationshipMillis;
        stats.nodes = nodeCount;
        stats.relationships = relCount;
        return success;
    }

    // TODO figure out where compute is called and pass it avgCitations
    public void compute(int iterations,
                        int[] sourceDegreeData,
                        int[] sourceChunkStartingIndex,
                        int[] relationshipTarget,
                        int[] relationshipWeight
                        double avgCitations) {
        previousPageRanks = new int[nodeCount];
        pageRanksAtomic = new AtomicIntegerArray(nodeCount);

        stats.iterations = iterations;
        long before = System.currentTimeMillis();

        for (int iteration = 0; iteration < iterations; iteration++) {
            long beforeIteration = System.currentTimeMillis();
            startIteration(sourceChunkStartingIndex, sourceDegreeData, relationshipWeight, avgCitations);
            iterateParallel(iteration, sourceDegreeData, sourceChunkStartingIndex, relationshipTarget, relationshipWeight, avgCitations);
            long afterIteration = System.currentTimeMillis();
            log.info("Time for iteration " + iteration + "  " + (afterIteration - beforeIteration) + " millis");
        }
        long after = System.currentTimeMillis();
        stats.computeMillis = (after - before);

    }

    @Override
    public void compute(int iterations, RelationshipType... relationshipTypes) {
        compute(iterations,
                algorithm.sourceDegreeData,
                algorithm.sourceChunkStartingIndex,
                algorithm.relationshipTarget,
                algorithm.relationshipWeight
                algorithm.avgCitations);
    }

    private int getEndNode(int node, int [] sourceChunkStartingIndex) {
        int endNode = node;
        while(endNode < nodeCount &&
                (sourceChunkStartingIndex[endNode] - sourceChunkStartingIndex[node] <= BATCH_SIZE)) {
            endNode++;
        }
        return endNode;
    }

    private void iterateParallel(int iter,
                                 int[] sourceDegreeData,
                                 int[] sourceChunkStartingIndex,
                                 int[] relationshipTarget,
                                 int[] relationshipWeight) {
        int batches = (int)nodeCount/BATCH_SIZE;
        List<Future> futures = new ArrayList<>(batches);
        int nodeIter = 0;
        while(nodeIter < nodeCount) {
            // Process BATCH_SIZE relationships in one batch, aligned to the chunksize.
            final int start = nodeIter;
            final int end = getEndNode(nodeIter, sourceChunkStartingIndex);
            Future future = pool.submit(new Runnable() {
                @Override
                public void run() {
                    for (int i = start; i < end; i++) {
                        int chunkIndex = sourceChunkStartingIndex[i];
                        int degree = sourceDegreeData[i];

                        for (int j = 0; j < degree; j++) {
                            int source = i;
                            int target = relationshipTarget[chunkIndex + j];
                            int weight = relationshipWeight==null ? 1 : relationshipWeight[chunkIndex + j];
                            pageRanksAtomic.addAndGet(target, weight * previousPageRanks[source]);
                        }
                    }
                }
            });
            nodeIter = end;
            futures.add(future);
        }

        PageRankUtils.waitForTasks(futures);
    }

    private void iterateParallelAvgCitations(int iter,
                                 int[] sourceDegreeData,
                                 int[] sourceChunkStartingIndex,
                                 int[] relationshipTarget,
                                 int[] relationshipWeight
                                 double avgCitations) {
        int batches = (int)nodeCount/BATCH_SIZE;
        List<Future> futures = new ArrayList<>(batches);
        int nodeIter = 0;
        while(nodeIter < nodeCount) {
            // Process BATCH_SIZE relationships in one batch, aligned to the chunksize.
            final int start = nodeIter;
            final int end = getEndNode(nodeIter, sourceChunkStartingIndex);
            Future future = pool.submit(new Runnable() {
                @Override
                public void run() {
                    for (int i = start; i < end; i++) {
                        int chunkIndex = sourceChunkStartingIndex[i];
                        int degree = sourceDegreeData[i];

                        for (int j = 0; j < degree; j++) {
                            int source = i;
                            int target = relationshipTarget[chunkIndex + j];
                            int weight = relationshipWeight==null ? 1 : relationshipWeight[chunkIndex + j] / avgCitations;
                            pageRanksAtomic.addAndGet(target, weight * previousPageRanks[source]);
                        }
                    }
                }
            });
            nodeIter = end;
            futures.add(future);
        }

        PageRankUtils.waitForTasks(futures);
    }

    // This sums up the weights of all relationshipWeights for this node
    private int getTotalWeightForNode(int node,
                                      int[] sourceChunkStartingIndex,
                                      int[] sourceDegreeData,
                                      int[] relationshipWeight) {
        int degree = sourceDegreeData[node];
        if (relationshipWeight==null) return degree;

        int chunkIndex = sourceChunkStartingIndex[node];
        int totalWeight = 0;
        for (int i = 0; i < degree; i++) {
            totalWeight += relationshipWeight[chunkIndex + i];
        }

        return totalWeight;
    }

    private int getTotalWeightForNodeAvgCitations(int node,
                                      int[] sourceChunkStartingIndex,
                                      int[] sourceDegreeData,
                                      int[] relationshipWeight
                                      double avgCitations) {
        int degree = sourceDegreeData[node];
        if (relationshipWeight==null) return degree;

        int chunkIndex = sourceChunkStartingIndex[node];
        int totalWeight = 0;
        for (int i = 0; i < degree; i++) {
            totalWeight += relationshipWeight[chunkIndex + i];
        }

        return totalWeight * avgCitations;
    }

    private void startIteration(int[] sourceChunkStartingIndex,
                                int[] sourceDegreeData,
                                int[] relationshipWeight) {
        for (int node = 0; node < nodeCount; node++) {
            int weightedDegree = getTotalWeightForNode(node, sourceChunkStartingIndex, sourceDegreeData, relationshipWeight);

            if (weightedDegree == -1) {
                continue;
            }
            int prevRank = pageRanksAtomic.get(node);
            previousPageRanks[node] =  toInt(ALPHA * toFloat(prevRank) / weightedDegree);
            pageRanksAtomic.set(node, ONE_MINUS_ALPHA_INT);
        }
    }

    public void writeResultsToDB(String property) {
        this.property = property;
        stats.write = true;
        long before = System.currentTimeMillis();
        AlgoUtils.writeBackResults(pool, db, this, WRITE_BATCH);
        stats.writeMillis = System.currentTimeMillis() - before;
        stats.property = getPropertyName();
    }

    public double getResult(long node) {
        double val = 0;
        int logicalIndex = getNodeIndex((int)node);

        if (logicalIndex >= 0 && pageRanksAtomic.length() >= logicalIndex) {
            val = toFloat(pageRanksAtomic.get(logicalIndex));
        }
        return val;
    }

    public String getPropertyName() {
        return property;
    }

    public long numberOfNodes() {
        return nodeCount;
    };

    public long numberOfRels(){
        return relCount;
    };

    @Override
    public PageRankStatistics getStatistics() {
        return stats;
    }
}
