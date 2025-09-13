<%@page import="java.util.concurrent.atomic.AtomicLong"%>
<%@page import="java.util.concurrent.ConcurrentHashMap"%>
<%@page import="java.util.Objects"%>
<%@page import="java.net.URI"%>
<%@page import="java.net.http.HttpClient.Redirect"%>
<%@page import="java.net.http.HttpResponse"%>
<%@page import="java.net.http.HttpResponse.BodyHandlers"%>
<%@page import="java.net.http.HttpRequest.BodyPublishers"%>
<%@page import="java.net.http.HttpRequest"%>
<%@page import="java.net.http.HttpClient"%>
<%@page import="org.json.JSONObject"%>
<%@page import="java.nio.charset.StandardCharsets"%>
<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%> 
<%@ page import="javax.sql.*,javax.naming.InitialContext,javax.servlet.http.*,javax.servlet.*"%>
<%@ include file="AMF_utils.jsp" %>
<%@ include file="Create_SKU.jsp" %>
<%@ include file="BMC_Data.jsp" %>
<%! 
static final Map<Integer,String> SESSIONS = new ConcurrentHashMap<>();
static volatile BMCData DATA = null;//lateinit
static final AtomicLong LAST_SKU_UPDATE = new AtomicLong();
%>
<%
if(DATA==null){
	synchronized(this){
		String storeLocation = request.getServletContext().getInitParameter("STORE_LOCATION");
		String skipScoreUpdate = request.getServletContext().getInitParameter("NO_UPDATE");
		DATA=new BMCData(FileObjectStore.of(Path.of(storeLocation)).bind(request, 30000L)).skipScoreUpdate(skipScoreUpdate);
		var prevSessions = DATA.store.get("monkeyCity/sessions");
		if(prevSessions != null)
			prevSessions.toMap().forEach((k,v) -> SESSIONS.put(Integer.parseInt(k), v.toString()));
	}
}

