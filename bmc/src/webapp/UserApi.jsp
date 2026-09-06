
<%@page import="xyz.hydar.bmc.Util"%>
<%@page import="org.json.JSONArray"%>
<%@page import="org.json.JSONObject"%>
<%@page import="java.io.IOException"%>
<%@page import="java.nio.file.Path"%>
<%@page import="java.util.List"%>
<%@page import="xyz.hydar.bmc.FileObjectStore"%>
<%@page import="xyz.hydar.bmc.Profile"%>	
<%!
static volatile FileObjectStore store;
static volatile List<String> keys;
static final JSONObject SAM = new JSONObject(2).put("user_id",1).put("username","sam ninjakiwi");
%>
<%
if(store==null)
	try{
		String storeLocation = request.getServletContext().getInitParameter("STORE_LOCATION");
		store = FileObjectStore.of(Path.of(storeLocation));
		Profile.store = store;
		keys = store.list();
	}catch(IOException ioe){
		throw new RuntimeException(ioe);
	}
String user = request.getParameter("user");
var profile = Profile.get(user);
response.setContentType("application/json");
response.resetBuffer();
out.print(
(switch(request.getParameter("op")){
case "user"->new JSONObject(2)
	.put("clan_name",Profile.clans.get(profile.optInt("clan")))
	.put("avatar","https://avatars.nkstatic.com/mega/"+profile.optString("avatar","nk_monkey.png"));
case "friends"->new JSONArray(
		Util.jStreamS(profile.optJSONArray("friends",new JSONArray()))
			.map(x->new JSONObject(2)
					.put("user_id", x)
					.put("username", Profile.get(x).optString("hydarUsername", profile.optString("username")))
					)
			.toList()
		).put(SAM);
case "following"->new JSONObject();
case "followers"->new JSONObject();
default->null;
}).toString());%>