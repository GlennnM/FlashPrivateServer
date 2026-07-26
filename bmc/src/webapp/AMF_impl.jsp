<%@page import="java.net.http.HttpClient.Redirect"%>
<%@page import="java.net.http.HttpResponse.BodyHandlers"%>
<%@page import="java.net.http.HttpResponse"%>
<%@page import="java.net.URI"%>
<%@page import="java.net.http.HttpRequest.BodyPublishers"%>
<%@page import="java.net.http.*"%>
<%@page import="java.security.NoSuchAlgorithmException"%>
<%@page import="java.security.MessageDigest"%>
<%@page import="java.util.stream.Stream"%>
<%@page import="java.util.stream.IntStream"%>
<%@page import="xyz.hydar.ee.HydarEE.Context"%>
<%@page import="java.util.HexFormat,org.json.*,java.util.List,org.openamf.io.*,org.openamf.*"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ include file="AMF_utils.jsp" %>
<%@ include file="ObjectStore.jsp" %>
<%-- 
WIP AMF gateway. make sure to include AMF_utils.jsp and openamf & json-java JARs. 
should connect to database or maybe object storage for accounts
for a list of their commands and return types see 'future_stuff.txt'
--%>
<%
%>
<%!
static{
	//VERY DUMB THING TO DO AN UPDATE THAT SHOULD HAPPEN ANYWAYS BUT isnt implemented FIXME:remove
	
	var hydar = xyz.hydar.ee.Hydar.hydars.get(0);
	if(hydar.ee.ctx.getAttribute("done2")==null){
		new Thread(()->{
			try{
				Thread.sleep(100);
				hydar.ee.ctx.setAttribute("done2", 1);
				if(Files.exists(Path.of("../src/webapp/AMF.jsp")))
					hydar.ee.compile(Path.of("../src/webapp/AMF.jsp"));
				hydar.ee.ctx.setAttribute("done2", null);
			}catch(InterruptedException e){
				Thread.currentThread().interrupt();
			}
		}).start();
	}
}
%>
<%!
static class AMFImpl{
	private final ObjectStore store;
	static volatile Map<Integer, Integer> neo_store = null;
	static final JSONObject nk_store = new JSONObject();
	static final JSONObject nk_ach = new JSONObject();
	static final Set<String> games = Set.of("Battle Blocks Defense","Battle Panic","Battles","BSM2","BTD4","BTD5","Fortress Destroyer","MonkeyCity","SAS TD","SAS3","SAS4","Tower Keepers");
	//TODO: when nk back up, check actual sas TD currency id
	static final Map<String, Integer> currID = Map.of("BTD5", 1, "SAS TD", 3, "Battles", 5, "BSM2", 6, "MonkeyCity", 7);
	public AMFImpl(ObjectStore store){
		this.store = store;
	}
	%>
	<%-- STORE/ACHIEVEMENTS --%>
	<%!
	public int getNeoCost(int id, Context ctx){
		synchronized(nk_store){//intended
			if(neo_store == null){
				for(String g: games){
					try{
						Path f = Path.of(ctx.getRealPath("/amf_data/costs.json"));
						neo_store = 
								new JSONObject(Files.readString(f))
								.toMap().entrySet().stream()
								.collect(
									Collectors.toMap(x->Integer.parseInt(x.getKey()), x->(int)x.getValue())
								);
						
					}catch(IOException ioe){
						ioe.printStackTrace();
					}
				}
			}
		}
		return neo_store.getOrDefault(id, Integer.MIN_VALUE);
	}
	public JSONArray getStore(String game, Context ctx){
		if(nk_store.isEmpty()){
			synchronized(nk_store){
				for(String g: games){
					try{
						Path f = Path.of(ctx.getRealPath("/amf_data/store/" + g +".json"));
						nk_store.put(g, new JSONArray(Files.readString(f)));
					}catch(IOException ioe){
						ioe.printStackTrace();
					}
				}
			}
		}
		return nk_store.getJSONArray(game);
	}
	public JSONArray getAchievements(String game, Context ctx){
		if(nk_ach.isEmpty()){
			synchronized(nk_ach){
				for(String g: games){
					try{
						Path f = Path.of(ctx.getRealPath("/amf_data/ach/" + g +".json"));
						nk_ach.put(g, new JSONArray(Files.readString(f)));
					}catch(IOException ioe){
						ioe.printStackTrace();
					}
				}
			}
		}
		return nk_ach.getJSONArray(game);
	}
	public JSONArray getMyAchievements(String game, String userID, Context ctx){
		JSONArray j = new JSONArray(getAchievements(game, ctx).toString());
		JSONObject myAch = store.get("amf", userID, game, "ach");
		
		for(var x: Util.jIter(j)){
			int perc = myAch==null ? 0 : myAch.optInt(""+x.getInt("id"));
			x.put("perc",(double) perc)
				.put("credited",perc>100)
				.put("userid",Double.parseDouble(userID));
		}
		return j;
	}
	public JSONArray setAchievement(String userID, String token, String game, double ach_id, double perc, Context ctx){
		if(!games.contains(game))
			return null;
		verifyNK(userID, token);
		JSONArray j = getAchievements(game, ctx);
		int achID = (int)ach_id;
		var ach = Util.jStream(j).filter(x->x.getInt("id") == achID).findFirst().orElse(null);
		if(ach == null)
			return null;
		JSONArray ret = new JSONArray().put(achID);
		LongAdder ap = new LongAdder();
		store.update(List.of("amf", userID, game, "ach"),x->{
			if(x==null){
				x = new JSONObject();
			}
			int oldPerc = (int) x.optDouble(""+achID,0d);
			int newPerc = Math.max(oldPerc, (int) perc);
			ret.put((double)newPerc);
			if(newPerc > 0)
				x.put(""+achID, newPerc);
			ret.put((oldPerc < 100 && newPerc >= 100) ? "u" : "n");
			if(oldPerc < 100 && newPerc >= 100){
				ap.add(ach.getInt("points"));
			}
			return x;
		});
		if(ap.sum()>0)
			addAP(userID, (int)ap.sum());
		return ret;
	}

