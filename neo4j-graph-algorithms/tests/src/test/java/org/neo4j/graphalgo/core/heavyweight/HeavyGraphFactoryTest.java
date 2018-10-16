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
package org.neo4j.graphalgo.core.heavyweight;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.neo4j.graphalgo.PropertyMapping;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.RelationshipConsumer;
import org.neo4j.graphalgo.api.WeightedRelationshipConsumer;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphdb.*;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.graphalgo.TestDatabaseCreator;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author mknobloch
 */
@RunWith(MockitoJUnitRunner.class)
public class HeavyGraphFactoryTest {

    private static GraphDatabaseService db;

    private static long id1;
    private static long id2;
    private static long id3;

    @Mock
    private RelationshipConsumer relationConsumer;

    @Mock
    private WeightedRelationshipConsumer weightedRelationConsumer;

    @BeforeClass
    public static void setup() {

        db = TestDatabaseCreator.createTestDatabase();

        try (final Transaction transaction = db.beginTx()) {
            final Node node1 = db.createNode(Label.label("Node1"));
            node1.setProperty("prop1", 1);

            final Node node2 = db.createNode(Label.label("Node2"));
            node2.setProperty("prop2", 2);

            final Node node3 = db.createNode(Label.label("Node3"));
            node3.setProperty("prop3", 3);

            final Relationship rel1 = node1.createRelationshipTo(node2, RelationshipType.withName("REL1"));
            final Relationship rel2 = node1.createRelationshipTo(node3, RelationshipType.withName("REL2"));
            final Relationship rel3 = node2.createRelationshipTo(node3, RelationshipType.withName("REL3"));
            rel1.setProperty("prop1", 1);
            rel2.setProperty("prop2", 2);
            rel3.setProperty("prop3", 3);
            transaction.success();

            id1 = node1.getId();
            id2 = node2.getId();
            id3 = node3.getId();
        }

    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (db != null) db.shutdown();
    }

    @Test
    public void testAnyLabel() throws Exception {

        final Graph graph = new GraphLoader((GraphDatabaseAPI) db)
                .withAnyLabel()
                .withAnyRelationshipType()
                .load(HeavyGraphFactory.class);

        assertEquals(3, graph.nodeCount());
    }

    @Test
    public void testWithLabel() throws Exception {

        final Graph graph = new GraphLoader((GraphDatabaseAPI) db)
                .withLabel("Node1")
                .withoutRelationshipWeights()
                .withAnyRelationshipType()
                .load(HeavyGraphFactory.class);

        assertEquals(1, graph.nodeCount());
    }

    @Test
    public void testAnyRelation() throws Exception {
        final Graph graph = new GraphLoader((GraphDatabaseAPI) db)
                .withAnyLabel()
                .withoutRelationshipWeights()
                .withAnyRelationshipType()
                .load(HeavyGraphFactory.class);

        graph.forEachRelationship(graph.toMappedNodeId(id1), Direction.OUTGOING, relationConsumer);
        verify(relationConsumer, times(1)).accept(eq(graph.toMappedNodeId(id1)), eq(graph.toMappedNodeId(id3)), anyLong());
        verify(relationConsumer, times(1)).accept(eq(graph.toMappedNodeId(id1)), eq(graph.toMappedNodeId(id2)), anyLong());
        Mockito.reset(relationConsumer);

        graph.forEachRelationship(graph.toMappedNodeId(id2), Direction.OUTGOING, relationConsumer);
        verify(relationConsumer, times(1)).accept(eq(graph.toMappedNodeId(id2)), eq(graph.toMappedNodeId(id3)), anyLong());
        Mockito.reset(relationConsumer);
    }

    @Test
    public void testWithRelation() throws Exception {
        final Graph graph = new GraphLoader((GraphDatabaseAPI) db)
                .withAnyLabel()
                .withoutRelationshipWeights()
                .withRelationshipType("REL1")
                .load(HeavyGraphFactory.class);

        graph.forEachRelationship(graph.toMappedNodeId(id1), Direction.OUTGOING, relationConsumer);
        verify(relationConsumer, times(1)).accept(eq(graph.toMappedNodeId(id1)), eq(graph.toMappedNodeId(id2)), anyLong());
        verify(relationConsumer, never()).accept(eq(graph.toMappedNodeId(id1)), eq(graph.toMappedNodeId(id3)), anyLong());
        Mockito.reset(relationConsumer);

        graph.forEachRelationship(graph.toMappedNodeId(id2), Direction.OUTGOING, relationConsumer);
        verify(relationConsumer, never()).accept(anyInt(), anyInt(), anyLong());
        Mockito.reset(relationConsumer);
    }

    @Test
    public void testWithProperty() throws Exception {

        final Graph graph = new GraphLoader((GraphDatabaseAPI) db)
                .withAnyLabel()
                .withAnyRelationshipType()
                .withRelationshipWeightsFromProperty("prop1", 0.0)
                .load(HeavyGraphFactory.class);

        graph.forEachRelationship(graph.toMappedNodeId(id1), Direction.OUTGOING, weightedRelationConsumer);
        verify(weightedRelationConsumer, times(1))
                .accept(eq(graph.toMappedNodeId(id1)), eq(graph.toMappedNodeId(id2)), anyLong(), eq(1.0));
    }

    @Test
    public void testWithNodeProperties() throws Exception {
        final HeavyGraph graph = (HeavyGraph) new GraphLoader((GraphDatabaseAPI) db)
                .withoutRelationshipWeights()
                .withAnyRelationshipType()
                .withOptionalNodeProperties(
                        PropertyMapping.of("prop1", "prop1", 0D),
                        PropertyMapping.of("prop2", "prop2", 0D),
                        PropertyMapping.of("prop3", "prop3", 0D)
                )
                .load(HeavyGraphFactory.class);

        assertEquals(1.0, graph.nodeProperties("prop1").get(graph.toMappedNodeId(0L)), 0.01);
        assertEquals(2.0, graph.nodeProperties("prop2").get(graph.toMappedNodeId(1L)), 0.01);
        assertEquals(3.0, graph.nodeProperties("prop3").get(graph.toMappedNodeId(2L)), 0.01);
    }
}
