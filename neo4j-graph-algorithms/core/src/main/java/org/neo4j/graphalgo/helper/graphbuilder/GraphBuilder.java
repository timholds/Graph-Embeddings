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

import org.neo4j.graphalgo.core.utils.ExceptionUtil;
import org.neo4j.graphalgo.core.utils.TransactionWrapper;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.internal.kernel.api.Write;
import org.neo4j.internal.kernel.api.exceptions.InvalidTransactionTypeKernelException;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.HashSet;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The GraphBuilder intends to ease the creation
 * of test graphs with well known properties
 *
 * @author mknblch
 */
public abstract class GraphBuilder<ME extends GraphBuilder<ME>> {

    private final ME self;

    protected final HashSet<Node> nodes;
    protected final HashSet<Relationship> relationships;

    private final GraphDatabaseAPI api;
    private final TransactionWrapper tx;
    private final Random random;

    protected Label label;
    protected RelationshipType relationship;

    protected GraphBuilder(GraphDatabaseAPI api, Label label, RelationshipType relationship, Random random) {
        this.api = api;
        this.label = label;
        this.relationship = relationship;
        this.tx = new TransactionWrapper(api);
        nodes = new HashSet<>();
        relationships = new HashSet<>();
        this.self = me();
        this.random = random;
    }

    /**
     * set the label for all subsequent {@link GraphBuilder#createNode()} operations
     * in the current and in derived builders.
     *
     * @param label the label
     * @return child instance to make methods of the child class accessible.
     */
    public ME setLabel(String label) {
        if (null == label) {
            return self;
        }
        this.label = Label.label(label);
        return self;
    }

    /**
     * set the relationship type for all subsequent {@link GraphBuilder#createRelationship(Node, Node)}
     * operations in the current and in derived builders.
     *
     * @param relationship the name of the relationship type
     * @return child instance to make methods of the child class accessible.
     */
    public ME setRelationship(String relationship) {
        if (null == relationship) {
            return self;
        }
        this.relationship = RelationshipType.withName(relationship);
        return self;
    }

    /**
     * create a relationship between p and q with the previously defined
     * relationship type
     *
     * @param p the source node
     * @param q the target node
     * @return the relationship object
     */
    public Relationship createRelationship(Node p, Node q) {
        final Relationship relationshipTo = p.createRelationshipTo(q, relationship);
        relationships.add(relationshipTo);
        return relationshipTo;
    }

    /**
     * create a new node and set a label if previously defined
     *
     * @return the created node
     */
    public Node createNode() {
        Node node = api.createNode();
        if (null != label) {
            node.addLabel(label);
        }
        nodes.add(node);
        return node;
    }

    /**
     * run node consumer in tx as long as he returns true
     *
     * @param consumer the node consumer
     * @return child instance to make methods of the child class accessible.
     */
    public ME forEachNodeInTx(Consumer<Node> consumer) {
        withinTransaction(() -> nodes.forEach(consumer));
        return self;
    }

    public ME forEachRelInTx(Consumer<Relationship> consumer) {
        withinTransaction(() -> relationships.forEach(consumer));
        return self;
    }

    /**
     * runs the write consumer in a transaction
     *
     * @param consumer the write consumer
     * @return child instance to make methods of the child class accessible.
     */
    public ME writeInTransaction(Consumer<Write> consumer) {
        tx.accept(ktx -> {
            try {
                consumer.accept(ktx.dataWrite());
            } catch (InvalidTransactionTypeKernelException e) {
                ExceptionUtil.throwKernelException(e);
            }
        });
        return self;
    }

    /**
     * run the runnable in a transaction
     *
     * @param runnable the runnable
     * @return child instance to make methods of the child class accessible.
     */
    public ME withinTransaction(Runnable runnable) {
        tx.accept(__ -> runnable.run());
        return self;
    }

    /**
     * run supplier within a transaction and returns its result
     *
     * @param supplier the supplier
     * @param <T>      the return type
     * @return child instance to make methods of the child class accessible.
     */
    public <T> T withinTransaction(Supplier<T> supplier) {
        return tx.apply(__ -> supplier.get());
    }

    /**
     * create a new default builder with its own node-set but
     * inherits the current label and relationship type
     *
     * @return a new default builder
     */
    public DefaultBuilder newDefaultBuilder() {
        return new DefaultBuilder(api, label, relationship, random);
    }

    /**
     * create a new ring builder with its own node-set but
     * inherits current label and relationship type.
     *
     * @return a new ring builder
     */
    public RingBuilder newRingBuilder() {
        return new RingBuilder(api, label, relationship, random);
    }

    /**
     * creates a grid of nodes
     * inherits current label and relationship type.
     *
     * @return the GridBuilder
     */
    public GridBuilder newGridBuilder() {
        return new GridBuilder(api, label, relationship, random);
    }

    /**
     * create lines of nodes where each node is connected to its successor
     * inherits current label and relationship type.
     *
     * @return the LineBuilder
     */
    public LineBuilder newLineBuilder() {
        return new LineBuilder(api, label, relationship, random);
    }

    /**
     * create a complete graph where each node is interconnected
     * inherits current label and relationship type.
     *
     * @return the CompleteGraphBuilder
     */
    public CompleteGraphBuilder newCompleteGraphBuilder() {
        return new CompleteGraphBuilder(api, label, relationship, random);
    }

    protected double randomDouble() {
        return random.nextDouble();
    }

    /**
     * return child instance for method chaining from methods of the abstract parent class
     *
     * @return self (child instance)
     */
    protected abstract ME me();

    /**
     * create a new default builder
     *
     * @param api the neo4j api
     * @return a new default builder
     */
    public static DefaultBuilder create(GraphDatabaseAPI api) {
        return new DefaultBuilder(api, null, null, RNGHolder.rng);
    }

    /**
     * create a new default builder with a defined RNG
     *
     * @param api    the neo4j api
     * @param random the random number generator
     * @return a new default builder
     */
    public static DefaultBuilder create(GraphDatabaseAPI api, Random random) {
        return new DefaultBuilder(api, null, null, random);
    }

    private static final class RNGHolder {
        static final Random rng = new Random();
    }
}
