<%@page import="xyz.hydar.ee.HydarEE.Context"%>
<%@page import="java.util.HexFormat,org.json.*,java.util.List,org.openamf.io.*,org.openamf.*"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ include file="ObjectStore.jsp" %>
<%-- 
WIP AMF gateway. make sure to include AMF_utils.jsp and openamf & json-java JARs. 
should connect to database or maybe object storage for accounts
for a list of their commands and return types see 'future_stuff.txt'
--%>
<%
%>
<%!
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
%>
<%!
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
		JSONArray j = getAchievements(game, ctx);
		for(int i=0;i<j.length();i++){
			j.getJSONObject(i).put("perc",0d).put("credited",0d).put("userid",Double.parseDouble(userID));
		}
		return j;
	}
	public JSONObject getData(String userID, String game){
		if(!games.contains(game))
			return Util.blankSave();
		return store.update(List.of("amf", userID, game, "save"),x->{
			if(x==null){
				x = new JSONObject().put("save", Util.blankSave())
						.put("inventory", JSONObject.NULL)
						.put("lastSaved", System.currentTimeMillis());
			}
			return x;
		}).getJSONObject("save");
	}
	public Double saveData(String userID, String token, String game, Map<?,?> data){
		if(!games.contains(game))
			return null;
		return store.update(List.of("amf", userID, game, "save"),x->{
			int transid = (int) (double) data.get("transid");//xd
			if(x==null){
				x = new JSONObject().put("save", new JSONObject(data))
						.put("inventory", JSONObject.NULL)
						.put("lastSaved", System.currentTimeMillis());
			}else{
				if(x.getJSONObject("save").get("transid") == JSONObject.NULL || 
			
					transid > (int)x.getJSONObject("save").optDouble("transid", -1d)){
				x.put("save", new JSONObject(data))
					.put("lastSaved", System.currentTimeMillis());
				}
			}
			return x;
		}).getJSONObject("save").getDouble("transid");
	}
}
 %>
 <%!
 public static class Util{
	 public static JSONObject blankSave(){
		 return new JSONObject()
			.put("gcash",JSONObject.NULL)
			.put("data",JSONObject.NULL)
			.put("transid",JSONObject.NULL)
			.put("active",1.0d)
			.put("glevel",JSONObject.NULL)
			.put("gxp",JSONObject.NULL)
			.put("gnum",JSONObject.NULL);
	 }
 }
 %>