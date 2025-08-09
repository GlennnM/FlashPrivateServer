<%@page import="java.time.LocalDate"%>
<%@page import="java.io.InputStreamReader"%>
<%@page import="java.io.BufferedReader"%>
<%@page import="java.time.temporal.ChronoUnit"%>
<%@page import="java.time.ZoneId"%>
<%@page import="java.time.LocalDateTime"%>
<%@page import="java.time.Instant"%>
<%@page import="java.nio.charset.StandardCharsets"%>
<%@page import="java.io.ByteArrayOutputStream"%>
<%@page import="java.nio.file.Paths"%>
<%@page session='false' %>
<%@page import = "java.util.Random,java.io.ByteArrayInputStream,java.io.ObjectInputStream,java.io.ObjectOutputStream,java.util.Base64,java.util.List,java.util.Date,java.util.TimeZone,java.io.File,java.io.Writer,java.io.FileWriter,java.nio.file.Path,java.util.Calendar,java.io.IOException,java.nio.file.Files"%>
<%!
static volatile Instant lastUpdate=Instant.ofEpochMilli(0);
static volatile String data;
static volatile long cacheOffset=-1;
%><%
response.resetBuffer();
//Calendar cache = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
LocalDate cache = LocalDate.ofInstant(lastUpdate, ZoneId.of("GMT"));
LocalDate now = LocalDate.now(ZoneId.of("GMT"));
LocalDate origin = LocalDateTime.of(2018, 7, 1, 0, 0)
	.atZone(ZoneId.of("GMT"))
	.toLocalDate();
Random x = new Random(33888522196117857l);
StringBuilder output = new StringBuilder();

long offset= ChronoUnit.DAYS.between(cache,now);
if(offset!=0){
	StringBuilder dat=new StringBuilder(1000);
	long eventOffset=ChronoUnit.DAYS.between(origin,now);
	long co=cacheOffset;
	if(cacheOffset<0){
		String j=new String(request.getServletContext()
				.getResourceAsStream("/dc/challenge-id-archive")
				.readAllBytes(),StandardCharsets.ISO_8859_1);
		dat.append(j);
		co=0;
	}else
		dat.append(data);
	for(long i=0;i<co;i++)
		x.nextDouble();
	for(long i=co;i<eventOffset;i++){
		x.nextDouble();
		var event = ChronoUnit.DAYS.addTo(origin,i);
		if(event.getYear()>2022||(event.getYear()==2022&&event.getMonthValue()>=5)){
			dat.append("\n");
			ByteArrayOutputStream bo = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(bo);
			oos.writeObject(x);
			oos.close();
			ObjectInputStream ois = new ObjectInputStream(
					new ByteArrayInputStream(bo.toByteArray()));
			Random prev= (Random)(ois.readObject());
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
			int start=q.indexOf("\"id\"")+7;
			String id = q.substring(start,q.indexOf("\"",start));
			//System.out.println(id);
			dat.append(id);
			x = prev;
		}
		
	}
	data=dat.toString();
	cacheOffset=eventOffset;
	lastUpdate=Instant.now();
}
out.print(data);
%>