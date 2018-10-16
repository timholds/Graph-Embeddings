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
package org.neo4j.graphalgo.api;

import org.neo4j.graphalgo.core.GraphDimensions;
import org.neo4j.graphalgo.core.HugeNullWeightMap;
import org.neo4j.graphalgo.core.HugeWeightMap;
import org.neo4j.graphalgo.core.IdMap;
import org.neo4j.graphalgo.core.NodeImporter;
import org.neo4j.graphalgo.core.NullWeightMap;
import org.neo4j.graphalgo.core.WeightMap;
import org.neo4j.graphalgo.core.huge.HugeIdMap;
import org.neo4j.graphalgo.core.huge.HugeNodeImporter;
import org.neo4j.graphalgo.core.utils.ApproximatedImportProgress;
import org.neo4j.graphalgo.core.utils.ImportProgress;
import org.neo4j.graphalgo.core.utils.ProgressLogger;
import org.neo4j.graphalgo.core.utils.ProgressLoggerAdapter;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.kernel.api.StatementConstants;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.logging.NullLog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The Abstract Factory defines the construction of the graph
 *
 * @author mknblch
 */
public abstract class GraphFactory {

    public static final String TASK_LOADING = "LOADING";

    protected final ExecutorService threadPool;
    protected final GraphDatabaseAPI api;
    protected final GraphSetup setup;
    protected final GraphDimensions dimensions;
    protected final ImportProgress progress;
    protected final Log log;
    protected final ProgressLogger progressLogger;

    public GraphFactory(GraphDatabaseAPI api, GraphSetup setup) {
        this.threadPool = setup.executor;
        this.api = api;
        this.setup = setup;
        this.log = setup.log;
        this.progressLogger = progressLogger(log, setup.logMillis, TimeUnit.MILLISECONDS);
        dimensions = new GraphDimensions(api, setup).call();
        progress = importProgress(progressLogger, dimensions, setup);
    }

    public abstract Graph build();

    protected ImportProgress importProgress(
            ProgressLogger progressLogger,
            GraphDimensions dimensions,
            GraphSetup setup) {
        long relOperations = 0L;
        if (setup.loadIncoming || setup.loadAsUndirected) {
            relOperations += dimensions.maxRelCount();
        }
        if (setup.loadOutgoing || setup.loadAsUndirected) {
            relOperations += dimensions.maxRelCount();
        }
        return new ApproximatedImportProgress(
                progressLogger,
                setup.tracker,
                dimensions.hugeNodeCount(),
                relOperations
        );
    }

    protected IdMap loadIdMap() {
        final NodeImporter nodeImporter = new NodeImporter(
                api,
                setup.tracker,
                progress,
                dimensions.nodeCount(),
                dimensions.labelId());
        return nodeImporter.call();
    }

    protected HugeIdMap loadHugeIdMap(AllocationTracker tracker) {
        final HugeNodeImporter nodeImporter = new HugeNodeImporter(
                api,
                tracker,
                progress,
                dimensions.hugeNodeCount(),
                dimensions.allNodesCount(),
                dimensions.labelId());
        return nodeImporter.call();
    }

    protected WeightMapping newWeightMap(int propertyId, double defaultValue) {
        return propertyId == StatementConstants.NO_SUCH_PROPERTY_KEY
                ? new NullWeightMap(defaultValue)
                : new WeightMap(dimensions.nodeCount(), defaultValue, propertyId);
    }

    protected HugeWeightMapping hugeWeightMapping(
            AllocationTracker tracker,
            int propertyId,
            double defaultValue) {
        return propertyId == StatementConstants.NO_SUCH_PROPERTY_KEY
                    ? new HugeNullWeightMap(defaultValue)
                    : new HugeWeightMap(dimensions.hugeNodeCount(), defaultValue, tracker);
    }

    private static ProgressLogger progressLogger(Log log, long time, TimeUnit unit) {
        if (log == NullLog.getInstance()) {
            return ProgressLogger.NULL_LOGGER;
        }
        ProgressLoggerAdapter logger = new ProgressLoggerAdapter(log, TASK_LOADING);
        if (time > 0) {
            logger.withLogIntervalMillis((int) Math.min(unit.toMillis(time), Integer.MAX_VALUE));
        }
        return logger;
    }
}
