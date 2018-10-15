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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.huge.HugeGraphFactory;
import org.neo4j.graphalgo.core.utils.AtomicDoubleArray;
import org.neo4j.graphalgo.core.utils.paged.PagedAtomicDoubleArray;
import org.neo4j.graphalgo.core.utils.paged.PagedAtomicIntegerArray;
import org.neo4j.graphalgo.helper.graphbuilder.DefaultBuilder;
import org.neo4j.graphalgo.helper.graphbuilder.GraphBuilder;
import org.neo4j.graphalgo.core.heavyweight.HeavyGraphFactory;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.ProgressTimer;
import org.neo4j.graphalgo.impl.triangle.TriangleCountAlgorithm;
import org.neo4j.graphalgo.impl.triangle.TriangleCountForkJoin;
import org.neo4j.graphalgo.impl.triangle.TriangleCountQueue;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.test.rule.ImpermanentDatabaseRule;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author mknblch
 */
@RunWith(Parameterized.class)
public class TriangleCountExpTest {

    private static final String LABEL = "Node";
    private static final String RELATIONSHIP = "REL";
    private static final long TRIANGLE_COUNT = 1000L;
    private static final double EXPECTED_COEFFICIENT = 0.666;

    private static long centerId;

    @ClassRule
    public static final ImpermanentDatabaseRule DB = new ImpermanentDatabaseRule();

