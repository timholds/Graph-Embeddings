/**
 * Copyright (c) 2017 "Neo4j, Inc." <http://neo4j.com>
 *
 * This file is part of Neo4j Graph Algorithms <http://github.com/neo4j-contrib/neo4j-graph-algorithms>.
 *
 * Neo4j Graph Algorithms is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphalgo;

import com.carrotsearch.hppc.IntSet;
import com.carrotsearch.hppc.ObjectArrayList;
import com.carrotsearch.hppc.cursors.IntCursor;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.HugeGraph;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.ProcedureConfiguration;
import org.neo4j.graphalgo.core.neo4jview.DirectIdMapping;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.ProgressLogger;
import org.neo4j.graphalgo.core.utils.ProgressTimer;
import org.neo4j.graphalgo.core.utils.TerminationFlag;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeLongArray;
import org.neo4j.graphalgo.core.write.Exporter;
import org.neo4j.graphalgo.core.write.Translators;
import org.neo4j.graphalgo.impl.*;
import org.neo4j.graphalgo.impl.multistepscc.MultistepSCC;
import org.neo4j.graphalgo.impl.scc.SCCAlgorithm;
import org.neo4j.graphalgo.impl.scc.SCCTarjan;
import org.neo4j.graphalgo.impl.scc.SCCTunedTarjan;
import org.neo4j.graphalgo.results.SCCResult;
import org.neo4j.graphalgo.results.SCCStreamResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import org.neo4j.values.storable.IntValue;
import org.neo4j.values.storable.Values;

import java.util.Map;
import java.util.stream.Stream;

/**
 * @author mknblch
 */
public class StronglyConnectedComponentsProc {

    public static final String CONFIG_WRITE_PROPERTY = "partitionProperty";
    public static final String CONFIG_CLUSTER = "partition";

    @Context
    public GraphDatabaseAPI api;

    @Context
    public Log log;

    @Context
    public KernelTransaction transaction;

    // default algo.scc -> iterative tarjan
    @Procedure(value = "algo.scc", mode = Mode.WRITE)
    @Description("CALL algo.scc(label:String, relationship:String, config:Map<String, Object>) YIELD " +
            "loadMillis, computeMillis, writeMillis, setCount, maxSetSize, minSetSize")
    public Stream<SCCResult> sccDefaultMethod(
            @Name(value = "label", defaultValue = "") String label,
            @Name(value = "relationship", defaultValue = "") String relationship,
            @Name(value = "config", defaultValue = "{}") Map<String, Object> config) {

        return sccIterativeTarjan(label, relationship, config);
    }

    // default algo.scc -> iter tarjan
    @Procedure(value = "algo.scc.stream")
    @Description("CALL algo.scc.stream(label:String, relationship:String, config:Map<String, Object>) YIELD " +
            "loadMillis, computeMillis, writeMillis, setCount, maxSetSize, minSetSize")
    public Stream<SCCAlgorithm.StreamResult> sccDefaultMethodStream(
            @Name(value = "label", defaultValue = "") String label,
            @Name(value = "relationship", defaultValue = "") String relationship,
            @Name(value = "config", defaultValue = "{}") Map<String, Object> config) {

        return sccIterativeTarjanStream(label, relationship, config);
    }

