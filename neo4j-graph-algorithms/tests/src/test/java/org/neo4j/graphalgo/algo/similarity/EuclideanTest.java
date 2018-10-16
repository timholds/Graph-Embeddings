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
package org.neo4j.graphalgo.algo.similarity;

import org.junit.*;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.similarity.EuclideanProc;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.Map;

import static java.lang.Math.sqrt;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.*;
import static org.neo4j.helpers.collection.MapUtil.map;

public class  EuclideanTest {

    private static GraphDatabaseAPI db;
    private Transaction tx;
    public static final String STATEMENT_STREAM = "MATCH (i:Item) WITH i ORDER BY id(i) MATCH (p:Person) OPTIONAL MATCH (p)-[r:LIKES]->(i)\n" +
            "WITH {item:id(p), weights: collect(coalesce(r.stars,0))} as userData\n" +
            "WITH collect(userData) as data\n" +
            "call algo.similarity.euclidean.stream(data,$config) " +
            "yield item1, item2, count1, count2, intersection, similarity " +
            "RETURN * ORDER BY item1,item2";

    public static final String STATEMENT = "MATCH (i:Item) WITH i ORDER BY id(i) MATCH (p:Person) OPTIONAL MATCH (p)-[r:LIKES]->(i)\n" +
            "WITH {item:id(p), weights: collect(coalesce(r.stars,0))} as userData\n" +
            "WITH collect(userData) as data\n" +

            "CALL algo.similarity.euclidean(data, $config) " +
            "yield p25, p50, p75, p90, p95, p99, p999, p100, nodes, similarityPairs " +
            "RETURN *";

    public static final String STORE_EMBEDDING_STATEMENT = "MATCH (i:Item) WITH i ORDER BY id(i) MATCH (p:Person) OPTIONAL MATCH (p)-[r:LIKES]->(i)\n" +
            "WITH p, collect(coalesce(r.stars,0)) as userData\n" +
            "SET p.embedding = userData";

    public static final String EMBEDDING_STATEMENT = "MATCH (p:Person)\n" +
            "WITH {item:id(p), weights: p.embedding} as userData\n" +
            "WITH collect(userData) as data\n" +

            "CALL algo.similarity.euclidean(data, $config) " +
            "yield p25, p50, p75, p90, p95, p99, p999, p100, nodes, similarityPairs " +
            "RETURN *";

    @BeforeClass
    public static void beforeClass() throws KernelException {
        db = TestDatabaseCreator.createTestDatabase();
        db.getDependencyResolver().resolveDependency(Procedures.class).registerProcedure(EuclideanProc.class);
        db.execute(buildDatabaseQuery()).close();
    }

    @AfterClass
    public static void AfterClass() {
        db.shutdown();
    }

    @Before
    public void setUp() throws Exception {
        tx = db.beginTx();
    }

    @After
    public void tearDown() throws Exception {
        tx.close();
    }

    private static void buildRandomDB(int size) {
        db.execute("MATCH (n) DETACH DELETE n").close();
        db.execute("UNWIND range(1,$size/10) as _ CREATE (:Person) CREATE (:Item) ",singletonMap("size",size)).close();
        String statement =
                "MATCH (p:Person) WITH collect(p) as people " +
                "MATCH (i:Item) WITH people, collect(i) as items " +
                "UNWIND range(1,$size) as _ " +
                "WITH people[toInteger(rand()*size(people))] as p, items[toInteger(rand()*size(items))] as i " +
                "MERGE (p)-[:LIKES]->(i) RETURN count(*) ";
        db.execute(statement,singletonMap("size",size)).close();
    }
    private static String buildDatabaseQuery() {
        return  "CREATE (a:Person {name:'Alice'})\n" +
                "CREATE (b:Person {name:'Bob'})\n" +
                "CREATE (c:Person {name:'Charlie'})\n" +
                "CREATE (d:Person {name:'Dana'})\n" +
                "CREATE (i1:Item {name:'p1'})\n" +
                "CREATE (i2:Item {name:'p2'})\n" +
                "CREATE (i3:Item {name:'p3'})\n" +

                "CREATE" +
                " (a)-[:LIKES {stars:1}]->(i1),\n" +
                " (a)-[:LIKES {stars:2}]->(i2),\n" +
                " (a)-[:LIKES {stars:5}]->(i3),\n" +
                " (b)-[:LIKES {stars:1}]->(i1),\n" +
                " (b)-[:LIKES {stars:3}]->(i2),\n" +
                " (c)-[:LIKES {stars:4}]->(i3)\n";
        // a: 1,2,5
        // b: 1,3,0
        // c: 0,0,4
        // a - b: sqrt(26) = 5.1
        // a - c: sqrt(6) = 2.5
        // b - c: sqrt(26) = 5.1
    }


