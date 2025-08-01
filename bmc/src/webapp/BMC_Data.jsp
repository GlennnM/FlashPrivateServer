<%@page import="java.util.zip.CRC32"%>
<%@page import="java.io.File"%>
<%@page import="java.util.concurrent.ConcurrentMap"%>
<%@page import="java.util.Base64"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.util.List"%>
<%@page import="java.io.IOException"%>
<%@page import="java.nio.file.Files"%>
<%@page import="java.nio.file.Path"%>
<%@page import="java.util.concurrent.ConcurrentHashMap"%>
<%@page import="java.util.concurrent.atomic.AtomicReference"%>
<%@page import="java.util.Objects"%>
<%@page import="java.net.URI"%>
<%@page import="java.net.http.HttpClient.Redirect"%>
<%@page import="java.net.http.HttpResponse"%>
<%@page import="java.net.http.HttpResponse.BodyHandlers"%>
<%@page import="java.net.http.HttpRequest.BodyPublishers"%>
<%@page import="java.net.http.HttpRequest"%>
<%@page import="java.net.http.HttpClient"%>
<%@page import="org.json.JSONObject"%>
<%@page import="static java.nio.charset.StandardCharsets.UTF_8"%>
<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%> 
<%@ page import="javax.sql.*,javax.naming.InitialContext,javax.servlet.http.*,javax.servlet.*"%>
<%! 
/**does stuff like putCity(0,{},..)*/
public static class BMCData{
	
}
/**stuff like put(url, ..., ...)*/
public static interface ObjectStore{
	public default JSONObject get(String... url){
		return get(String.join("/", url));
	}
	public default JSONObject get(Iterable<String> url){
		return get(String.join("/", url));
	}
	public default JSONObject get(String url, JSONObject fallback){
		var ret = get(url);
		return ret == null ? fallback : ret;
	}
	public default JSONObject get(Iterable<String> url, JSONObject fallback){
		var ret = get(url);
		return ret == null ? fallback : ret;
	}
	public JSONObject get(String url);
	public boolean has(String url);
	public boolean delete(String url);
	public boolean put(String url, JSONObject payload);
}
/**uses b64's of the urls so it is always in the same folder*/
public static class FileObjectStore implements ObjectStore{
	private final Path root;
	//we lock using this, without ever adding to it
	private final ConcurrentMap<Path,Void> urlLock = new ConcurrentHashMap<>();
	public FileObjectStore(Path root) throws IOException{
		if(!Files.exists(root))
			Files.createDirectories(root);
		if(!Files.isDirectory(root))
			throw new IllegalArgumentException("Not a dir: "+root);
		this.root = root;
	}
	private Path map(String url){
		String newURL = Base64.getEncoder().encodeToString(url.getBytes(UTF_8));
		CRC32 crc = new CRC32();
		crc.update(url.getBytes(UTF_8));
		int bucket = (int)(crc.getValue()) & 0x7ff;
		return root.resolve(new StringBuilder()
				.append(bucket)
				.append(File.separatorChar)
				.append(newURL)
				.toString()
			);
	}
	@Override
	public JSONObject get(String url){
		Path path = map(url);
		AtomicReference<JSONObject> holder = new AtomicReference<>();//for stupid lambda thing
		urlLock.compute(path,(k,v)->{
			try{
				holder.setOpaque(new JSONObject(Files.readString(path)));
			}catch(IOException e){
				holder.setOpaque(null);
			}
			return null;
		});
		return holder.getOpaque();
	}
	@Override
	public boolean has(String url){
		Path path = map(url);
		AtomicReference<Boolean> holder = new AtomicReference<>();//for stupid lambda thing
		urlLock.compute(path,(k,v)->{
			holder.setOpaque(Files.exists(path));
			return null;
		});
		return holder.getOpaque();
	}
	@Override
	public boolean put(String url, JSONObject payload){
		Path path = map(url);
		AtomicReference<Boolean> holder = new AtomicReference<>();//for stupid lambda thing
		urlLock.compute(path,(k,v)->{
			try{
				Files.createDirectories(path);
				Files.writeString(path, payload.toString());
				holder.setOpaque(true);
			}catch(IOException e){
				holder.setOpaque(false);
			}
			return null;
		});
		return holder.getOpaque();
	}
	@Override
	public boolean delete(String url){
		Path path = map(url);
		AtomicReference<Boolean> holder = new AtomicReference<>();//for stupid lambda thing
		urlLock.compute(path,(k,v)->{
			try{
				Files.delete(path);
				holder.setOpaque(true);
			}catch(IOException e){
				holder.setOpaque(false);
			}
			return null;
		});
		return holder.getOpaque();
	}
}
//public static class DBObjectStore ?!?!!
//public static class S3ObjectStore ?!???!?!?!?!?!
%>
<%

var fos = new FileObjectStore(Path.of("."));
fos.put("data/0/ach", new JSONObject());
fos.delete("data/0/ach");
fos.put("data/0/ach", new JSONObject());
%><%=
fos.get("data/0/ach")
%>