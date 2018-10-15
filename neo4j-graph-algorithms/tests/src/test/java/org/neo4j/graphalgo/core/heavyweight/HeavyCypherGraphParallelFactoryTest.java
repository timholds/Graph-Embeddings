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
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.helpers.collection.Iterators;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.test.TestGraphDatabaseFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class HeavyCypherGraphParallelFactoryTest {

    private static final int COUNT = 10000;
    private static GraphDatabaseService db;

    @BeforeClass
    public static void setUp() {

        db = new TestGraphDatabaseFactory().newImpermanentDatabase();

        Iterators.count(db.execute("UNWIND range(1," + COUNT + ") AS id CREATE (n {id:id})-[:REL {prop:id%10}]->(n)"));
    }

    @AfterClass
    public static void tearDown() {
        db.shutdown();
    }


    @Test
    public void testLoadNoneParallelCypher() throws Exception {
        String nodeStatement = "MATCH (n) RETURN id(n) as id";
        String relStatement = "MATCH (n)-[r:REL]->(m) RETURN id(n) as source, id(m) as target, r.prop as weight";

        loadAndTestGraph(nodeStatement, relStatement, false);
    }

    @Test
    public void testLoadNodesParallelCypher() throws Exception {
        String nodeStatement = "MATCH (n) WITH n SKIP {skip} LIMIT {limit} RETURN id(n) as id";
        String relStatement = "MATCH (n)-[r:REL]->(m) RETURN id(n) as source, id(m) as target, r.prop as weight";

        loadAndTestGraph(nodeStatement, relStatement, false);
    }

    @Test
    public void testLoadRelationshipsParallelCypher() throws Exception {
        String nodeStatement = "MATCH (n) RETURN id(n) as id";
        String relStatement = "MATCH (n)-[r:REL]->(m)  WITH * SKIP {skip} LIMIT {limit} RETURN id(n) as source, id(m) as target, r.prop as weight";

        loadAndTestGraph(nodeStatement, relStatement, false);
    }

    @Test
    public void testLoadRelationshipsParallelAccumulateWeightCypher() throws Exception {
        String nodeStatement = "MATCH (n) RETURN id(n) as id";
        String relStatement =
                "MATCH (n)-[r:REL]->(m) WITH * SKIP {skip} LIMIT {limit} RETURN id(n) as source, id(m) as target, r.prop/2.0 as weight " +
                "UNION ALL "+
                "MATCH (n)-[r:REL]->(m) WITH * SKIP {skip} LIMIT {limit} RETURN id(n) as source, id(m) as target, r.prop/2.0 as weight ";

        loadAndTestGraph(nodeStatement, relStatement, true);
    }

    @Test
    public void testLoadCypherBothParallel() throws Exception {
        String nodeStatement = "MATCH (n) WITH n SKIP {skip} LIMIT {limit} RETURN id(n) as id";
        String relStatement = "MATCH (n)-[r:REL]->(m) WITH * SKIP {skip} LIMIT {limit} RETURN id(n) as source, id(m) as target, r.prop as weight";

        loadAndTestGraph(nodeStatement, relStatement, false);
    }

    protected void loadAndTestGraph(String nodeStatement, String relStatement, boolean accumulateWeights) {
        final Graph graph = new GraphLoader((GraphDatabaseAPI) db)
                .withExecutorService(Pools.DEFAULT)
                .withBatchSize(1000)
                .withAccumulateWeights(accumulateWeights)
                .withRelationshipWeightsFromProperty("prop",0d)
                .withLabel(nodeStatement)
                .withRelationshipType(relStatement)
                .load(HeavyCypherGraphFactory.class);

        Assert.assertEquals(COUNT, graph.nodeCount());
        AtomicInteger relCount = new AtomicInteger();
        graph.forEachNode(node -> {relCount.addAndGet(graph.degree(node, Direction.OUTGOING));return true;});
        Assert.assertEquals(COUNT, relCount.get());
        AtomicInteger total = new AtomicInteger();
        graph.forEachNode(n -> {
            graph.forEachRelationship(n, Direction.OUTGOING, (s, t, r, w) -> {
                total.addAndGet((int) w);
                return true;
            });
            return true;
        });
        assertEquals(9 * COUNT / 2, total.get());
    }
}
