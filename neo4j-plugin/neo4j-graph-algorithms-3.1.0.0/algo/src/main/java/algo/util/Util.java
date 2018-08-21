package algo.util;

import algo.Pools;
import algo.path.RelationshipTypeAndDirections;
import org.neo4j.collection.primitive.PrimitiveLongIterator;
import org.neo4j.graphdb.*;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.helpers.collection.Pair;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.*;

import static java.lang.String.format;

/**
 * @author mh
 * @since 24.04.16
 */
public class Util {
    public static final Label[] NO_LABELS = new Label[0];
    public static final String NODE_COUNT = "MATCH (n) RETURN count(*) as result";
    public static final String REL_COUNT = "MATCH ()-->() RETURN count(*) as result";
    public static final String COMPILED = "compiledExperimentalFeatureNotSupportedForProductionUse";


    public static String labelString(Node n) {
        return StreamSupport.stream(n.getLabels().spliterator(),false).map(Label::name).sorted().collect(Collectors.joining(":"));
    }
    public static List<String> labelStrings(Node n) {
        return StreamSupport.stream(n.getLabels().spliterator(),false).map(Label::name).sorted().collect(Collectors.toList());
    }
    public static Label[] labels(Object labelNames) {
        if (labelNames==null) return NO_LABELS;
        if (labelNames instanceof List) {
            List names = (List) labelNames;
            Label[] labels = new Label[names.size()];
            int i = 0;
            for (Object l : names) {
                if (l==null) continue;
                labels[i++] = Label.label(l.toString());
            }
            if (i <= labels.length) return Arrays.copyOf(labels,i);
            return labels;
        }
        return new Label[]{Label.label(labelNames.toString())};
    }

    public static RelationshipType type(Object type) {
        if (type == null) throw new RuntimeException("No relationship-type provided");
        return RelationshipType.withName(type.toString());
    }

    @SuppressWarnings("unchecked")
    public static LongStream ids(Object ids) {
        if (ids == null) return LongStream.empty();
        if (ids instanceof Number) return LongStream.of(((Number)ids).longValue());
        if (ids instanceof Node) return LongStream.of(((Node)ids).getId());
        if (ids instanceof Relationship) return LongStream.of(((Relationship)ids).getId());
        if (ids instanceof Collection) {
            Collection<Object> coll = (Collection<Object>) ids;
            return coll.stream().mapToLong( (o) -> ((Number)o).longValue());
        }
        if (ids instanceof Iterable) {
            Spliterator<Object> spliterator = ((Iterable) ids).spliterator();
            return StreamSupport.stream(spliterator,false).mapToLong( (o) -> ((Number)o).longValue());
        }
        throw new RuntimeException("Can't convert "+ids.getClass()+" to a stream of long ids");
    }

    public static Stream<Object> stream(Object values) {
        if (values == null) return Stream.empty();
        if (values instanceof Collection) return ((Collection)values).stream();
        if (values instanceof Object[]) return Stream.of(((Object[])values));
        if (values instanceof Iterable) {
            Spliterator<Object> spliterator = ((Iterable) values).spliterator();
            return StreamSupport.stream(spliterator,false);
        }
        return Stream.of(values);
    }

    public static Stream<Node> nodeStream(GraphDatabaseService db, Object ids) {
        return stream(ids).map(id -> node(db, id));
    }

    public static Node node(GraphDatabaseService db, Object id) {
        if (id instanceof Node) return (Node)id;
        if (id instanceof Number) return db.getNodeById(((Number)id).longValue());
        throw new RuntimeException("Can't convert "+id.getClass()+" to a Node");
    }

    public static Stream<Relationship> relsStream(GraphDatabaseService db, Object ids) {
        return stream(ids).map(id -> relationship(db, id));
    }

    public static Relationship relationship(GraphDatabaseService db, Object id) {
        if (id instanceof Relationship) return (Relationship)id;
        if (id instanceof Number) return db.getRelationshipById(((Number)id).longValue());
        throw new RuntimeException("Can't convert "+id.getClass()+" to a Relationship");
    }

    public static double doubleValue(PropertyContainer pc, String prop, Number defaultValue) {
        return toDouble(pc.getProperty(prop, defaultValue));

    }

    public static double doubleValue(PropertyContainer pc, String prop) {
        return doubleValue(pc, prop, 0);
    }

