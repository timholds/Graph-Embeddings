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

import org.junit.Test;
import org.neo4j.graphalgo.impl.yens.WeightedPath;

import static org.junit.Assert.*;

/**
 * @author mknblch
 */
public class WeightedPathTest {

    @Test
    public void testConcat() throws Exception {
        final WeightedPath a = new WeightedPath(3);
        final WeightedPath b = new WeightedPath(1);
        a.append(0);
        a.append(1);
        a.append(2);
        b.append(42);
        final WeightedPath concat = a.concat(b);
        assertArrayEquals(new int[]{0, 1, 2, 42}, concat.toArray());
    }

    @Test
    public void testConcatTailEmpty() throws Exception {
        final WeightedPath a = new WeightedPath(3);
        final WeightedPath b = new WeightedPath(1);
        a.append(0);
        a.append(1);
        a.append(2);
        final WeightedPath concat = a.concat(b);
        assertArrayEquals(new int[]{0, 1, 2}, concat.toArray());
    }

    @Test
    public void testConcatHeadEmpty() throws Exception {
        final WeightedPath a = new WeightedPath(3);
        final WeightedPath b = new WeightedPath(1);
        a.append(0);
        a.append(1);
        a.append(2);
        final WeightedPath concat = b.concat(a);
        assertArrayEquals(new int[]{0, 1, 2}, concat.toArray());
    }

    @Test
    public void testEquality() throws Exception {
        final WeightedPath a = new WeightedPath(3);
        final WeightedPath b = new WeightedPath(3);
        a.append(0);
        a.append(1);
        b.append(0);
        b.append(1);
        b.append(2);
        assertTrue(a.elementWiseEquals(b, 2));
        assertTrue(a.elementWiseEquals(b, 1));
        assertFalse(a.elementWiseEquals(b, 3));
        assertTrue(b.elementWiseEquals(a, 2));
        assertTrue(b.elementWiseEquals(a, 1));
        assertFalse(b.elementWiseEquals(a, 3));
    }

    @Test
    public void testGrow() throws Exception {
        final WeightedPath p = new WeightedPath(0);
        p.append(0);
        p.append(1);
        assertEquals(2, p.size());
    }

}
