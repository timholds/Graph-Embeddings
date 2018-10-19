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
package org.neo4j.graphalgo.algo;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.neo4j.graphalgo.PageRankProc;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class PersonalizedPageRankProcIntegrationTest {

    private static GraphDatabaseAPI db;
    private static Map<Long, Double> expected = new HashMap<>();

    private static final String DB_CYPHER = "" +
            "CREATE (a:Label1 {name:\"a\"})\n" +
            "CREATE (b:Label1 {name:\"b\"})\n" +
            "CREATE (c:Label1 {name:\"c\"})\n" +
            "CREATE (d:Label1 {name:\"d\"})\n" +
            "CREATE (e:Label1 {name:\"e\"})\n" +
            "CREATE (f:Label1 {name:\"f\"})\n" +
            "CREATE (g:Label1 {name:\"g\"})\n" +
            "CREATE (h:Label1 {name:\"h\"})\n" +
            "CREATE (i:Label1 {name:\"i\"})\n" +
            "CREATE (j:Label1 {name:\"j\"})\n" +
            "CREATE (k:Label2 {name:\"k\"})\n" +
            "CREATE (l:Label2 {name:\"l\"})\n" +
            "CREATE (m:Label2 {name:\"m\"})\n" +
            "CREATE (n:Label2 {name:\"n\"})\n" +
            "CREATE (o:Label2 {name:\"o\"})\n" +
            "CREATE (p:Label2 {name:\"p\"})\n" +
            "CREATE (q:Label2 {name:\"q\"})\n" +
            "CREATE (r:Label2 {name:\"r\"})\n" +
            "CREATE (s:Label2 {name:\"s\"})\n" +
            "CREATE (t:Label2 {name:\"t\"})\n" +
            "CREATE\n" +
            "  (b)-[:TYPE1{foo:1.0}]->(c),\n" +
            "  (c)-[:TYPE1{foo:1.2}]->(b),\n" +
            "  (d)-[:TYPE1{foo:1.3}]->(a),\n" +
            "  (d)-[:TYPE1{foo:1.7}]->(b),\n" +
            "  (e)-[:TYPE1{foo:1.1}]->(b),\n" +
            "  (e)-[:TYPE1{foo:2.2}]->(d),\n" +
            "  (e)-[:TYPE1{foo:1.5}]->(f),\n" +
            "  (f)-[:TYPE1{foo:3.5}]->(b),\n" +
            "  (f)-[:TYPE1{foo:2.9}]->(e),\n" +
            "  (g)-[:TYPE2{foo:3.2}]->(b),\n" +
            "  (g)-[:TYPE2{foo:5.3}]->(e),\n" +
            "  (h)-[:TYPE2{foo:9.5}]->(b),\n" +
            "  (h)-[:TYPE2{foo:0.3}]->(e),\n" +
            "  (i)-[:TYPE2{foo:5.4}]->(b),\n" +
            "  (i)-[:TYPE2{foo:3.2}]->(e),\n" +
            "  (j)-[:TYPE2{foo:9.5}]->(e),\n" +
            "  (k)-[:TYPE2{foo:4.2}]->(e)\n";

    @AfterClass
    public static void tearDown() throws Exception {
        if (db != null) db.shutdown();
    }

    @BeforeClass
    public static void setup() throws KernelException {
        db = TestDatabaseCreator.createTestDatabase();
        try (Transaction tx = db.beginTx()) {
            db.execute(DB_CYPHER).close();
            tx.success();
        }

        db.getDependencyResolver()
                .resolveDependency(Procedures.class)
                .registerProcedure(PageRankProc.class);


        try (Transaction tx = db.beginTx()) {
            final Label label = Label.label("Label1");
            expected.put(db.findNode(label, "name", "a").getId(), 0.243);
            expected.put(db.findNode(label, "name", "b").getId(), 1.844);
            expected.put(db.findNode(label, "name", "c").getId(), 1.777);
            expected.put(db.findNode(label, "name", "d").getId(), 0.218);
            expected.put(db.findNode(label, "name", "e").getId(), 0.243);
            expected.put(db.findNode(label, "name", "f").getId(), 0.218);
            expected.put(db.findNode(label, "name", "g").getId(), 0.150);
            expected.put(db.findNode(label, "name", "h").getId(), 0.150);
            expected.put(db.findNode(label, "name", "i").getId(), 0.150);
            expected.put(db.findNode(label, "name", "j").getId(), 0.150);
            tx.success();
        }
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[]{"Heavy"},
                new Object[]{"Light"},
                new Object[]{"Kernel"},
                new Object[]{"Huge"}
        );
    }

    @Parameterized.Parameter
    public String graphImpl;

    @Test
    public void testPageRankStream() throws Exception {
        final Map<Long, Double> actual = new HashMap<>();
        runQuery(
                "CALL algo.pageRank.stream('Label1', 'TYPE1', {graph:'"+graphImpl+"'}) YIELD node, score",
                row -> actual.put(
                        row.getNode("node").getId(),
                        (Double) row.get("score")));

        assertMapEquals(expected, actual);
    }

    @Test
    public void testPageRankWriteBack() throws Exception {
        runQuery(
                "CALL algo.pageRank('Label1', 'TYPE1', {graph:'"+graphImpl+"'}) YIELD writeMillis, write, writeProperty",
                row -> {
                    assertTrue(row.getBoolean("write"));
                    assertEquals("pagerank", row.getString("writeProperty"));
                    assertTrue(
                            "write time not set",
                            row.getNumber("writeMillis").intValue() >= 0);
                });

        assertResult("pagerank");
    }

    @Test
    public void testPageRankWriteBackUnderDifferentProperty() throws Exception {
        runQuery(
                "CALL algo.pageRank('Label1', 'TYPE1', {writeProperty:'foobar', graph:'"+graphImpl+"'}) YIELD writeMillis, write, writeProperty",
                row -> {
                    assertTrue(row.getBoolean("write"));
                    assertEquals("foobar", row.getString("writeProperty"));
                    assertTrue(
                            "write time not set",
                            row.getNumber("writeMillis").intValue() >= 0);
                });

        assertResult("foobar");
    }

    @Test
    public void testPageRankParallelWriteBack() throws Exception {
        runQuery(
                "CALL algo.pageRank('Label1', 'TYPE1', {batchSize:3, write:true, graph:'"+graphImpl+"'}) YIELD writeMillis, write, writeProperty",
                row -> assertTrue(
                        "write time not set",
                        row.getNumber("writeMillis").intValue() >= 0));

        assertResult("pagerank");
    }

    @Test
    public void testPageRankParallelExecution() throws Exception {
        final Map<Long, Double> actual = new HashMap<>();
        runQuery(
                "CALL algo.pageRank.stream('Label1', 'TYPE1', {batchSize:2, graph:'"+graphImpl+"'}) YIELD nodeId, node, score",
                row -> {
                    final long nodeId = row.getNumber("nodeId").longValue();
                    final Node node = row.getNode("node");
                    assertEquals(node.getId(), nodeId);
                    actual.put(nodeId, (Double) row.get("score"));
                });
        assertMapEquals(expected, actual);
    }

    private static void runQuery(
            String query,
            Consumer<Result.ResultRow> check) {
        runQuery(query, new HashMap<>(), check);
    }

    private static void runQuery(
            String query,
            Map<String, Object> params,
            Consumer<Result.ResultRow> check) {
        try (Result result = db.execute(query, params)) {
            result.accept(row -> {
                check.accept(row);
                return true;
            });
        }
    }

    private void assertResult(final String scoreProperty) {
        try (Transaction tx = db.beginTx()) {
            for (Map.Entry<Long, Double> entry : expected.entrySet()) {
                double score = ((Number) db
                        .getNodeById(entry.getKey())
                        .getProperty(scoreProperty)).doubleValue();
                assertEquals(
                        "score for " + entry.getKey(),
                        entry.getValue(),
                        score,
                        0.1);
            }
            tx.success();
        }
    }

    private static void assertMapEquals(
            Map<Long, Double> expected,
            Map<Long, Double> actual) {
        assertEquals("number of elements", expected.size(), actual.size());
        HashSet<Long> expectedKeys = new HashSet<>(expected.keySet());
        for (Map.Entry<Long, Double> entry : actual.entrySet()) {
            assertTrue(
                    "unknown key " + entry.getKey(),
                    expectedKeys.remove(entry.getKey()));
            assertEquals(
                    "value for " + entry.getKey(),
                    expected.get(entry.getKey()),
                    entry.getValue(),
                    0.1);
        }
        for (Long expectedKey : expectedKeys) {
            fail("missing key " + expectedKey);
        }
    }
}
