package algo.algo;

import org.neo4j.procedure.Description;
import algo.result.RelationshipResult;
import algo.util.Util;
import org.neo4j.graphdb.*;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Cover {

    @Context
    public GraphDatabaseAPI db;

    @Procedure
    @Description("algo.algo.cover(nodes) yield rel - returns all relationships between this set of nodes")
    public Stream<RelationshipResult> cover(@Name("nodes") Object nodes) {
        Set<Node> nodeSet = Util.nodeStream(db, nodes).collect(Collectors.toSet());
        return nodeSet.parallelStream()
                .flatMap(n ->
                        Util.inTx(db,() ->
                        StreamSupport.stream(n.getRelationships(Direction.OUTGOING)
                                .spliterator(),false)
                                .filter(r -> nodeSet.contains(r.getEndNode()))
                                .map(RelationshipResult::new)));
    }
}
