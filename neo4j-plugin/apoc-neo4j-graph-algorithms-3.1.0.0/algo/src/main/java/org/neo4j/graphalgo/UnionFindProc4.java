package org.neo4j.graphalgo;

import algo.Pools;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.ProcedureConfiguration;
import org.neo4j.graphalgo.core.ProcedureConstants;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphalgo.core.utils.ProgressTimer;
import org.neo4j.graphalgo.core.utils.dss.DisjointSetStruct;
import org.neo4j.graphalgo.impl.GraphUnionFind;
import org.neo4j.graphalgo.impl.ParallelUnionFindFJMerge;
import org.neo4j.graphalgo.impl.ParallelUnionFindForkJoin;
import org.neo4j.graphalgo.results.UnionFindResult;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;

import java.util.Map;
import java.util.stream.Stream;

/**
 * @author mknblch
 */
public class UnionFindProc4 {

    public static final String CONFIG_THRESHOLD = "threshold";
    public static final String CONFIG_CLUSTER_PROPERTY = "clusterProperty";
    public static final String DEFAULT_CLUSTER_PROPERTY = "cluster";

    @Context
    public GraphDatabaseAPI api;

    @Context
    public Log log;

    @Procedure(value = "algo.unionFind.exp3", mode = Mode.WRITE)
    @Description("CALL algo.unionFind(label:String, relationship:String, " +
            "{property:'weight', threshold:0.42, defaultValue:1.0, write: true, clusterProperty:'cluster'}) " +
            "YIELD nodes, setCount, loadMillis, computeMillis, writeMillis")
    public Stream<UnionFindResult> unionFind(
            @Name(value = "label", defaultValue = "") String label,
            @Name(value = "relationship", defaultValue = "") String relationship,
            @Name(value = "config", defaultValue = "{}") Map<String, Object> config) {

        ProcedureConfiguration configuration = ProcedureConfiguration.create(config)
                .overrideNodeLabelOrQuery(label)
                .overrideRelationshipTypeOrQuery(relationship);

        UnionFindResult.Builder builder = UnionFindResult.builder();

        // loading
        final Graph graph;
        try (ProgressTimer timer = builder.timeLoad()) {
            graph = load(configuration);
        };

        // evaluation
        final DisjointSetStruct struct;
        try (ProgressTimer timer = builder.timeEval()) {
            struct = evaluate(graph, configuration);
        };

        if (configuration.isWriteFlag()) {
            // write back
            builder.timeWrite(() ->
                    write(graph, struct, configuration));
        }

        return Stream.of(builder
                .withNodeCount(graph.nodeCount())
                .withSetCount(struct.getSetCount())
                .build());
    }

    @Procedure(value = "algo.unionFind.exp3.stream")
    @Description("CALL algo.unionFind.stream(label:String, relationship:String, " +
            "{property:'propertyName', threshold:0.42, defaultValue:1.0) " +
            "YIELD nodeId, setId - yields a setId to each node id")
    public Stream<DisjointSetStruct.Result> unionFindStream(
            @Name(value = "label", defaultValue = "") String label,
            @Name(value = "relationship", defaultValue = "") String relationship,
            @Name(value = "config", defaultValue = "{}") Map<String, Object> config) {

        ProcedureConfiguration configuration = ProcedureConfiguration.create(config)
                .overrideNodeLabelOrQuery(label)
                .overrideRelationshipTypeOrQuery(relationship);

        // loading
        final Graph graph = load(configuration);

        // evaluation
        return evaluate(graph, configuration)
                .resultStream(graph);
    }

    private Graph load(ProcedureConfiguration config) {
        return new GraphLoader(api)
                .withOptionalLabel(config.getNodeLabelOrQuery())
                .withOptionalRelationshipType(config.getRelationshipOrQuery())
                .withOptionalRelationshipWeightsFromProperty(
                        config.getProperty(),
                        config.getPropertyDefaultValue(1.0))
                .withExecutorService(Pools.DEFAULT)
                .load(HeavyGraphFactory.class);
    }

    private DisjointSetStruct evaluate(Graph graph, ProcedureConfiguration config) {

        final DisjointSetStruct struct;

        if (config.getBatchSize(-1) != -1) {
            if (config.containsKeys(ProcedureConstants.PROPERTY_PARAM, CONFIG_THRESHOLD)) {
                final Double threshold = config.get(CONFIG_THRESHOLD, 0.0);
                log.debug("Computing union find with threshold in parallel" + threshold);
                struct = new ParallelUnionFindForkJoin(graph, Pools.DEFAULT, config.getBatchSize())
                        .compute(threshold)
                        .getStruct();
            } else {
                log.debug("Computing union find without threshold in parallel");
                struct = new ParallelUnionFindForkJoin(graph, Pools.DEFAULT, config.getBatchSize())
                        .compute()
                        .getStruct();
            }
        } else {
            if (config.containsKeys(ProcedureConstants.PROPERTY_PARAM, CONFIG_THRESHOLD)) {
                final Double threshold = config.get(CONFIG_THRESHOLD, 0.0);
                log.debug("Computing union find with threshold " + threshold);
                struct = new GraphUnionFind(graph).compute(threshold);
            } else {
                log.debug("Computing union find without threshold");
                struct = new GraphUnionFind(graph).compute();
            }
        }

        return struct;
    }

    private void write(Graph graph, DisjointSetStruct struct, ProcedureConfiguration config) {
        log.debug("Writing results");
        new DisjointSetStruct.DSSExporter(api,
                graph,
                config.get(CONFIG_CLUSTER_PROPERTY, DEFAULT_CLUSTER_PROPERTY))
                .write(struct);
    }

}
