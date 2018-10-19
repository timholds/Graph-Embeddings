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
package org.neo4j.graphalgo.impl.louvain;

import org.neo4j.collection.primitive.PrimitiveIntIterable;
import org.neo4j.collection.primitive.PrimitiveIntIterator;
import org.neo4j.graphalgo.api.IdMapping;
import org.neo4j.graphalgo.core.utils.ParallelUtil;
import org.neo4j.graphalgo.core.utils.StatementApi;
import org.neo4j.internal.kernel.api.Write;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.values.storable.Values;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutorService;

/**
 * @author mknblch
 */
public class LouvainCommunityExporter extends StatementApi {

    private final ExecutorService pool;
    private final int concurrency;
    private final IdMapping mapping;
    private final int nodeCount;
    private Integer propertyId;

    public LouvainCommunityExporter(GraphDatabaseAPI api,
                                    ExecutorService pool,
                                    int concurrency,
                                    IdMapping mapping,
                                    int nodeCount,
                                    String propertyName) {
        super(api);
        this.pool = pool;
        this.concurrency = concurrency;
        this.mapping = mapping;
        this.nodeCount = nodeCount;

            propertyId = applyInTransaction(statement -> statement.tokenWrite().propertyKeyGetOrCreateForName(propertyName));

    }

    public void export(int[][] communities) {
        final Collection<PrimitiveIntIterable> batchIterables = ParallelUtil.batchIterables(concurrency, nodeCount);
        final ArrayList<Runnable> tasks = new ArrayList<>();
        batchIterables.forEach(it -> tasks.add(new NodeBatchExporter(it, communities)));
        ParallelUtil.run(tasks, pool);
    }

    private class NodeBatchExporter implements Runnable {

        private final PrimitiveIntIterable iterable;
        private final int[][] communities;

        private NodeBatchExporter(PrimitiveIntIterable iterable, int[][] communities) {
            this.iterable = iterable;
            this.communities = communities;
        }

        @Override
        public void run() {
            acceptInTransaction(statement -> {
                final Write dataWriteOperations = statement.dataWrite();
                for(PrimitiveIntIterator it = iterable.iterator(); it.hasNext(); ) {
                    final int id = it.next();
                    // build int array
                    final int[] data = new int[communities.length];
                    for (int i = 0; i < data.length; i++) {
                        try {
                            data[i] = communities[i][id];
                        } catch (Exception e) {
                            throw e; // TODO
                        }
                    }
                    dataWriteOperations.nodeSetProperty(
                            mapping.toOriginalNodeId(id),
                            propertyId,
                            Values.intArray(data));
                }
            });
        }
    }
}
