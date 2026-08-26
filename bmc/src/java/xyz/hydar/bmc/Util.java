package xyz.hydar.bmc;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Spliterators;
import java.util.concurrent.ThreadFactory;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.json.JSONArray;
import org.json.JSONObject;

public class Util{
	public static final ThreadFactory TFAC;
	//'conditional compile' for 19+
	static {
		ThreadFactory tmp;
		try {
			Method meth=Thread.class.getMethod("ofVirtual");
			Object threadBuilder=meth.invoke(null);
			Class<?> builderClass=meth.getReturnType();
			builderClass.getMethod("name",String.class,long.class)
				.invoke(threadBuilder,"client-vthread-",0);
			tmp=(ThreadFactory)builderClass
					.getMethod("factory")
					.invoke(threadBuilder);
			System.out.println("Using vthread factory");
		}catch(IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			System.out.println("Using normal thread factory");
			tmp=Thread::new;
		}
		TFAC=tmp;
	}
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
			.put("hcoins",0)
			.put("currencies", new JSONObject());
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