    // algo.scc.tarjan
    @Procedure(value = "algo.scc.recursive.tarjan", mode = Mode.WRITE)
    @Description("CALL algo.scc.tarjan(label:String, relationship:String, config:Map<String, Object>) YIELD " +
            "loadMillis, computeMillis, writeMillis, setCount, maxSetSize, minSetSize")
    public Stream<SCCResult> sccTarjan(
            @Name(value = "label", defaultValue = "") String label,
            @Name(value = "relationship", defaultValue = "") String relationship,
            @Name(value = "config", defaultValue = "{}") Map<String, Object> config) {

        ProcedureConfiguration configuration = ProcedureConfiguration.create(config);

        SCCResult.Builder builder = SCCResult.builder();

        ProgressTimer loadTimer = builder.timeLoad();
        Graph graph = new GraphLoader(api, Pools.DEFAULT)
                .init(log, label, relationship, configuration)
                .withOptionalLabel(label)
                .withOptionalRelationshipType(relationship)
                .withoutRelationshipWeights()
                .withDirection(Direction.OUTGOING)
                .load(configuration.getGraphImpl());
        loadTimer.stop();

        if (graph.nodeCount() == 0) {
            return Stream.of(builder.build());
        }

        final TerminationFlag terminationFlag = TerminationFlag.wrap(transaction);
        SCCTarjan tarjan = new SCCTarjan(graph)
                .withProgressLogger(ProgressLogger.wrap(log, "SCC(Tarjan)"))
                .withTerminationFlag(terminationFlag);

        builder.timeEval(() -> {
            tarjan.compute();
            builder.withMaxSetSize(tarjan.getMaxSetSize())
                    .withMinSetSize(tarjan.getMinSetSize())
                    .withSetCount(tarjan.getConnectedComponents().size());
        });

        if (configuration.isWriteFlag()) {
            builder.timeWrite(() -> {
                final ObjectArrayList<IntSet> connectedComponents = tarjan.getConnectedComponents();
                tarjan.release();
                Exporter.of(new DirectIdMapping(connectedComponents.size()), api)
                        .withLog(log)
                        .parallel(Pools.DEFAULT, configuration.getConcurrency(), terminationFlag)
                        .build()
                        .write(
                                configuration.get(CONFIG_WRITE_PROPERTY, CONFIG_CLUSTER),
                                propertyId -> (ops, id) -> {
                                    final int setId = (int) (id);
                                    IntValue property = Values.intValue(setId + 1);
                                    for (final IntCursor iCursor : connectedComponents.get(setId)) {
                                        ops.nodeSetProperty(
                                                graph.toOriginalNodeId(iCursor.value),
                                                propertyId,
                                                property);
                                    }
                                }
                        );
            });
        }

        return Stream.of(builder.build());
    }

    // algo.scc.tunedTarjan
    @Procedure(value = "algo.scc.recursive.tunedTarjan", mode = Mode.WRITE)
    @Description("CALL algo.scc.recursive.tunedTarjan(label:String, relationship:String, config:Map<String, Object>) YIELD " +
            "loadMillis, computeMillis, writeMillis, setCount, maxSetSize, minSetSize")
    public Stream<SCCResult> sccTunedTarjan(
            @Name(value = "label", defaultValue = "") String label,
            @Name(value = "relationship", defaultValue = "") String relationship,
            @Name(value = "config", defaultValue = "{}") Map<String, Object> config) {

        ProcedureConfiguration configuration = ProcedureConfiguration.create(config);

        SCCResult.Builder builder = SCCResult.builder();

        ProgressTimer loadTimer = builder.timeLoad();
        Graph graph = new GraphLoader(api, Pools.DEFAULT)
                .init(log, label, relationship, configuration)
                .withoutRelationshipWeights()
                .withDirection(Direction.OUTGOING)
                .load(configuration.getGraphImpl());
        loadTimer.stop();

        if (graph.nodeCount() == 0) {
            return Stream.of(builder.build());
        }

        final TerminationFlag terminationFlag = TerminationFlag.wrap(transaction);
        SCCTunedTarjan tarjan = new SCCTunedTarjan(graph)
                .withProgressLogger(ProgressLogger.wrap(log, "SCC(TunedTarjan)"))
                .withTerminationFlag(terminationFlag);

        builder.timeEval(tarjan::compute);

        builder.withMaxSetSize(tarjan.getMaxSetSize())
                .withMinSetSize(tarjan.getMinSetSize())
                .withSetCount(tarjan.getSetCount());

        if (configuration.isWriteFlag()) {
            builder.timeWrite(() -> Exporter
                    .of(api, graph)
                    .withLog(log)
                    .parallel(Pools.DEFAULT, configuration.getConcurrency(), terminationFlag)
                    .build()
                    .write(
                            configuration.get(CONFIG_WRITE_PROPERTY, CONFIG_CLUSTER),
                            tarjan.getConnectedComponents(),
                            Translators.OPTIONAL_INT_ARRAY_TRANSLATOR
                    ));
        }

        return Stream.of(builder.build());
    }

