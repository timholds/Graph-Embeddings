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
package org.neo4j.graphalgo.core.huge;

import org.neo4j.graphalgo.api.HugeRelationshipConsumer;
import org.neo4j.graphalgo.api.RelationshipIntersect;
import org.neo4j.graphalgo.api.IntersectionConsumer;
import org.neo4j.graphalgo.core.utils.paged.HugeLongArray;

/**
 * An instance of this is not thread-safe; Iteration/Intersection on multiple threads will
 * throw misleading {@link NullPointerException}s.
 * Instances are however safe to use concurrently with other {@link org.neo4j.graphalgo.api.HugeRelationshipIterator}s.
 */

class HugeGraphIntersectImpl implements RelationshipIntersect {

    private HugeAdjacencyList adjacency;
    private HugeLongArray offsets;
    private HugeAdjacencyList.Cursor empty;
    private HugeAdjacencyList.Cursor cache;
    private HugeAdjacencyList.Cursor cacheA;
    private HugeAdjacencyList.Cursor cacheB;

    HugeGraphIntersectImpl(final HugeAdjacencyList adjacency, final HugeLongArray offsets) {
        assert adjacency != null;
        assert offsets != null;
        this.adjacency = adjacency;
        this.offsets = offsets;
        cache = adjacency.newCursor();
        cacheA = adjacency.newCursor();
        cacheB = adjacency.newCursor();
        empty = adjacency.newCursor();
    }

    /*
    @Override
    public void forEachRelationship(long nodeId, HugeRelationshipConsumer consumer) {
        HugeAdjacencyList.Cursor cursor = cursor(nodeId, cache, offsets, adjacency);
        consumeNodes(nodeId, cursor, consumer);
    }

    @Override
    public int degree(final long node) {
        return degree(node, offsets, adjacency);
    }

    */
    @Override
    public void intersectAll(long nodeIdA, IntersectionConsumer consumer) {
        HugeLongArray offsets = this.offsets;
        HugeAdjacencyList adjacency = this.adjacency;

        HugeAdjacencyList.Cursor mainCursor = cursor(nodeIdA, cache, offsets, adjacency);
        long nodeIdB = mainCursor.skipUntil(nodeIdA);
        if (nodeIdB <= nodeIdA) {
            return;
        }

        HugeAdjacencyList.Cursor lead, follow, cursorA = cacheA, cursorB = cacheB;
        long nodeIdC, currentA, s, t;
        boolean hasNext = true;

        while (hasNext) {
            cursorB = cursor(nodeIdB, cursorB, offsets, adjacency);
            nodeIdC = cursorB.skipUntil(nodeIdB);
            if (nodeIdC > nodeIdB) {
                cursorA.copyFrom(mainCursor);
                currentA = cursorA.advance(nodeIdC);

                if (currentA == nodeIdC) {
                    consumer.accept(nodeIdA, nodeIdB, nodeIdC);
                }

                if (cursorA.remaining() <= cursorB.remaining()) {
                    lead = cursorA;
                    follow = cursorB;
                } else {
                    lead = cursorB;
                    follow = cursorA;
                }

                while (lead.hasNextVLong() && follow.hasNextVLong()) {
                    s = lead.nextVLong();
                    t = follow.advance(s);
                    if (t == s) {
                        consumer.accept(nodeIdA, nodeIdB, s);
                    }
                }
            }

            if (hasNext = mainCursor.hasNextVLong()) {
                nodeIdB = mainCursor.nextVLong();
            }
        }
    }

    private int degree(long node, HugeLongArray offsets, HugeAdjacencyList array) {
        long offset = offsets.get(node);
        if (offset == 0L) {
            return 0;
        }
        return array.getDegree(offset);
    }

    private HugeAdjacencyList.Cursor cursor(
            long node,
            HugeAdjacencyList.Cursor reuse,
            HugeLongArray offsets,
            HugeAdjacencyList array) {
        final long offset = offsets.get(node);
        if (offset == 0L) {
            return empty;
        }
        return array.deltaCursor(reuse, offset);
    }

    private void consumeNodes(
            long startNode,
            HugeAdjacencyList.Cursor cursor,
            HugeRelationshipConsumer consumer) {
        //noinspection StatementWithEmptyBody
        while (cursor.hasNextVLong() && consumer.accept(startNode, cursor.nextVLong())) ;
    }
}
