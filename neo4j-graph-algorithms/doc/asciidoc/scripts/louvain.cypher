// tag::create-sample-graph[]

MERGE (nAlice:User {id:'Alice'})
MERGE (nBridget:User {id:'Bridget'})
MERGE (nCharles:User {id:'Charles'})
MERGE (nDoug:User {id:'Doug'})
MERGE (nMark:User {id:'Mark'})
MERGE (nMichael:User {id:'Michael'})

MERGE (nAlice)-[:FRIEND]->(nBridget)
MERGE (nAlice)-[:FRIEND]->(nCharles)
MERGE (nMark)-[:FRIEND]->(nDoug)
MERGE (nBridget)-[:FRIEND]->(nMichael)
MERGE (nCharles)-[:FRIEND]->(nMark)
MERGE (nAlice)-[:FRIEND]->(nMichael)
MERGE (nCharles)-[:FRIEND]->(nDoug);

// end::create-sample-graph[]

// tag::stream-sample-graph[]

CALL algo.louvain.stream('User', 'FRIEND', {})
YIELD nodeId, community

MATCH (user:User) WHERE id(user) = nodeId

RETURN user.id AS user, community
ORDER BY community;

// end::stream-sample-graph[]

// tag::write-sample-graph[]

CALL algo.louvain('User', 'FRIEND',
  {write:true, writeProperty:'community'})
YIELD nodes, communityCount, iterations, loadMillis, computeMillis, writeMillis; 

// end::write-sample-graph[]

// tag::write-yelp[]

CALL algo.louvain('Business', 'CO_OCCURENT_REVIEWS',
  {write:true, writeProperty:'community'})
YIELD nodes, communityCount, iterations, loadMillis, computeMillis, writeMillis; 

// tag::end-yelp[]

// tag::cypher-loading[]

CALL algo.louvain(
  'MATCH (p:User) RETURN id(p) as id',
  'MATCH (p1:User)-[f:FRIEND]-(p2:User)
   RETURN id(p1) as source, id(p2) as target, f.weight as weight',
  {graph:'cypher',write:true});

// end::cypher-loading[]

// tag::huge-projection[]

CALL algo.louvain('User', 'FRIEND',{graph:'huge'})
YIELD nodes, communityCount, iterations, loadMillis, computeMillis, writeMillis; 

// end::huge-projection[]