    // algo.scc.tunedTarjan.stream
    @Procedure(value = "algo.scc.recursive.tunedTarjan.stream", mode = Mode.WRITE)
    @Description("CALL algo.scc.recursive.tunedTarjan.stream(label:String, relationship:String, config:Map<String, Object>) YIELD " +
            "nodeId, partition")
    public Stream<SCCAlgorithm.StreamResult> sccTunedTarjanStream(
            @Name(value = "label", defaultValue = "") String label,
            @Name(value = "relationship", defaultValue = "") String relationship,
            @Name(value = "config", defaultValue = "{}") Map<String, Object> config) {

        ProcedureConfiguration configuration = ProcedureConfiguration.create(config);

        Graph graph = new GraphLoader(api, Pools.DEFAULT)
                .init(log, label, relationship, configuration)
                .withoutRelationshipWeights()
                .withDirection(Direction.OUTGOING)
                .load(configuration.getGraphImpl());

        if (graph.nodeCount() == 0) {
            return Stream.empty();
        }

        return new SCCTunedTarjan(graph)
                .withProgressLogger(ProgressLogger.wrap(log, "SCC(TunedTarjan)"))
                .withTerminationFlag(TerminationFlag.wrap(transaction))
                .compute()
                .resultStream();
    }

    // algo.scc.iterative
    @Procedure(value = "algo.scc.iterative", mode = Mode.WRITE)
    @Description("CALL algo.scc.iterative(label:String, relationship:String, config:Map<String, Object>) YIELD " +
            "loadMillis, computeMillis, writeMillis, setCount, maxSetSize, minSetSize")
    public Stream<SCCResult> sccIterativeTarjan(
            @Name(value = "label", defaultValue = "") String label,
            @Name(value = "relationship", defaultValue = "") String relationship,
            @Name(value = "config", defaultValue = "{}") Map<String, Object> config) {

        final ProcedureConfiguration configuration = ProcedureConfiguration.create(config);

        final SCCResult.Builder builder = SCCResult.builder();

        final ProgressTimer loadTimer = builder.timeLoad();
        final Graph graph = new GraphLoader(api, Pools.DEFAULT)
                .init(log, label, relationship, configuration)
                .withoutRelationshipWeights()
                .withDirection(Direction.OUTGOING)
                .load(configuration.getGraphImpl());
        loadTimer.stop();

        if (graph.nodeCount() == 0) {
            return Stream.of(builder.build());
        }

        final AllocationTracker tracker = AllocationTracker.create();
        final TerminationFlag terminationFlag = TerminationFlag.wrap(transaction);
        final SCCAlgorithm tarjan = SCCAlgorithm.iterativeTarjan(graph, tracker)
                .withProgressLogger(ProgressLogger.wrap(log, "SCC(IterativeTarjan)"))
                .withTerminationFlag(terminationFlag);

        builder.timeEval(tarjan::compute);

        builder.withSetCount(tarjan.getSetCount())
                .withMinSetSize(tarjan.getMinSetSize())
                .withMaxSetSize(tarjan.getMaxSetSize());

        if (configuration.isWriteFlag()) {
            builder.timeWrite(() -> write(configuration,  graph, terminationFlag, tarjan));
        }

        return Stream.of(builder.build());
    }

