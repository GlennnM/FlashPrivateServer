package xyz.hydar.bmc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

public class AMFServiceWithContext extends AMFService {

	private final Map<String,Queue<Object>> pool = new ConcurrentHashMap<>();
	//request -> pooled obj's taken by that request
	private final Map<String, Map<Object,List<Object>>> toReturn = new ConcurrentHashMap<>();
	private static final int MAX_POOL_PER_ID = 5;
	public AMFServiceWithContext(String name) {
		super(name);
	}
	public Object getFromPool(String id, Object context, Supplier<Object> supplier) {
		var queue = pool.computeIfAbsent(id, x->new ConcurrentLinkedQueue<>());
		var ret =  queue.poll();
		if(ret==null)ret= supplier.get();
		var rem = toReturn.computeIfAbsent(id, x->Collections.synchronizedMap(new WeakHashMap<>()))
			.computeIfAbsent(context, x->new ArrayList<>());
		rem.add(ret);
		return ret;
	}
	public void finishUsingPool(String id, Object context) {
		var queue = pool.computeIfAbsent(id, x->new ConcurrentLinkedQueue<>());
		toReturn.computeIfAbsent(id, x-> Collections.synchronizedMap(new WeakHashMap<>()))
			.computeIfPresent(context, (k,v)->{
				if(v==null || queue.size() > MAX_POOL_PER_ID)return null;
				v.forEach(x->queue.offer(x));
				return null;
			});
	}
	@Override
	public final Object apply(List<?> input) throws Exception {
		return apply(input, AMFService.class);
	}
	public Object apply(List<?> input, Object context) throws Exception {
		if (svc == null)
			return null;
		return svc.apply(input);
	}
}
