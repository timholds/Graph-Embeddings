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
import org.neo4j.graphalgo.ShortestPathDeltaSteppingProc;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.DoubleConsumer;

import static org.junit.Assert.assertNotEquals;
import static org.mockito.AdditionalMatchers.eq;
import static org.mockito.Matchers.anyDouble;
import static org.mockito.Mockito.*;


/**         5     5      5
 *      (A)---(B)---(C)----.
 *    5/ 2    2     2     2 \
 *  (S)---(G)---(H)---(I)---(X)
 *    3\    3     3     3   /
 *      (D)---(E)---(F)----°
 *
 * S->X: {S,G,H,I,X}:8, {S,D,E,F,X}:12, {S,A,B,C,X}:20
 */
@RunWith(Parameterized.class)
public final class ShortestPathDeltaSteppingProcUndirectedTest {

    private static GraphDatabaseAPI api;

    @BeforeClass
    public static void setup() throws KernelException {
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
                        "CREATE" +

                        " (s)-[:TYPE {cost:5}]->(a),\n" + // line 1
                        " (a)-[:TYPE {cost:5}]->(b),\n" +
                        " (b)-[:TYPE {cost:5}]->(c),\n" +
                        " (c)-[:TYPE {cost:5}]->(x),\n" +

                        " (s)-[:TYPE {cost:3}]->(d),\n" + // line 2
                        " (d)-[:TYPE {cost:3}]->(e),\n" +
                        " (e)-[:TYPE {cost:3}]->(f),\n" +
                        " (f)-[:TYPE {cost:3}]->(x),\n" +

                        " (s)-[:TYPE {cost:2}]->(g),\n" + // line 3
                        " (g)-[:TYPE {cost:2}]->(h),\n" +
                        " (h)-[:TYPE {cost:2}]->(i),\n" +
                        " (i)-[:TYPE {cost:2}]->(x)";

        api = TestDatabaseCreator.createTestDatabase();

        api.getDependencyResolver()
                .resolveDependency(Procedures.class)
                .registerProcedure(ShortestPathDeltaSteppingProc.class);

        try (Transaction tx = api.beginTx()) {
            api.execute(cypher);
            tx.success();
        }
    }

    @AfterClass
    public static void shutdownGraph() throws Exception {
       if (api != null) api.shutdown();
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[]{"Heavy"},
                new Object[]{"Light"},
                new Object[]{"Kernel"}
        );
    }

    @Parameterized.Parameter
    public String graphImpl;

    @Test
    public void testOutgoingResultStream() throws Exception {

        final DoubleConsumer consumer = mock(DoubleConsumer.class);

        final String cypher = "MATCH(n:Node {name:'x'}) WITH n CALL algo.shortestPath.deltaStepping.stream(n, 'cost', 3.0,{graph:'"+graphImpl+"', direction: 'OUTGOING'}) " +
                "YIELD nodeId, distance RETURN nodeId, distance";

        api.execute(cypher).accept(row -> {
            long nodeId = row.getNumber("nodeId").longValue();
            double distance = row.getNumber("distance").doubleValue();

            consumer.accept(distance);
            System.out.printf("%d:%.1f, ",
                    nodeId,
                    distance);
            return true;
        });

        verify(consumer, times(11)).accept(anyDouble());
        verify(consumer, times(10)).accept(eq(Double.POSITIVE_INFINITY, 0.1d));
    }

    @Test
    public void testUndirectedResultStream() throws Exception {

        final DoubleConsumer consumer = mock(DoubleConsumer.class);

        final String cypher = "MATCH(n:Node {name:'x'}) WITH n CALL algo.shortestPath.deltaStepping.stream(n, 'cost', 3.0,{graph:'"+graphImpl+"'}) " +
                "YIELD nodeId, distance RETURN nodeId, distance";

        api.execute(cypher).accept(row -> {
            long nodeId = row.getNumber("nodeId").longValue();
            double distance = row.getNumber("distance").doubleValue();

            consumer.accept(distance);
            System.out.printf("%d:%.1f, ",
                    nodeId,
                    distance);
            return true;
        });

        verify(consumer, times(11)).accept(anyDouble());
        verify(consumer, times(1)).accept(eq(8, 0.1d));
    }
}
