<%@page import = "java.util.Random,java.util.Base64,java.util.List,java.util.Date,java.util.TimeZone,java.util.Calendar,java.nio.file.Files"%><%
try {
	Random x = new Random(33888522196117857l);
	
	Calendar event = Calendar.getInstance(TimeZone.getTimeZone("GMT")); 
	Calendar now = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
	now.setTime(new Date());
	event.set(2018,7,1,0,0,0);
	int eventOffset=0;
	while(now.get(Calendar.YEAR)!=event.get(Calendar.YEAR)||now.get(Calendar.DAY_OF_YEAR)!=event.get(Calendar.DAY_OF_YEAR)){
		event.add(Calendar.HOUR,24);
		x.nextDouble();
		eventOffset++;
	} 
	int year = 2012+(int)(x.nextDouble()*7);
	int month=0;
	if(year==2018){
		month = 1+(int)(x.nextDouble()*6);
	}else month = 1+(int)(x.nextDouble()*12);
	List<String> s = Files.readAllLines(Paths.get("./dc/challenges-month-"+year+((month<10)?"0":"")+month),StandardCharsets.UTF_8);
	int day = (int)(x.nextDouble()*s.size());
	if(month==1&&year==2012)
		day+=27;
	String e=s.get(day).trim();
	String n=e.substring(8);
	String q=new String(Base64.getDecoder().decode(n.getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8);
	q=q.replace("\""+e.substring(0,8)+"\"","null");
	out.print(Base64.getEncoder().encodeToString(q.getBytes(StandardCharsets.UTF_8)));
} catch (Exception e) {
	e.printStackTrace();
	
}%>