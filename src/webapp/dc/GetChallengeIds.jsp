<%@page import = "java.util.Random,java.io.ByteArrayInputStream,java.io.ObjectInputStream,java.io.ObjectOutputStream,java.util.Base64,java.util.List,java.util.Date,java.util.TimeZone,java.io.File,java.io.Writer,java.io.FileWriter,java.nio.file.Path,java.util.Calendar,java.io.IOException,java.nio.file.Files"%><%

Calendar now = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
Calendar cache = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
now.setTime(new Date());
Random x = new Random(33888522196117857l);
StringBuilder output = new StringBuilder();
try{
	Path p = Paths.get("./dc/GetChallengeIds.txt");
	File f = p.toFile();
	cache.setTime(new Date(f.lastModified()));
	if(now.get(Calendar.YEAR)!=cache.get(Calendar.YEAR)||now.get(Calendar.DAY_OF_YEAR)!=cache.get(Calendar.DAY_OF_YEAR)){
		throw new IOException();
	} 
	//add random stuff so it doesn't get cached by browser
	response.sendRedirect("/dc/GetChallengeIds.txt?hydar="+(now.getTime().getTime())+"."+(long)(Math.random()*33888522196117857l));
	
}catch(IOException eee){
	Writer fileWriter = new FileWriter("./dc/GetChallengeIds.txt", false);
	Calendar event = Calendar.getInstance(TimeZone.getTimeZone("GMT")); 
	event.set(2018,7,1,0,0,0);
	int eventOffset=0;
	String i=Files.readString(Paths.get("./dc/challenge-id-archive"));
	out.print(i);
	fileWriter.write(i);
	out.print("\n");
	fileWriter.write("\n");
	while(now.get(Calendar.YEAR)!=event.get(Calendar.YEAR)||now.get(Calendar.DAY_OF_YEAR)!=event.get(Calendar.DAY_OF_YEAR)){
		event.add(Calendar.HOUR,24);
		x.nextDouble();
		eventOffset++;
		if(event.get(Calendar.YEAR)>2022||(event.get(Calendar.YEAR)==2022&&event.get(Calendar.MONTH)>=5)){
			
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
			fileWriter.write(id);
			if(now.get(Calendar.YEAR)!=event.get(Calendar.YEAR)||now.get(Calendar.DAY_OF_YEAR)!=event.get(Calendar.DAY_OF_YEAR)){
				out.print("\n");
				fileWriter.write("\n");
			}
			x = prev;
		}
		
	}
	fileWriter.close();
}
%>