package xyz.hydar.flash.util;

import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import xyz.hydar.net.ClientContext;
import xyz.hydar.net.ClientOptions;
/**Stores data from flash.properties, extracted to constants*/
public class FlashConfig{
	public final Map<String,String> cfg;
	public final String HOST;
	public final ClientOptions BATTLES;
	public final boolean BATTLES_ENABLED;
	public final boolean BATTLES_NIO;
	public final int BATTLES_PORT;
	public final boolean BTD5_ENABLED;
	public final boolean BTD5_NIO;
	public final int BTD5_PORT;
	public final boolean SAS4_ENABLED;
	public final boolean SAS4_NIO;
	public final int SAS4_PORT;
	public final boolean SAS3_ENABLED;
	public final boolean SAS3_NIO;
	public final int SAS3_PORT;
	public final boolean CS_ENABLED;
	public final boolean CS_NIO;
	public final int CS_PORT;
	public final ClientOptions BATTLES_GAME;
	public final ClientOptions BTD5;
	public final ClientOptions BTD5_GAME;
	public final ClientOptions SAS4;
	public final ClientOptions SAS4_GAME;
	public final ClientOptions SAS3;
	public final int SAS3_MOBCAP;
	public final ClientOptions CS;
	public final Ports battlesPorts;
	public final Ports sas4Ports;
	public final Ports btd5Ports;
	public final int MAX_THREADS;
	public final int THREADS_PER_IP;
	public final int scaleEarly;
	public final LongAdder threadCount=new LongAdder();
	static final Properties DEFAULTS=new Properties();
	static {
		try {
			DEFAULTS.load(new StringReader("""
			MAX_THREADS=1024
			THREADS_PER_IP=8
			HOST=localhost
			Battles.enabled=true
			Battles.port=4480
			Battles.nio=true
			Battles.client=mspt=100;timeout=1000;in=();out=(locked)
			Battles.gameClient=mspt=50;timeout=90000;in=();out=(1024,1024,locked)
			Battles.gamePorts=8129;32000;5
			Battles.mode=ALL
			BTD5.enabled=true
			BTD5.port=5577
			BTD5.nio=true
			BTD5.client=mspt=100;timeout=1000;in=();out=(locked)
			BTD5.gameClient=mspt=50;timeout=1000;in=();out=(1024,2048,locked)
			BTD5.gamePorts=8127;32000;5
			BTD5.mode=ALL
			SAS4.enabled=true
			SAS4.port=8124
			SAS4.nio=true
			SAS4.client=mspt=100;timeout=10000;in=();out=()
			SAS4.gameClient=mspt=50;timeout=180000;in=(1024,32768,direct);out=(locked,direct)
			SAS4.scaleEarly=8192
			SAS4.gamePorts=8128;32000;5
			SAS4.mode=ALL
			SAS3.enabled=true
			SAS3.port=8044
			SAS3.mobcap=10000
			SAS3.nio=true
			SAS3.client=mspt=50;timeout=60000;in=(1024,16384);out=(1024,1024,locked)
			CS.enabled=true
			CS.port=7988
			CS.nio=true
			CS.client=mspt=50;timeout=1000;in=();out=(locked)
			"""));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public final Map<InetAddress,LongAdder> threadsPerIp=new ConcurrentHashMap<>();
	/**Check maxThreads and threadsPerIp to ensure ctx is allowed to start.*/
	public void acquire(ClientContext ctx) {
		var ip=ctx.getInetAddress();
		if(ip==null) {
			ctx.close();
			return;
		}
		var all=threadCount;
		var local=threadsPerIp.computeIfAbsent(ip, (x)->new LongAdder());
		if(all.sum()>MAX_THREADS||local.sum()>THREADS_PER_IP) {
			ctx.alive=false;
			return;
		}
		all.increment();
		local.increment();
	}
	/**Decrement maxThreads and threadsPerIp for ctx after it closes.*/
	public void release(ClientContext ctx) {
		threadCount.decrement();
		threadsPerIp.computeIfPresent(ctx.getInetAddress(), (x,y)->{
			if(y.sum()<=1)return null;
			y.decrement();
			return y;
		});
	}
	/**Stores a rule for allocating ports in for loop format, i.e. 5002;5;32000. "0" will use any port.*/
	public static record Ports(int min, int max, int step) {
		static Ports DEFAULT=new Ports(0,0,0);
		public static Ports from(String str) {
			String[] h=str.split(";");
			if(h.length==3)
				return new Ports(i(h[0].trim()),i(h[1].trim()),i(h[2].trim()));
			return DEFAULT;
		}
		public Ports() {
			this(0,0,0);
		}
	}
	public FlashConfig(String filename) throws IOException {
		var properties = new Properties(DEFAULTS);
		try {
			properties.load(Files.newBufferedReader(Path.of(filename)));
		}catch(IOException ioe) {
			System.out.println("File "+filename+" not found. Using defaults.");
		}
		cfg=properties.stringPropertyNames().stream()
				.collect(Collectors.toUnmodifiableMap(x->x,properties::getProperty));

		MAX_THREADS=i(get("MAX_THREADS"));
		THREADS_PER_IP=i(get("THREADS_PER_IP"));
		HOST=get("HOST");
		BATTLES=ClientOptions.from(get("Battles.client"),Scheduler.ses);
		BATTLES_ENABLED=b(get("Battles.enabled"));
		BATTLES_PORT=i(get("Battles.port"));
		BATTLES_NIO=b(get("Battles.nio"));
		CS_ENABLED=b(get("CS.enabled"));
		CS_PORT=i(get("CS.port"));
		CS_NIO=b(get("CS.nio"));
		BATTLES_GAME=ClientOptions.from(get("Battles.gameClient"));
		BTD5_ENABLED=b(get("BTD5.enabled"));
		BTD5_PORT=i(get("BTD5.port"));
		BTD5_NIO=b(get("BTD5.nio"));
		BTD5=ClientOptions.from(get("BTD5.client"),Scheduler.ses);
		BTD5_GAME=ClientOptions.from(get("BTD5.gameClient"),Scheduler.ses);
		SAS4=ClientOptions.from(get("SAS4.client"));
		SAS4_GAME=ClientOptions.from(get("SAS4.gameClient"));
		SAS4_ENABLED=b(get("SAS4.enabled"));
		SAS4_PORT=i(get("SAS4.port"));
		SAS4_NIO=b(get("SAS4.nio"));
		SAS3=ClientOptions.from(get("SAS3.client"));
		SAS3_MOBCAP=i(get("SAS3.mobcap"));
		SAS3_ENABLED=b(get("SAS3.enabled"));
		SAS3_PORT=i(get("SAS3.port"));
		SAS3_NIO=b(get("SAS3.nio"));
		CS=ClientOptions.from(get("CS.client"),Scheduler.ses);
		battlesPorts=Ports.from(get("Battles.gamePorts"));
		sas4Ports=Ports.from(get("SAS4.gamePorts"));
		btd5Ports=Ports.from(get("BTD5.gamePorts"));
		scaleEarly=i(get("SAS4.scaleEarly"));
		
		//System.out.println(cfg);
	}
	//utility functions for parsing data
	public String get(String key) {
		return cfg.get(key);
	}
	private static int prefix(String s, int tail) {
		return Integer.parseInt(s.substring(0,s.length()-tail));
	}
	private static int i(String s){
		s=s.trim();
		if(s.endsWith("K")) return prefix(s,1)*1024;
		else if(s.endsWith("M"))return prefix(s,1)*1024*1024;
		else if(s.endsWith("G"))return prefix(s,1)*1024*1024*1024;
		else if(s.endsWith("T"))return prefix(s,1)*1024*1024*1024*1024;
		else if(s.endsWith("m"))return prefix(s,1)*60*1000;
		else if(s.endsWith("h"))return prefix(s,1)*60*60*1000;
		else if(s.endsWith("d"))return prefix(s,1)*60*60*24*1000;
		else if(s.endsWith("ms"))return prefix(s,2);
		else if(s.endsWith("s"))return prefix(s,1)*1000;
		return Integer.parseInt(s);
	}
	private static boolean b(String s){
		return Boolean.parseBoolean(s);
	}
}