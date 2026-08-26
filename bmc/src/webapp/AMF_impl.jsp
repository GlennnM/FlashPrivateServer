<%@page import="java.util.Set"%>
<%@page import="java.util.stream.Collectors"%>
<%@page import="java.nio.file.Files"%>
<%@page import="java.util.concurrent.atomic.LongAdder"%>
<%@page import="java.util.function.UnaryOperator"%>
<%@page import="xyz.hydar.bmc.Util"%>
<%@page import="xyz.hydar.bmc.AMFService.NKVerifyException"%>
<%@page import="xyz.hydar.bmc.ByteAMF"%>
<%@page import="xyz.hydar.bmc.AMFBodies"%>
<%@page import="xyz.hydar.bmc.FileObjectStore"%>
<%@page import="java.util.HashMap"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.util.Map"%>
<%@page import="java.nio.file.Path"%>
<%@page import="java.io.IOException"%>
<%@page import="xyz.hydar.bmc.ObjectStore"%>
<%@page import="java.util.regex.Pattern"%>
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
<%-- 
WIP AMF gateway. make sure to include AMF_utils.jsp and openamf & json-java JARs. 
should connect to database or maybe object storage for accounts
for a list of their commands and return types see 'future_stuff.txt'
--%>
<%
//TODO: is desynced from bmc
//TODO: hydar account
//TODO: consec logins/dailies
//TODO: better b64 storage
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
	static volatile Map<String, Map<Integer, JSONObject>> nk_store_map = null;
	static final JSONObject nk_ach = new JSONObject();
	static final Pattern avatars = Pattern.compile("[\\w\\-.]*");
	static final List<String> clans = List.of("Black Cobras","Blue Wolves", "Dark Matter","Falcons","Iron Phoenix","Night Jackals","Red Storm","Scorpions","Shining Blade","The Watchers","Thunderbolts","White Tigers","XIII","Scorpions","Scorpions","Scorpions");
			
	static final Set<String> games = Set.of("Battle Blocks Defense","Battle Panic","Battles","BSM2","BTD4","BTD5","Fortress: Destroyer","MonkeyCity","SAS TD","SAS3","SAS4","Tower Keepers");
	static final Map<String, Integer> currID = Map.of("BTD5", 1, "SAS TD", 2, "Battles", 5, "BSM2", 6, "MonkeyCity", 7, "SAS4", 9);
	public AMFImpl(ObjectStore store){
		this.store = store;
	}
	%>
	<%-- STORE/ACHIEVEMENTS --%>
	<%!
	public int getNeoCost(int id, Context ctx){
		synchronized(nk_store){//intended
			if(neo_store == null){
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
		return neo_store.getOrDefault(id, Integer.MIN_VALUE);
	}
	public JSONArray getStore(String game, Context ctx){
		if(nk_store.isEmpty()){
			synchronized(nk_store){
				for(String g: games){
					try{
						Path f = Path.of(ctx.getRealPath("/amf_data/store/" + g.replace(":","") +".json"));
						nk_store.put(g, new JSONArray(Files.readString(f)));
					}catch(IOException ioe){
						ioe.printStackTrace();
					}
				}
			}
		}
		return nk_store.getJSONArray(game);
	}
	public Map<Integer, JSONObject> getStoreMap(String game, Context ctx){
		if(nk_store_map == null){
			synchronized(nk_ach){
				Map<String, Map<Integer,JSONObject>> tmpMap = new HashMap<>();
				for(var g: games)
					tmpMap.put(g, 
						Util.jStream(getStore(g, ctx))
							.collect(
								Collectors.toMap(x->x.getInt("id"),x->x)
							)
					);
				nk_store_map = Map.copyOf(tmpMap);
			}
		}
		return nk_store_map.get(game);
	}
	public JSONArray getAchievements(String game, Context ctx){
		if(nk_ach.isEmpty()){
			synchronized(nk_ach){
				for(String g: games){
					try{
						Path f = Path.of(ctx.getRealPath("/amf_data/ach/" + g.replace(":","") +".json"));
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
		}//IO.println(j);
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
		JSONObject profile = getProfile(userID);
		return new JSONObject(2)
				.put("koins",(double)profile.getInt("nkoins"))
				.put("points",(double)profile.getInt("ap"));
	}
	
	public List<Object> buyNeoItems(String userID, String token, String game, List<?> items, Context ctx){
		if(!games.contains(game) || !currID.containsKey(game))
			return List.of("An error occurred");
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


	public List<Object> getInventory(String userID, String token, String game, Context context){
		verifyNK(userID, token);//so invalid token warning can happen early
		if(!games.contains(game) || !currID.containsKey(game))
			return List.of("An error occurred");
		verifyNK(userID, token);
		List<Object> res = new ArrayList<>();
		//get items from store table
		var store = getStoreMap(game, context);
		
		var inv = getSave(userID, game)
				.optJSONObject("inventory");
		if (inv == null || inv == JSONObject.NULL) return List.of();
		inv.toMap()
		.forEach((k,v)->
			res.add(
				new JSONObject(store.get(Integer.parseInt(k)).toString())
					.put("quantity",v)
				)
		);
		return res;
	}
	public void importInventory(String userID, String token, String game, Map<?,?> inventory){
		verifyNK(userID, token);//so invalid token warning can happen early
		updateSave(userID, game, x->{
			inventory.keySet().stream().mapToInt(q->Integer.parseInt((String)q) + (int)(double)inventory.get(q)).sum();
			x.put("inventory", inventory);
			return x;
		});
	}
	public List<Object> getNeoInventory(String userID, String token, String game){
		if(!games.contains(game) || !currID.containsKey(game))
			return List.of("An error occurred");
		verifyNK(userID, token);
		List<Object> res = new ArrayList<>();
		getSave(userID, game)
				.getJSONObject("neoInventory")
				.toMap()
				.forEach((k,v)->res.add(new JSONObject(2).put("id",k).put("quantity",v)));
		return res;
	}
	public JSONObject buyNeoItems_v2(String userID, String token, String game, List<?> items, Context ctx){
		var res = buyNeoItems(userID, token, game, items, ctx);
		for(var o: res)
			if(o instanceof List<?> newItems){
				for(var item: newItems)
					if(item instanceof JSONObject j)
						j.put("uuid","").put("tag","");
				return new JSONObject(2).put("success",true).put("items",res);
			}

		return new JSONObject(1).put("success",false);
	}
	public boolean consumeNeoPrem_v2(String userID, String token, String game, String uuid){
		if(!games.contains(game) || !currID.containsKey(game))
			return false;
		verifyNK(userID, token);
		//wipe inv since this only happens in bmc, where the "neo prems" are always consumed instantly
		updateSave(userID, game, x->x.put("neoInventory", new JSONObject()));
		return true;
	}
	public JSONObject getNeoInventory_v2(String userID, String token, String game){
		var inv = getNeoInventory(userID, token, game);
		for(var item: inv)
			if(item instanceof JSONObject j)
				j.put("uuid","").put("tag","");
		return new JSONObject().put("success",true).put("items",inv);
	}
	public JSONObject getBalance(String userID, String token, String game){
		if(!games.contains(game) || !currID.containsKey(game))
			return new JSONObject();
		verifyNK(userID, token);
		return new JSONObject()
				.put("currid", currID.get(game))
				.put("currency",
					getProfile(userID).getJSONObject("currencies").optInt(game)
				);
	}
	public JSONObject getClan(String userID){
		int clan = getProfile(userID).optInt("clan", 11);
		return new JSONObject(2).put("clan",clans.get(clan)).put("id",clan);
	}
	public JSONObject getClanV2(String userID){
		int clan = getProfile(userID).optInt("clan", 11);
		return new JSONObject(2).put("name",clans.get(clan)).put("id",clan);
	}
	public JSONObject getAvatar(String userID){
		return new JSONObject(1).put("avatar",
				getProfile(userID).optString("avatar", "nk-monkey.png")
			);
	}
	public Object setClan(String userID, String token, int clan){
		verifyNK(userID, token);
		updateProfile(userID,x->x.put("clan",clan));
		return null;
	}
	public Object setAvatar(String userID, String token, String avatar){
		verifyNK(userID, token);
		if(!avatars.matcher(avatar).matches())
			return null;
		updateProfile(userID,x->x.put("avatar",avatar));
		return null;
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
			var res = update.apply(x);
			return res == FileObjectStore.UNCHANGED ? x : res;
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
			return new JSONObject().put("save",JSONObject.NULL);
		return store.update(List.of("amf", userID, game, "save"),x->{
			if(x==null){
				x = new JSONObject().put("save", JSONObject.NULL)
						.put("neoInventory", new JSONObject())
						.put("inventory", JSONObject.NULL)
						.put("lastSaved", System.currentTimeMillis());
			}
			var res = update.apply(x);
			return res == FileObjectStore.UNCHANGED ? x : res;
		});
	}
	public JSONObject getProfile(String userID){
		return updateProfile(userID, x->FileObjectStore.UNCHANGED);
	}
	public JSONObject getSave(String userID, String game){
		return updateSave(userID, game, x->FileObjectStore.UNCHANGED);
	}
	public Object getSaveData(String userID, String game){
		return game.equals("MonkeyCity") ? null : getSave(userID, game).get("save");
	}
	public Double setSaveOffset(String userID, String token, String game, int nkid){
		verifyNK(userID, token);
		return updateSave(userID, game, x->{
			//account for nk only increasing id by 1 on resync
			int hTID = (int)x.getJSONObject("save").optDouble("transid");
			int hOffset = hTID - nkid;
			x.getJSONObject("save").put("transid", nkid);
			if(hOffset!=0)
				x.put("offset", hTID - nkid);
			return x;
		}).getJSONObject("save").getDouble("transid");
	}
	public Double saveData(String userID, String token, String game, Map<?,?> data){
		int transid = (int) (double) data.get("transid");//xd
		verifyNK(userID, token);
		return updateSave(userID, game, x->{
			int hTID = (int)x.getJSONObject("save").optDouble("transid", -1d);
			int hOffset = x.optInt("offset");
			//TODO: does hOffset need to be used at all? the client will use the lower one
			if(x.get("save")==null || x.get("save") == JSONObject.NULL || transid > (hTID /*+ hOffset*/)){
				x.put("save", new JSONObject(data))
					.put("lastSaved", System.currentTimeMillis());
			}
			return x;
		}).getJSONObject("save").getDouble("transid");
	}

}
 %>
 <%!
 
 %>