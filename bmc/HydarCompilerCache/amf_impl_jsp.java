import java.io.PrintWriter;import java.net.http.HttpClient.Redirect;import java.net.http.HttpResponse.BodyHandlers;import java.net.http.HttpResponse;import java.net.URI;import java.net.http.HttpRequest.BodyPublishers;import java.net.http.*;import java.security.NoSuchAlgorithmException;import java.security.MessageDigest;import java.util.stream.Stream;import java.util.stream.IntStream;import xyz.hydar.ee.HydarEE.Context;import java.util.HexFormat;import org.json.*;import java.util.List;import org.openamf.io.*;import org.openamf.*;import java.util.Comparator;import java.util.stream.IntStream;import java.util.Arrays;import java.util.function.Function;import java.util.HashMap;import java.util.Date;import java.io.Serializable;import org.json.JSONArray;import java.util.Map;import org.json.JSONObject;import java.util.stream.Collectors;import java.util.ArrayList;import java.util.Base64;import java.io.DataOutputStream;import java.io.OutputStream;import java.util.Iterator;import java.io.ByteArrayInputStream;import java.io.ByteArrayOutputStream;import java.nio.file.Files;import java.nio.file.Paths;import java.io.DataInputStream;import java.io.IOException;import java.io.InputStream;import java.io.PrintStream;import java.util.HexFormat;import java.util.List;import org.openamf.io.*;import org.openamf.*;import xyz.hydar.ee.HydarEE.HttpServletRequest;import static java.nio.charset.StandardCharsets.UTF_8;import java.util.zip.CRC32;import java.util.function.*;import java.io.*;import java.nio.file.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.util.*;import org.json.*;public class amf_impl_jsp extends xyz.hydar.ee.HydarEE.JspServlet{/**statics*/
	public static class AMFBodies extends ArrayList<AMFBody> {
		private AMFBodies(AMFMessage msg) {
			super(IntStream.range(0,msg.getBodyCount())
					.mapToObj(msg::getBodyAt)
					.sorted(Comparator.comparing(AMFBody::getTarget))
					.toList());
		}

		public static AMFBodies from(AMFMessage msg) {
			return new AMFBodies(msg);
		}

		public static AMFBodies from(InputStream is) throws IOException {
			PrintStream old = System.out;
			return new AMFBodies(new AMFDeserializer(new DataInputStream(is)).getAMFMessage());
		}

		public static AMFBodies from(String filePath) throws IOException {
			return from(Files.newInputStream(Paths.get(filePath)));
		}

		public static AMFBodies fromHex(String... hex) throws IOException {
			return from(disHex(hex));
		}

		public static AMFBodies from64(String... s) throws IOException {
			return from(dis64(s));
		}

		public static AMFBodies from(byte[] b) throws IOException {
			return from(dis(b));
		}

		private static DataInputStream disHex(String... s) throws IOException {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			for (String s1 : s)
				baos.write(HexFormat.of().parseHex(s1));
			return dis(baos.toByteArray());
		}

		private static DataInputStream dis64(String... s) throws IOException {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			for (String s1 : s)
				baos.write(Base64.getDecoder().decode(s1));
			return dis(baos.toByteArray());
		}

		private static DataInputStream dis(byte[] b) {
			return new DataInputStream(new ByteArrayInputStream(b));
		}
		
		public static String getObjectTypeDescription(byte type) {
			switch (type) {
			case AMFBody.DATA_TYPE_UNKNOWN:
				return "UNKNOWN";
			case AMFBody.DATA_TYPE_NUMBER:
				return "NUMBER";
			case AMFBody.DATA_TYPE_BOOLEAN:
				return "BOOLEAN";
			case AMFBody.DATA_TYPE_STRING:
				return "STRING";
			case AMFBody.DATA_TYPE_OBJECT:
				return "OBJECT";
			case 4:
				return "MOVIECLIP";
			case AMFBody.DATA_TYPE_NULL:
				return "NULL";
			case 6:
				return "UNDEFINED";
			case 7:
				return "REFERENCE";
			case 8:
				return "MIXED_ARRAY";
			case 9:
				return "OBJECT_END";
			case AMFBody.DATA_TYPE_ARRAY:
				return "ARRAY";
			case AMFBody.DATA_TYPE_DATE:
				return "DATE";
			case 12:
				return "LONG_STRING";
			case AMFBody.DATA_TYPE_AS_OBJECT:
				return "AS_OBJECT";
			case 14:
				return "RECORDSET";
			case AMFBody.DATA_TYPE_XML:
				return "XML";
			case AMFBody.DATA_TYPE_CUSTOM_CLASS:
				return "CUSTOM_CLASS";
			default:
				return "UNKNOWN: 0x" + Integer.toBinaryString(type);
			}
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append('[');
			forEach(x -> {
				sb.append("{AMFBody: {target=");
				sb.append(x.getTarget());
				sb.append(", response=");
				sb.append(x.getResponse());
				sb.append(", type=");
				sb.append(getObjectTypeDescription(x.getType()));
				sb.append(", value=");
				if (x.getValue() instanceof ArrayList<?>) {
					sb.append('[');
					String c = ((ArrayList<?>) (x.getValue())).stream()
							.map(y -> "" + y + ":" + y.getClass().getCanonicalName()).collect(Collectors.joining(","));
					sb.append(c);
					sb.append(']');
				} else if (x.getValue() instanceof String x2)
					sb.append(x2 + ":" + x2.length());
				else if (x.getValue() != null)
					sb.append(x.getValue() + ":" + x.getValue().getClass().getCanonicalName());
				else
					sb.append("null");
				sb.append("}\n");
			});
			if (sb.length() > 1)
				sb.deleteCharAt(sb.length() - 1);
			sb.append("]");
			return sb.toString();
		}
		/**public static List<?> generics(Object x3){
			return Arrays.asList(((ParameterizedType)x3.getClass().getGenericSuperclass())
				      .getActualTypeArguments());
		}*/
	}

	public static class JsonAMFSerializer extends AMFSerializer {
		JsonAMFSerializer(DataOutputStream dis) {
			super(dis);
		}

		public void writeMapAsObj(Map<?, ?> j) throws IOException {
			this.outputStream.writeByte(3);
			for (var entry : j.entrySet()) {
				/**TODO: allow byte[] of ach data*/
				this.outputStream.writeUTF((String) entry.getKey());
				this.writeData(entry.getValue());
				//System.out.println(entry.getValue().getClass().getCanonicalName());
			}

			this.outputStream.writeShort(0);
			this.outputStream.writeByte(9);
		}

		public void writeJSONObject(JSONObject j) throws IOException {
			writeMapAsObj(j.toMap());
		}

		public void writeJSONArray(JSONArray j) throws IOException {
			List<Object> list = j.toList();
			this.outputStream.writeByte(10);
			this.outputStream.writeInt(list.size());
			for (Object object : list) {
				System.out.println(object.getClass().getCanonicalName());
				writeData(object);
			}
		}

		public void writeBoolean(Boolean b) throws IOException {
			this.outputStream.writeByte(1);
			this.outputStream.writeBoolean(b);
		}
		@Override
		public void writeData(Object obj) throws IOException {
			if (obj instanceof Map<?, ?> j)
				writeMapAsObj(j);
			else if (obj instanceof JSONArray j)
				writeJSONArray(j);
			else if (obj instanceof JSONObject k)
				writeJSONObject(k);
			else if (obj instanceof Boolean b)
				writeBoolean(b);
			else
				super.writeData(obj);
		}
	}

	private static class ByteAMF extends JsonAMFSerializer {
		private ByteArrayOutputStream baos;

		public static ByteAMF serializer() {
			var baos = new ByteArrayOutputStream();
			return new ByteAMF(baos).setBAOS(baos);
		}

		private ByteAMF setBAOS(ByteArrayOutputStream baos) {
			this.baos = baos;
			return this;
		}

		public ByteAMF(ByteArrayOutputStream baos) {
			super(new DataOutputStream(baos));
		}

		public byte[] get() {
			return baos.toByteArray();
		}

	}

	enum AMFType {
		UNKNOWN(AMFBody.DATA_TYPE_UNKNOWN), NUMBER(AMFBody.DATA_TYPE_NUMBER), BOOLEAN(AMFBody.DATA_TYPE_BOOLEAN),
		STRING(AMFBody.DATA_TYPE_STRING), OBJECT(AMFBody.DATA_TYPE_OBJECT), NULL(AMFBody.DATA_TYPE_NULL),
		ARRAY(AMFBody.DATA_TYPE_ARRAY), DATE(AMFBody.DATA_TYPE_DATE);

		byte code;

		public static AMFType fromPattern(Object o) {
			if (o instanceof String s)
				for (AMFType t : values()) {
					if (t.toString().equals(s))
						return t;
				}
			return infer(o);
		}

		public static AMFType infer(Object o) {
			if (o == null || o == JSONObject.NULL)
				return NULL;
			else if (o instanceof AMFType t)
				return t;
			else if (o instanceof Number)
				return NUMBER;
			else if (o instanceof Boolean)
				return BOOLEAN;
			else if (o instanceof String)
				return STRING;
			else if ((o instanceof List<?>) || (o instanceof JSONArray))
				return ARRAY;
			else if ((o instanceof Date))
				return DATE;
			else if ((o instanceof Map) || (o instanceof JSONObject) || (Class<?>) o.getClass() == Object.class
					|| (o instanceof Serializable))
				return OBJECT;
			return UNKNOWN;
		}

		public boolean allows(AMFType t) {
			return (this == t) || (t == null) || (t == NULL);
		}

		public static byte inferCode(Object o) {
			return infer(o).code;
		}

		AMFType(byte b) {
			code = b;
		}
	}

	private static class AMFService {

		public String name;
		private List<AMFType> inputTypes;
		private static Map<String, AMFService> services = new HashMap<>();
		private Function<List<?>, ?> svc = null;

		public AMFService inputs(List<?> list) {
			inputTypes = list.stream().map(AMFType::fromPattern).toList();
			return this;
		}

		public AMFService inputs(Object... list) {
			return inputs(Arrays.asList(list));
		}

		public AMFService(String name, Function<List<?>, ?> svc) {
			this(name, svc, null);
		}

		public AMFService(String name) {
			this(name, null, null);
		}

		private AMFService(String name, Function<List<?>, ?> svc, List<AMFType> inputTypes) {
			this.name = name;
			this.svc = svc;
			this.inputTypes = inputTypes;
		}

		private AMFService register() {
			services.put(this.name, this);
			return this;
		}

		protected Object apply(List<?> input) throws Exception {
			if (svc == null)
				return null;
			return svc.apply(input);
		}

		public static void accept(InputStream input, OutputStream output) throws IOException {
			AMFMessage out = new AMFMessage();
			var serializer = new JsonAMFSerializer(new DataOutputStream(output));
			var h = AMFBodies.from(input);
			System.out.println(h);
			for (var body : h) {
				out.addBody(accept(body));
			}
			serializer.serialize(out);
		}

		public static AMFBody accept(AMFBody input) {
			String name = input.getTarget();
			AMFService svc = getService(name);
			String response = input.getResponse();
			List<?> list;
			if (!(input.getValue() instanceof List<?>)) {
				list = List.of(input.getValue());
			} else
				list = (List<?>) input.getValue();
			Object ret = null;
			if (svc != null && svc.validateList(list)) {
				try {
					return new AMFBody(response + "/onResult", "null", svc.apply(list), AMFType.inferCode(ret));
				} catch (NKVerifyException e) {
					return new AMFBody(response + "/onResult", "null", "Invalid token", AMFType.inferCode(ret));
				}catch (Exception e) {
					e.printStackTrace();
					return new AMFBody(response + "/onStatus", "null", "error occurred: "+e.getClass().toString(), AMFType.inferCode(ret));
				}
			}
			//System.out.println(":(");
			//System.out.println(input.getValue());
			return new AMFBody(response + "/onStatus", "null", "Bad arguments", AMFType.inferCode(ret));
		}
		
		public static AMFService getService(String target){
			return services.get(target);
		}
		
		protected boolean validate(int index, Object item) {
			return true;
		}

		protected boolean validateType(int index, Object item) {
			//System.out.println(""+inputTypes.get(index)+":"+AMFType.infer(item));
			return inputTypes.get(index).allows(AMFType.infer(item));
		}

		private boolean validateList(List<?> items) {
			int i = 0;
			if (inputTypes == null)
				return true;
			if (inputTypes.size() != items.size()) {
				//System.out.println(""+inputTypes+""+items.stream().map(AMFType::infer).collect(Collectors.toList()));
				return false;
			}
			for (Object o : items) {
				if (!validate(i, o) || !validateType(i, o)) {
					return false;
				}
				i++;
			}
			return true;
		}
	}
	public static class NKVerifyException extends RuntimeException{}
	
	/**stuff like put(url, ..., ...)*/
	public static interface ObjectStore {
		public default JSONObject get(String... url) {
			return get(String.join("/", url));
		}
	
		public default JSONObject get(Iterable<String> url) {
			return get(String.join("/", url));
		}
		
		public default boolean has(String... url) {
			return has(String.join("/", url));
		}
	
		public default boolean has(Iterable<String> url) {
			return has(String.join("/", url));
		}
		public default JSONObject get(String url, JSONObject fallback) {
			var ret = get(url);
			return ret == null ? fallback : ret;
		}
	
		public default JSONObject get(Iterable<String> url, JSONObject fallback) {
			var ret = get(url);
			return ret == null ? fallback : ret;
		}
	
		public default boolean put(Iterable<String> url, JSONObject payload) {
			return put(String.join("/", url), payload);
		}
	
		public default JSONObject update(Iterable<String> url, UnaryOperator<JSONObject> update) {
			return update(String.join("/", url), update);
		}
	
		public default boolean delete(String... url) {
			return delete(String.join("/", url));
		}
	
		public default boolean delete(Iterable<String> url) {
			return delete(String.join("/", url));
		}

		public List<String> list();
		
		public JSONObject get(String url);
	
		public boolean has(String url);
	
		public boolean delete(String url);
	
		public boolean put(String url, JSONObject payload);
	
		public default JSONObject update(String url, UnaryOperator<JSONObject> update) {
			var input = get(url);
			put(url, update.apply(get(url)));
			return input;
		}
	}

/**
uses b64's of the urls so it is always in the same folder
*/
public static class FileObjectStore implements ObjectStore {
	private final Path root;
	/**
		allows you to specify that an update() did nothing. 
		update() will still return your input value with any mutations
	*/
	public static final JSONObject UNCHANGED = new JSONObject();
	//stored in cache if something is deleted or not found(in constrast, null = 'not cached')
	public static final JSONObject NOT_PRESENT = new JSONObject();
	
	public static final ConcurrentMap<Path, FileObjectStore> INSTANCES = new ConcurrentHashMap<>();

	public final int maxCacheSize;
	private final ConcurrentMap<String, JSONObject> cache;
	private final Queue<String> order = new ConcurrentLinkedQueue<>();
	private final NavigableSet<String> modif = new ConcurrentSkipListSet<>();
	private final LongAdder cacheSize = new LongAdder();
	private volatile boolean delayedFlush = false;
	
	private FileObjectStore(Path root, int maxCacheSize) throws IOException {
		this.maxCacheSize = maxCacheSize;
		this.cache = new ConcurrentHashMap<>(maxCacheSize);
		if (!Files.exists(root))
			Files.createDirectories(root);
		if (!Files.isDirectory(root))
			throw new IllegalArgumentException("Not a dir: " + root);
		this.root = root;
	}
	public static FileObjectStore of(Path root) throws IOException {
		return of(root, 100000);
	}
	public static FileObjectStore of(Path root, int maxCacheSize) throws IOException {
		return INSTANCES.computeIfAbsent(root, x->{
			try{
				return new FileObjectStore(root, maxCacheSize);
			}catch(IOException ioe){
				throw new RuntimeException(ioe);
			}
		});
	}
	/**
	* Activates delayed flush. Only ever call once and before ever calling compute().
	* In order to prevent recompiling leakage, runs then removes previous shutdown hooks.
	* Then kills flusher services.
	*/
	public FileObjectStore bind(HttpServletRequest request, long flushInterval){
		if(delayedFlush)
			return this;
		var flusher = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
		var flusherHook = new Thread(this::flush);
		
		@SuppressWarnings("unchecked")
		var hooks =  (List<Thread>)(request.getServletContext().getAttribute("OBJECT_HOOKS_"+root.toString()));
		if(hooks == null)
			hooks = new ArrayList<Thread>();
		hooks.forEach(x->x.start());
		hooks.forEach(x->{
			try{
				x.join();
			}catch(InterruptedException ie){
				Thread.currentThread().interrupt();
			}
		});
		hooks.forEach(Runtime.getRuntime()::removeShutdownHook);
		hooks.clear();
		hooks.add(flusherHook);
		Runtime.getRuntime().addShutdownHook(flusherHook);
		request.getServletContext().setAttribute("OBJECT_HOOKS_"+root.toString(), hooks);
		

		@SuppressWarnings("unchecked")
		var flushers =  (List<ScheduledExecutorService>)(request.getServletContext().getAttribute("OBJECT_FLUSHERS_"+root.toString()));
		if(flushers == null)
			flushers = new ArrayList<ScheduledExecutorService>();
		flushers.forEach(ScheduledExecutorService::shutdown);
		flushers.clear();
		flushers.add(flusher);
		request.getServletContext().setAttribute("OBJECT_FLUSHERS_"+root.toString(), flushers);
		
		this.delayedFlush = true;
		flusher.scheduleAtFixedRate(this::flush, flushInterval, flushInterval, TimeUnit.MILLISECONDS);
		return this;
	}
	/**
	*	Perform an update, while hiding the cache.
	*	update can accept null and return UNCHANGED,
	*	but should not return or expect NOT_PRESENT.
	*		
	*	This method itself can return NOT_PRESENT but not UNCHANGED.
	*/
	private JSONObject compute(String unmappedKey, UnaryOperator<JSONObject> update){
		//automatically do the cache updates here
		var CHANGED = new AtomicBoolean(true);
		Path p = map(unmappedKey);
		String key = p.toString();
		//outside the update(non-reentrant), but before it so it must succeed before continuing
		tryEvict();
		var result = cache.compute(p.toString(),(k,v)->{
			if(v == null){
				//System.err.println("Cache MISS "+key);
				cacheSize.increment();
				try{
					v = Files.exists(p) ? new JSONObject(Files.readString(p)) : NOT_PRESENT;
				}catch(IOException ioe){
					throw new RuntimeException(ioe);
				}
			}
			var ret =  update.apply(v == NOT_PRESENT ? null : v);
			CHANGED.setPlain(ret != UNCHANGED);
			return ret == null ? NOT_PRESENT : 
				ret == UNCHANGED ? v : ret;
		});
		//update LRU order
		order.remove(key);
		order.add(key);
		if(CHANGED.getPlain())
			modif.add(key);
		if(!delayedFlush)
			flush();
		return result;
	}
	//Evict if cache size is greater than MAX_CACHE_SIZE. Fails if write-through fails.
	public void tryEvict(){
		//System.err.println("Cache size " + cacheSize.sum());
		if(cacheSize.sum() > maxCacheSize){
			String oldest = order.poll();
			if(oldest!=null){
				//System.err.println("Evicting "+oldest);
				if(modif.contains(oldest)){
					flushOne(oldest);
				}
				cache.remove(oldest);
				cacheSize.decrement();
			}
		}
	}
	//Write all modified entries to disk.
	public void flush(){
		//System.err.println("Flushing entries. "+modif);
		var failures = new ArrayList<String>();
		modif.forEach(key->{
			try{
				writeThrough(key);
			}catch(IOException e){
				failures.add(key);
			}
		});
		modif.clear();
		if(!failures.isEmpty()){
			//System.err.println("WARNING: Write failures "+failures);
			modif.addAll(failures);
		}
	}
	//Used on evict. Must succeed or evict will not happen.
	public void flushOne(String key){
		//System.err.println("Flushing entry. "+key);
		try{
			writeThrough(key);
			modif.remove(key);
		}catch(IOException e){
			throw new RuntimeException(e);//not a warning, since data could be lost
		}
	}
	private void writeThrough(String key) throws IOException{
		var val = cache.get(key);
		var path = Path.of(key);
		if(val == NOT_PRESENT){
			Files.deleteIfExists(path);
		}else{
			Files.createDirectories(path.getParent());
			Files.writeString(path, val.toString());
		}
	}
	public List<String> dump() {
		try {
			return Files.walk(root, 2).filter(Files::isRegularFile)//.peek(System.out::println)
					.map(x -> {
						try {
							return x.getParent().getFileName().toString() + "->"
									+ new String(Base64.getDecoder().decode(x.getFileName().toString().trim()), UTF_8)
									+ " -> " + Files.readString(x);
						} catch (IOException e) {
							return "";
						}
					}).toList();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	@Override
	public List<String> list() {
		try {
			return Files.walk(root, 2).filter(Files::isRegularFile)//.peek(System.out::println)
					.map(x -> new String(Base64.getDecoder().decode(x.getFileName().toString().trim()), UTF_8))
					.sorted()
					.toList();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	public Path map(String url) {
		String newURL = Base64.getEncoder().encodeToString(url.getBytes(UTF_8));
		CRC32 crc = new CRC32();
		crc.update(url.getBytes(UTF_8));
		int bucket = (int) (crc.getValue()) & 0x7ff;
		return root.resolve(new StringBuilder().append(bucket).append(File.separatorChar).append(newURL).toString());
	}

	@Override
	public JSONObject get(String url) {
		var res =  compute(url, x -> UNCHANGED);
		return res == NOT_PRESENT ? null : res;
	}

	@Override
	public boolean has(String url) {
		return compute(url, x -> UNCHANGED) != NOT_PRESENT;
	}

	@Override
	public boolean put(String url, JSONObject payload) {
		compute(url, x->payload);
		return true;
	}
	
	@Override
	public JSONObject update(String url, UnaryOperator<JSONObject> update) {
		var res =  compute(url, update);
		return res == NOT_PRESENT ? null : res;
	}

	@Override
	public boolean delete(String url) {
		AtomicBoolean PRESENT = new AtomicBoolean();
		var res =  compute(url, x ->{
			PRESENT.setPlain(x != null);
			return null;
		});
		return PRESENT.getPlain();
	}
}
//public static class DBObjectStore ?!?!!
//public static class S3ObjectStore ?!???!?!?!?!?!


static{
	//VERY DUMB THING TO DO AN UPDATE THAT SHOULD HAPPEN ANYWAYS BUT isnt implemented FIXME:remove
	
	var hydar = xyz.hydar.ee.Hydar.hydars.get(0);
	if(hydar.ee.ctx.getAttribute("done2")==null){
		new Thread(()->{
			try{
				Thread.sleep(100);
				hydar.ee.ctx.setAttribute("done2", 1);
				if(Files.exists(Path.of("../src/webapp/AMF.jsp")))
					hydar.ee.compile(Path.of("../src/webapp/AMF.jsp"));
				hydar.ee.ctx.setAttribute("done2", null);
			}catch(InterruptedException e){
				Thread.currentThread().interrupt();
			}
		}).start();
	}
}

static class AMFImpl{
	private final ObjectStore store;
	static final JSONObject nk_store = new JSONObject();
	static final JSONObject nk_ach = new JSONObject();
	static final Set<String> games = Set.of("Battle Blocks Defense","Battle Panic","Battles","BSM2","BTD4","BTD5","Fortress Destroyer","MonkeyCity","SAS TD","SAS3","SAS4","Tower Keepers");
	public AMFImpl(ObjectStore store){
		this.store = store;
	}
	
	public JSONArray getStore(String game, Context ctx){
		if(nk_store.isEmpty()){
			synchronized(nk_store){
				for(String g: games){
					try{
						Path f = Path.of(ctx.getRealPath("/amf_data/store/" + g +".json"));
						nk_store.put(g, new JSONArray(Files.readString(f)));
					}catch(IOException ioe){
						ioe.printStackTrace();
					}
				}
			}
		}
		return nk_store.getJSONArray(game);
	}
	public JSONArray getAchievements(String game, Context ctx){
		if(nk_ach.isEmpty()){
			synchronized(nk_ach){
				for(String g: games){
					try{
						Path f = Path.of(ctx.getRealPath("/amf_data/ach/" + g +".json"));
						nk_ach.put(g, new JSONArray(Files.readString(f)));
					}catch(IOException ioe){
						ioe.printStackTrace();
					}
				}
			}
		}
		return nk_ach.getJSONArray(game);
	}
	public JSONArray getMyAchievements(String game, String userID, Context ctx){
		JSONArray j = new JSONArray(getAchievements(game, ctx).toString());
		JSONObject myAch = store.get("amf", userID, game, "ach");
		
		for(var x: Util.jIter(j)){
			int perc = myAch==null ? 0 : myAch.optInt(""+x.getInt("id"));
			x.put("perc",(double) perc)
				.put("credited",perc>100)
				.put("userid",Double.parseDouble(userID));
		}
		return j;
	}
	public JSONArray setAchievement(String userID, String token, String game, double ach_id, double perc, Context ctx){
		if(!games.contains(game))
			return null;
		verifyNK(userID, token);
		JSONArray j = getAchievements(game, ctx);
		int achID = (int)ach_id;
		var ach = Util.jStream(j).filter(x->x.getInt("id") == achID).findFirst().orElse(null);
		if(ach == null)
			return null;
		JSONArray ret = new JSONArray().put(achID);
		LongAdder ap = new LongAdder();
		store.update(List.of("amf", userID, game, "ach"),x->{
			if(x==null){
				x = new JSONObject();
			}
			int oldPerc = (int) x.optDouble(""+achID,0d);
			int newPerc = Math.max(oldPerc, (int) perc);
			ret.put((double)newPerc);
			if(newPerc > 0)
				x.put(""+achID, newPerc);
			ret.put((oldPerc < 100 && newPerc >= 100) ? "u" : "n");
			if(oldPerc < 100 && newPerc >= 100){
				ap.add(ach.getInt("points"));
			}
			return x;
		});
		if(ap.sum()>0)
			addAP(userID, (int)ap.sum());
		return ret;
	}

	
	public JSONObject getKoins(String userID, String token){
		verifyNK(userID, token);//so invalid token warning can happen early
		JSONObject profile = updateProfile(userID, x->x);
		return new JSONObject(2)
				.put("koins",(double)profile.getInt("nkoins"))
				.put("points",(double)profile.getInt("ap"));
	}
	private void addAP(String userID, int ap){
		updateProfile(userID, x->x.put("ap",x.getInt("ap")+ap));
	}
	private JSONObject updateProfile(String userID, UnaryOperator<JSONObject> update){
		return store.update(List.of("amf", userID, "info"),x->{
			if(x==null){
				x = Util.blankProfile(userID);
			}
			return update.apply(x);
		});
	}
	public void verifyNK(String userID, String token){
		
		String hash = Util.hash(token);
		boolean[] success = {false};
		updateProfile(userID, x->{
			if(x.get("nkToken") == JSONObject.NULL || !hash.equals(x.getString("nkToken"))){
				if(isNKToken(userID, token)){ 
					x.put("nkToken", Util.hash(token));
					success[0] = true;
				}
			}else
				success[0] = true;
			return x;
		});
		if(!success[0])
			throw new NKVerifyException();
	}
	public boolean DO_NK_AUTH=false;
	public boolean isNKToken(String userID, String token){
		if(!DO_NK_AUTH)
			return token.length() > 30;
		try{
			HttpClient client = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).build();
			AMFMessage nkAuth = new AMFMessage();
			var serializer = ByteAMF.serializer();
			nkAuth.addBody(new AMFBody("user.get_koins", "/2", List.of(userID, token), AMFBody.DATA_TYPE_ARRAY));
			serializer.serialize(nkAuth);
			byte[] amfPayload = serializer.get();
			HttpRequest req = HttpRequest.newBuilder()
					.header("Content-Type", "x-amf")
					.POST(BodyPublishers.ofByteArray(amfPayload))
					.uri(URI.create("https://mynk.ninjakiwi.com/gateway"))
					.build();
			HttpResponse<byte[]> amfResponse = client.send(req, BodyHandlers.ofByteArray());
			
			
			if(amfResponse.statusCode() != 200)
				return false;
			AMFBodies bodies = AMFBodies.from(amfResponse.body());
			AMFBody b = bodies.iterator().next(); 
			if(b.getTarget().contains("onStatus") )
				return false;
			b = bodies.iterator().next(); 
			if(b.getTarget().contains("onStatus") 
			|| !(b.getValue() instanceof Map<?,?> koin) 
			|| !koin.containsKey("koins"))
				return false;
		}catch(Exception e){
			e.printStackTrace();
			return false;
		}
		return true;
	}

	
	private JSONObject updateSave(String userID, String game, UnaryOperator<JSONObject> update){
		if(!games.contains(game))
			return new JSONObject().put("save",Util.blankSave());
		return store.update(List.of("amf", userID, game, "save"),x->{
			if(x==null){
				x = new JSONObject().put("save", Util.blankSave())
						.put("inventory", JSONObject.NULL)
						.put("lastSaved", System.currentTimeMillis());
			}
			return update.apply(x);
		});
	}
	public JSONObject getData(String userID, String game){
		return updateSave(userID, game, x->x).getJSONObject("save");
	}
	public Double saveData(String userID, String token, String game, Map<?,?> data){
		int transid = (int) (double) data.get("transid");//xd
		verifyNK(userID, token);
		return updateSave(userID, game, x->{
			if(transid > (int)x.getJSONObject("save").optDouble("transid", -1d)){
				x.put("save", new JSONObject(data))
					.put("lastSaved", System.currentTimeMillis());
			}
			return x;
		}).getJSONObject("save").getDouble("transid");
	}

}
 
 public static class Util{
	 public static String hash(String input){
		 try{
			 MessageDigest md = MessageDigest.getInstance("SHA3-256");
			 byte[] encodedhash = md.digest(input.getBytes(UTF_8));
			 return Base64.getEncoder().encodeToString(encodedhash);
		 }catch(NoSuchAlgorithmException nsae){
			 throw new RuntimeException(nsae); 
		 }
	 }
	 public static Iterable<Integer> jIterI(JSONArray array) {
		return (() -> Spliterators.iterator(jStreamI(array).spliterator()));
	}

	public static Iterable<String> jIterS(JSONArray array) {
		return (() -> Spliterators.iterator(jStreamS(array).spliterator()));
	}

	public static Iterable<JSONObject> jIter(JSONArray array) {
		return (() -> Spliterators.iterator(jStream(array).spliterator()));
	}

	public static Stream<JSONObject> jStream(JSONArray array) {
		return IntStream.range(0, array.length()).mapToObj(array::getJSONObject);
	}

	public static IntStream jStreamI(JSONArray array) {
		return IntStream.range(0, array.length()).map(array::getInt);
	}

	public static Stream<String> jStreamS(JSONArray array) {
		return IntStream.range(0, array.length()).mapToObj(array::getString);
	}
	 public static JSONObject blankProfile(String userID){
		 return new JSONObject()
			.put("hydarUsername","hydar")
			.put("nkUsername","hydar")
			.put("userID",JSONObject.NULL)
			.put("hydarUserID",JSONObject.NULL)
			.put("nkToken",JSONObject.NULL)
			.put("hydarToken",JSONObject.NULL)
			.put("avatar","nk-monkey.png")
			.put("clan",11)
			.put("timeCreated", System.currentTimeMillis())
			.put("ap",0)
			.put("nkoins",0)
			.put("hcoins",0);
	 }
	 public static JSONObject blankSave(){
		 return new JSONObject()
			.put("gcash",JSONObject.NULL)
			.put("data",new JSONObject())
			.put("transid",-1)
			.put("active",1.0d)
			.put("glevel",JSONObject.NULL)
			.put("gxp",JSONObject.NULL)
			.put("gnum",JSONObject.NULL);
	 }
 }
 public void _jspService(xyz.hydar.ee.HydarEE.HttpServletRequest request, xyz.hydar.ee.HydarEE.HttpServletResponse response) {xyz.hydar.ee.HydarEE.HttpSession session = request.getSession();PrintWriter out = response.getWriter();try{
response.setContentType("text/html; charset=UTF-8");
out.write("""







































""");
out.write("""

""");
out.write("""

""");
out.write("""












""");
out.write("""

""");
out.write("""

""");
out.write("""

""");
out.write("""

""");

out.write("""

""");
out.write("""

""");
out.write("""

	""");
out.write("""

	""");
out.write("""

	""");
out.write("""

	""");
out.write("""

	""");
out.write("""

	""");
out.write("""

 """);
out.write("");}catch(Exception jsp_e){
if(!response.isCommitted())response.sendError(500);jsp_e.printStackTrace();}finally{if(out!=null)out.close();}

}
}