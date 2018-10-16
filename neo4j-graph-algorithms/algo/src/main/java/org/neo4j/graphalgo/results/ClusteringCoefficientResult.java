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
package org.neo4j.graphalgo.results;

/**
 * @author mknblch
 */
public class ClusteringCoefficientResult {

    public final long loadMillis;
    public final long computeMillis;
    public final long writeMillis;
    public final long nodeCount;
    public final double coefficient;

    public ClusteringCoefficientResult(long loadMillis, long computeMillis, long writeMillis, long nodeCount, double coefficient) {
        this.loadMillis = loadMillis;
        this.computeMillis = computeMillis;
        this.writeMillis = writeMillis;
        this.nodeCount = nodeCount;
        this.coefficient = coefficient;
    }

    public static class Builder extends AbstractResultBuilder<ClusteringCoefficientResult> {

        private long nodeCount;
        private double averageClusteringCoefficient;

        public Builder withNodeCount(long nodeCount) {
            this.nodeCount = nodeCount;
            return this;
        }

        public Builder withCoefficient(double averageClusteringCoefficient) {
            this.averageClusteringCoefficient = averageClusteringCoefficient;
            return this;
        }

        @Override
        public ClusteringCoefficientResult build() {
            return new ClusteringCoefficientResult(loadDuration, evalDuration, writeDuration, nodeCount, averageClusteringCoefficient);
        }

    }

}