    public static Direction parseDirection(String direction) {
        if (null == direction) {
            return Direction.BOTH;
        }
        try {
            return Direction.valueOf(direction.toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException(format("Cannot convert value '%s' to Direction. Legal values are '%s'",
                    direction, Arrays.toString(Direction.values())));
        }
    }

    public static RelationshipType[] toRelTypes(List<String> relTypeStrings) {
        RelationshipType[] relTypes = new RelationshipType[relTypeStrings.size()];
        for (int i = 0; i < relTypes.length; i++) {
            relTypes[i] = RelationshipType.withName(relTypeStrings.get(i));
        }
        return relTypes;
    }

    public static RelationshipType[] allRelationshipTypes(GraphDatabaseService db) {
        return Iterables.asArray(RelationshipType.class, db.getAllRelationshipTypes());
    }

    public static RelationshipType[] typesAndDirectionsToTypesArray(String typesAndDirections) {
        List<RelationshipType> relationshipTypes = new ArrayList<>();
        for (Pair<RelationshipType, Direction> pair : RelationshipTypeAndDirections.parse(typesAndDirections)) {
            if (null != pair.first()) {
                relationshipTypes.add(pair.first());
            }
        }
        return relationshipTypes.toArray(new RelationshipType[relationshipTypes.size()]);
    }

    public static <T> Future<T> inTxFuture(ExecutorService pool, GraphDatabaseAPI db, Callable<T> callable) {
        try {
            return pool.submit(() -> {
                try (Transaction tx = db.beginTx()) {
                    T result = callable.call();
                    tx.success();
                    return result;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Error executing in separate transaction", e);
        }
    }
    public static <T> T inTx(GraphDatabaseAPI db, Callable<T> callable) {
        try {
            return inTxFuture(Pools.DEFAULT, db, callable).get();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error executing in separate transaction: "+e.getMessage(), e);
        }
    }
    public static <T> T inThread(Callable<T> callable) {
        try {
            return inFuture(callable).get();
        } catch (Exception e) {
            throw new RuntimeException("Error executing in separate thread: "+e.getMessage(), e);
        }
    }

    public static <T> Future<T> inFuture(Callable<T> callable) {
        return Pools.DEFAULT.submit(callable);
    }

    public static Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }

    public static Map<String, Object> subMap(Map<String, ?> params, String prefix) {
        Map<String, Object> config = new HashMap<>(10);
        int len = prefix.length() + (prefix.isEmpty() || prefix.endsWith(".") ? 0 : 1);
        for (Map.Entry<String, ?> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix)) {
                config.put(key.substring(len), entry.getValue());
            }
        }
        return config;
    }

    public static long toLong(Object value) {
        if (value instanceof Number) return ((Number)value).longValue();
        return Long.parseLong(value.toString());
    }

    public static boolean toBoolean(Object value) {
        if ((value == null || value instanceof Number && (((Number) value).longValue()) == 0L || value instanceof String && (value.equals("") || ((String) value).equalsIgnoreCase("false") || ((String) value).equalsIgnoreCase("no")|| ((String) value).equalsIgnoreCase("0"))|| value instanceof Boolean && value.equals(false))) {
            return false;
        }
        return true;
    }

    public static String encodeUrlComponent(String value) {
        try {
            return URLEncoder.encode(value,"UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Unsupported character set utf-8");
        }
    }

    public static Stream<List<Object>> partitionSubList(List<Object> data, int partitions) {
        return partitionSubList(data,partitions,null);
    }
    public static Stream<List<Object>> partitionSubList(List<Object> data, int partitions, List<Object> tombstone) {
        if (partitions==0) partitions=1;
        List<Object> list = new ArrayList<>(data);
        int total = list.size();
        int batchSize = Math.max((int)Math.ceil((double)total / partitions),1);
        Stream<List<Object>> stream = IntStream.range(0, partitions).parallel()
                .mapToObj((part) -> list.subList(Math.min(part * batchSize, total), Math.min((part + 1) * batchSize, total)))
                .filter(partition -> !partition.isEmpty());
        return tombstone == null ? stream : Stream.concat(stream,Stream.of(tombstone));
    }

    public static Long runNumericQuery(GraphDatabaseService db, String query, Map<String, Object> params) {
        if (params == null) params = Collections.emptyMap();
        try (ResourceIterator<Long> it = db.execute(query,params).<Long>columnAs("result")) {
            return it.next();
        }
    }

    public static long nodeCount(GraphDatabaseService db) {
        return runNumericQuery(db,NODE_COUNT,null);
    }
    public static long relCount(GraphDatabaseService db) {
        return runNumericQuery(db,REL_COUNT,null);
    }

    public static LongStream toLongStream(PrimitiveLongIterator it) {
        PrimitiveIterator.OfLong iterator = new PrimitiveIterator.OfLong() {

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public long nextLong() {
                return it.next();
            }
        };
        return StreamSupport.longStream(Spliterators.spliteratorUnknownSize(
                iterator,
                Spliterator.ORDERED | Spliterator.IMMUTABLE | Spliterator.NONNULL), false);
    }

    public static String readResourceFile(String name) {
		InputStream is = Util.class.getClassLoader().getResourceAsStream(name);
		return new Scanner(is).useDelimiter("\\Z").next();
	}

    public static <T> List<T> take(Iterator<T> iterator, int batchsize) {
        ArrayList<T> result = new ArrayList<>(batchsize);
        while (iterator.hasNext() && batchsize-- > 0) {
            result.add(iterator.next());
        }
        return result;
    }

    public static Map<String, Object> merge(Map<String, Object> first, Map<String, Object> second) {
        if (second == null || second.isEmpty()) return first;
        if (first == null || first.isEmpty()) return second;
        Map<String,Object> combined = new HashMap<>(first);
        combined.putAll(second);
        return combined;
    }

    public static Map<String,Object> map(Object ... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i+=2) {
            if (values[i] == null) continue;
            map.put(values[i].toString(),values[i+1]);
        }
        return map;
    }

