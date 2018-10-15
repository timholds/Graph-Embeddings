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
package org.neo4j.graphalgo.core.utils;

import org.neo4j.logging.Log;
import org.neo4j.logging.NullLog;

import java.util.function.Supplier;

/**
 * @author mknblch
 */
public interface ProgressLogger {

    ProgressLogger NULL_LOGGER = new ProgressLoggerAdapter(NullLog.getInstance(), "NULL");
    Supplier<String> NO_MESSAGE = () -> null;

    static ProgressLogger wrap(Log log, String task) {
        return new ProgressLoggerAdapter(log, task);
    }

    void logProgress(double percentDone, Supplier<String> msg);

    void log(Supplier<String> msg);

    default void log(String msg) {
        log(() -> msg);
    }

    default void logDone(Supplier<String> msg) {
        log(msg);
    }

    default void logProgress(double numerator, double denominator, Supplier<String> msg) {
        logProgress(numerator / denominator, msg);
    }

    default void logProgress(double numerator, double denominator) {
        logProgress(numerator, denominator, NO_MESSAGE);
    }

    default void logProgress(double percentDone) {
        logProgress(percentDone, NO_MESSAGE);
    }

    default void logDone() {
        logDone(NO_MESSAGE);
    }
}