	%>
	<%-- PROFILE --%>
	<%!
	public JSONObject getKoins(String userID, String token){
		verifyNK(userID, token);//so invalid token warning can happen early
		JSONObject profile = updateProfile(userID, x->x);
		return new JSONObject(2)
				.put("koins",(double)profile.getInt("nkoins"))
				.put("points",(double)profile.getInt("ap"));
	}
	public Object buyItems(String userID, String token, String game, List<?> items, Context ctx){
		if(!games.contains(game) || !currID.containsKey(game))
			return "An error occurred";
		verifyNK(userID, token);
		List<Object> res = new ArrayList<>();
		List<Object> newItems = new ArrayList<>();
		Map<Integer,Integer> itemsToAdd = new HashMap<>();
		updateProfile(userID, x->{
			var cur = x.getJSONObject("currencies");
			for(var item: items){
				if(item instanceof List<?> i){
					int itemID = (int) (double) i.get(0);
					int quantity = (int) (double) i.get(1);
					int change = getNeoCost(itemID, ctx) * quantity;
					int old = cur.optInt(game);
					if(change == Integer.MIN_VALUE){
						newItems.add("Selected item does not exist");
					}else if(old >= change){
						cur.put(game, cur.optInt(game) - change);
						itemsToAdd.compute(itemID,(k,v)-> v==null ? quantity : v+quantity);
						newItems.add(new JSONObject()
								.put("cost",change)
								.put("id",itemID)
								.put("quantity",quantity)
							);
					}else {
						newItems.add("Not enough coins to buy");
					}
				}else newItems.add("An error occurred");
				
				//results.add(new ArrayList<>(itemsToAdd.keySet()));
			}//CHECK BSM2 RESP!!!!!
			res.add(cur.get(game));
			res.add(newItems);
			return x;
		});
		//an integer is your balance, a list contains all the new items
		//??????
		updateSave(userID, game, x->{
			JSONObject neoInv = x.getJSONObject("neoInventory");
			itemsToAdd.forEach((k,v)->neoInv.put(""+k, neoInv.optInt(""+k) + v));
			return x;
		});
		return res;
	}
	public Object getNeoInventory(String userID, String token, String game){
		if(!games.contains(game) || !currID.containsKey(game))
			return "An error occurred";
		verifyNK(userID, token);
		List<Object> res = new ArrayList<>();
		updateSave(userID, game, x->x)
				.getJSONObject("neoInventory")
				.toMap()
				.forEach((k,v)->res.add(new JSONObject(2).put("id",k).put("quantity",v)));
		IO.println(res);
		return res;
	}
	public JSONObject getBalance(String userID, String token, String game){
		if(!games.contains(game) || !currID.containsKey(game))
			return new JSONObject();
		verifyNK(userID, token);
		return new JSONObject()
				.put("currid", currID.get(game))
				.put("currency",
					updateProfile(userID, x->x).getJSONObject("currencies").optInt(game)
				);
	}
	public JSONObject getCurrency(String userID, String token, String game, double amount, String source, String message){
		if(!games.contains(game))
			return new JSONObject(1).put("bal",0);
		verifyNK(userID, token);
		
		return new JSONObject(1).put("bal",
			updateProfile(userID, x->{
				var cur = x.getJSONObject("currencies");
				cur.put(game, cur.optInt(game) + (int)amount);
				return x;
			}).getJSONObject("currencies").optInt(game)
		);
	}
	private void addAP(String userID, int ap){
		updateProfile(userID, x->x.put("ap",x.getInt("ap")+ap));
	}
	private JSONObject updateProfile(String userID, UnaryOperator<JSONObject> update){
		return store.update(List.of("amf", userID, "info"),x->{
			if(x==null){
				x = Util.blankProfile(userID);
			}
			if(!x.has("currencies"))
				x.put("currencies", new JSONObject());
			return update.apply(x);
		});
	}
	public void verifyNK(String userID, String token){
		
		String hash = Util.hash(token);
		boolean[] success = {false};
		updateProfile(userID, x->{
			if(x.get("nkToken") == JSONObject.NULL || !hash.equals(x.getString("nkToken"))){
				if(isNKToken(userID, token)){ 
					x.put("nkToken", Util.hash(token));
					success[0] = true;
				}
			}else
				success[0] = true;
			return x;
		});
		if(!success[0])
			throw new NKVerifyException();
	}
	public boolean DO_NK_AUTH=false;
	public boolean isNKToken(String userID, String token){
		if(!DO_NK_AUTH)
			return token.length() > 30;
		try{
			HttpClient client = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).build();
			AMFMessage nkAuth = new AMFMessage();
			var serializer = ByteAMF.serializer();
			nkAuth.addBody(new AMFBody("user.get_koins", "/2", List.of(userID, token), AMFBody.DATA_TYPE_ARRAY));
			serializer.serialize(nkAuth);
			byte[] amfPayload = serializer.get();
			HttpRequest req = HttpRequest.newBuilder()
					.header("Content-Type", "x-amf")
					.POST(BodyPublishers.ofByteArray(amfPayload))
					.uri(URI.create("https://mynk.ninjakiwi.com/gateway"))
					.build();
			HttpResponse<byte[]> amfResponse = client.send(req, BodyHandlers.ofByteArray());
			
			
			if(amfResponse.statusCode() != 200)
				return false;
			AMFBodies bodies = AMFBodies.from(amfResponse.body());
			AMFBody b = bodies.iterator().next(); 
			if(b.getTarget().contains("onStatus") )
				return false;
			b = bodies.iterator().next(); 
			if(b.getTarget().contains("onStatus") 
			|| !(b.getValue() instanceof Map<?,?> koin) 
			|| !koin.containsKey("koins"))
				return false;
		}catch(Exception e){
			e.printStackTrace();
			return false;
		}
		return true;
	}

	%>
	<%-- SAVE --%>
	<%!
	private JSONObject updateSave(String userID, String game, UnaryOperator<JSONObject> update){
		if(!games.contains(game))
			return new JSONObject().put("save",Util.blankSave());
		return store.update(List.of("amf", userID, game, "save"),x->{
			if(x==null){
				x = new JSONObject().put("save", Util.blankSave())
						.put("neoInventory", new JSONObject())
						.put("inventory", JSONObject.NULL)
						.put("lastSaved", System.currentTimeMillis());
			}
			return update.apply(x);
		});
	}
	public JSONObject getData(String userID, String game){
		return updateSave(userID, game, x->x).getJSONObject("save");
	}
	public Double saveData(String userID, String token, String game, Map<?,?> data){
		int transid = (int) (double) data.get("transid");//xd
		verifyNK(userID, token);
		return updateSave(userID, game, x->{
			if(transid > (int)x.getJSONObject("save").optDouble("transid", -1d)){
				x.put("save", new JSONObject(data))
					.put("lastSaved", System.currentTimeMillis());
			}
			return x;
		}).getJSONObject("save").getDouble("transid");
	}

}
 %>
 <%!
 public static class Util{
	 public static String hash(String input){
		 try{
			 MessageDigest md = MessageDigest.getInstance("SHA3-256");
			 byte[] encodedhash = md.digest(input.getBytes(UTF_8));
			 return Base64.getEncoder().encodeToString(encodedhash);
		 }catch(NoSuchAlgorithmException nsae){
			 throw new RuntimeException(nsae); 
		 }
	 }
	 public static Iterable<Integer> jIterI(JSONArray array) {
		return (() -> Spliterators.iterator(jStreamI(array).spliterator()));
	}

	public static Iterable<String> jIterS(JSONArray array) {
		return (() -> Spliterators.iterator(jStreamS(array).spliterator()));
	}

	public static Iterable<JSONObject> jIter(JSONArray array) {
		return (() -> Spliterators.iterator(jStream(array).spliterator()));
	}

	public static Stream<JSONObject> jStream(JSONArray array) {
		return IntStream.range(0, array.length()).mapToObj(array::getJSONObject);
	}

	public static IntStream jStreamI(JSONArray array) {
		return IntStream.range(0, array.length()).map(array::getInt);
	}

	public static Stream<String> jStreamS(JSONArray array) {
		return IntStream.range(0, array.length()).mapToObj(array::getString);
	}
	 public static JSONObject blankProfile(String userID){
		 return new JSONObject()
			.put("hydarUsername","hydar")
			.put("nkUsername","hydar")
			.put("userID",JSONObject.NULL)
			.put("hydarUserID",JSONObject.NULL)
			.put("nkToken",JSONObject.NULL)
			.put("hydarToken",JSONObject.NULL)
			.put("avatar","nk-monkey.png")
			.put("clan",11)
			.put("timeCreated", System.currentTimeMillis())
			.put("ap",0)
			.put("nkoins",0)
			.put("hcoins",0)
			.put("currencies", new JSONObject());
	 }
	 public static JSONObject blankSave(){
		 return new JSONObject()
			.put("gcash",JSONObject.NULL)
			.put("data",new JSONObject())
			.put("transid",-1)
			.put("active",1.0d)
			.put("glevel",JSONObject.NULL)
			.put("gxp",JSONObject.NULL)
			.put("gnum",JSONObject.NULL);
	 }
 }
 %>