    private void write(ProcedureConfiguration configuration, Graph graph, TerminationFlag terminationFlag, SCCAlgorithm tarjan) {

        if (graph instanceof HugeGraph) {
            final HugeLongArray connectedComponents = tarjan.getConnectedComponents();
            graph.release();
            tarjan.release();
            Exporter.of(api, graph)
                    .withLog(log)
                    .parallel(Pools.DEFAULT, configuration.getConcurrency(), terminationFlag)
                    .build()
                    .write(
                            configuration.get(CONFIG_WRITE_PROPERTY, CONFIG_CLUSTER),
                            connectedComponents,
                            HugeLongArray.Translator.INSTANCE
                    );
            return;
        }

        final int[] connectedComponents = tarjan.getConnectedComponents();
        graph.release();
        tarjan.release();
        Exporter.of(api, graph)
                .withLog(log)
                .parallel(Pools.DEFAULT, configuration.getConcurrency(), terminationFlag)
                .build()
                .write(
                        configuration.get(CONFIG_WRITE_PROPERTY, CONFIG_CLUSTER),
                        connectedComponents,
                        Translators.OPTIONAL_INT_ARRAY_TRANSLATOR
                );

    }

    // algo.scc.iterative.stream
    @Procedure(value = "algo.scc.iterative.stream", mode = Mode.WRITE)
    @Description("CALL algo.scc.iterative.stream(label:String, relationship:String, config:Map<String, Object>) YIELD " +
            "nodeId, partition")
    public Stream<SCCAlgorithm.StreamResult> sccIterativeTarjanStream(
            @Name(value = "label", defaultValue = "") String label,
            @Name(value = "relationship", defaultValue = "") String relationship,
            @Name(value = "config", defaultValue = "{}") Map<String, Object> config) {

        final ProcedureConfiguration configuration = ProcedureConfiguration.create(config);

        final Graph graph = new GraphLoader(api, Pools.DEFAULT)
                .init(log, label, relationship, configuration)
                .withoutRelationshipWeights()
                .withDirection(Direction.OUTGOING)
                .load(configuration.getGraphImpl());

        if (graph.nodeCount() == 0) {
            graph.release();
            return Stream.empty();
        }

        final AllocationTracker tracker = AllocationTracker.create();
        final SCCAlgorithm compute = SCCAlgorithm.iterativeTarjan(graph, tracker)
                .withProgressLogger(ProgressLogger.wrap(log, "SCC(IterativeTarjan)"))
                .withTerminationFlag(TerminationFlag.wrap(transaction))
                .compute();

        graph.release();

        return compute.resultStream();
    }


    // algo.scc.multistep
    @Procedure(value = "algo.scc.multistep", mode = Mode.WRITE)
    @Description("CALL algo.scc.multistep(label:String, relationship:String, {write:true, concurrency:4, cutoff:100000}) YIELD " +
            "loadMillis, computeMillis, writeMillis, setCount, maxSetSize, minSetSize")
    public Stream<SCCResult> multistep(
            @Name(value = "label", defaultValue = "") String label,
            @Name(value = "relationship", defaultValue = "") String relationship,
            @Name(value = "config", defaultValue = "{}") Map<String, Object> config) {

        ProcedureConfiguration configuration = ProcedureConfiguration.create(config);

        SCCResult.Builder builder = SCCResult.builder();

        ProgressTimer loadTimer = builder.timeLoad();
        Graph graph = new GraphLoader(api, Pools.DEFAULT)
                .init(log, label, relationship, configuration)
                .withoutRelationshipWeights()
                .load(configuration.getGraphImpl());
        loadTimer.stop();

        if (graph.nodeCount() == 0) {
            graph.release();
            return Stream.of(builder.build());
        }

        final TerminationFlag terminationFlag = TerminationFlag.wrap(transaction);
        final MultistepSCC multistep = new MultistepSCC(graph, org.neo4j.graphalgo.core.utils.Pools.DEFAULT,
                configuration.getConcurrency(),
                configuration.getNumber("cutoff", 100_000).intValue())
                .withProgressLogger(ProgressLogger.wrap(log, "SCC(MultiStep)"))
                .withTerminationFlag(terminationFlag);

        builder.timeEval(multistep::compute);

        builder.withMaxSetSize(multistep.getMaxSetSize())
                .withMinSetSize(multistep.getMinSetSize())
                .withSetCount(multistep.getSetCount());

        if (configuration.isWriteFlag()) {
            final int[] connectedComponents = multistep.getConnectedComponents();
            graph.release();
            multistep.release();
            builder.timeWrite(() -> Exporter
                    .of(api, graph)
                    .withLog(log)
                    .parallel(Pools.DEFAULT, configuration.getConcurrency(), terminationFlag)
                    .build()
                    .write(
                            configuration.get(CONFIG_WRITE_PROPERTY, CONFIG_CLUSTER),
                            connectedComponents,
                            Translators.OPTIONAL_INT_ARRAY_TRANSLATOR
                    ));
        }

        return Stream.of(builder.build());
    }

