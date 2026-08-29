<%@page import="xyz.hydar.bmc.Profile"%>
<%@page import="java.io.InputStream"%>
<%@page import="java.io.ByteArrayOutputStream"%>
<%@page import="xyz.hydar.bmc.AMFService"%>
<%@page import="java.util.concurrent.atomic.AtomicLong"%>
<%@page import="java.nio.charset.StandardCharsets"%>
<%@page import="java.io.FileInputStream"%>
<%@page import="java.sql.SQLException"%>
<%@page import="xyz.hydar.ee.HydarEE.Context"%>
<%@page import="java.util.HexFormat,java.util.List,org.openamf.io.*,org.openamf.*"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
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
public static volatile AMFImpl DATA = null;
public static volatile Context ctx = null;
static final AtomicLong LAST_SKU_UPDATE = new AtomicLong();
%>
<%
if(DATA==null){
	synchronized(Profile.class){
		String storeLocation = request.getServletContext().getInitParameter("STORE_LOCATION");
		String skipScoreUpdate = request.getServletContext().getInitParameter("NO_UPDATE");
		var store = FileObjectStore.of(Path.of(storeLocation)).bind(30000L);
		Profile.setStore(store);
		DATA=new AMFImpl(store);
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
			var data = DATA.getSaveData(uid, game);
			return (data == null || data == JSONObject.NULL) ? null : new JSONArray().put(data);
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
			return DATA.getKoins(userID, token);
		}
	}.inputs("userID","token").register();
	new AMFService("user.get_clan"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String userID=(String)args.get(0);  
			return DATA.getClan(userID);
		}
	}.inputs("userID").register();
	new AMFService("user.get_avatar"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String userID=(String)args.get(0);
			String token=(String)args.get(1);
			return DATA.getAvatar(userID);
			
		}
	}.inputs("userID","token").register();
	new AMFService("hydar.set_clan"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String userID=(String)args.get(0);
			String token=(String)args.get(1);
			int clan = (int)(double)args.get(2);
			return DATA.setClan(userID, token, clan);
		}
	}.inputs("userID","token",11.0).register();
	new AMFService("hydar.set_avatar"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String userID=(String)args.get(0);
			String token=(String)args.get(1);
			String avatar = (String)args.get(2);
			return DATA.setAvatar(userID, token, avatar);
		}
	}.inputs("userID","token","avatar").register();
	new AMFService("hydar.set_inventory"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String userID=(String)args.get(0);
			String token=(String)args.get(1);
			String gameName = (String)args.get(2);
			Map<?,?> inventory = (Map<?,?>)args.get(3);
			DATA.importInventory(userID, token, gameName, inventory);
			return 1;
		}
	}.inputs("userID","token","gameName","OBJECT").register();
	new AMFService("hydar.set_save_offset"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String userID=(String)args.get(0);
			String token=(String)args.get(1);
			String gameName = (String)args.get(2);
			int nkid = (int)(double)args.get(3);
			DATA.setSaveOffset(userID, token, gameName, nkid);
			return 1;
		}
	}.inputs("userID","token","gameName",1.0).register();
	new AMFService("user.get_inventory"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String game=(String)args.get(0);
			String userID=(String)args.get(1);
			String token=(String)args.get(2);
			String username=(String)args.get(3);
			return DATA.getInventory(userID, token, game,ctx);
		}
	}.inputs("gameName","userID","token","username").register();
	new AMFService("game.get_inventory"){
		@Override
		public Object apply(List<?> args) throws Exception{
			return new JSONArray();
		}
	}.inputs("gameName","userID","token","username").register();
	new AMFService("prem.buyItems"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String userID=(String)args.get(0);
			String game=(String)args.get(1);
			double currID=(double)args.get(2);
			String token=(String)args.get(3);
			List<?> items = (List<?>)args.get(4);
			return DATA.buyNeoItems(userID, token, game, items, ctx);//.put("currency",4608d).put("currid",1d);
		}
	}.inputs("userID","gameName",7.0,"token",List.of(List.of("id","quantity"))).register();
	new AMFService("prem.getBalance"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String userID=(String)args.get(0);
			String token=(String)args.get(1);
			String game=(String)args.get(2);
			return DATA.getBalance(userID, token, game);//.put("currency",4608d).put("currid",1d);
		}
	}.inputs("userID","token","gameName").register();
	//this can be synced now --> add to user/info
	new AMFService("prem.getCurrency"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String userID=(String)args.get(0);
			String game=(String)args.get(1);
			String token=(String)args.get(2);
			double currID=(double)args.get(3);
			double amount=(double)args.get(4);
			String source=(String)args.get(5);
			String message=(String)args.get(6);
			return DATA.getCurrency(userID, token, game, amount, source, message);//.put("currency",4608d).put("currid",1d);
		}
	}.inputs("userID","gameName","token",0.0, 17.0, "10", "0").register();
	new AMFService("prem.get_game_currency_inventory"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String game=(String)args.get(0);
			String userID=(String)args.get(1);
			String token=(String)args.get(2);
			return DATA.getNeoInventory(userID, token, game);
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
	new AMFService("user.set_achievement"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String userID=(String)args.get(0);
			String token=(String)args.get(1);
			String game=(String)args.get(2);
			double ach_id=(double)args.get(3);
			double perc=(double)args.get(4);
			return DATA.setAchievement(userID, token, game, ach_id, perc, ctx);
		}
	}.inputs("userID","token","gameName", 0d, 100d, "username").register();
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
	new AMFService("game.check_reward"){
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
	new AMFService("game.consecutive_logins"){
		@Override
		public Object apply(List<?> args) throws Exception{
			String userID=(String)args.get(0);
			String token=(String)args.get(1);
			String game=(String)args.get(2);
			//reuse v1(todo: simplify for all the v2s with same input/output)
			return 1;
		}
	}
	.inputs("userID","token","game")
	.register();
	new AMFService("game.save_score"){
		@Override
		public Object apply(List<?> args) throws Exception{
			String userID=(String)args.get(0);
			String token=(String)args.get(1);
			double id = (double)args.get(2);
			double score = (double)args.get(3);
			String username = (String)args.get(4);
			return score;
		}
	}
	.inputs("userID","token",469d, 119d, "username")
	.register();
	for(String s: List.of("prem.getBalance","prem.getCurrency",
			"user.get_koins", "user.get_avatar", "user.set_achievement",
			"game.get_data", "game.save_data", "game.get_my_achievements", "game.check_reward","game.get_store", "game.save_score")){
		new AMFService("v2."+s){
			@Override
			public Object apply(List<?> args) throws Exception{
				return AMFService.getService(s).apply(args);
			}
		}
		.inputs(AMFService.getService(s).getInputTypes())
		.register();
	}
	new AMFService("v2.game.track"){
		@Override
		public Object apply(List<?> args) throws Exception{
			return null;
		}
	}
	.inputs("userID","game","metric","data",-1.0,"","","unavailable")
	.register();
	
	new AMFService("v2.user.get_inventory"){
		@Override
		public Object apply(List<?> args) throws Exception{
			String game=(String)args.get(0);
			String userID=(String)args.get(1);
			String token=(String)args.get(2);
			return new JSONObject().put("success",true).put("items",DATA.getInventory(userID, token, game, ctx));
		}
	}
	.inputs("game","userID","token","username")
	.register();
	//"name" instead of "clan"
	new AMFService("v2.user.get_clan"){
		@Override
		public Object apply(List<?> args) throws SQLException{
			String userID=(String)args.get(0);
			return DATA.getClanV2(userID);
		}
	}.inputs("userID").register();
	new AMFService("v2.prem.buyNeoPremItem"){
		@Override
		public Object apply(List<?> args) throws Exception{
			String userID=(String)args.get(0);
			String token=(String)args.get(1);
			String game=(String)args.get(2);
			double currID=(double)args.get(3);
			String itemID = (String)args.get(4);
			double quantity=(double)args.get(5);
			String tag=(String)args.get(6);
			return DATA.buyNeoItems_v2(userID, token, game, List.of(List.of(Double.parseDouble(itemID), quantity)), ctx);
			//must contain items, items must contain "uuid" str and "tag" (any)
		}
	}
	.inputs("userID","token","game",7.0,"1", 5.0,"item")
	.register();
	new AMFService("v2.prem.getNeoPremInventory"){
		@Override
		public Object apply(List<?> args) throws Exception{
			String userID=(String)args.get(0);
			String token=(String)args.get(1);
			String game=(String)args.get(2);
			return DATA.getNeoInventory_v2(userID, token, game);
		}
	}
	.inputs("userID","token","game",7.0)
	.register();
	new AMFService("v2.prem.consumeNeoPremItem"){
		@Override
		public Object apply(List<?> args) throws Exception{
			String userID=(String)args.get(0);
			String token=(String)args.get(1);
			String game=(String)args.get(2);
			String uuid=(String)args.get(3);
			return DATA.consumeNeoPrem_v2(userID, token, game, uuid);
		}
	}
	.inputs("userID","token","game","uuid")
	.register();
	
	new AMFService("v2.game.consecutive_logins"){
		@Override
		public Object apply(List<?> args) throws Exception{
			String userID=(String)args.get(0);
			String token=(String)args.get(1);
			String game=(String)args.get(2);
			double rewardID = (double)args.get(3);
			return 1;
		}
	}
	.inputs("userID","token","gameName",1d)
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
		   		//AMFService.accept(new ByteArrayInputStream(data),baos);
		   	}
		   //	out.println("response: ");
		   	//out.println(AMFBodies.from(baos.toByteArray()));
	   	} 
		for(String filename:List.of("/4238_.txt")){
		   	try(InputStream file=request.getServletContext().getResourceAsStream(filename)){
   				out.println("File: "+AMFBodies.from(file));
		   	}
		} 
		for(String filename:List.of("/616_.txt","/616a.txt","/616a_.txt","/616b.txt","/616b_.txt","/617_.txt","/618_.txt","/617.txt","/618.txt","/19621_.txt","/resyncq.txt","/resyncr.txt","/servertimeandscores.txt","/btd5-myresponse.txt","/btd5-request.txt","/btd5-response.txt")){
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