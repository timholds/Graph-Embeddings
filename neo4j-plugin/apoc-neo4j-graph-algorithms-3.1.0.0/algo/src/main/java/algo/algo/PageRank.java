package algo.algo;

import algo.algo.pagerank.PageRank.PageRankStatistics;
import org.neo4j.procedure.*;
import algo.Pools;
import algo.algo.algorithms.AlgoUtils;
import algo.algo.pagerank.PageRankArrayStorageParallelCypher;
import algo.algo.pagerank.PageRankArrayStorageParallelSPI;
import algo.result.NodeScore;
import algo.util.Util;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import static algo.algo.algorithms.AlgoUtils.*;

public class PageRank {

    private static final String SETTING_PAGE_RANK_ITERATIONS = "iterations";
    private static final String SETTING_PAGE_RANK_TYPES = "types";

    static final ExecutorService pool = Pools.DEFAULT;
    static final Long DEFAULT_PAGE_RANK_ITERATIONS = 20L;

    @Context
    public GraphDatabaseAPI db;

    @Context
    public Log log;


    @Procedure("algo.algo.pageRank.stream")
    @Description("CALL algo.algo.pageRank.stream(nodes) YIELD node, score - calculates page rank for given nodes")
    public Stream<NodeScore> pageRank(
            @Name("nodes") List<Node> nodes) {
        return innerPageRank(
                DEFAULT_PAGE_RANK_ITERATIONS,
                nodes);
    }

    @Procedure("algo.algo.pageRank2.stream")
    @Description("CALL algo.algo.pageRank2.stream(nodes) YIELD node, score - calculates page rank for given nodes")
    public Stream<NodeScore> pageRank2(
            @Name("nodes") List<Node> nodes) {
        return innerPageRank2(
                DEFAULT_PAGE_RANK_ITERATIONS,
                nodes);
    }

    @Procedure("algo.algo.pageRankWithConfig")
    @Description(
            "CALL algo.algo.pageRankWithConfig(nodes,{iterations:_,types:_}) YIELD node, score, info - calculates page rank" +
                    " for given nodes")
    public Stream<NodeScore> pageRankWithConfig(
            @Name("nodes") List<Node> nodes,
            @Name("config") Map<String, Object> config) {
            return innerPageRank(
                    (Long) config.getOrDefault(SETTING_PAGE_RANK_ITERATIONS, DEFAULT_PAGE_RANK_ITERATIONS),
                    nodes,
                    Util.typesAndDirectionsToTypesArray((String) config.getOrDefault(SETTING_PAGE_RANK_TYPES, "")));
    }
    @Procedure(value = "algo.algo.pageRankStats",mode = Mode.WRITE)
    @Description(
            "CALL algo.algo.pageRankStats({iterations:_,types:_,write:true,...}) YIELD nodeCount - calculates page rank on graph " +
                    " for given nodes and potentially writes back")
    public Stream<PageRankStatistics> pageRankStats(@Name("config") Map<String, Object> config) {
        Long iterations = (Long) config.getOrDefault(SETTING_PAGE_RANK_ITERATIONS, DEFAULT_PAGE_RANK_ITERATIONS);
        RelationshipType[] types = Util.typesAndDirectionsToTypesArray((String) config.getOrDefault(SETTING_PAGE_RANK_TYPES, ""));
        return innerPageRankStats(iterations.intValue(), config, types);
    }

