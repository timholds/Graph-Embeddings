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
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.neo4j.graphalgo.BetweennessCentralityProc;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphalgo.core.utils.ParallelUtil;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.impl.betweenness.BetweennessCentrality;
import org.neo4j.graphalgo.impl.betweenness.BetweennessCentralitySuccessorBrandes;
import org.neo4j.graphalgo.impl.betweenness.ParallelBetweennessCentrality;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 *   .0                 .0
 *  (a)                 (f)
 *   | \               / |
 *   |  \8.0  9.0  8.0/  |
 *   |  (c)---(d)---(e)  |
 *   |  /            \   |
 *   | /              \  |
 *  (b)                (g)
 *   .0                 .0
 * @author mknblch
 */
@RunWith(MockitoJUnitRunner.class)
public class BetweennessCentralityTest2 {

    private static GraphDatabaseAPI db;
    private static Graph graph;

    interface TestConsumer {
        void accept(String name, double centrality);
    }

    @Mock
    private TestConsumer testConsumer;

    @BeforeClass
    public static void setupGraph() throws KernelException {

        final String cypher =
                "CREATE (a:Node {name:'a'})\n" +
                        "CREATE (b:Node {name:'b'})\n" +
                        "CREATE (c:Node {name:'c'})\n" +
                        "CREATE (d:Node {name:'d'})\n" +
                        "CREATE (e:Node {name:'e'})\n" +
                        "CREATE (f:Node {name:'f'})\n" +
                        "CREATE (g:Node {name:'g'})\n" +

                        "CREATE" +
                        " (a)-[:TYPE]->(b),\n" +
                        " (a)-[:TYPE]->(c),\n" +
                        " (b)-[:TYPE]->(c),\n" +
                        " (c)-[:TYPE]->(d),\n" +
                        " (d)-[:TYPE]->(e),\n" +
                        " (e)-[:TYPE]->(f),\n" +
                        " (e)-[:TYPE]->(g),\n" +
                        " (f)-[:TYPE]->(g)";

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
                .forEach(r -> testConsumer.accept(name(r.nodeId), r.centrality));

        verifyMock(testConsumer);
    }

    @Test
    public void testPBC() throws Exception {

        new ParallelBetweennessCentrality(graph, Pools.DEFAULT, 4)
                .compute()
                .resultStream()
                .forEach(r -> testConsumer.accept(name(r.nodeId), r.centrality));

        verifyMock(testConsumer);
    }

    @Test
    public void testSuccessorBrandes() throws Exception {

        new BetweennessCentralitySuccessorBrandes(graph, Pools.DEFAULT)
                .compute()
                .resultStream()
                .forEach(r -> testConsumer.accept(name(r.nodeId), r.centrality));

        verifyMock(testConsumer);
    }

    public void verifyMock(TestConsumer mock) {
        verify(mock, times(1)).accept(eq("a"), eq(0.0));
        verify(mock, times(1)).accept(eq("b"), eq(0.0));
        verify(mock, times(1)).accept(eq("c"), eq(8.0));
        verify(mock, times(1)).accept(eq("d"), eq(9.0));
        verify(mock, times(1)).accept(eq("e"), eq(8.0));
        verify(mock, times(1)).accept(eq("f"), eq(0.0));
        verify(mock, times(1)).accept(eq("g"), eq(0.0));
    }


    @Test
    public void testIterateParallel() throws Exception {

        final AtomicIntegerArray ai = new AtomicIntegerArray(1001);

        ParallelUtil.iterateParallel(Pools.DEFAULT, 1001, 8, i -> {
            ai.set(i, i);
        });

        for (int i = 0; i < 1001; i++) {
            assertEquals(i, ai.get(i));
        }
    }

}
