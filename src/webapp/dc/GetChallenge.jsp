<%@page import="java.nio.charset.StandardCharsets"%>
<%@page import="java.nio.file.Paths"%>
<%@page import = "java.util.Random,java.io.ByteArrayInputStream,java.io.ObjectInputStream,java.io.ObjectOutputStream,java.util.Base64,java.util.List,java.util.Date,java.util.TimeZone,java.io.File,java.io.Writer,java.io.FileWriter,java.nio.file.Path,java.util.Calendar,java.io.IOException,java.nio.file.Files"%><%
Calendar now = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
Calendar cache = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
now.setTime(new Date());
StringBuilder output = new StringBuilder();
try{
	Path p = Paths.get("./dc/GetChallenge.txt");
	File f = p.toFile();
	cache.setTime(new Date(f.lastModified()));
	if(now.get(Calendar.YEAR)!=cache.get(Calendar.YEAR)||now.get(Calendar.DAY_OF_YEAR)!=cache.get(Calendar.DAY_OF_YEAR)){
		throw new IOException();
	} 
	//add random stuff so it doesn't get cached by browser
	response.sendRedirect("/dc/GetChallenge.txt"+"?hydar="+(now.getTime().getTime())+"."+(long)(Math.random()*33888522196117857l));
	
}
catch(IOException eee){
	Writer fileWriter = new FileWriter("./dc/GetChallenge.txt", false);
	Random x = new Random(33888522196117857l);
	
	Calendar event = Calendar.getInstance(TimeZone.getTimeZone("GMT")); 
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
	String e=s.get(day).trim();
	if(month==1&&year==2012)
		day+=27;
	String n=e.substring(8);
	String q=new String(Base64.getDecoder().decode(n.getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8);
	q=q.replace("\""+e.substring(0,8)+"\"","null");
	String data=Base64.getEncoder().encodeToString(q.getBytes(StandardCharsets.UTF_8));
	out.print(data);
	fileWriter.write(data);
	fileWriter.close();
}%>