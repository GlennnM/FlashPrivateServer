package xyz.hydar.bmc;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.UnaryOperator;
import java.util.zip.CRC32;

import org.json.JSONObject;

public class FileObjectStore implements ObjectStore {
	private final Path root;
	/**
	 * allows you to specify that an update() did nothing. update() will still
	 * return your input value with any mutations
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

	public static volatile List<Thread> hooks = null;
	public static volatile List<ScheduledExecutorService> flushers = null;
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
		return INSTANCES.computeIfAbsent(root, x -> {
			try {
				return new FileObjectStore(root, maxCacheSize);
			} catch (IOException ioe) {
				throw new RuntimeException(ioe);
			}
		});
	}

	/**
	 * Activates delayed flush. Only ever call once and before ever calling
	 * compute(). In order to prevent recompiling leakage, runs then removes
	 * previous shutdown hooks. Then kills flusher services.
	 */
	public synchronized FileObjectStore bind(long flushInterval) {
		if (delayedFlush)
			return this;
		var flusher = Executors.newSingleThreadScheduledExecutor(Util.TFAC);
		var flusherHook = new Thread(this::flush);

		//@SuppressWarnings("unchecked")
		//var hooks = hooks;
		if (hooks == null)
			hooks = new ArrayList<Thread>();
		hooks.forEach(x -> x.start());
		hooks.forEach(x -> {
			try {
				x.join();
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
			}
		});
		hooks.forEach(Runtime.getRuntime()::removeShutdownHook);
		hooks.clear();
		hooks.add(flusherHook);
		Runtime.getRuntime().addShutdownHook(flusherHook);
		//request.getServletContext().setAttribute("OBJECT_HOOKS_" + root.toString(), hooks);

		if (flushers == null)
			flushers = new ArrayList<ScheduledExecutorService>();
		flushers.forEach(ScheduledExecutorService::shutdown);
		flushers.clear();
		flushers.add(flusher);
		//request.getServletContext().setAttribute("OBJECT_FLUSHERS_" + root.toString(), flushers);

		this.delayedFlush = true;
		flusher.scheduleAtFixedRate(this::flush, flushInterval, flushInterval, TimeUnit.MILLISECONDS);
		return this;
	}

	/**
	 * Perform an update, while hiding the cache. update can accept null and return
	 * UNCHANGED, but should not return or expect NOT_PRESENT.
	 * 
	 * This method itself can return NOT_PRESENT but not UNCHANGED.
	 */
	private JSONObject compute(String unmappedKey, UnaryOperator<JSONObject> update) {
		// automatically do the cache updates here
		var CHANGED = new AtomicBoolean(true);
		Path p = map(unmappedKey);
		String key = p.toString();
		// outside the update(non-reentrant), but before it so it must succeed before
		// continuing
		tryEvict();
		var result = cache.compute(p.toString(), (k, v) -> {
			if (v == null) {
				// System.err.println("Cache MISS "+key);
				cacheSize.increment();
				try {
					v = Files.exists(p) ? new JSONObject(Files.readString(p)) : NOT_PRESENT;
				} catch (IOException ioe) {
					throw new RuntimeException(ioe);
				}
			}
			var ret = update.apply(v == NOT_PRESENT ? null : v);
			CHANGED.setPlain(ret != UNCHANGED);
			return ret == null ? NOT_PRESENT : ret == UNCHANGED ? v : ret;
		});
		// update LRU order
		order.remove(key);
		order.add(key);
		if (CHANGED.getPlain())
			modif.add(key);
		if (!delayedFlush)
			flush();
		return result;
	}

//Evict if cache size is greater than MAX_CACHE_SIZE. Fails if write-through fails.
	public void tryEvict() {
		// System.err.println("Cache size " + cacheSize.sum());
		if (cacheSize.sum() > maxCacheSize) {
			String oldest = order.poll();
			if (oldest != null) {
				// System.err.println("Evicting "+oldest);
				if (modif.contains(oldest)) {
					flushOne(oldest);
				}
				cache.remove(oldest);
				cacheSize.decrement();
			}
		}
	}

//Write all modified entries to disk.
	public void flush() {
		// System.err.println("Flushing entries. "+modif);
		var failures = new ArrayList<String>();
		modif.forEach(key -> {
			try {
				writeThrough(key);
			} catch (IOException e) {
				failures.add(key);
			}
		});
		modif.clear();
		if (!failures.isEmpty()) {
			// System.err.println("WARNING: Write failures "+failures);
			modif.addAll(failures);
		}
	}

//Used on evict. Must succeed or evict will not happen.
	public void flushOne(String key) {
		// System.err.println("Flushing entry. "+key);
		try {
			writeThrough(key);
			modif.remove(key);
		} catch (IOException e) {
			throw new RuntimeException(e);// not a warning, since data could be lost
		}
	}

	private void writeThrough(String key) throws IOException {
		var val = cache.get(key);
		var path = Path.of(key);
		if (val == NOT_PRESENT) {
			Files.deleteIfExists(path);
		} else {
			Files.createDirectories(path.getParent());
			Files.writeString(path, val.toString());
		}
	}

	public List<String> dump() {
		try {
			return Files.walk(root, 2).filter(Files::isRegularFile)// .peek(System.out::println)
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
			return Files.walk(root, 2).filter(Files::isRegularFile)// .peek(System.out::println)
					.map(x -> new String(Base64.getDecoder().decode(x.getFileName().toString().trim()), UTF_8)).sorted()
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
		var res = compute(url, x -> UNCHANGED);
		return res == NOT_PRESENT ? null : res;
	}

	@Override
	public boolean has(String url) {
		return compute(url, x -> UNCHANGED) != NOT_PRESENT;
	}

	@Override
	public boolean put(String url, JSONObject payload) {
		compute(url, x -> payload);
		return true;
	}

	@Override
	public JSONObject update(String url, UnaryOperator<JSONObject> update) {
		var res = compute(url, update);
		return res == NOT_PRESENT ? null : res;
	}

	@Override
	public boolean delete(String url) {
		AtomicBoolean PRESENT = new AtomicBoolean();
		compute(url, x -> {
			PRESENT.setPlain(x != null);
			return null;
		});
		return PRESENT.getPlain();
	}
}