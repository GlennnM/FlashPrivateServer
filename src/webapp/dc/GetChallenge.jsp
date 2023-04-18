<%@page import="java.io.InputStreamReader"%>
<%@page import="java.time.LocalDate"%>
<%@page import="java.util.List"%>
<%@page import="java.util.Base64"%>
<%@page import="java.util.Random"%>
<%@page import="java.time.temporal.ChronoUnit"%>
<%@page import="java.time.ZoneId"%>
<%@page import="java.time.Instant"%>
<%@page import="java.time.LocalDateTime"%>
<%@page import="java.io.BufferedReader"%>
<%@page import="java.nio.charset.StandardCharsets"%>
<%@page session='false' %>
<%@page import="java.nio.file.Paths"%>
<%!
static volatile Instant lastUpdate=Instant.ofEpochMilli(0);
static volatile String data;
%><%
response.resetBuffer();
//Calendar cache = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
LocalDateTime cache = LocalDateTime.ofInstant(lastUpdate, ZoneId.of("GMT"));
LocalDateTime origin = LocalDateTime.of(2018, 7, 1, 0, 0)
	.atZone(ZoneId.of("GMT"))
	.toLocalDateTime();
LocalDateTime now = LocalDateTime.now(ZoneId.of("GMT"));


StringBuilder output = new StringBuilder();
long offset= ChronoUnit.DAYS.between(cache,now);
if(offset!=0){
	Random x = new Random(33888522196117857l);
	
	long eventOffset=ChronoUnit.DAYS.between(origin,now);
	for(long i=0;i<eventOffset;i++){
		x.nextDouble();
	} 
	int year = 2012+(int)(x.nextDouble()*7);
	int month=0;
	if(year==2018){
		month = 1+(int)(x.nextDouble()*6);
	}else month = 1+(int)(x.nextDouble()*12);
	List<String> s = new BufferedReader(
		new InputStreamReader(
			request.getServletContext() 
			.getResourceAsStream("/dc/challenges-month-"+year+((month<10)?"0":"")+month)
			)
		).lines().toList();
	int day = (int)(x.nextDouble()*s.size());
	String e=s.get(day).trim();
	if(month==1&&year==2012)
		day+=27;
	String n=e.substring(8);
	String q=new String(Base64.getDecoder().decode(n.getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8);
	q=q.replace("\""+e.substring(0,8)+"\"","null");
	String data_=Base64.getEncoder().encodeToString(q.getBytes(StandardCharsets.UTF_8));
	data=data_;
	lastUpdate=Instant.now();
} 
out.print(data);
%>