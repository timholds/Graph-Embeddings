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
package org.neo4j.graphalgo.algo;

import org.junit.Rule;
import org.junit.Test;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.huge.HugeGraphFactory;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.impl.closeness.MSClosenessCentrality;
import org.neo4j.graphalgo.impl.DangalchevClosenessCentrality;
import org.neo4j.test.rule.ImpermanentDatabaseRule;

/**
 *
 * @author mknblch
 */
public class ClosenessCentralityIntegrationTest_546 {

    @Rule
    public ImpermanentDatabaseRule db = new ImpermanentDatabaseRule();

    private String name(long id) {
        String[] name = {""};
        db.execute("MATCH (n) WHERE id(n) = " + id + " RETURN n.id as name")
                .accept(row -> {
                    name[0] = row.getString("name");
                    return false;
                });
        if (name[0].isEmpty()) {
            throw new IllegalArgumentException("unknown id " + id);
        }
        return name[0];
    }

    @Test
    public void test547() throws Exception {

        String importQuery =
                "CREATE (alice:Person{id:\"Alice\"}),\n" +
                        "       (michael:Person{id:\"Michael\"}),\n" +
                        "       (karin:Person{id:\"Karin\"}),\n" +
                        "       (chris:Person{id:\"Chris\"}),\n" +
                        "       (will:Person{id:\"Will\"}),\n" +
                        "       (mark:Person{id:\"Mark\"})\n" +
                        "CREATE (michael)<-[:KNOWS]-(karin),\n" +
                        "       (michael)-[:KNOWS]->(chris),\n" +
                        "       (will)-[:KNOWS]->(michael),\n" +
                        "       (mark)<-[:KNOWS]-(michael),\n" +
                        "       (mark)-[:KNOWS]->(will),\n" +
                        "       (alice)-[:KNOWS]->(michael),\n" +
                        "       (will)-[:KNOWS]->(chris),\n" +
                        "       (chris)-[:KNOWS]->(karin);";

        db.execute(importQuery);

        final Graph graph = new GraphLoader(db, Pools.DEFAULT)
                .withLabel("Person")
                .withRelationshipType("KNOWS")
                .asUndirected(true)
                .load(HugeGraphFactory.class);

        System.out.println("547:");
        new MSClosenessCentrality(graph, 2, Pools.DEFAULT, false)
                .compute()
                .resultStream()
                .forEach(this::print);

    }

    @Test
    public void test547_residual() throws Exception {

        String importQuery =
                "CREATE (alice:Person{id:\"Alice\"}),\n" +
                        "       (michael:Person{id:\"Michael\"}),\n" +
                        "       (karin:Person{id:\"Karin\"}),\n" +
                        "       (chris:Person{id:\"Chris\"}),\n" +
                        "       (will:Person{id:\"Will\"}),\n" +
                        "       (mark:Person{id:\"Mark\"})\n" +
                        "CREATE (michael)<-[:KNOWS]-(karin),\n" +
                        "       (michael)-[:KNOWS]->(chris),\n" +
                        "       (will)-[:KNOWS]->(michael),\n" +
                        "       (mark)<-[:KNOWS]-(michael),\n" +
                        "       (mark)-[:KNOWS]->(will),\n" +
                        "       (alice)-[:KNOWS]->(michael),\n" +
                        "       (will)-[:KNOWS]->(chris),\n" +
                        "       (chris)-[:KNOWS]->(karin);";

        db.execute(importQuery);

        final Graph graph = new GraphLoader(db, Pools.DEFAULT)
                .withLabel("Person")
                .withRelationshipType("KNOWS")
                .asUndirected(true)
                .load(HugeGraphFactory.class);

        System.out.println("547 Dangalchev:");
        new DangalchevClosenessCentrality(graph, 2, Pools.DEFAULT)
                .compute()
                .resultStream()
                .forEach(this::print);

    }

    @Test
    public void test546() throws Exception {

        String importQuery =
                "CREATE (nAlice:User {id:'Alice'})\n" +
                        ",(nBridget:User {id:'Bridget'})\n" +
                        ",(nCharles:User {id:'Charles'})\n" +
                        ",(nMark:User {id:'Mark'})\n" +
                        ",(nMichael:User {id:'Michael'})\n" +
                        "CREATE (nAlice)-[:FRIEND]->(nBridget)\n" +
                        ",(nAlice)<-[:FRIEND]-(nBridget)\n" +
                        ",(nAlice)-[:FRIEND]->(nCharles)\n" +
                        ",(nAlice)<-[:FRIEND]-(nCharles)\n" +
                        ",(nMark)-[:FRIEND]->(nMichael)\n" +
                        ",(nMark)<-[:FRIEND]-(nMichael);";

        db.execute(importQuery);

        final Graph graph = new GraphLoader(db, Pools.DEFAULT)
                .withLabel("Person")
                .withRelationshipType("KNOWS")
                .asUndirected(true)
                .load(HugeGraphFactory.class);

        System.out.println("546:");
        new MSClosenessCentrality(graph, 2, Pools.DEFAULT, false)
                .compute()
                .resultStream()
                .forEach(this::print);

    }

    @Test
    public void test546_residual() throws Exception {

        String importQuery =
                "CREATE (nAlice:User {id:'Alice'})\n" +
                        ",(nBridget:User {id:'Bridget'})\n" +
                        ",(nCharles:User {id:'Charles'})\n" +
                        ",(nMark:User {id:'Mark'})\n" +
                        ",(nMichael:User {id:'Michael'})\n" +
                        "CREATE (nAlice)-[:FRIEND]->(nBridget)\n" +
                        ",(nAlice)<-[:FRIEND]-(nBridget)\n" +
                        ",(nAlice)-[:FRIEND]->(nCharles)\n" +
                        ",(nAlice)<-[:FRIEND]-(nCharles)\n" +
                        ",(nMark)-[:FRIEND]->(nMichael)\n" +
                        ",(nMark)<-[:FRIEND]-(nMichael);";

        db.execute(importQuery);

        final Graph graph = new GraphLoader(db, Pools.DEFAULT)
                .withLabel("Person")
                .withRelationshipType("KNOWS")
                .asUndirected(true)
                .load(HugeGraphFactory.class);

        System.out.println("546 Dangalchev:");
        new DangalchevClosenessCentrality(graph, 2, Pools.DEFAULT)
                .compute()
                .resultStream()
                .forEach(this::print);

    }

    private void print(MSClosenessCentrality.Result result) {
        System.out.printf("%s | %.3f%n", name(result.nodeId), result.centrality);
    }

    private void print(DangalchevClosenessCentrality.Result result) {
        System.out.printf("%s | %.3f%n", name(result.nodeId), result.centrality);
    }
}
