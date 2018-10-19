// tag::create-sample-graph[]

MERGE (alice:Person{id:"Alice"})
MERGE (michael:Person{id:"Michael"})
MERGE (karin:Person{id:"Karin"})
MERGE (chris:Person{id:"Chris"})
MERGE (will:Person{id:"Will"})
MERGE (mark:Person{id:"Mark"})

MERGE (michael)-[:KNOWS]->(karin)
MERGE (michael)-[:KNOWS]->(chris)
MERGE (will)-[:KNOWS]->(michael)
MERGE (mark)-[:KNOWS]->(michael)
MERGE (mark)-[:KNOWS]->(will)
MERGE (alice)-[:KNOWS]->(michael)
MERGE (will)-[:KNOWS]->(chris)
MERGE (chris)-[:KNOWS]->(karin);

// end::create-sample-graph[]

// tag::stream-triples[]

CALL algo.triangle.stream('Person','KNOWS')
YIELD nodeA,nodeB,nodeC

MATCH (a:Person) WHERE id(a) = nodeA
MATCH (b:Person) WHERE id(b) = nodeB
MATCH (c:Person) WHERE id(c) = nodeC

RETURN a.id AS nodeA, b.id AS nodeB, c.id AS nodeC

// end::stream-triples[]


// tag::triangle-write-sample-graph[]

CALL algo.triangleCount('Person', 'KNOWS',
  {concurrency:4, write:true, writeProperty:'triangles',clusteringCoefficientProperty:'coefficient'})
YIELD loadMillis, computeMillis, writeMillis, nodeCount, triangleCount, averageClusteringCoefficient;

// end::triangle-write-sample-graph[]

// tag::triangle-stream-sample-graph[]

CALL algo.triangleCount.stream('Person', 'KNOWS', {concurrency:4})
YIELD nodeId, triangles, coefficient

MATCH (p:Person) WHERE id(p) = nodeId

RETURN p.id AS name, triangles, coefficient
ORDER BY coefficient DESC

// end::triangle-stream-sample-graph[]

// tag::triangle-write-yelp[]

CALL algo.triangleCount('User', 'FRIEND',
  {concurrency:4, write:true, writeProperty:'triangles',clusteringCoefficientProperty:'coefficient'})
YIELD loadMillis, computeMillis, writeMillis, nodeCount, triangleCount, averageClusteringCoefficient;

// end::triangle-write-yelp[]

// tag::cypher-loading[]

CALL algo.triangleCount(
  'MATCH (p:Person) RETURN id(p) as id',
  'MATCH (p1:Person)-[:KNOWS]->(p2:Person) RETURN id(p1) as source,id(p2) as target',
  {concurrency:4, write:true, writeProperty:'triangle',graph:'cypher', clusteringCoefficientProperty:'coefficient'})
YIELD loadMillis, computeMillis, writeMillis, nodeCount, triangleCount, averageClusteringCoefficient

// end::cypher-loading[]
