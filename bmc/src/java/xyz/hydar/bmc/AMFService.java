package xyz.hydar.bmc;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.openamf.AMFBody;
import org.openamf.AMFMessage;

public class AMFService {

	public String name;
	private List<AMFType> inputTypes;
	private static Map<String, AMFService> services = new HashMap<>();
	private Function<List<?>, ?> svc = null;

	public static class NKVerifyException extends RuntimeException{
		public NKVerifyException() {
			super();
		}
		public NKVerifyException(String msg) {
			super(msg);
		}
		private static final long serialVersionUID = -6378961186468259857L;
	}
	public AMFService inputs(List<?> list) {
		inputTypes = list.stream().map(AMFType::fromPattern).toList();
		return this;
	}

	public AMFService inputs(Object... list) {
		return inputs(Arrays.asList(list));
	}

	public List<AMFType> getInputTypes(){
		return inputTypes;
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

	public AMFService register() {
		services.put(this.name, this);
		return this;
	}
	 
	public Object apply(List<?> input) throws Exception {
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