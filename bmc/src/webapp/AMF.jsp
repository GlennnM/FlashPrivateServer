<%@page import="java.nio.charset.StandardCharsets"%>
<%@page import="java.io.FileInputStream"%>
<%@page import="java.sql.SQLException"%>
<%@page import="xyz.hydar.ee.HydarEE.Context"%>
<%@page import="java.util.HexFormat,java.util.List,org.openamf.io.*,org.openamf.*"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ include file="AMF_utils.jsp" %>
<%@ include file="AMF_impl.jsp" %>
<%-- 
WIP AMF gateway. make sure to include AMF_utils.jsp and openamf & json-java JARs. 
should connect to database or maybe object storage for accounts
for a list of their commands and return types see 'future_stuff.txt'
--%>
<!DOCTYPE html> 
<html>
<head>
<meta charset="UTF-8"> 
<title>hydar AMF gateway</title>  
</head> 
<body style='white-space:pre-line'>
<pre><%
%><%!

static void syncGameFromNK(){
	
}
static void syncUserFromNK(){
	
}
public static volatile AMFImpl DATA = null;
public static volatile Context ctx = null;
static final AtomicLong LAST_SKU_UPDATE = new AtomicLong();
%>
<%
if(DATA==null){
	synchronized(this){
		String storeLocation = request.getServletContext().getInitParameter("STORE_LOCATION");
		String skipScoreUpdate = request.getServletContext().getInitParameter("NO_UPDATE");
		DATA=new AMFImpl(FileObjectStore.of(Path.of(storeLocation)).bind(request, 30000L));
	}
}
%>
<%!
static{ 
	new AMFService("echo.echo",(x)->"<<TESTCONN>>").inputs("STRING").register();
	//this one first, then store/ach
	//use Hydar object store
	new AMFService("game.get_data"){
		@Override 
		public Object apply(List<?> args) throws SQLException{
			String uid=(String)args.get(0);
			String game=(String)args.get(1);
			return new JSONArray().put(DATA.getData(uid, game));
		}
	} 
	//these represent types, we use descriptive names for strings
	//see AMFType for all the types
	.inputs("userID","gameName")
	.register();
	new AMFService("game.get_store"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String game=(String)args.get(0); 
			return DATA.getStore(game, ctx);
		}
	}.inputs("gameName").register();
	new AMFService("user.get_koins"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String userID=(String)args.get(0);
			String token=(String)args.get(1);
			return new JSONObject().put("koins",1d).put("points",0d);
		}
	}.inputs("userID","token").register();
	new AMFService("user.get_clan"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String userID=(String)args.get(0);
			return new JSONObject().put("clan","White Tigers").put("id",11.0);
		}
	}.inputs("userID").register();
	new AMFService("user.get_avatar"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String userID=(String)args.get(0);
			String token=(String)args.get(1);
			return new JSONObject().put("avatar","nk_monkey.png");
			
		}
	}.inputs("userID","token").register();
	new AMFService("user.get_inventory"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String game=(String)args.get(0);
			String userID=(String)args.get(1);
			String token=(String)args.get(2);
			String username=(String)args.get(3);
			return new JSONArray();
		}
	}.inputs("gameName","userID","token","username").register();
	new AMFService("game.get_inventory"){
		@Override
		public Object apply(List<?> args) throws Exception{
			//reuse v1(todo: simplify for all the v2s with same input/output)
			return AMFService.getService("game.save_data").apply(args);
		}
	}.inputs("gameName","userID","token","username").register();
	new AMFService("prem.getBalance"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String game=(String)args.get(0);
			String userID=(String)args.get(1);
			String token=(String)args.get(2);
			return new JSONObject().put("currency",4608d).put("currid",1d);
		}
	}.inputs("gameName","userID","token").register();
	new AMFService("prem.get_game_currency_inventory"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String game=(String)args.get(0);
			String userID=(String)args.get(1);
			String token=(String)args.get(2);
			return new JSONArray()
					.put(new JSONObject().put("quantity",24d).put("id",40d))
					.put(new JSONObject().put("quantity",1d).put("id",38d));
		}
	}.inputs("gameName","userID","token").register();
	new AMFService("game.get_my_achievements"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String userID=(String)args.get(0);
			String token=(String)args.get(1);
			String game=(String)args.get(2);
			return DATA.getMyAchievements(game, userID, ctx);
		}
	}.inputs("userID","token","gameName").register();
	new AMFService("game.save_data"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String userID=(String)args.get(0);
			String token=(String)args.get(1);
			String game=(String)args.get(2); 
			Map<?,?> save=(Map<?,?>)args.get(3);
			return DATA.saveData(userID, token, game, save);
		}
	}.inputs("userID","token","gameName","OBJECT").register();

	new AMFService("game.get_server_time"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			return (double) (System.currentTimeMillis() / 1000);
		}
	}.inputs().register();
	
	new AMFService("game.track"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String game=(String)args.get(0);
			String userID=(String)args.get(1);
			String token=(String)args.get(2);
			String param=(String)args.get(3);
			return JSONObject.NULL;
		}
	}.inputs("gameName","userID","task","param").register();
	/**V2 AMF STUFF(mostly the same)*/
	new AMFService("v2.game.get_data"){
		@Override
		public Object apply(List<?> args) throws Exception{
			//reuse v1(todo: simplify for all the v2s with same input/output)
			return AMFService.getService("game.get_data").apply(args);
		}
	}
	.inputs("userID","gameName")
	.register();
	new AMFService("v2.game.save_data"){
		@Override
		public Object apply(List<?> args) throws Exception{
			//reuse v1(todo: simplify for all the v2s with same input/output)
			return AMFService.getService("game.save_data").apply(args);
		}
	}
	.inputs("userID","gameName","token","OBJECT")
	.register();
	new AMFService("v2.game.check_reward"){
		@Override
		public Object apply(List<?> args) throws Exception{
			String userID=(String)args.get(0);
			String token=(String)args.get(1);
			Double rewardID=(Double)args.get(2);
			//reuse v1(todo: simplify for all the v2s with same input/output)
			return List.of(rewardID,true,1.0);
		}
	}
	.inputs("userID","token",0.0)
	.register();
}%>
<%
	if(ctx==null)
		ctx=request.getServletContext();

   	if(request.getMethod().equals("POST")){
   		//process POST data as sent by game etc
   		response.setContentType("application/x-amf");
   		response.resetBuffer();
   		AMFService.accept(request.getInputStream(),response.getOutputStream());
   		return; 
   	}else{
	   	//run test cases on GET
	   	//(use save request/save response in fiddler to get some test data)
	   	
	   out.println("request: ");
	   	for(String filename: List.of("/getcurrency2.txt")){
		   	var baos=new ByteArrayOutputStream();
		   	try(InputStream file=request.getServletContext().getResourceAsStream(filename)){
		   		byte[] data=file.readAllBytes();
		   		out.println("File: "+AMFBodies.from(data));
		   		AMFService.accept(new ByteArrayInputStream(data),baos);
		   	}
		   	out.println("response: ");
		   	out.println(AMFBodies.from(baos.toByteArray()));
	   	}
		for(String filename:List.of("/4238_.txt")){
		   	try(InputStream file=request.getServletContext().getResourceAsStream(filename)){
   				out.println("File: "+AMFBodies.from(file));
		   	}
		} 
		for(String filename:List.of("/btd5-myrequest.txt","/btd5-myresponse.txt","/btd5-myresponse-ig.txt","/btd5-request.txt","/btd5-response.txt")){
		   	try(InputStream file=request.getServletContext().getResourceAsStream(filename)){
   				out.println("File: "+AMFBodies.from(file));
		   	}
		} 
   	}
    //hydar 
 %>
</pre>
</body>
</html>