    @Test
    public void euclideanSingleMultiThreadComparision() {
        int size = 333;
        buildRandomDB(size);
        Result result1 = db.execute(STATEMENT_STREAM, map("config", map("similarityCutoff",-0.1,"concurrency", 1)));
        Result result2 = db.execute(STATEMENT_STREAM, map("config", map("similarityCutoff",-0.1,"concurrency", 2)));
        Result result4 = db.execute(STATEMENT_STREAM, map("config", map("similarityCutoff",-0.1,"concurrency", 4)));
        Result result8 = db.execute(STATEMENT_STREAM, map("config", map("similarityCutoff",-0.1,"concurrency", 8)));
        int count=0;
        while (result1.hasNext()) {
            Map<String, Object> row1 = result1.next();
            assertEquals(row1.toString(), row1,result2.next());
            assertEquals(row1.toString(), row1,result4.next());
            assertEquals(row1.toString(), row1,result8.next());
            count++;
        }
        int people = size/10;
        assertEquals((people * people - people)/2,count);
    }

    @Test
    public void euclideanSingleMultiThreadComparisionTopK() {
        int size = 333;
        buildRandomDB(size);

        Result result1 = db.execute(STATEMENT_STREAM, map("config", map("similarityCutoff",-0.1,"topK",1,"concurrency", 1)));
        Result result2 = db.execute(STATEMENT_STREAM, map("config", map("similarityCutoff",-0.1,"topK",1,"concurrency", 2)));
        Result result4 = db.execute(STATEMENT_STREAM, map("config", map("similarityCutoff",-0.1,"topK",1,"concurrency", 4)));
        Result result8 = db.execute(STATEMENT_STREAM, map("config", map("similarityCutoff",-0.1,"topK",1,"concurrency", 8)));
        int count=0;
        while (result1.hasNext()) {
            Map<String, Object> row1 = result1.next();
            assertEquals(row1.toString(), row1,result2.next());
            assertEquals(row1.toString(), row1,result4.next());
            assertEquals(row1.toString(), row1,result8.next());
            count++;
        }
        int people = size/10;
        assertEquals(people,count);
    }

    @Test
    public void topNeuclideanStreamTest() {
        Result results = db.execute(STATEMENT_STREAM, map("config",map("top",2)));
        assert02(results.next());
        assert13(results.next());
        assertFalse(results.hasNext());
    }

    @Test
    public void euclideanStreamTest() {
        // System.out.println(db.execute("MATCH (i:Item) WITH i ORDER BY id(i) MATCH (p:Person) OPTIONAL MATCH (p)-[r:LIKES]->(i) RETURN p,r,i").resultAsString());
        // a: 1,2,5
        // b: 1,3,0
        // c: 0,0,4
        // d: 0,0,0
        // a0 - b1: sqrt(26) = 5.1
        // a0 - c2: sqrt(6) = 2.5
        // a0 - d3: sqrt(1+4+25) = 5.5
        // b1 - c2: sqrt(26) = 5.1
        // b1 - d3: sqrt(10) =  3.2
        // c2 - d3: sqrt(16) = 4
        // System.out.println(db.execute(query).resultAsString());

        Result results = db.execute(STATEMENT_STREAM, map("config",map("concurrency",1)));
        assertTrue(results.hasNext());
        assert01(results.next());
        assert02(results.next());
        assert03(results.next());
        assert12(results.next());
        assert13(results.next());
        assert23(results.next());
        assertFalse(results.hasNext());
    }

