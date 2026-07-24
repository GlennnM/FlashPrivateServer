<%@page import="java.util.Map"%>
<%@page import="java.nio.file.Files"%>
<%@page import="java.io.IOException"%>
<%@page import="java.nio.file.Path"%>
<%@page import="java.nio.charset.StandardCharsets"%>
<%@page import="xyz.hydar.ee.HydarEE.Context"%>
<%@page import="java.io.FileInputStream"%>
<%@page import="java.sql.SQLException"%>
<%@page import="xyz.hydar.ee.HydarEE.HttpServletRequest"%>
<%@page import="java.util.HexFormat,org.json.*,java.util.List,org.openamf.io.*,org.openamf.*"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%-- 
WIP AMF gateway. make sure to include AMF_utils.jsp and openamf & json-java JARs. 
should connect to database or maybe object storage for accounts
for a list of their commands and return types see 'future_stuff.txt'
--%>
<%
%><%!
static class AMFImpl{
	static final JSONObject store = new JSONObject();
	static final JSONObject ach = new JSONObject();
	static final List<String> games = List.of("Battle Blocks Defense","Battle Panic","Battles","BSM2","BTD4","BTD5","Fortress Destroyer","MonkeyCity","SAS TD","SAS3","SAS4","Tower Keepers");
	
	public JSONArray getStore(String game, Context ctx){
		if(store.isEmpty()){
			synchronized(store){
				for(String g: games){
					try{
						Path f = Path.of(ctx.getRealPath("/amf_data/store/" + g +".json"));
						store.put(g, new JSONArray(Files.readString(f)));
					}catch(IOException ioe){
						ioe.printStackTrace();
					}
				}
			}
		}
		return store.getJSONArray(game);
	}
	public JSONArray getAchievements(String game, Context ctx){
		if(ach.isEmpty()){
			synchronized(ach){
				for(String g: games){
					try{
						Path f = Path.of(ctx.getRealPath("/amf_data/ach/" + g +".json"));
						ach.put(g, new JSONArray(Files.readString(f)));
					}catch(IOException ioe){
						ioe.printStackTrace();
					}
				}
			}
		}
		return ach.getJSONArray(game);
	}
	public JSONArray getMyAchievements(String game, String userID, Context ctx){
		JSONArray j = getAchievements(game, ctx);
		for(int i=0;i<j.length();i++){
			j.getJSONObject(i).put("perc",0d).put("credited",0d).put("userid",Double.parseDouble(userID));
		}
		return j;
	}
	public JSONArray getData(String userID, String game){
		
		return new JSONArray().put(new JSONObject()
				.put("gcash",JSONObject.NULL)
				.put("data",JSONObject.NULL)
				.put("transid",JSONObject.NULL)
				.put("active",1.0d)
				.put("glevel",JSONObject.NULL)
				.put("gxp",JSONObject.NULL)
				.put("gnum",JSONObject.NULL));
	}
	public void saveData(String userID, String token, String game, Map<?,?> data){
		
	}
}
 %>