LAST_SKU_UPDATE.accumulateAndGet(System.currentTimeMillis(), (current, given)->{
	try{
		if(given - current > 24*3600*1000){
			createSKU(7,"",request);
			return given;
		}
	}catch(IOException | NoSuchAlgorithmException ioe){
		throw new RuntimeException(ioe);
	}
	return current;
});
if(request.getMethod().equals("POST")){   
	int userID = Integer.parseInt(request.getParameter("userID"));
	String operation =request.getParameter("operation");
	var json = new JSONObject(new String(request.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
	String token = Objects.toString(json.opt("token"));
	long sid = json.optLong("sid");
	String nkApiId = Objects.toString(json.opt("nkApiId"));
	String sessionID = Objects.toString(json.opt("sessionID"));
	String action = json.optString("action"); 
	System.out.println("->"+action);
	JSONObject reply = new JSONObject();
	response.resetBuffer();
	response.setContentType("application/json");
	if(!operation.equals("handshake"))
		if(sessionID==null || !Objects.equals(sessionID,SESSIONS.get(userID))){
			reply.put("sessionID",-1).put("success",false).put("status", "unauthorised")
			.put("error", "bmc_unauthorised")
			.put("bmc_code", "try_again")
			.put("reason", "No handshake");
			out.print(reply);
			return;
			//throw new RuntimeException("No handshake");//TODO: error about same sessions
		}
	switch(operation){
	case "handshake":  
		sessionID = session.getId();
		sid = System.currentTimeMillis();
		//TODO: new idea - just store a hash of the token, give each token hash a different save
		if(!("false".equals(request.getServletContext().getInitParameter("DO_NK_AUTH")))){
			HttpClient client = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).build();
			AMFMessage nkAuth = new AMFMessage();
			var serializer = ByteAMF.serializer();
			boolean hadAch = !DATA.store.has("monkeyCity",""+userID,"achievements");
			if(hadAch){
				nkAuth.addBody(new AMFBody("game.get_my_achievements", "/1", List.of(userID, token, "MonkeyCity"), AMFBody.DATA_TYPE_ARRAY));
			}
			nkAuth.addBody(new AMFBody("user.get_koins", "/2", List.of(userID, token), AMFBody.DATA_TYPE_ARRAY));
			
					
			serializer.serialize(nkAuth);
			byte[] amfPayload = serializer.get();
			HttpRequest req = HttpRequest.newBuilder()
					.header("Content-Type", "x-amf")
					.POST(BodyPublishers.ofByteArray(amfPayload))
					.uri(URI.create("https://mynk.ninjakiwi.com/gateway"))
					.build();
			HttpResponse<byte[]> amfResponse = client.send(req, BodyHandlers.ofByteArray());
			
			
			if(amfResponse.statusCode() != 200)
				throw new RuntimeException("Auth failure "+amfResponse.statusCode());
			AMFBodies bodies = AMFBodies.from(amfResponse.body());
			//System.out.println(bodies);
			AMFBody b = bodies.iterator().next(); 
			if(b.getTarget().contains("onStatus") )
				throw new RuntimeException("Auth failure 500");
			if(hadAch)//koins would give an object(Map)
				DATA.saveAchIfNew(userID, new JSONArray((List<?>)(b.getValue())));
			b = bodies.iterator().next(); 
			if(b.getTarget().contains("onStatus") 
			|| !(b.getValue() instanceof Map<?,?> koin) 
			|| !koin.containsKey("koins"))
				throw new RuntimeException("Auth failure 500");
			
			
		} else {
			System.err.println("WARNING: AUTH SKIPPED!!!");
		}
		//we have succeeded
		SESSIONS.put(userID,sessionID);
		DATA.store.put("monkeyCity/sessions", new JSONObject(SESSIONS));
		session.setAttribute("handshake", true);
		reply = new JSONObject()
			.put("payload",new JSONObject())
			.put("nkApiID",userID)
			.put("token",token)
			.put("sessionID",session.getId())
			.put("success",true)
			.put("sid",sid)
			.put("serverTime",sid);
		break;
		case "core":
			if(action.equals("GET")){
				reply = DATA.getCore(userID);
			}else if(action.equals("PUT")){
				boolean success = DATA.updateCore(userID, json.getJSONObject("payload"));
				reply
					.put("nkApiID",userID)
					.put("sessionID",session.getId())
					.put("success",success)
					.put("sid",System.currentTimeMillis())
					.put("tid",json.get("tid"));
			}
			break;
		case "cities":
			String cityID = request.getParameter("cityID");
			if("list".equals(cityID)){
				reply = DATA.getCityList(userID);
				break;
			}else if(cityID==null){
				if(action.equals("GET")){
					reply = DATA.getCities(userID);
				}else if(action.equals("PUT")){

					boolean success = DATA.putCities(userID, json.getJSONObject("payload"));
					//System.out.println(new FileObjectStore(Path.of("./objects")).dump());
					reply
						.put("nkApiID",userID)
						.put("sessionID",session.getId())
						.put("success",success)
						.put("sid",System.currentTimeMillis())
						.put("tid",json.get("tid"));
				}
			}else{
				int city = Integer.parseInt(cityID);
				String target = request.getParameter("target");
				if(action.equals("GET")){
					reply = target == null ? 
							DATA.getCity(userID, city):
							DATA.getCityThing(userID, city, target);
				}else if(action.equals("PUT")){
					boolean success = target == null ? 
							DATA.putCity(userID, city, json.getJSONObject("payload")):
							DATA.putCityThing(userID, Integer.parseInt(cityID), target, json.getJSONObject("payload"));
					reply
						.put("nkApiID",userID)
						.put("sessionID",session.getId())
						.put("success", success)
						.put("sid",System.currentTimeMillis())
						.put("tid",json.get("tid"));
				}
			}
			break;
		case "crate":
			String target = request.getParameter("target");
			if(action.equals("GET")){
				reply = DATA.getCrates(userID);
			}else if(action.equals("PUT")){
				boolean success = target == null ? 
						DATA.useCrate(userID): 
						switch(target){
							case "bonus" -> DATA.modifyCrates(userID, json.getJSONObject("payload").optInt("amount"));
							default -> true;
						};
				reply
					.put("nkApiID",userID)
					.put("sessionID",session.getId())
					.put("success", success)
					.put("sid",System.currentTimeMillis())
					.put("tid",json.get("tid"));
			}
			//none, request, send, get/put for crate, buy/sends(exists???), bonus(??????)
			//put with no payload = use 1 crate?
			break;
		case "pvp":
			cityID = request.getParameter("cityID");
			target = request.getParameter("target");
			int cityIndex = Integer.parseInt(cityID);
			if(action.equals("GET")){
				reply = switch(target){
					case "core" ->  DATA.getPVPCore(userID, cityIndex);
					case "friends" ->  DATA.getFriends(json.getJSONObject("payload").optJSONArray("friends"));
					default -> reply;
				};
			}else if(action.equals("PUT")){
				reply = switch(target){
					case "attacks" ->  
						switch(request.getParameter("action")){
							case "send" -> DATA.sendAttack(userID, cityIndex, json.getJSONObject("payload"));
							case "link" -> DATA.linkAttack(userID, cityIndex, 
									request.getParameter("attackID"),
									json.getJSONObject("payload"));
							case "start" -> DATA.startAttack(userID, cityIndex, request.getParameter("attackID"));
							case "resolve" -> reply;
							default -> reply;
						};
					case "pacifist" ->  reply;//TODO: pacifist(payload NULL, put)
					default -> reply;
				};
			}
			reply
				.put("success", true);

			/**
			.put("status", "ok")
			.put("error", "bmc_tech")
			.put("reason", "Not implemented");*/
			
			//core, friends, timestamp, quickmatch/honor/level???
			break;
			//need to add expiration of room -> disconnects and adds to history for all users
			//relevant operations - join/history
			//room needs to store when it was made
			//on either join or history, remove the room from ct/user and add it to ct/user/history or smth
			
			//score - fail to set if inactive event
			//detect end of week and don't allow 'now' to go past that
		case "contest":
			target = request.getParameter("target");
			cityID = request.getParameter("cityID");
			int city = Integer.parseInt(cityID);
			if(action.equals("GET")){

				reply = target == null ? 
						null:
						switch(target){
							case "score" -> DATA.getCTScores(userID, city, request.getParameter("room"));
							case "history" -> DATA.getCTHistory(userID, city);
							default -> DATA.getCT(userID, city);
						};
				if(reply==null)
					reply = new JSONObject();
				reply
					.put("success", true);
			}
			else if(action.equals("PUT")){
				reply = target == null ? 
						null:
						switch(target){
							case "loot" ->  DATA.lootCT(userID, city, request.getParameter("room"), json.getJSONObject("payload"));
							case "score" ->  DATA.updateCTScore(userID, city, request.getParameter("room"), json.getJSONObject("payload"));
							case "join" -> DATA.joinCT(userID, city, json.getJSONObject("payload"));
							case "history" -> DATA.closeCTHistory(userID, city, request.getParameter("room"), request.getParameter("action"));
							default -> null;
						};
				reply
					.put("nkApiID",userID)
					.put("sessionID",session.getId())
					.put("success", reply.optBoolean("success", true))
					.put("sid",System.currentTimeMillis())
					.put("tid",json.get("tid"));
			}
			//none(info about current ct prob), join, score/room, loot/room, history(TODO: generate based on ach?), history/room/claim, history/room/close
			break;
		case "knowledge":
			//unused
			break;
	}
	out.print(reply);
	return;
}else{
	%><html><body>
	the thing you were looking for wasnt there or something
	</body></html>
	<%
}

%>
