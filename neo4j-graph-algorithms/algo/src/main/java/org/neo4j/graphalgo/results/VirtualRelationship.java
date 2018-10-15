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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;

import static java.util.Arrays.asList;

/**
 * @author mh
 * @since 16.03.16
 */
public class VirtualRelationship implements Relationship {
    private static AtomicLong MIN_ID = new AtomicLong(-1);
    private final Node startNode;
    private final Node endNode;
    private final RelationshipType type;
    private final long id;
    private final Map<String, Object> props = new HashMap<>();

    public VirtualRelationship(Node startNode, Node endNode, RelationshipType type) {
        this.id = MIN_ID.getAndDecrement();
        this.startNode = startNode;
        this.endNode = endNode;
        this.type = type;
    }

    public VirtualRelationship(long id, Node startNode, Node endNode, RelationshipType type, Map<String, Object> props) {
        this.id = id;
        this.startNode = startNode;
        this.endNode = endNode;
        this.type = type;
        this.props.putAll(props);
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void delete() {
        if (getStartNode() instanceof VirtualNode) ((VirtualNode) getStartNode()).delete(this);
        if (getEndNode() instanceof VirtualNode) ((VirtualNode) getEndNode()).delete(this);
    }

    @Override
    public Node getStartNode() {
        return startNode;
    }

    @Override
    public Node getEndNode() {
        return endNode;
    }

    @Override
    public Node getOtherNode(Node node) {
        return node.equals(startNode) ? endNode : node.equals(endNode) ? startNode : null;
    }

    @Override
    public Node[] getNodes() {
        return new Node[] {
                startNode, endNode
        } ;
    }

    @Override
    public RelationshipType getType() {
        return type;
    }

    @Override
    public boolean isType(RelationshipType relationshipType) {
        return relationshipType.name().equals(type.name());
    }

    @Override
    public GraphDatabaseService getGraphDatabase() {
        return getStartNode().getGraphDatabase();
    }

    @Override
    public boolean hasProperty(String s) {
        return props.containsKey(s);
    }

    @Override
    public Object getProperty(String s) {
        return props.get(s);
    }

    @Override
    public Object getProperty(String s, Object o) {
        Object res = props.get(s);
        return res == null ? o : res;
    }

    @Override
    public void setProperty(String s, Object o) {
        props.put(s, o);
    }

    @Override
    public Object removeProperty(String s) {
        return props.remove(s);
    }

    @Override
    public Iterable<String> getPropertyKeys() {
        return props.keySet();
    }

    @Override
    public Map<String, Object> getProperties(String... strings) {
        Map<String, Object> res = new LinkedHashMap<>(props);
        res.keySet().retainAll(asList(strings));
        return res;
    }

    @Override
    public Map<String, Object> getAllProperties() {
        return props;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof Relationship && id == ((Relationship) o).getId();

    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }

    public Relationship withProperties(Map<String, Object> props) {
        this.props.putAll(props);
        return this;
    }

    @Override
    public String toString()
    {
        return "VirtualRelationship{" + "startNode=" + startNode.getLabels() + ", endNode=" + endNode.getLabels() + ", " +
                "type=" + type + '}';
    }
}
