package xyz.hydar.flash.util;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import xyz.hydar.net.ClientContext;
import xyz.hydar.net.ClientOptions;

public class FlashConfig{
	public final Map<String,String> cfg;
	public final String HOST;
	public final ClientOptions BATTLES;
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
	public LongAdder threadCount=new LongAdder();
	public Map<InetAddress,LongAdder> threadsPerIp=new ConcurrentHashMap<>();
	public void acquire(ClientContext ctx) {
		var all=threadCount;
		var local=threadsPerIp.computeIfAbsent(ctx.getInetAddress(), (x)->new LongAdder());
		if(all.sum()>MAX_THREADS||all.sum()>THREADS_PER_IP) {
			ctx.alive=false;
			return;
		}
		all.increment();
		local.increment();
	}
	public void release(ClientContext ctx) {
		threadCount.decrement();
		threadsPerIp.computeIfPresent(ctx.getInetAddress(), (x,y)->{
			if(y.sum()<=1)return null;
			y.decrement();
			return y;
		});
	}
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
		}//TODO: how increment???
	}
	public String get(String key) {
		return cfg.get(key);
	}
	public FlashConfig(String filename) throws IOException {
		var properties = new Properties();
		properties.load(Files.newBufferedReader(Path.of(filename)));
		cfg=properties.stringPropertyNames().stream()
				.collect(Collectors.toUnmodifiableMap(x->x,properties::getProperty));

		MAX_THREADS=i(get("MAX_THREADS"));
		THREADS_PER_IP=i(get("THREADS_PER_IP"));
		HOST=get("HOST");
		BATTLES=ClientOptions.from(get("Battles.client"),Scheduler.ses);
		BATTLES_GAME=ClientOptions.from(get("Battles.gameClient"));
		BTD5=ClientOptions.from(get("BTD5.client"),Scheduler.ses);
		BTD5_GAME=ClientOptions.from(get("BTD5.gameClient"),Scheduler.ses);
		SAS4=ClientOptions.from(get("SAS4.client"));
		SAS4_GAME=ClientOptions.from(get("SAS4.gameClient"));
		SAS3=ClientOptions.from(get("SAS3.client"));
		SAS3_MOBCAP=i(get("SAS3.mobcap"));
		CS=ClientOptions.from(get("CS.client"));
		battlesPorts=Ports.from(get("Battles.gamePorts"));
		sas4Ports=Ports.from(get("SAS4.gamePorts"));
		btd5Ports=Ports.from(get("BTD5.gamePorts"));
		scaleEarly=i(get("SAS4.scaleEarly"));
		
		
		System.out.println(cfg);
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