    @Test
    public void topKEuclideanStreamTest() {
        Map<String, Object> params = map("config", map( "concurrency", 1,"topK", 1));

        Result results = db.execute(STATEMENT_STREAM, params);
        assertTrue(results.hasNext());
        assert02(results.next());
        assert13(results.next());
        assert02(flip(results.next()));
        assert13(flip(results.next()));
        assertFalse(results.hasNext());
    }

    private Map<String, Object> flip(Map<String, Object> row) {
        return map("similarity", row.get("similarity"),"intersection", row.get("intersection"),
                "item1",row.get("item2"),"count1",row.get("count2"),
                "item2",row.get("item1"),"count2",row.get("count1"));
    }

    private void assertSameSource(Result results, int count, long source) {
        Map<String, Object> row;
        long target = 0;
        for (int i = 0; i<count; i++) {
            if (target == source) target++;
            assertTrue(results.hasNext());
            row = results.next();
            assertEquals(source, row.get("item1"));
            assertEquals(target, row.get("item2"));
            target++;
        }
    }


    @Test
    public void topK4euclideanStreamTest() {
        Map<String, Object> params = map("config", map("topK", 4, "concurrency", 4, "similarityCutoff", -0.1));
        System.out.println(db.execute(STATEMENT_STREAM,params).resultAsString());

        Result results = db.execute(STATEMENT_STREAM,params);
        assertSameSource(results, 3, 0L);
        assertSameSource(results, 3, 1L);
        assertSameSource(results, 3, 2L);
        assertSameSource(results, 3, 3L);
        assertFalse(results.hasNext());
    }

    @Test
    public void topK3euclideanStreamTest() {
        // a0 - b1: sqrt(26) = 5.1
        // a0 - c2: sqrt(6) = 2.5
        // a0 - d3: sqrt(1+4+25) = 5.5
        // b1 - c2: sqrt(26) = 5.1
        // b1 - d3: sqrt(10) =  3.2
        // c2 - d3: sqrt(16) = 4
        Map<String, Object> params = map("config", map("concurrency", 3, "topK", 3));

        System.out.println(db.execute(STATEMENT_STREAM, params).resultAsString());

        Result results = db.execute(STATEMENT_STREAM, params);
        assertSameSource(results, 3, 0L);
        assertSameSource(results, 3, 1L);
        assertSameSource(results, 3, 2L);
        assertSameSource(results, 3, 3L);
        assertFalse(results.hasNext());
    }

    @Test
    public void simpleEuclideanTest() {
        Map<String, Object> params = map("config", map());

        Map<String, Object> row = db.execute(STATEMENT,params).next();
        assertEquals((double) row.get("p25"), 3.16, 0.01);
        assertEquals((double) row.get("p50"), 4.00, 0.01);
        assertEquals((double) row.get("p75"), 5.10, 0.01);
        assertEquals((double) row.get("p95"), 5.48, 0.01);
        assertEquals((double) row.get("p99"), 5.48, 0.01);
        assertEquals((double) row.get("p100"), 5.48, 0.01);
    }

    @Test
    public void simpleEuclideanFromEmbeddingTest() {
        db.execute(STORE_EMBEDDING_STATEMENT);

        Map<String, Object> params = map("config", map());

        Map<String, Object> row = db.execute(EMBEDDING_STATEMENT,params).next();
        assertEquals((double) row.get("p25"), 3.16, 0.01);
        assertEquals((double) row.get("p50"), 4.00, 0.01);
        assertEquals((double) row.get("p75"), 5.10, 0.01);
        assertEquals((double) row.get("p95"), 5.48, 0.01);
        assertEquals((double) row.get("p99"), 5.48, 0.01);
        assertEquals((double) row.get("p100"), 5.48, 0.01);
    }

