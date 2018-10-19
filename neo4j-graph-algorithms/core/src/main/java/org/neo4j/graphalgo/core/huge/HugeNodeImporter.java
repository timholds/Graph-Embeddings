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

import org.neo4j.graphalgo.core.BaseNodeImporter;
import org.neo4j.graphalgo.core.utils.ImportProgress;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

public final class HugeNodeImporter extends BaseNodeImporter<HugeIdMap> {
    private final AllocationTracker tracker;
    private final long allNodesCount;

    public HugeNodeImporter(
            GraphDatabaseAPI api,
            AllocationTracker tracker,
            ImportProgress progress,
            long nodeCount,
            long allNodesCount,
            int labelId) {
        super(api, progress, nodeCount, labelId);
        this.tracker = tracker;
        this.allNodesCount = allNodesCount;
    }

    @Override
    protected HugeIdMap newNodeMap(final long nodeCount) {
        return new HugeIdMap(nodeCount, allNodesCount, tracker);
    }

    @Override
    protected void addNodeId(final HugeIdMap map, final long nodeId) {
        map.add(nodeId);
    }

    @Override
    protected void finish(final HugeIdMap map) {
    }
}
