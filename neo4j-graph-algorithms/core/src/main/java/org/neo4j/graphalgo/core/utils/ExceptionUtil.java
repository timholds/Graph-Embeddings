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

import org.neo4j.helpers.Exceptions;
import org.neo4j.internal.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.api.exceptions.Status;

public final class ExceptionUtil {

    /**
     * @deprecated look at usage sites and replace with the proper replacements
     *             according to https://goo.gl/Ivn2kc
     */
    @Deprecated
    public static void throwUnchecked(final Throwable exception) {
        Exceptions.throwIfUnchecked(exception);
        throw new RuntimeException(exception);
    }

    public static <T> T throwKernelException(KernelException e) {
        Status status = e.status();
        String codeString = status.code().serialize();
        String message = e.getMessage();
        String newMessage;
        if (message == null || message.isEmpty()) {
            newMessage = codeString;
        } else {
            newMessage = codeString + ": " + message;
        }
        throw new RuntimeException(newMessage, e);
    }

    private ExceptionUtil() {
        throw new UnsupportedOperationException("No instances");
    }
}