    @Test
    public void simpleEuclideanWriteTest() {
        Map<String, Object> params = map("config", map( "write",true, "similarityCutoff", 4.0));

        db.execute(STATEMENT,params).close();

        String checkSimilaritiesQuery = "MATCH (a)-[similar:SIMILAR]-(b)" +
                "RETURN a.name AS node1, b.name as node2, similar.score AS score " +
                "ORDER BY id(a), id(b)";

        System.out.println(db.execute(checkSimilaritiesQuery).resultAsString());
        Result result = db.execute(checkSimilaritiesQuery);

        // a0 - b1: sqrt(26) = 5.1
        // a0 - c2: sqrt(6) = 2.5
        // a0 - d3: sqrt(1+4+25) = 5.5
        // b1 - c2: sqrt(26) = 5.1
        // b1 - d3: sqrt(10) =  3.2
        // c2 - d3: sqrt(16) = 4

        assertTrue(result.hasNext());
        Map<String, Object> row = result.next();
        assertEquals(row.get("node1"), "Alice");
        assertEquals(row.get("node2"), "Charlie");
        assertEquals((double) row.get("score"), 2.45, 0.01);

        assertTrue(result.hasNext());
        row = result.next();
        assertEquals(row.get("node1"), "Bob");
        assertEquals(row.get("node2"), "Dana");
        assertEquals((double) row.get("score"), 3.16, 0.01);

        assertTrue(result.hasNext());
        row = result.next();
        assertEquals(row.get("node1"), "Charlie");
        assertEquals(row.get("node2"), "Alice");
        assertEquals((double) row.get("score"), 2.45, 0.01);


        assertTrue(result.hasNext());
        row = result.next();
        assertEquals(row.get("node1"), "Charlie");
        assertEquals(row.get("node2"), "Dana");
        assertEquals((double) row.get("score"), 4.0, 0.01);

        assertTrue(result.hasNext());
        row = result.next();
        assertEquals(row.get("node1"), "Dana");
        assertEquals(row.get("node2"), "Bob");
        assertEquals((double) row.get("score"), 3.16, 0.01);

        assertTrue(result.hasNext());
        row = result.next();
        assertEquals(row.get("node1"), "Dana");
        assertEquals(row.get("node2"), "Charlie");
        assertEquals((double) row.get("score"), 4.0, 0.01);

        assertFalse(result.hasNext());
    }

    private void assert23(Map<String, Object> row) {
        assertEquals(2L, row.get("item1"));
        assertEquals(3L, row.get("item2"));
        assertEquals(1L, row.get("count1"));
        assertEquals(0L, row.get("count2"));
        assertEquals(0L, row.get("intersection"));
        assertEquals(sqrt(16), row.get("similarity"));
    }

    private void assert13(Map<String, Object> row) {
        assertEquals(1L, row.get("item1"));
        assertEquals(3L, row.get("item2"));
        assertEquals(2L, row.get("count1"));
        assertEquals(0L, row.get("count2"));
        assertEquals(0L, row.get("intersection"));
        assertEquals(sqrt(10), row.get("similarity"));
    }

    private void assert12(Map<String, Object> row) {
        assertEquals(1L, row.get("item1"));
        assertEquals(2L, row.get("item2"));
        assertEquals(2L, row.get("count1"));
        assertEquals(1L, row.get("count2"));
        // assertEquals(0L, row.get("intersection"));
        assertEquals(sqrt(5*5+1), row.get("similarity"));
    }

    private void assert03(Map<String, Object> row) {
        assertEquals(0L, row.get("item1"));
        assertEquals(3L, row.get("item2"));
        assertEquals(3L, row.get("count1"));
        assertEquals(0L, row.get("count2"));
        assertEquals(0L, row.get("intersection"));
        assertEquals(sqrt(5*5+2*2+1), row.get("similarity"));
    }

    private void assert02(Map<String, Object> row) {
        assertEquals(0L, row.get("item1"));
        assertEquals(2L, row.get("item2"));
        assertEquals(3L, row.get("count1"));
        assertEquals(1L, row.get("count2"));
        // assertEquals(1L, row.get("intersection"));
        assertEquals(sqrt(6), row.get("similarity"));
    }

    private void assert01(Map<String, Object> row) {
        assertEquals(0L, row.get("item1"));
        assertEquals(1L, row.get("item2"));
        assertEquals(3L, row.get("count1"));
        assertEquals(2L, row.get("count2"));
        // assertEquals(2L, row.get("intersection"));
        assertEquals(sqrt(5*5+1), row.get("similarity"));
    }
}