    // algo.scc.multistep.stream
    @Procedure(value = "algo.scc.multistep.stream")
    @Description("CALL algo.scc.multistep.stream(label:String, relationship:String, {write:true, concurrency:4, cutoff:100000}) YIELD " +
            "nodeId, partition")
    public Stream<SCCStreamResult> multistepStream(
            @Name(value = "label", defaultValue = "") String label,
            @Name(value = "relationship", defaultValue = "") String relationship,
            @Name(value = "config", defaultValue = "{}") Map<String, Object> config) {

        ProcedureConfiguration configuration = ProcedureConfiguration.create(config);

        Graph graph = new GraphLoader(api, Pools.DEFAULT)
                .init(log, label, relationship, configuration)
                .withoutRelationshipWeights()
                .load(configuration.getGraphImpl());

        if (graph.nodeCount() == 0) {
            graph.release();
            return Stream.empty();
        }

        final MultistepSCC multistep = new MultistepSCC(graph, org.neo4j.graphalgo.core.utils.Pools.DEFAULT,
                configuration.getConcurrency(),
                configuration.getNumber("cutoff", 100_000).intValue())
                .withProgressLogger(ProgressLogger.wrap(log, "SCC(MultiStep)"))
                .withTerminationFlag(TerminationFlag.wrap(transaction));

        multistep.compute();
        graph.release();
        return multistep.resultStream();
    }

    // algo.scc.forwardBackward.stream
    @Procedure(value = "algo.scc.forwardBackward.stream")
    @Description("CALL algo.scc.forwardBackward.stream(long startNodeId, label:String, relationship:String, {write:true, concurrency:4}) YIELD " +
            "nodeId, partition")
    public Stream<ForwardBackwardScc.Result> fwbwStream(
            @Name(value = "startNodeId", defaultValue = "0") long startNodeId,
            @Name(value = "label", defaultValue = "") String label,
            @Name(value = "relationship", defaultValue = "") String relationship,
            @Name(value = "config", defaultValue = "{}") Map<String, Object> config) {

        ProcedureConfiguration configuration = ProcedureConfiguration.create(config);

        Graph graph = new GraphLoader(api, Pools.DEFAULT)
                .init(log, label, relationship, configuration)
                .withoutRelationshipWeights()
                .load(configuration.getGraphImpl());

        if (graph.nodeCount() == 0) {
            graph.release();
            return Stream.empty();
        }

        final ForwardBackwardScc algo = new ForwardBackwardScc(graph, Pools.DEFAULT,
                configuration.getConcurrency())
                .withProgressLogger(ProgressLogger.wrap(log, "SCC(ForwardBackward)"))
                .withTerminationFlag(TerminationFlag.wrap(transaction))
                .compute(graph.toMappedNodeId(startNodeId));
        graph.release();
        return algo.resultStream();
    }
}
