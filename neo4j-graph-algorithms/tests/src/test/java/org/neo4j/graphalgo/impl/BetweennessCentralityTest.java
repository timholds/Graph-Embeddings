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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.graphalgo.BetweennessCentralityProc;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphalgo.core.utils.AtomicDoubleArray;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.impl.betweenness.*;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 *  (A)-->(B)-->(C)-->(D)-->(E)
 *  0.0   3.0   4.0   3.0   0.0
 *
 * @author mknblch
 */
public class BetweennessCentralityTest {

    private static GraphDatabaseAPI db;
    private static Graph graph;

    @BeforeClass
    public static void setupGraph() throws KernelException {

        final String cypher =
                "CREATE (a:Node {name:'a'})\n" +
                        "CREATE (b:Node {name:'b'})\n" +
                        "CREATE (c:Node {name:'c'})\n" +
                        "CREATE (d:Node {name:'d'})\n" +
                        "CREATE (e:Node {name:'e'})\n" +
                        "CREATE" +
                        " (a)-[:TYPE]->(b),\n" +
                        " (b)-[:TYPE]->(c),\n" +
                        " (c)-[:TYPE]->(d),\n" +
                        " (d)-[:TYPE]->(e)";


        db = TestDatabaseCreator.createTestDatabase();

        try (Transaction tx = db.beginTx()) {
            db.execute(cypher);
            tx.success();
        }

        db.getDependencyResolver()
                .resolveDependency(Procedures.class)
                .registerProcedure(BetweennessCentralityProc.class);

        graph = new GraphLoader(db)
                .withAnyRelationshipType()
                .withAnyLabel()
                .withoutNodeProperties()
                .load(HeavyGraphFactory.class);

    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (db != null) db.shutdown();
        graph = null;
    }

    private String name(long id) {
        String[] name = {""};
        db.execute("MATCH (n:Node) WHERE id(n) = " + id + " RETURN n.name as name")
                .accept(row -> {
                    name[0] = row.getString("name");
                    return false;
                });
        return name[0];
    }

    @Test
    public void testBC() throws Exception {

        new BetweennessCentrality(graph)
                .compute()
                .resultStream()
                .forEach(r -> System.out.println(name(r.nodeId) + " -> " + r.centrality));
    }

    @Test
    public void testMBC() throws Exception {

        new MaxDepthBetweennessCentrality(graph, 3)
                .compute()
                .resultStream()
                .forEach(r -> System.out.println(name(r.nodeId) + " -> " + r.centrality));
    }


    @Test
    public void testRABrandes() throws Exception {

        new RABrandesBetweennessCentrality(graph, Pools.DEFAULT, 3, new RandomSelectionStrategy(graph, 0.3))
                .compute()
                .resultStream()
                .forEach(r -> System.out.println(name(r.nodeId) + " -> " + r.centrality));
    }

    @Test
    public void testPBC() throws Exception {

        new ParallelBetweennessCentrality(graph, Pools.DEFAULT, 4)
                .compute()
                .resultStream()
                .forEach(r -> System.out.println(name(r.nodeId) + " -> " + r.centrality));
    }

    @Test
    public void testSuccessorBrandes() throws Exception {

        final TestConsumer mock = mock(TestConsumer.class);

        final BetweennessCentralitySuccessorBrandes bc =
                new BetweennessCentralitySuccessorBrandes(graph, Pools.DEFAULT)
                        .compute();

        final AtomicDoubleArray centrality = bc.getCentrality();


        for (int i = 0; i < centrality.length(); i++) {
            System.out.println(i + ":" + centrality.get(i));
        }

        bc.resultStream().forEach(r -> mock.consume(name(r.nodeId), r.centrality));

        verify(mock, times(1)).consume(eq("a"), eq(0.0));
        verify(mock, times(1)).consume(eq("b"), eq(3.0));
        verify(mock, times(1)).consume(eq("c"), eq(4.0));
        verify(mock, times(1)).consume(eq("d"), eq(3.0));
        verify(mock, times(1)).consume(eq("e"), eq(0.0));
    }

    interface TestConsumer {

        void consume(String name, double centrality);
    }
}
