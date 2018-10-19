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

import org.neo4j.collection.primitive.PrimitiveLongIterable;
import org.neo4j.collection.primitive.PrimitiveLongIterator;
import org.neo4j.graphalgo.api.HugeBatchNodeIterable;
import org.neo4j.graphalgo.api.HugeIdMapping;
import org.neo4j.graphalgo.api.HugeNodeIterator;
import org.neo4j.graphalgo.core.utils.LazyBatchCollection;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeLongArray;
import org.neo4j.graphalgo.core.utils.paged.PageUtil;
import org.neo4j.graphalgo.core.utils.paged.SparseLongArray;

import java.util.Collection;
import java.util.function.LongPredicate;

/**
 * This is basically a long to int mapper. It sorts the id's in ascending order so its
 * guaranteed that there is no ID greater then nextGraphId / capacity
 */
public final class HugeIdMap implements HugeIdMapping, HugeNodeIterator, HugeBatchNodeIterable {

    static final long NOT_FOUND = -1L;

    // page size to use when loading nodes in parallel
    static final int PAGE_SIZE = PageUtil.pageSizeFor(Long.BYTES);

    private long nextGraphId;
    private HugeLongArray graphIds;
    private SparseLongArray nodeToGraphIds;

    /**
     * initialize the map with maximum node capacity
     */
    HugeIdMap(long capacity, long fullCapacity, AllocationTracker tracker) {
        graphIds = HugeLongArray.newArray(capacity, tracker);
        nodeToGraphIds = SparseLongArray.newArray(fullCapacity, tracker);
    }

    void add(long longValue) {
        long internalId = nextGraphId++;
        nodeToGraphIds.set(longValue, internalId);
        graphIds.set(internalId, longValue);
    }

    @Override
    public long toHugeMappedNodeId(long nodeId) {
        return nodeToGraphIds.get(nodeId);
    }

    @Override
    public long toOriginalNodeId(long nodeId) {
        return graphIds.get(nodeId);
    }

    @Override
    public boolean contains(final long nodeId) {
        return nodeToGraphIds.contains(nodeId);
    }

    @Override
    public long nodeCount() {
        return nextGraphId;
    }

    @Override
    public void forEachNode(LongPredicate consumer) {
        final long count = nodeCount();
        for (long i = 0L; i < count; i++) {
            if (!consumer.test(i)) {
                return;
            }
        }
    }

    @Override
    public PrimitiveLongIterator hugeNodeIterator() {
        return new IdIterator(nodeCount());
    }

    @Override
    public Collection<PrimitiveLongIterable> hugeBatchIterables(int batchSize) {
        return LazyBatchCollection.of(
                nodeCount(),
                batchSize,
                IdIterable::new);
    }

    private static final class IdIterable implements PrimitiveLongIterable {
        private final long start;
        private final long length;

        private IdIterable(long start, long length) {
            this.start = start;
            this.length = length;
        }

        @Override
        public PrimitiveLongIterator iterator() {
            return new IdIterator(start, length);
        }
    }

    private static final class IdIterator implements PrimitiveLongIterator {

        private long current;
        private long limit; // exclusive upper bound

        private IdIterator(long length) {
            this.current = 0;
            this.limit = length;
        }

        private IdIterator(long start, long length) {
            this.current = start;
            this.limit = start + length;
        }

        @Override
        public boolean hasNext() {
            return current < limit;
        }

        @Override
        public long next() {
            return current++;
        }
    }
}
