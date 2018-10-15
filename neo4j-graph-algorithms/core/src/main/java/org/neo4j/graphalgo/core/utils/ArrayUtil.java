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

public final class ArrayUtil {

    public static final int LINEAR_SEARCH_LIMIT = 64;

    public static boolean binarySearch(int[] arr, int length, int key) {
        int low = 0;
        int high = length - 1;
        while (high - low > LINEAR_SEARCH_LIMIT) {
            int mid = (low + high) >>> 1;
            int midVal = arr[mid];
            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return true;
        }
        return linearSearch2(arr, low, high, key);

    }

    // TODO eval
    public static boolean linearSearch2(int[] arr, int low, int high, int key) {
        for (int i = low; i <= high; i++) {
            if (arr[i] == key) return true;
            if (arr[i] > key) return false;
        }
        return false;
    }

    public static boolean linearSearch(int[] arr, int length, int key) {
        int i = 0;
        for (; i < length - 4; i += 4) {
            if (arr[i] == key) return true;
            if (arr[i + 1] == key) return true;
            if (arr[i + 2] == key) return true;
            if (arr[i + 3] == key) return true;
        }
        for (; i < length; i++) {
            if (arr[i] == key) {
                return true;
            }
        }
        return false;
    }

    private static boolean linearSearch(int[] arr, int low, int high, int key) {
        int i = low;
        for (; i < high - 3; i += 4) {
            if (arr[i] > key) return false;
            if (arr[i] == key) return true;
            if (arr[i + 1] == key) return true;
            if (arr[i + 2] == key) return true;
            if (arr[i + 3] == key) return true;
        }
        for (; i <= high; i++) {
            if (arr[i] == key) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the index where {@code (ids[idx] <= id) && (ids[idx + 1] > id)}.
     * The result differs from that of {@link java.util.Arrays#binarySearch(long[], long)}
     * in that this method returns a positive index even if the array does not
     * directly contain the searched value.
     * It returns -1 iff the value is smaller than the smallest one in the array.
     */
    public static int binaryLookup(long id, long[] ids) {
        int length = ids.length;

        int low = 0;
        int high = length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = ids[mid];

            if (midVal < id) {
                low = mid + 1;
            } else if (midVal > id) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return low - 1;
    }

    /**
     * Find the index where {@code (ids[idx] <= id) && (ids[idx + 1] > id)}.
     * The result differs from that of {@link java.util.Arrays#binarySearch(long[], long)}
     * in that this method returns a positive index even if the array does not
     * directly contain the searched value.
     * It returns -1 iff the value is smaller than the smallest one in the array.
     */
    public static int binaryLookup(int id, int ids[]) {
        int length = ids.length;

        int low = 0;
        int high = length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = ids[mid];

            if (midVal < id) {
                low = mid + 1;
            } else if (midVal > id) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return low - 1;
    }

    private ArrayUtil() {
        throw new UnsupportedOperationException("No instances");
    }
}
