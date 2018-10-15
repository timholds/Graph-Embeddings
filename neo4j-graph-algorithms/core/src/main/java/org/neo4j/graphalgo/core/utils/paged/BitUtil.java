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
package org.neo4j.graphalgo.core.utils.paged;

public final class BitUtil {

    public static boolean isPowerOfTwo(final int value) {
        return value > 0 && ((value & (~value + 1)) == value);
    }

    /**
     * returns the previous highest power of two, or the current value if it's already a power of two or zero
     */
    public static int previousPowerOfTwo(int v) {
        v |= (v >>  1);
        v |= (v >>  2);
        v |= (v >>  4);
        v |= (v >>  8);
        v |= (v >> 16);
        return v - (v >>> 1);
    }

    /**
     * returns the previous highest power of two, or the current value if it's already a power of two or zero
     */
    public static long previousPowerOfTwo(long v) {
        v |= (v >>  1);
        v |= (v >>  2);
        v |= (v >>  4);
        v |= (v >>  8);
        v |= (v >> 16);
        v |= (v >> 32);
        return v - (v >>> 1);
    }

    public static int nearbyPowerOfTwo(int x) {
        int next = nextHighestPowerOfTwo(x);
        int prev = next >>> 1;
        return (next - x) <= (x - prev) ? next : prev;
    }

    public static long nearbyPowerOfTwo(long x) {
        long next = nextHighestPowerOfTwo(x);
        long prev = next >>> 1;
        return (next - x) <= (x - prev) ? next : prev;
    }

    /**
     * returns the next highest power of two, or the current value if it's already a power of two or zero
     */
    public static int nextHighestPowerOfTwo(int v) {
        v--;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        v++;
        return v;
    }

    /**
     * returns the next highest power of two, or the current value if it's already a power of two or zero
     */
    public static long nextHighestPowerOfTwo(long v) {
        v--;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        v |= v >> 32;
        v++;
        return v;
    }

    public static long align(long value, int alignment) {
        assert isPowerOfTwo(alignment) : "alignment must be a power of 2:" + alignment;
        return value + (long) (alignment - 1) & (long) (~(alignment - 1));
    }

    private BitUtil() {
        throw new UnsupportedOperationException("No instances");
    }
}
