<%@page import="java.util.Arrays"%>
<%@page import="java.time.LocalTime"%>
<%@page import="java.time.format.DateTimeFormatter"%>
<%@page import="java.time.Duration"%>
<%@page import="java.time.LocalDate"%>
<%@page import="java.time.ZoneId"%>
<%@page import="java.time.LocalDateTime"%>
<%@page import="java.time.Instant"%>
<%@page import="java.util.Map"%>
<%@page import="java.util.concurrent.atomic.AtomicLong"%>
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
<%@page import="java.nio.charset.StandardCharsets"%>
<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%> 
<%@ page import="javax.sql.*,javax.naming.InitialContext,javax.servlet.http.*,javax.servlet.*"%>
<%@ include file="BMC_Data.jsp" %>
<%-- Has nothing to do with bmc(todo: move). Requires client modification - the original request is AMF.--%>
<%! 
//in memory(free rewards if server restarts oh no)
static final ConcurrentMap<Integer, short[]> claimed = new ConcurrentHashMap<>();
static final AtomicLong LAST_CLAIM_RESET = new AtomicLong();
static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HH:mm:ss");
%>
<%
LAST_CLAIM_RESET.accumulateAndGet(System.currentTimeMillis(), (current, given)->{
	var currentLDT = LocalDate.ofInstant(Instant.ofEpochMilli(current), ZoneId.of("UTC"));
	var givenLDT = LocalDate.ofInstant(Instant.ofEpochMilli(given), ZoneId.of("UTC"));
	if(givenLDT.isAfter(currentLDT)){
		claimed.clear();
		return given;
	}
	return current;
});

if(request.getMethod().equals("POST")){   
	int userID = Integer.parseInt(request.getParameter("userID"));
	int rewardID = Integer.parseInt(request.getParameter("rewardID"));
	String operation =request.getParameter("operation");
	JSONObject reply = new JSONObject();


	switch(operation){
	case "check":
		boolean success=true;
		for(short id: claimed.computeIfAbsent(userID, x->new short[0]))
			if(id == rewardID){
				success=false;
				var now = LocalDateTime.ofInstant(Instant.now(), ZoneId.of("UTC")).plusDays(1);
				var tomorrow = now.toLocalDate().plusDays(1).atStartOfDay();
				var timeLeft = HHMMSS.format(LocalTime.ofSecondOfDay(Duration.between(now, tomorrow).toSeconds()));
				reply.put("hoursToWait", timeLeft);
			}
			reply.put("available", success);
		break;
	case "claim":
		//sets would be cleaner but memory usage or something
		claimed.compute(userID, (k, v)->{
			boolean su = true;
			if(v==null)
				v = new short[0];
			for(short id: v)
				if(id == rewardID){
					su = false;
				}
			reply.put("success", su);
			if(su){
				var newArr = Arrays.copyOf(v, v.length+1);
				newArr[v.length] = (short)rewardID;
				return newArr;
			}
			return v;
		});
		break;
	}
	response.resetBuffer();
	response.setContentType("application/json");
	//because the function we chose to use in sas4 uses this format
	out.print(new JSONObject().put("data",reply.toString()));
	return;
}else{
	%><html><body>
	the thing you were looking for wasnt there or something
	</body></html>
	<%
}

%>
