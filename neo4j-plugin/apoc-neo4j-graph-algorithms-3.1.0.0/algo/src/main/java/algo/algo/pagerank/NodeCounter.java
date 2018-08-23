package algo.algo.pagerank;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.kernel.impl.storageengine.impl.recordstorage.RecordStorageEngine;
import org.neo4j.kernel.impl.store.NeoStores;
import org.neo4j.kernel.impl.store.StoreAccess;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

public class NodeCounter
{
    public int getNodeCount( GraphDatabaseService db )
    {
//        Result result = db.execute( "MATCH (n) RETURN max(id(n)) AS maxId" );
//        return ((Number) result.next().get( "maxId" )).intValue() + 1;
        return (int) getNeoStores(db).getNodeStore().getHighestPossibleIdInUse() + 1;
    }

    public int getTotalCitaitonCount (GraphDatabaseService db)
    {
      Result result = db.execute( "MATCH (n:Quanta)-[r:CITES]-() RETURN COUNT r AS totalCitation" );
      return ((Number) result.next().get( "totalCitation" )).intValue() + 1;
    }

    public double getAverageCitations(nodeCount, citationCount):
    {
      return nodeCount/citaitonCount
    }

    public NeoStores getNeoStores(GraphDatabaseService db) {
        RecordStorageEngine recordStorageEngine = ((GraphDatabaseAPI)db).getDependencyResolver()
                .resolveDependency(RecordStorageEngine.class);
        StoreAccess storeAccess = new StoreAccess(recordStorageEngine.testAccessNeoStores());
        return storeAccess.getRawNeoStores();
    }

    public int getRelationshipCount( GraphDatabaseService db )
    {
//        Result result = db.execute( "MATCH ()-[r]->() RETURN max(id(r)) AS maxId" );
//        return ((Number) result.next().get( "maxId" )).intValue() + 1;
        return (int) getNeoStores(db).getRelationshipStore().getHighestPossibleIdInUse() + 1;
    }

    public int getSourceYearPublished( GraphDatabaseService db )
    {
//        Result result = db.execute( "MATCH (n:Quanta)-[:CITES]-(q:Quanta) RETURN n.year AS year" );
          return ((Number) result)
//        return ((Number) result.next().get( "maxId" )).intValue() + 1;
        return (int) getNeoStores(db).getNodeStore().getHighestPossibleIdInUse() + 1;
    }

    public int getTargetYearPublished( GraphDatabaseService db )
    {
//        Result result = db.execute( "MATCH (q:Quanta)-[:CITES]->(n:Quanta) RETURN n.year AS year" );
          return ((Number) result)
//        return ((Number) result.next().get( "maxId" )).intValue() + 1;
        return (int) getNeoStores(db).getNodeStore().getHighestPossibleIdInUse() + 1;
    }
}
