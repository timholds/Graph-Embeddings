package org.neo4j.graphalgo.algo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.neo4j.graphalgo.LabelPropagationProc;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.helpers.Exceptions;
import org.neo4j.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestGraphDatabaseFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class LabelPropagationProcIntegrationTest {

    private static final String DB_CYPHER = "" +
            "CREATE (a:A {id: 0, partition: 42}) " +
            "CREATE (b:B {id: 1, partition: 42}) " +

            "CREATE (a)-[:X]->(:A {id: 2, weight: 1.0, partition: 1}) " +
            "CREATE (a)-[:X]->(:A {id: 3, weight: 2.0, partition: 1}) " +
            "CREATE (a)-[:X]->(:A {id: 4, weight: 1.0, partition: 1}) " +
            "CREATE (a)-[:X]->(:A {id: 5, weight: 1.0, partition: 1}) " +
            "CREATE (a)-[:X]->(:A {id: 6, weight: 8.0, partition: 2}) " +

            "CREATE (b)-[:X]->(:B {id: 7, weight: 1.0, partition: 1}) " +
            "CREATE (b)-[:X]->(:B {id: 8, weight: 2.0, partition: 1}) " +
            "CREATE (b)-[:X]->(:B {id: 9, weight: 1.0, partition: 1}) " +
            "CREATE (b)-[:X]->(:B {id: 10, weight: 1.0, partition: 1}) " +
            "CREATE (b)-[:X]->(:B {id: 11, weight: 8.0, partition: 2})";

    @Parameterized.Parameters(name = "parallel={0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[]{false},
                new Object[]{true}
        );
    }

    private GraphDatabaseAPI db;
    private final boolean parallel;

    public LabelPropagationProcIntegrationTest(boolean parallel) {
        this.parallel = parallel;
    }

    @Before
    public void setup() throws KernelException {
        db = (GraphDatabaseAPI)
                new TestGraphDatabaseFactory()
                        .newImpermanentDatabaseBuilder()
                        .newGraphDatabase();
        try (Transaction tx = db.beginTx()) {
            db.execute(DB_CYPHER).close();
            tx.success();
        }

        db.getDependencyResolver()
                .resolveDependency(Procedures.class)
                .registerProcedure(LabelPropagationProc.class);
    }

    @After
    public void shutdown() {
        db.shutdown();
    }

    @Test
    public void shouldUseDefaultValues() throws Exception {
        String query = "CALL algo.labelPropagation()";

        runQuery(query, row -> {
            assertEquals(1, row.getNumber("iterations").intValue());
            assertEquals("weight", row.getString("weightProperty"));
            assertEquals("partition", row.getString("partitionProperty"));
            assertTrue(row.getBoolean("write"));
        });
    }

    @Test
    public void shouldTakeParametersFromConfig() throws Exception {
        String query = "CALL algo.labelPropagation(null, null, null, {iterations:5,write:false,weightProperty:'score',partitionProperty:'key'})";

        runQuery(query, row -> {
            assertEquals(5, row.getNumber("iterations").intValue());
            assertFalse(row.getBoolean("write"));
            assertEquals("score", row.getString("weightProperty"));
            assertEquals("key", row.getString("partitionProperty"));
        });
    }

    @Test
    public void shouldRunLabelPropagation() throws Exception {
        String query = parallel
                ? "CALL algo.labelPropagation(null, 'X', 'OUTGOING', {batchSize:1})"
                : "CALL algo.labelPropagation(null, 'X')";
        String check = "MATCH (n) WHERE n.id IN [0,1] RETURN n.partition AS partition";

        runQuery(query, row -> {
            assertEquals(2, row.getNumber("nodes").intValue());
            assertTrue(row.getBoolean("write"));

            assertTrue(
                    "load time not set",
                    row.getNumber("loadMillis").intValue() >= 0);
            assertTrue(
                    "compute time not set",
                    row.getNumber("computeMillis").intValue() >= 0);
            assertTrue(
                    "write time not set",
                    row.getNumber("writeMillis").intValue() >= 0);
        });
        runQuery(check, row ->
                assertEquals(2, row.getNumber("partition").intValue()));
    }

    @Test
    public void shouldFallbackToNodeIdsForNonExistingPartitionKey() throws Exception {
        String query = parallel
                ? "CALL algo.labelPropagation(null, 'X', 'OUTGOING', {partitionProperty: 'foobar', batchSize:1})"
                : "CALL algo.labelPropagation(null, 'X', 'OUTGOING', {partitionProperty: 'foobar'})";
        String checkA = "MATCH (n) WHERE n.id = 0 RETURN n.foobar as partition";
        String checkB = "MATCH (n) WHERE n.id = 1 RETURN n.foobar as partition";

        runQuery(query, row ->
            assertEquals("foobar", row.getString("partitionProperty")));
        runQuery(checkA, row ->
                assertEquals(6, row.getNumber("partition").intValue()));
        runQuery(checkB, row ->
                assertEquals(11, row.getNumber("partition").intValue()));
    }

    @Test
    public void shouldFilterByLabel() throws Exception {
        String query = parallel
                ? "CALL algo.labelPropagation('A', 'X', 'OUTGOING', {batchSize:1})"
                : "CALL algo.labelPropagation('A', 'X')";
        String checkA = "MATCH (n) WHERE n.id = 0 RETURN n.partition as partition";
        String checkB = "MATCH (n) WHERE n.id = 1 RETURN n.partition as partition";

        runQuery(query);
        runQuery(checkA, row ->
                assertEquals(2, row.getNumber("partition").intValue()));
        runQuery(checkB, row ->
                assertEquals(42, row.getNumber("partition").intValue()));
    }

    @Test
    public void shouldPropagateIncoming() throws Exception {
        String query = parallel
                ? "CALL algo.labelPropagation('A', 'X', 'INCOMING', {batchSize:1})"
                : "CALL algo.labelPropagation('A', 'X', 'INCOMING')";
        String check = "MATCH (n:A) WHERE n.id <> 0 RETURN n.partition as partition";

        runQuery(query);
        runQuery(check, row ->
                assertEquals(42, row.getNumber("partition").intValue()));
    }

    @Test
    public void shouldNotPropagateBoth() throws Exception {
        String query = "CALL algo.labelPropagation('A', 'X', 'BOTH')";
        try {
            runQuery(query);
            fail();
        } catch (QueryExecutionException e) {
            Throwable cause = Exceptions.peel(e, ((Predicate<Throwable>) IllegalArgumentException.class::isInstance).negate());
            assertEquals(
                    "Direction BOTH is not allowed. Legal values are '[OUTGOING, INCOMING]'.",
                    cause.getMessage());
        }
    }

    private void runQuery(String query) {
        runQuery(query, row -> {});
    }

    private void runQuery(
            String query,
            Consumer<Result.ResultRow> check) {
        try (Transaction tx = db.beginTx();
             Result result = db.execute(query)) {
            result.accept(row -> {
                check.accept(row);
                return true;
            });
            tx.success();
        }
    }
}
