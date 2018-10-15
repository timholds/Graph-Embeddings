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

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphalgo.core.huge.HugeGraphFactory;
import org.neo4j.graphalgo.core.utils.ParallelUtil;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.ProgressTimer;
import org.neo4j.graphalgo.helper.graphbuilder.GraphBuilder;
import org.neo4j.graphalgo.impl.triangle.TriangleCountForkJoin;
import org.neo4j.graphalgo.impl.triangle.TriangleCountQueue;
import org.neo4j.graphalgo.impl.triangle.TriangleStream;
import org.neo4j.test.rule.ImpermanentDatabaseRule;

import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

@RunWith(Parameterized.class)
public final class LargerTriangleCountTest {

    private static final int TRIANGLE_COUNT = 500;
    private static final long SEED = 1337L << 42;
    private static final String LABEL = "Node";
    private static final String RELATIONSHIP = "REL";

    @ClassRule
    public static final ImpermanentDatabaseRule DB = new ImpermanentDatabaseRule();

    @Parameterized.Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[]{HeavyGraphFactory.class, "heavy"},
                new Object[]{HugeGraphFactory.class, " huge"}
        );
    }

    private final Class<? extends GraphFactory> graphImpl;
    private final String graphName;
    private Graph graph;

    @BeforeClass
    public static void createDb() {
        GraphBuilder.create(DB, new Random(SEED))
                .setLabel(LABEL)
                .setRelationship(RELATIONSHIP)
                .newCompleteGraphBuilder()
                .createCompleteGraph(TRIANGLE_COUNT, 0.2);
    }

    public LargerTriangleCountTest(
            Class<? extends GraphFactory> graphImpl,
            String graphName) {
        this.graphImpl = graphImpl;
        this.graphName = graphName;
    }

    @Before
    public void setUp() {
        graph = new GraphLoader(DB)
                .withLabel(LABEL)
                .withRelationshipType(RELATIONSHIP)
                .withoutRelationshipWeights()
                .withoutNodeWeights()
                .withSort(true)
                .asUndirected(true)
                .withConcurrency(1)
                .load(graphImpl);
    }

    @Test
    public void testQueuePar() {
        long triangleCount = new TriangleCountQueue(graph, Pools.DEFAULT, Pools.DEFAULT_CONCURRENCY)
                .compute()
                .getTriangleCount();
        System.out.printf("[%s][par][ queue] count = %d%n", graphName, triangleCount);
    }

    @Test
    public void testForkJoinPar() {
        long triangleCount = new TriangleCountForkJoin(
                graph,
                Pools.FJ_POOL,
                ParallelUtil.threadSize(Pools.DEFAULT_CONCURRENCY, TRIANGLE_COUNT)  // 2 * TRIANGLE_COUNT
        ).compute().getTriangleCount();
        System.out.printf("[%s][par][ forkj] count = %d%n", graphName, triangleCount);
    }

    @Test
    public void testStreamPar() {
        long triangleCount = new TriangleStream(graph, Pools.DEFAULT, Pools.DEFAULT_CONCURRENCY).resultStream().count();
        System.out.printf("[%s][par][stream] count = %d%n", graphName, triangleCount);
    }

    @Test
    public void testQueueSeq() {
        long triangleCount = new TriangleCountQueue(graph, null, 1)
                .compute()
                .getTriangleCount();
        System.out.printf("[%s][seq][ queue] count = %d%n", graphName, triangleCount);
    }

    @Test
    public void testForkJoinSeq() {
        long triangleCount = new TriangleCountForkJoin(
                graph,
                new NoForkJoinPool(),
                2 * TRIANGLE_COUNT
        ).compute().getTriangleCount();
        System.out.printf("[%s][seq][ forkj] count = %d%n", graphName, triangleCount);
    }

    @Test
    public void testStreamSeq() {
        long triangleCount = new TriangleStream(graph, Pools.DEFAULT, 1).resultStream().count();
        System.out.printf("[%s][seq][stream] count = %d%n", graphName, triangleCount);
    }

    private static final class NoForkJoinPool extends ForkJoinPool {
        @Override
        public <T> T invoke(final ForkJoinTask<T> task) {
            return task.invoke();
        }
    }
}
