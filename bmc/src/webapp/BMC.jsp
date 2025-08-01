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
<%@page import="java.nio.charset.StandardCharsets"%>
<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%> 
<%@ page import="javax.sql.*,javax.naming.InitialContext,javax.servlet.http.*,javax.servlet.*"%>
<%@ include file="AMF_utils.jsp" %>
<%! static volatile JSONObject CORE = null;%>
<%
if(CORE==null){
	CORE = new JSONObject(new String(request.getServletContext().getResourceAsStream("/bmc_core.json").readAllBytes(),
			StandardCharsets.UTF_8));
}
if(request.getMethod().equals("POST")){  
	int userID = Integer.parseInt(request.getParameter("userID"));
	String operation =request.getParameter("operation");
	int cityID = 0;
	var json = new JSONObject(new String(request.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
	String token = Objects.toString(json.opt("token"));
	long sid = json.optLong("sid");
	String nkApiId = Objects.toString(json.opt("nkApiId"));
	String sessionID = Objects.toString(json.opt("sessionID"));
	JSONObject reply = new JSONObject();
	if(!operation.equals("handshake"))
		if(session.getAttribute("handshake") == null)
			throw new RuntimeException("No handshake");
	switch(operation){
	case "handshake":
		sessionID = session.getId();
		sid = System.currentTimeMillis();
		
		AMFMessage nkAuth = new AMFMessage();
		var serializer = ByteAMF.serializer();
		var body = new AMFBody("user.get_koins", "/1", List.of(userID, token), AMFBody.DATA_TYPE_ARRAY);
		nkAuth.addBody(body);
		serializer.serialize(nkAuth);
		byte[] amfPayload = serializer.get();
		
		try(HttpClient client = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).build()){
			HttpRequest req = HttpRequest.newBuilder()
					.header("Content-Type", "x-amf")
					.header("Accept-Encoding","gzip, deflate, br")
					.header("Accept-Language","en-US")
					.header("Referer","https://assets.nkstatic.com/nklogin/Banana.swf?gamename=BTD5")
					.header("Origin","https://assets.nkstatic.com")
					.header("Sec-Fetch-Dest","embed")
					.header("Sec-Fetch-Mode","no-cors")
					.header("Sec-Fetch-Site","cross-site")
					.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) NinjaKiwiArchive/1.1.0 Chrome/80.0.3987.86 Electron/8.0.1 Safari/537.36")
					.header("X-Requested-With","ShockwaveFlash/11.2.999.999")
					.POST(BodyPublishers.ofByteArray(amfPayload))
					.uri(URI.create("https://mynk.ninjakiwi.com/gateway"))
					.build();
			HttpResponse<byte[]> amfResponse = client.send(req, BodyHandlers.ofByteArray());
			if(amfResponse.statusCode() != 200)
				throw new RuntimeException("Auth failure "+amfResponse.statusCode());
			AMFBodies bodies = AMFBodies.from(amfResponse.body());
			System.out.println(bodies);
			AMFBody b = bodies.iterator().next();
			if(b.getTarget().contains("onStatus") || 
					b.getType() != AMFBody.DATA_TYPE_OBJECT ||
					((Map<?,?>)b.getValue()).get("koins")==null)
				throw new RuntimeException("Auth failure 500");
		}
		//we have succeeded
		session.setAttribute("handshake", true);
		session.setAttribute("userID", userID);
		session.setAttribute("nkApiID", userID);
		session.setAttribute("token", token);
		session.setAttribute("sid", sid);
		
		//If UID not in DB, create it with a new API ID. we need a db anyways theres stuff to store
		//or do we???
		//files moment
		//Contact NK to verify token(??? sus)
		//All other requests must verify the API id.
		//the token is the real authenticator, so we can maybe just use a seeded API id???
		//yes
		//get_inventory??????
		//below is the proof of auth
		//session.setAttribute(api id...
				
		reply = new JSONObject()
			.put("payload",new JSONObject())
			.put("nkApiID",session.getAttribute("nkApiID"))
			.put("token",session.getAttribute("token"))
			.put("sessionID",session.getId())
			.put("success",true)
			.put("sid",session.getAttribute("sid"));
		break;
		case "core":
			//NOTE: most stuff here is actually user specific
			reply = CORE;
			return;
		case "cities":
			//NOTE: most stuff here is actually user specific
			reply = CORE;
			return;
	}
	response.resetBuffer();
	out.print(reply.toString());
	//TODO: nka does not seem to save session cookie
	return;
}else{
	%><html><body>
	the thing you were looking for wasnt there or something
	</body></html>
	<%
}
%>