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
package org.neo4j.graphalgo.impl;

import com.carrotsearch.hppc.IntDoubleMap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.graphalgo.TestDatabaseCreator;

import static org.junit.Assert.assertEquals;


/**         5     5      5
 *      (1)---(2)---(3)----.
 *    5/ 2\2  2 \2  2 \2  2 \
 *  (S)---(7)---(8)---(9)---(X)--//->(S)
 *    3\  /3 3  /3 3  /3 3  /
 *      (4)---(5)---(6)----°
 *
 * S->X: {S,G,H,I,X}:8, {S,D,E,F,X}:12, {S,A,B,C,X}:20
 */
public final class ShortestPathsTest {

    private static GraphDatabaseAPI api;

    private static Graph graph;

    private static long head, tail, outstanding;

    @BeforeClass
    public static void setup() {
        final String cypher =
                "CREATE (s:Node {name:'s'})\n" +
                        "CREATE (a:Node {name:'a'})\n" +
                        "CREATE (b:Node {name:'b'})\n" +
                        "CREATE (c:Node {name:'c'})\n" +
                        "CREATE (d:Node {name:'d'})\n" +
                        "CREATE (e:Node {name:'e'})\n" +
                        "CREATE (f:Node {name:'f'})\n" +
                        "CREATE (g:Node {name:'g'})\n" +
                        "CREATE (h:Node {name:'h'})\n" +
                        "CREATE (i:Node {name:'i'})\n" +
                        "CREATE (x:Node {name:'x'})\n" +
                        "CREATE (q:Node {name:'q'})\n" + // outstanding node
                        "CREATE" +
                        " (s)-[:TYPE {cost:5}]->(a),\n" +
                        " (a)-[:TYPE {cost:5}]->(b),\n" +
                        " (b)-[:TYPE {cost:5}]->(c),\n" +
                        " (c)-[:TYPE {cost:5}]->(x),\n" +

                        " (a)-[:TYPE {cost:2}]->(g),\n" +
                        " (b)-[:TYPE {cost:2}]->(h),\n" +
                        " (c)-[:TYPE {cost:2}]->(i),\n" +

                        " (s)-[:TYPE {cost:3}]->(d),\n" +
                        " (d)-[:TYPE {cost:3}]->(e),\n" +
                        " (e)-[:TYPE {cost:3}]->(f),\n" +
                        " (f)-[:TYPE {cost:3}]->(x),\n" +

                        " (d)-[:TYPE {cost:3}]->(g),\n" +
                        " (e)-[:TYPE {cost:3}]->(h),\n" +
                        " (f)-[:TYPE {cost:3}]->(i),\n" +

                        " (s)-[:TYPE {cost:2}]->(g),\n" +
                        " (g)-[:TYPE {cost:2}]->(h),\n" +
                        " (h)-[:TYPE {cost:2}]->(i),\n" +
                        " (i)-[:TYPE {cost:2}]->(x),\n" +

                        " (x)-[:TYPE {cost:2}]->(s)"; // create cycle

        api = TestDatabaseCreator.createTestDatabase();
        try (Transaction tx = api.beginTx()) {
            api.execute(cypher);
            tx.success();
        }

        head = getNode("s").getId();
        tail = getNode("x").getId();
        outstanding = getNode("q").getId();

        graph = new GraphLoader(api)
                .withLabel("Node")
                .withRelationshipType("TYPE")
                .withRelationshipWeightsFromProperty("cost", Double.MAX_VALUE)
                .load(HeavyGraphFactory.class);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (api != null) api.shutdown();
        graph = null;
    }

    @Test
    public void testPaths() throws Exception {

        final ShortestPaths sssp = new ShortestPaths(graph);

        final IntDoubleMap sp = sssp.compute(head)
                .getShortestPaths();

        assertEquals(8, sp.get(graph.toMappedNodeId(tail)),0.1);
        assertEquals(Double.POSITIVE_INFINITY, sp.get(graph.toMappedNodeId(outstanding)),0.1);
    }

    public static Node getNode(String name) {
        final Node[] node = new Node[1];
        api.execute("MATCH (n:Node) WHERE n.name = '" + name + "' RETURN n").accept(row -> {
            node[0] = row.getNode("n");
            return false;
        });
        return node[0];
    }
}
