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

import com.carrotsearch.hppc.predicates.IntIntPredicate;
import com.carrotsearch.hppc.predicates.LongLongPredicate;
import org.neo4j.graphalgo.api.HugeIdMapping;
import org.neo4j.graphalgo.api.HugeNodeIterator;
import org.neo4j.graphalgo.api.IdMapping;
import org.neo4j.graphalgo.api.NodeIterator;
import org.neo4j.graphalgo.core.utils.dss.DisjointSetStruct;
import org.neo4j.graphalgo.core.utils.paged.PagedDisjointSetStruct;

import java.util.stream.Stream;

public final class DSSResult {
    public final DisjointSetStruct struct;
    public final PagedDisjointSetStruct hugeStruct;

    public DSSResult(final DisjointSetStruct struct) {
        this(struct, null);
    }

    public DSSResult(final PagedDisjointSetStruct hugeStruct) {
        this(null, hugeStruct);
    }

    private DSSResult(DisjointSetStruct struct, PagedDisjointSetStruct hugeStruct) {
        assert (struct != null && hugeStruct == null) || (struct == null && hugeStruct != null);
        this.struct = struct;
        this.hugeStruct = hugeStruct;
    }

    public int getSetCount() {
        return struct != null ? struct.getSetCount() : hugeStruct.getSetCount();
    }

    public Stream<DisjointSetStruct.Result> resultStream(IdMapping idMapping) {
        return struct != null
                ? struct.resultStream(idMapping)
                : hugeStruct.resultStream(((HugeIdMapping) idMapping));
    }

    public void forEach(NodeIterator nodes, IntIntPredicate consumer) {
        if (struct != null) {
            nodes.forEachNode(nodeId -> consumer.apply(nodeId, struct.find(nodeId)));
        } else {
            nodes.forEachNode(nodeId -> consumer.apply(nodeId, (int) hugeStruct.find(nodeId)));
        }
    }

    public void forEach(HugeNodeIterator nodes, LongLongPredicate consumer) {
        if (hugeStruct != null) {
            nodes.forEachNode(nodeId -> consumer.apply(nodeId, hugeStruct.find(nodeId)));
        } else {
            nodes.forEachNode(nodeId -> consumer.apply((int) nodeId, struct.find((int) nodeId)));
        }
    }
}