    public static Map<String, Object> map(List<Object> pairs) {
        Map<String, Object> res = new LinkedHashMap<>(pairs.size() / 2);
        Iterator<Object> it = pairs.iterator();
        while (it.hasNext()) {
            Object key = it.next();
            Object value = it.next();
            if (key != null) res.put(key.toString(), value);
        }
        return res;
    }

    public static Map<String, Object> mapFromLists(List<String> keys, List<Object> values) {
        if (keys == null || values == null || keys.size() != values.size())
            throw new RuntimeException("keys and values lists have to be not null and of same size");
        if (keys.isEmpty()) return Collections.<String,Object>emptyMap();
        if (keys.size()==1) return Collections.singletonMap(keys.get(0),values.get(0));
        ListIterator<Object> it = values.listIterator();
        Map<String, Object> res = new LinkedHashMap<>(keys.size());
        for (String key : keys) {
            res.put(key,it.next());
        }
        return res;
    }

    public static Map<String, Object> mapFromPairs(List<List<Object>> pairs) {
        if (pairs.isEmpty()) return Collections.<String,Object>emptyMap();
        Map<String,Object> map = new LinkedHashMap<>(pairs.size());
        for (List<Object> pair : pairs) {
            if (pair.isEmpty()) continue;
            Object key = pair.get(0);
            if (key==null) continue;
            Object value = pair.size() >= 2 ? pair.get(1) : null;
            map.put(key.toString(),value);
        }
        return map;
    }

    public static String cleanUrl(String url) {
        try {
            URL source = new URL(url);
            String file = source.getFile();
            if (source.getRef() != null) file += "#"+source.getRef();
            return new URL(source.getProtocol(),source.getHost(),source.getPort(),file).toString();
        } catch (MalformedURLException mfu) {
            return "invalid URL";
        }
    }

    public static <T> T getFuture(Future<T> f, Map<String, Long> errorMessages, AtomicInteger errors, T errorValue) {
        try {
            return f.get();
        } catch (InterruptedException | ExecutionException e) {
            errors.incrementAndGet();
            errorMessages.compute(e.getMessage(),(s, i) -> i == null ? 1 : i + 1);
            return errorValue;
        }
    }

    public static void logErrors(String message, Map<String, Long> errors, Log log) {
        if (!errors.isEmpty()) {
            log.bulk(l -> {
                l.warn(message);
                errors.forEach((k, v) -> l.warn("%d times: %s",v,k));
            });
        }
    }

    public static void checkAdmin(KernelTransaction tx, String procedureName) {
        if (!tx.securityContext().isAdmin()) throw new RuntimeException("This procedure "+ procedureName +" is only available to admin users");
    }

/*
    public static boolean isWriteableInstance(GraphDatabaseAPI db) {
        try {
            boolean isSlave = db instanceof HighlyAvailableGraphDatabase && !((HighlyAvailableGraphDatabase)db).isMaster();
            if (isSlave) return false;
            String role = db.execute("CALL dbms.cluster.role()").<String>columnAs("role").next();
            return role.equalsIgnoreCase("LEADER");
        } catch(QueryExecutionException e) {
            if (e.getStatusCode().equalsIgnoreCase("Neo.ClientError.Procedure.ProcedureNotFound")) return true;
            throw e;
        }
    }
*/
}
