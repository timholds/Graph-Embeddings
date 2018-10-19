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
package org.neo4j.graphalgo.core;

import com.carrotsearch.hppc.IntArrayList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphalgo.core.huge.HugeGraphFactory;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.test.rule.ImpermanentDatabaseRule;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author mknblch
 */
@RunWith(Parameterized.class)
public class GraphLoaderTest {

    @Rule
    public ImpermanentDatabaseRule db = new ImpermanentDatabaseRule();

    private Class<? extends GraphFactory> graphImpl;

    @Parameterized.Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[]{HeavyGraphFactory.class, "HeavyGraphFactory"},
                new Object[]{HugeGraphFactory.class, "HugeGraphFactory"}
        );
    }

    @SuppressWarnings("unused")
    public GraphLoaderTest(
            Class<? extends GraphFactory> graphImpl,
            String nameIgnoredOnlyForTestName) {
        this.graphImpl = graphImpl;
    }

    @Test
    public void testLargerGraphWithDeletions() {
        db.execute("FOREACH (x IN range(1, 4098) | CREATE (:Node {index:x}))");
        db.execute("MATCH (n) WHERE n.index IN [1, 2, 3] DELETE n");
        new GraphLoader(db, Pools.DEFAULT)
                .withLabel("Node")
                .withAnyRelationshipType()
                .load(graphImpl);
    }

    @Test
    public void testUndirectedNodeWithSelfReference() {
        runUndirectedNodeWithSelfReference("" +
                "CREATE (a:Node),(b:Node) " +
                "CREATE" +
                " (a)-[:REL]->(a)," +
                " (a)-[:REL]->(b)"
        );
    }

    @Test
    public void testUndirectedNodeWithSelfReference2() {
        runUndirectedNodeWithSelfReference("" +
                "CREATE (a:Node),(b:Node) " +
                "CREATE" +
                " (a)-[:REL]->(b)," +
                " (a)-[:REL]->(a)"
        );
    }

    private void runUndirectedNodeWithSelfReference(String cypher) {
        db.execute(cypher);
        final Graph graph = new GraphLoader(db)
                .withAnyLabel()
                .withAnyRelationshipType()
                .asUndirected(true)
                .load(graphImpl);

        assertEquals(2L, graph.nodeCount());
        checkRelationships(graph, 0, 0, 1);
        checkRelationships(graph, 1, 0);
    }

    @Test
    public void testUndirectedNodesWithMultipleSelfReferences() {
        runUndirectedNodesWithMultipleSelfReferences("" +
                "CREATE (a:Node),(b:Node),(c:Node),(d:Node) " +
                "CREATE" +
                " (a)-[:REL]->(a)," +
                " (b)-[:REL]->(b)," +
                " (a)-[:REL]->(b)," +
                " (b)-[:REL]->(c)," +
                " (b)-[:REL]->(d)"
        );
    }

    @Test
    public void testUndirectedNodesWithMultipleSelfReferences2() {
        runUndirectedNodesWithMultipleSelfReferences("" +
                "CREATE (a:Node),(b:Node),(c:Node),(d:Node) " +
                "CREATE" +
                " (a)-[:REL]->(b)," +
                " (a)-[:REL]->(a)," +
                " (b)-[:REL]->(c)," +
                " (b)-[:REL]->(d)," +
                " (b)-[:REL]->(b)"
        );
    }

    private void runUndirectedNodesWithMultipleSelfReferences(String cypher) {
        db.execute(cypher);
        final Graph graph = new GraphLoader(db)
                .withAnyLabel()
                .withAnyRelationshipType()
                .asUndirected(true)
                .load(graphImpl);

        assertEquals(4L, graph.nodeCount());
        checkRelationships(graph, 0, 0, 1);
        checkRelationships(graph, 1, 0, 1, 2, 3);
        checkRelationships(graph, 2, 1);
        checkRelationships(graph, 3, 1);
    }

    private void checkRelationships(Graph graph, int node, int... expected) {
        IntArrayList idList = new IntArrayList();
        graph.forEachOutgoing(node, (s, t, r) -> {
            idList.add(t);
            return true;
        });
        final int[] ids = idList.toArray();
        Arrays.sort(ids);
        Arrays.sort(expected);
        assertArrayEquals(expected, ids);
    }
}
