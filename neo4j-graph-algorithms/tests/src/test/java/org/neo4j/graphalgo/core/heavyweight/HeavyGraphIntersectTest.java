package org.neo4j.graphalgo.core.heavyweight;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.utils.Intersections;
import org.neo4j.graphdb.Result;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class HeavyGraphIntersectTest {
    private GraphDatabaseAPI gdb;

    @Before
    public void setUp() throws Exception {
        gdb = org.neo4j.graphalgo.TestDatabaseCreator.createTestDatabase();
    }

    @After
    public void tearDown() throws Exception {
        gdb.shutdown();
    }

    @Test
    public void countTriangles() {
        String statement = "CREATE (a)-[:X]->(b)-[:X]->(c)-[:X]->(a), " +
                "(c)-[:X]->(d)-[:X]->(b), " +
                "(a)-[:X]->(e)," +
                "(c)-[:X]->(f) " +
                "RETURN [id(a),id(b),id(c),id(d),id(e),id(f)] as ids";
        // triangles: a: 2, b: 4, c:4, d: 2, e: 0, f:0
        List<Long> ids = gdb.execute(statement).<List<Long>>columnAs("ids").next();
        // System.out.println("ids = " + ids);
        assertEquals(ids.subList(0,4), assertTriangles());
    }

    private List<Long> assertTriangles() {
        HeavyGraph graph = (HeavyGraph)new GraphLoader(gdb)
                .asUndirected(true)
                .withSort(true)
                .load(HeavyGraphFactory.class);

        String triangleQuery = "MATCH (a)--(b)--(c)--(a) where a <> b and b <> c and c <> a " +
                " AND id(b) < id(c) " +
                "WITH [id(a),id(b),id(c)] as ids order by ids[0],ids[1],ids[2]" +
                "RETURN ids[0] as start, collect(ids) as ids order by start";
        // System.out.println(gdb.execute(triangleQuery).resultAsString());
        Result result = gdb.execute(triangleQuery);
        List<Long> foundIds = new ArrayList<>();
        while (result.hasNext()) {
            Map<String, Object> row = result.next();
            long start = (long)row.get("start");
            List<List<Long>> triangles = (List<List<Long>>) row.get("ids");
            List<List<Long>> found = new ArrayList<>(triangles.size());
            graph.intersectAll((int)start, (nodeA, nodeB, nodeC) -> found.add(asList(nodeA,nodeB,nodeC)));
            assertEquals(triangles,found);
            foundIds.add(start);
        }
        return foundIds;
    }

    private static final int[] ONE = {1};
    private static final int[] EMTPY = new int[0];
    private static final int[] ONE_TWO = {1, 2};

    @Test public void testIntersect() {
        assertArrayEquals(EMTPY, Intersections.getIntersection(EMTPY, ONE));
        assertArrayEquals(EMTPY, Intersections.getIntersection(ONE, EMTPY));
        assertArrayEquals(ONE, Intersections.getIntersection(ONE, ONE));
        assertArrayEquals(ONE, Intersections.getIntersection(ONE, ONE_TWO));
        assertArrayEquals(ONE, Intersections.getIntersection(ONE_TWO, ONE));
        assertArrayEquals(ONE_TWO, Intersections.getIntersection(ONE_TWO, ONE_TWO));
        assertArrayEquals(new int[]{2}, Intersections.getIntersection(ONE_TWO, new int[] {0,2,3,4}));
        assertArrayEquals(new int[]{6}, Intersections.getIntersection(new int[] {0,1,6,10}, new int[] {4,5,6}));
        assertArrayEquals(new int[]{4,6}, Intersections.getIntersection(new int[] {0,1,2,3,4,6,10}, new int[] {4,5,6}));
    }
}