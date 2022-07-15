<%@page import = "java.util.Random,java.util.Base64,java.util.List,java.util.Date,java.util.TimeZone,java.util.Calendar,java.nio.file.Files"%><%
try {
	Random x = new Random(33888522196117857l);
	
	Calendar event = Calendar.getInstance(TimeZone.getTimeZone("GMT")); 
	Calendar now = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
	now.setTime(new Date());
	event.set(2018,7,1,0,0,0);
	int eventOffset=0;
	out.print(Files.readString(Paths.get("./dc/challenge-id-archive")));
	out.print("\n");
	while(now.get(Calendar.YEAR)!=event.get(Calendar.YEAR)||now.get(Calendar.DAY_OF_YEAR)!=event.get(Calendar.DAY_OF_YEAR)){
		event.add(Calendar.HOUR,24);
		x.nextDouble();
		eventOffset++;
		if(event.get(Calendar.YEAR)>2022||(event.get(Calendar.YEAR)==2022&&event.get(Calendar.MONTH)>=5)){
			int year = 2012+(int)(x.nextDouble()*7);
			int month=0;
			if(year==2018){
				month = 1+(int)(x.nextDouble()*6);
			}else month = 1+(int)(x.nextDouble()*12);
			List<String> s = Files.readAllLines(Paths.get("./dc/challenges-month-"+year+((month<10)?"0":"")+month),StandardCharsets.UTF_8);
			int day = (int)(x.nextDouble()*s.size());
			String e=s.get(day).trim();
			if(month==1&&year==2012)
				day+=27;
			String n=e.substring(8);
			String q=new String(Base64.getDecoder().decode(n.getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8);
			q=q.replace("\""+e.substring(0,8)+"\"","null");
			String id = q.substring(q.indexOf("\"id\"")+7,q.substring(q.indexOf("\"id\"")+7).indexOf("\"")+q.indexOf("\"id\"")+7);
			//System.out.println(id);
			out.print(id);
			if(now.get(Calendar.YEAR)!=event.get(Calendar.YEAR)||now.get(Calendar.DAY_OF_YEAR)!=event.get(Calendar.DAY_OF_YEAR))
				out.print("\n");
		}
	} 
} catch (Exception e) {
	e.printStackTrace();
	
}%>