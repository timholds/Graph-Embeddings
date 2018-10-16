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
package org.neo4j.graphalgo.helper.graphbuilder;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.Random;

/**
 * default builder makes methods
 * from abstract graphBuilder accessible
 *
 * @author mknblch
 */
public class DefaultBuilder extends GraphBuilder<DefaultBuilder> {

    protected DefaultBuilder(GraphDatabaseAPI api, Label label, RelationshipType relationship, Random random) {
        super(api, label, relationship, random);
    }

    /**
     * create a relationship within a transaction
     *
     * @param p the source node
     * @param q the target node
     * @return the relationship object
     */
    @Override
    public Relationship createRelationship(Node p, Node q) {
        return withinTransaction(() -> super.createRelationship(p, q));
    }

    /**
     * create a node within a transaction
     *
     * @return the node
     */
    @Override
    public Node createNode() {
        return withinTransaction(super::createNode);
    }

    @Override
    protected DefaultBuilder me() {
        return this;
    }
}
