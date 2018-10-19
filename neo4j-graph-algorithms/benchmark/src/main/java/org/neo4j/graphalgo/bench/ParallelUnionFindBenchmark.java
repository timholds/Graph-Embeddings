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
package org.neo4j.graphalgo.bench;

import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphalgo.core.utils.ParallelUtil;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.ProgressTimer;
import org.neo4j.graphalgo.impl.GraphUnionFind;
import org.neo4j.graphalgo.impl.MSColoring;
import org.neo4j.graphalgo.impl.ParallelUnionFindFJMerge;
import org.neo4j.graphalgo.impl.ParallelUnionFindForkJoin;
import org.neo4j.graphalgo.impl.ParallelUnionFindQueue;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.internal.kernel.api.Write;
import org.neo4j.internal.kernel.api.exceptions.EntityNotFoundException;
import org.neo4j.internal.kernel.api.exceptions.InvalidTransactionTypeKernelException;
import org.neo4j.internal.kernel.api.exceptions.TransactionFailureException;
import org.neo4j.io.fs.FileUtils;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.impl.core.ThreadToStatementContextBridge;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * @author mknblch
 */
@Threads(1)
@Fork(value = 1, jvmArgs = "-Xms4G")
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ParallelUnionFindBenchmark {

    public static final RelationshipType RELATIONSHIP_TYPE = RelationshipType.withName("TYPE");

    private static GraphDatabaseAPI db;

    private static Graph graph;

    private static ThreadToStatementContextBridge bridge;

    public static final String GRAPH_DIRECTORY = "/tmp/graph.db";

    private static File storeDir = new File(GRAPH_DIRECTORY);

//    @Param({"5", "10", "20", "50"})
    public static int numSets = 20;

    @Setup
    public static void setup() throws Exception {

        FileUtils.deleteRecursively(storeDir);
        if (storeDir.exists()) {
            throw new IllegalStateException("could not delete " + storeDir);
        }

        if (null != db) {
            db.shutdown();
        }

        db = (GraphDatabaseAPI) new GraphDatabaseFactory()
                .newEmbeddedDatabaseBuilder(storeDir)
                .setConfig(GraphDatabaseSettings.pagecache_memory, "4G")
                .newGraphDatabase();

        bridge = db.getDependencyResolver()
                .resolveDependency(ThreadToStatementContextBridge.class);

        try (ProgressTimer timer = ProgressTimer.start(l -> System.out.println("creating test graph took " + l + " ms"))) {
            createTestGraph(numSets, 100_000);
        }

        db.execute("MATCH (n) RETURN n");

        graph = new GraphLoader(db)
                .withExecutorService(Pools.DEFAULT)
                .withAnyLabel()
                .withRelationshipType(RELATIONSHIP_TYPE)
                .withDirection(Direction.OUTGOING)
                .load(HeavyGraphFactory.class);
    }

    @TearDown
    public void shutdown() throws IOException {
        graph.release();
        db.shutdown();
        Pools.DEFAULT.shutdownNow();

        FileUtils.deleteRecursively(storeDir);
        if (storeDir.exists()) {
            throw new IllegalStateException("Could not delete graph");
        }
    }


    private static void createTestGraph(int sets, int setSize) throws Exception {
        final int rIdx;
        try (Transaction tx = db.beginTx();
             KernelTransaction stm = bridge.getKernelTransactionBoundToThisThread(true)) {
            rIdx = stm
                    .tokenWrite()
                    .relationshipTypeGetOrCreateForName(RELATIONSHIP_TYPE.name());
            tx.success();
        }

        final ArrayList<Runnable> runnables = new ArrayList<>();
        for (int i = 0; i < sets; i++) {
            runnables.add(createLine(setSize, rIdx));
        }
        ParallelUtil.run(runnables, Pools.DEFAULT);
    }

    private static Runnable createLine(int size, int rIdx) {
        return () -> {
            try (Transaction tx = db.beginTx();
                 KernelTransaction stm = bridge.getKernelTransactionBoundToThisThread(true)) {
                final Write op = stm.dataWrite();
                long node = op.nodeCreate();
                for (int i = 1; i < size; i++) {
                    final long temp = op.nodeCreate();
                    try {
                        op.relationshipCreate(node, rIdx, temp);
                    } catch (EntityNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                    node = temp;
                }
                tx.success();
            } catch (InvalidTransactionTypeKernelException | TransactionFailureException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Benchmark
    public Object parallelUnionFindQueue_200000() {
        return new ParallelUnionFindQueue(graph, Pools.DEFAULT, 200_000, 8)
                .compute();
    }

    @Benchmark
    public Object parallelUnionFindQueue_400000() {
        return new ParallelUnionFindQueue(graph, Pools.DEFAULT, 400_000, 8)
                .compute();
    }

    @Benchmark
    public Object parallelUnionFindQueue_800000() {
        return new ParallelUnionFindQueue(graph, Pools.DEFAULT, 800_000, 8)
                .compute();
    }

    @Benchmark
    public Object parallelUnionFindForkJoinMerge_400000() {
        return new ParallelUnionFindFJMerge(graph, Pools.DEFAULT, 400_000, 8)
                .compute();
    }

    @Benchmark
    public Object parallelUnionFindForkJoinMerge_800000() {
        return new ParallelUnionFindFJMerge(graph, Pools.DEFAULT, 800_000, 8)
                .compute();
    }

    @Benchmark
    public Object parallelUnionFindForkJoin_400000() {
        return new ParallelUnionFindForkJoin(graph, 400_000, 8)
                .compute();
    }

    @Benchmark
    public Object parallelUnionFindForkJoin_800000() {
        return new ParallelUnionFindForkJoin(graph, 800_000, 8)
                .compute();
    }

    // TODO: not a benchmark, it's eirther extremely slow or does not terminate rn
    public Object multiSourceColoring() {
        return new MSColoring(graph, Pools.DEFAULT, 8)
                .compute()
                .getColors();
    }

    @Benchmark
    public Object sequentialUnionFind() {
        return new GraphUnionFind(graph)
                .compute();

    }

}
