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
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.helper.graphbuilder.GraphBuilder;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.ProgressTimer;
import org.neo4j.graphdb.Direction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Matchers.anyDouble;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 *     1
 *  (0)->(1)
 * 1 | 1 | 1
 *   v   v
 *  (2)->(3)
 * 1 | 1 | 1
 *   v   v
 *  (4)->(5)
 * 1 | 1 | 1
 *   v   v
 *  (6)->(7)
 * 1 | 1  | 1
 *   v    v
 *  (8)->(9)
 *
 * @author mknblch
 */
public class AllShortestPathsTest {

    private static final int width = 2, height = 5;

    private static final String PROPERTY = "property";
    private static final String LABEL = "Node";
    private static final String RELATIONSHIP = "REL";

    private static GraphDatabaseAPI db;
    private static Graph graph;

    @BeforeClass
    public static void setup() throws Exception {

        db = TestDatabaseCreator.createTestDatabase();

        try (ProgressTimer timer = ProgressTimer.start(t -> System.out.println("setup took " + t + "ms"))) {
            GraphBuilder.create(db)
                    .setLabel(LABEL)
                    .setRelationship(RELATIONSHIP)
                    .newGridBuilder()
                    .createGrid(width, height)
                    .forEachRelInTx(rel -> {
                        rel.setProperty(PROPERTY, 1.0);
                    });
        }

        try (ProgressTimer timer = ProgressTimer.start(t -> System.out.println("load took " + t + "ms"))) {
            graph = new GraphLoader(db)
                    .withLabel(LABEL)
                    .withRelationshipType(RELATIONSHIP)
                    .withRelationshipWeightsFromProperty(PROPERTY, 1.0)
                    .load(HeavyGraphFactory.class);
        }
    }

    @AfterClass
    public static void tearDown() {
        if (db!=null) db.shutdown();
    }

    @Test
    public void testResults() throws Exception {

        final ResultConsumer mock = mock(ResultConsumer.class);

        new AllShortestPaths(graph, Pools.DEFAULT, 4, Direction.OUTGOING)
                .resultStream()
                .peek(System.out::println)
                .forEach(r -> {
                    assertNotEquals(Double.POSITIVE_INFINITY, r.distance);
                    if (r.sourceNodeId == r.targetNodeId) {
                        assertEquals(0.0, r.distance, 0.1);
                    }
                    mock.test(r.sourceNodeId, r.targetNodeId, r.distance);
                });

        final int nodes = (width * height);

        verify(mock, times(45)).test(anyLong(), anyLong(), anyDouble());

        verify(mock, times(1)).test(0, 9, 5.0);
        verify(mock, times(1)).test(0, 0, 0.0);

    }

    interface ResultConsumer {

        void test(long source, long target, double distance);
    }
}
