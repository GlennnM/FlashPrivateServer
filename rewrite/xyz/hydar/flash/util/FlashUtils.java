package xyz.hydar.flash.util;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;



public class FlashUtils {
	public static boolean isInt(String param1) {
		return isNImpl(param1, Integer.MIN_VALUE, Integer.MAX_VALUE, 10);
	}
	public static boolean isShort(String param1) {
		return isNImpl(param1, Short.MIN_VALUE, Short.MAX_VALUE, 5);
	}
	public static boolean isByte(String param1) {
		return isNImpl(param1, Byte.MIN_VALUE, Byte.MAX_VALUE, 3);
	}
	public static byte parseByteElse(String str, byte defaultValue) {
		return isByte(str)?Byte.parseByte(str):defaultValue;
	}
	public static short parseShortElse(String str, short defaultValue) {
		return isShort(str)?Short.parseShort(str):defaultValue;
	}
	public static int parseIntElse(String str, int defaultValue) {
		return isInt(str)?Integer.parseInt(str):defaultValue;
	}
	private static boolean isNImpl(String param1, int min, int max, int maxDigits){
		param1=param1.trim();
		if(param1.startsWith("+")||param1.startsWith("-"))
			param1=param1.substring(1);
		if(param1.isEmpty()||param1.length()>maxDigits)
			return false;
		boolean allDigits=param1.chars().allMatch(Character::isDigit);
		if(param1.length()==maxDigits&&allDigits) {
			long test=Long.parseLong(param1);
			if(test<min||test>max)
				return false;
		}
		return allDigits;
	}
	public static int sas4MatchCode(String param1) {
		return isInt(param1)?Integer.parseInt(param1.trim()):
			Math.abs(param1.codePoints().reduce(5381, (x, y) -> ((x << 5) + x + y)));
	}
	public static int shortSearch(short[] haystack, short needle) {
		for(int i=0;i<haystack.length;i++) {
			if(haystack[i]==needle)
				return i;	
		}
		return -1;
	}
	public static String decode(String data){
		try(var in1 = new ByteArrayInputStream(data.getBytes(ISO_8859_1));
			var b64=Base64.getDecoder().wrap(in1);
			var inf = new InflaterInputStream(b64);
			var dis= new DataInputStream(inf);){
				return dis.readUTF();
		}catch(IOException ioe){return null;}
	}

	public static String encode(String data){
		var baos = new ByteArrayOutputStream(data.length());
		try(var b64=Base64.getEncoder().wrap(baos);
			var def = new DeflaterOutputStream(b64);
			var dos=new DataOutputStream(def)){
				dos.writeUTF(data);
		}catch(IOException ioe){return null;}
		return baos.toString(ISO_8859_1);
	}
	public static long now() {
		return System.currentTimeMillis();
	}
	public static long xor64s(long x){
	    x ^= x >>> 12;
	    x ^= x << 25;
	    x ^= x >>> 27;
	    return x * 0x2545F4914F6CDD1Dl;
	}
	public static int xorRange(long seed, int max) {
		return (int)((xor64s(seed)>>>32)%max);
	}
	public static <E extends Enum<E>> E pickFromIntersect(Set<E> set1, Set<E> set2) {
		EnumSet<? extends E> intersect=EnumSet.copyOf(set1);
		intersect.retainAll(set2);
		if(intersect.size()==0)return null;
		int index=ThreadLocalRandom.current().nextInt(intersect.size());
		int i=0;
		for(E e:intersect) {
			if(i++==index)
				return e;
		}
		return null;
	}
	public static final class BasicCache<K,V> extends LinkedHashMap<K, V>{
		private static final long serialVersionUID = 6382503150127593696L;
		private final int maxSize;
		public BasicCache(int maxSize) {
			super();
			this.maxSize=maxSize;
		}
		public static <K,V> Map<K,V> synced(int maxSize) {
			return Collections.synchronizedMap(new BasicCache<>(maxSize));
		}
		@Override
		protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
			return size()>maxSize;
		}
	}
	public static String upperNoise(String start,int length) {
		var rng=ThreadLocalRandom.current();
		char[] id = Arrays.copyOf(start.toCharArray(),length);
		for(int i=start.length();i<id.length;i++){
			id[i]=(char)('E'+rng.nextInt(22));
		}
		return new String(id);
	}
	public static void sleep(int millis) {
		try {
			Thread.sleep(millis);
		}catch(InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}
	public static boolean checkPort(int p) {
		try(ServerSocket test = new ServerSocket(p)){
			
		}catch(IOException ioe) { 
			return true;
		}
		return false;
	}
}