    @Procedure(value = "algo.algo.pageRankWithCypher",mode = Mode.WRITE)
    @Description("CALL algo.algo.pageRankWithCypher({iterations,node_cypher,rel_cypher,write,property,numCpu}) - calculates page rank based on cypher input")
    public Stream<PageRankStatistics> pageRankWithCypher(
            @Name("config") Map<String, Object> config) {
        Long iterations = (Long) config.getOrDefault(SETTING_PAGE_RANK_ITERATIONS, DEFAULT_PAGE_RANK_ITERATIONS);
        String nodeCypher = AlgoUtils.getCypher(config, AlgoUtils.SETTING_CYPHER_NODE, AlgoUtils.DEFAULT_CYPHER_NODE);
        String relCypher = AlgoUtils.getCypher(config, AlgoUtils.SETTING_CYPHER_REL, AlgoUtils.DEFAULT_CYPHER_REL);
        boolean shouldWrite = (boolean)config.getOrDefault(SETTING_WRITE, DEFAULT_PAGE_RANK_WRITE);
        Number weight = (Number) config.get(SETTING_WEIGHTED);
        Number batchSize = (Number) config.get(SETTING_BATCH_SIZE);
        int concurrency = ((Number) config.getOrDefault("concurrency",Pools.getNoThreadsInDefaultPool())).intValue();
        String property = (String) config.getOrDefault("property","pagerank");

        long beforeReading = System.currentTimeMillis();
        log.info("Pagerank: Reading data into local ds");
        PageRankArrayStorageParallelCypher pageRank = new PageRankArrayStorageParallelCypher(db, pool, log);
        boolean success = pageRank.readNodeAndRelCypherData(
                relCypher, nodeCypher,weight, batchSize, concurrency);
        if (!success) {
            String errorMsg = "Failure while reading cypher queries. Make sure the results are ordered.";
            log.info(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        long afterReading = System.currentTimeMillis();

        log.info("Pagerank: Graph stored in local ds in " + (afterReading - beforeReading) + " milliseconds");
        log.info("Pagerank: Number of nodes: " + pageRank.numberOfNodes());
        log.info("Pagerank: Number of relationships: " + pageRank.numberOfRels());

        // TODO figure out where to pass avgCitations to get included in weighting
        // I don't think I even need this anymore - avgCitations should be set in
        // PageRank.java class PageRankStatistics in the constructor
        // pageRank.avgCitations = pageRank.numberOfNodes()/pageRank.numberOfRels()
        // Need to set pageRank.setAvgCitations(avgCitations)

        pageRank.compute(iterations.intValue());

        long afterComputation = System.currentTimeMillis();
        log.info("Pagerank: Computations took " + (afterComputation - afterReading) + " milliseconds");

        if (shouldWrite) {
            pageRank.writeResultsToDB(property);
            long afterWrite = System.currentTimeMillis();
            log.info("Pagerank: Writeback took " + (afterWrite - afterComputation) + " milliseconds");
        }
        return Stream.of(pageRank.getStatistics());
    }

    private Stream<NodeScore> innerPageRank(Long iterations, List<Node> nodes, RelationshipType... types) {
        try {
            PageRankArrayStorageParallelSPI pageRank = new PageRankArrayStorageParallelSPI(db, pool);
            pageRank.compute(iterations.intValue(), types);
            return nodes.stream().map(node -> new NodeScore(node, pageRank.getResult(node.getId())));
        } catch (Exception e) {
            String errMsg = "Error encountered while calculating page rank";
            log.error(errMsg, e);
            throw new RuntimeException(errMsg, e);
        }
    }
    private Stream<NodeScore> innerPageRank2(Long iterations, List<Node> nodes, RelationshipType... types) {
        try {
            PageRankArrayStorageParallelSPI pageRank = new PageRankArrayStorageParallelSPI(db, pool);
            pageRank.compute2(iterations.intValue(), types);
            return nodes.stream().map(node -> new NodeScore(node, pageRank.getResult(node.getId())));
        } catch (Exception e) {
            String errMsg = "Error encountered while calculating page rank";
            log.error(errMsg, e);
            throw new RuntimeException(errMsg, e);
        }
    }
    private Stream<PageRankStatistics> innerPageRankStats(int iterations, Map<String,Object> config, RelationshipType... types) {
        try {
            PageRankArrayStorageParallelSPI pageRank = new PageRankArrayStorageParallelSPI(db, pool);
            pageRank.compute(iterations, types);
            if ((boolean)config.getOrDefault(SETTING_WRITE, DEFAULT_PAGE_RANK_WRITE)) {
                pageRank.writeResultsToDB();
            }
            return Stream.of(pageRank.getStatistics());
        } catch (Exception e) {
            String errMsg = "Error encountered while calculating page rank";
            log.error(errMsg, e);
            throw new RuntimeException(errMsg, e);
        }
    }
}