    @Parameterized.Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[]{HeavyGraphFactory.class, "Heavy"},
                new Object[]{HugeGraphFactory.class, "Huge"}
        );
    }

    @BeforeClass
    public static void setup() {
        try (ProgressTimer timer = ProgressTimer.start(t -> System.out.println("setup took " + t + "ms for " + TRIANGLE_COUNT + " nodes"))) {
            final RelationshipType type = RelationshipType.withName(RELATIONSHIP);
            final DefaultBuilder builder = GraphBuilder.create(DB)
                    .setLabel(LABEL)
                    .setRelationship(RELATIONSHIP)
                    .newDefaultBuilder();
            final Node center = builder.createNode();
            builder.newRingBuilder()
                    .createRing((int) TRIANGLE_COUNT)
                    .forEachNodeInTx(node -> {
                        center.createRelationshipTo(node, type);
                    });
            centerId = center.getId();
        }
    }


    private Graph graph;

    public TriangleCountExpTest(
            Class<? extends GraphFactory> graphImpl,
            String nameIgnoredOnlyForTestName) {
        try (ProgressTimer timer = ProgressTimer.start(t -> System.out.println("load took " + t + "ms"))) {
            graph = new GraphLoader(DB)
                    .withLabel(LABEL)
                    .withRelationshipType(RELATIONSHIP)
                    .withoutRelationshipWeights()
                    .withoutNodeWeights()
                    .withDirection(Direction.BOTH)
                    .withSort(true)
                    .asUndirected(true)
                    .load(graphImpl);
        }
    }


    @Test
    public void testQueue() {

        final TriangleCountAlgorithm algo = TriangleCountAlgorithm.instance(graph, Pools.DEFAULT, 1);
        try (ProgressTimer start = ProgressTimer.start(l -> System.out.println("took " + l + "ms"))) {
            algo.compute();
        }
        assertEquals(TRIANGLE_COUNT, algo.getTriangleCount());
        assertTriangles(algo.getTriangles());
        assertCoefficients(algo.getCoefficients());
        assertEquals(EXPECTED_COEFFICIENT, algo.getAverageCoefficient(), 0.001);
    }

    @Test
    public void testQueueParallel() {
        final TriangleCountAlgorithm algo = TriangleCountAlgorithm.instance(graph, Pools.DEFAULT, 4);
        try (ProgressTimer start = ProgressTimer.start(l -> System.out.println("took " + l + "ms"))) {
            algo.compute();
        }
        assertEquals(TRIANGLE_COUNT, algo.getTriangleCount());
        assertTriangles(algo.getTriangles());
        assertCoefficients(algo.getCoefficients());
        assertEquals(EXPECTED_COEFFICIENT, algo.getAverageCoefficient(), 0.001);
    }

    @Test
    public void testForkJoin() {
        final TriangleCountForkJoin algo = new TriangleCountForkJoin(graph, ForkJoinPool.commonPool(), 100_000);
        try (ProgressTimer start = ProgressTimer.start(l -> System.out.println("took " + l + "ms"))) {
            algo.compute();
        }
        assertEquals(TRIANGLE_COUNT, algo.getTriangleCount());
        assertTriangles(algo.getTriangles());
        assertClusteringCoefficient(algo.getClusteringCoefficients());
        assertEquals(EXPECTED_COEFFICIENT, algo.getAverageClusteringCoefficient(), 0.001);
    }

    @Test
    public void testForkJoinParallel() {
        final TriangleCountForkJoin algo = new TriangleCountForkJoin(graph, ForkJoinPool.commonPool(), 100);
        try (ProgressTimer start = ProgressTimer.start(l -> System.out.println("took " + l + "ms"))) {
            algo.compute();
        }
        assertEquals(TRIANGLE_COUNT, algo.getTriangleCount());
        assertTriangles(algo.getTriangles());
        assertClusteringCoefficient(algo.getClusteringCoefficients());
        assertEquals(EXPECTED_COEFFICIENT, algo.getAverageClusteringCoefficient(), 0.001);
    }

    private void assertTriangles(Object triangles) {
        if (triangles instanceof PagedAtomicIntegerArray) {
            assertTriangle((PagedAtomicIntegerArray) triangles);
        } else if (triangles instanceof AtomicIntegerArray){
            assertTriangle((AtomicIntegerArray) triangles);
        }
    }

    private void assertTriangle(AtomicIntegerArray triangles) {
        final int centerMapped = graph.toMappedNodeId(centerId);
        assertEquals(TRIANGLE_COUNT, triangles.get(centerMapped));
        for (int i = 0; i < triangles.length(); i++) {
            if (i == centerMapped) {
                continue;
            }
            assertEquals(2, triangles.get(i));
        }
    }

    private void assertTriangle(PagedAtomicIntegerArray triangles) {
        final int centerMapped = graph.toMappedNodeId(centerId);
        assertEquals(TRIANGLE_COUNT, triangles.get(centerMapped));
        for (int i = 0; i < triangles.size(); i++) {
            if (i == centerMapped) {
                continue;
            }
            assertEquals(2, triangles.get(i));
        }
    }

    private void assertCoefficients(Object coefficients) {
        if (coefficients instanceof double[]) {
            assertClusteringCoefficient((double[]) coefficients);
        } else if (coefficients instanceof PagedAtomicDoubleArray) {
            assertClusteringCoefficient((PagedAtomicDoubleArray) coefficients);
        } else if (coefficients instanceof AtomicDoubleArray){
            assertClusteringCoefficient((AtomicDoubleArray) coefficients);
        }
    }

    private void assertClusteringCoefficient(double[] coefficients) {
        final int centerMapped = graph.toMappedNodeId(centerId);
        for (int i = 0; i < coefficients.length; i++) {
            if (i == centerMapped) {
                continue;
            }
            assertEquals(EXPECTED_COEFFICIENT, coefficients[i], 0.01);
        }
    }

    private void assertClusteringCoefficient(AtomicDoubleArray coefficients) {
        final int centerMapped = graph.toMappedNodeId(centerId);
        for (int i = 0; i < coefficients.length(); i++) {
            if (i == centerMapped) {
                continue;
            }
            assertEquals(EXPECTED_COEFFICIENT, coefficients.get(i), 0.01);
        }
    }

    private void assertClusteringCoefficient(PagedAtomicDoubleArray coefficients) {
        final int centerMapped = graph.toMappedNodeId(centerId);
        for (int i = 0; i < coefficients.size(); i++) {
            if (i == centerMapped) {
                continue;
            }
            assertEquals(EXPECTED_COEFFICIENT, coefficients.get(i), 0.01);
        }
    }
}
