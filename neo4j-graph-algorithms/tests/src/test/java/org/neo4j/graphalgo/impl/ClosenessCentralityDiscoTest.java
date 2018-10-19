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

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.HugeGraph;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphalgo.core.huge.HugeGraphFactory;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.impl.closeness.HugeMSClosenessCentrality;
import org.neo4j.graphalgo.impl.closeness.MSClosenessCentrality;
import org.neo4j.test.rule.ImpermanentDatabaseRule;

import java.util.function.DoubleConsumer;

import static org.mockito.AdditionalMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 *
 * (a)--(b) (d)--(e)
 *   \  /
 *   (c)
 *
 *
 * @author mknblch
 */
public class ClosenessCentralityDiscoTest {

    @ClassRule
    public static final ImpermanentDatabaseRule DB = new ImpermanentDatabaseRule();

    @BeforeClass
    public static void setup() {
        final String cypher =
                "CREATE (a:Node {name:'a'})\n" +
                        "CREATE (b:Node {name:'b'})\n" +
                        "CREATE (c:Node {name:'c'})\n" +
                        "CREATE (d:Node {name:'d'})\n" +
                        "CREATE (e:Node {name:'e'})\n" +

                        "CREATE" +
                        " (a)-[:TYPE]->(b),\n" +
                        " (a)-[:TYPE]->(c),\n" +
                        " (b)-[:TYPE]->(c),\n" +

                        " (d)-[:TYPE]->(e)";

        DB.execute(cypher);
    }

    @Test
    public void testHeavy() throws Exception {
        final Graph graph = new GraphLoader(DB, Pools.DEFAULT)
                .withLabel("Node")
                .withRelationshipType("TYPE")
                .asUndirected(true)
                .load(HeavyGraphFactory.class);
        final MSClosenessCentrality algo = new MSClosenessCentrality(graph, 2, Pools.DEFAULT, true);
        final DoubleConsumer mock = mock(DoubleConsumer.class);
        algo.compute()
                .resultStream()
                .peek(System.out::println)
                .forEach(r -> mock.accept(r.centrality));
        verify(mock, times(3)).accept(eq(0.25, 0.01));
        verify(mock, times(2)).accept(eq(0.0, 0.01));
    }

    @Test
    public void testHuge() throws Exception {


        final HugeGraph graph = (HugeGraph) new GraphLoader(DB, Pools.DEFAULT)
                .withLabel("Node")
                .withRelationshipType("TYPE")
                .asUndirected(true)
                .load(HugeGraphFactory.class);
        final HugeMSClosenessCentrality algo = new HugeMSClosenessCentrality(
                graph,
                AllocationTracker.EMPTY,
                2,
                Pools.DEFAULT,
                true);
        final DoubleConsumer mock = mock(DoubleConsumer.class);
        algo.compute()
                .resultStream()
                .peek(System.out::println)
                .forEach(r -> mock.accept(r.centrality));

        verify(mock, times(3)).accept(eq(0.25, 0.01));
        verify(mock, times(2)).accept(eq(0.0, 0.01));
    }
}
