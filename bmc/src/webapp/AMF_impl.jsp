<%@page import="java.util.Objects"%>
<%@page import="xyz.hydar.bmc.AMFService"%>
<%@page import="xyz.hydar.bmc.Profile"%>
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
	public static boolean DO_NK_AUTH=true;
	private final ObjectStore store;
	static volatile Map<Integer, Integer> neo_store = null;
	static final JSONObject nk_store = new JSONObject();
	static volatile Map<String, Map<Integer, JSONObject>> nk_store_map = null;
	static final JSONObject nk_ach = new JSONObject();
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
				for(String g: Profile.games){
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
				for(var g: Profile.games)
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

	%>
	<%-- PROFILE --%>
	<%!
	public JSONObject getKoins(String userID, String token){
		verifyNK(userID, token);//so invalid token warning can happen early
		JSONObject profile = Profile.get(userID);
		return new JSONObject(2)
				.put("koins",(double)profile.getInt("nkoins"))
				.put("points",(double)profile.getInt("ap"));
	}
	
	public List<Object> buyNeoItems(String userID, String token, String game, List<?> items, Context ctx){
		if(! Profile.games.contains(game) || !currID.containsKey(game))
			return List.of("An error occurred");
		verifyNK(userID, token);
		List<Object> res = new ArrayList<>();
		List<Object> newItems = new ArrayList<>();
		Map<Integer,Integer> itemsToAdd = new HashMap<>();
		Profile.update(userID, x->{
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
			res.add(cur.opt(game));
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


	public List<Object> getInventory(String userID, String token, String game, String username,  Context context){
		verifyNK(userID, token);//so invalid token warning can happen early
		if(! Profile.games.contains(game) || !currID.containsKey(game))
			return List.of("An error occurred");
		Profile.update(userID, x->{
			return token.startsWith("hyd") ? x : x.put("nkUsername", username);
		});
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
		if(! Profile.games.contains(game) || !currID.containsKey(game))
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
		if(! Profile.games.contains(game) || !currID.containsKey(game))
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
		if(! Profile.games.contains(game) || !currID.containsKey(game))
			return new JSONObject();
		verifyNK(userID, token);
		return new JSONObject()
				.put("currid", currID.get(game))
				.put("currency",
					Profile.get(userID).getJSONObject("currencies").optInt(game)
				);
	}
	public JSONObject getClan(String userID){
		int clan = Profile.get(userID).optInt("clan", 11);
		return new JSONObject(2).put("clan",Profile.clans.get(clan)).put("id",clan);
	}
	public JSONObject getClanV2(String userID){
		int clan = Profile.get(userID).optInt("clan", 11);
		return new JSONObject(2).put("name",Profile.clans.get(clan)).put("id",clan);
	}
	public JSONObject getAvatar(String userID){
		return new JSONObject(1).put("avatar",
				Profile.get(userID).optString("avatar", "nk-monkey.png")
			);
	}
	public Object setClan(String userID, String token, int clan){
		verifyNK(userID, token);
		Profile.update(userID,x->x.put("clan",clan));
		return null;
	}
	public Object setAvatar(String userID, String token, String avatar){
		verifyNK(userID, token);
		if(!Profile.avatars.matcher(avatar).matches())
			return null;
		Profile.update(userID,x->x.put("avatar",avatar));
		return null;
	}
	public JSONObject getCurrency(String userID, String token, String game, double amount, String source, String message){
		if(! Profile.games.contains(game))
			return new JSONObject(1).put("bal",0);
		verifyNK(userID, token);
		
		return new JSONObject(1).put("bal",
			Profile.update(userID, x->{
				var cur = x.getJSONObject("currencies");
				cur.put(game, cur.optInt(game) + (int)amount);
				return x;
			}).getJSONObject("currencies").optInt(game)
		);
	}
	public void verifyNK(String userID, String token){
		if(DO_NK_AUTH) Profile.verifyNK(userID, token);
		else if(token.length()<30)throw new AMFService.NKVerifyException();
	}

	public static JSONArray setAchievement(String userID, String token, String game, double ach_id, double perc, Context ctx){
		if(!Profile.games.contains(game))return null;
		Path achDataPath = Path.of(ctx.getRealPath("/amf_data/ach"));
		return Profile.setAchievement(userID, token, game, ach_id, perc, achDataPath);
	}
	public static JSONArray getMyAchievements(String game, String userID,Context ctx){
		if(!Profile.games.contains(game))return null;
		Path achDataPath = Path.of(ctx.getRealPath("/amf_data/ach"));
		return Profile.getMyAchievements(game, userID, achDataPath);
	}

	%>
	<%-- SAVE --%>
	<%!
	private JSONObject updateSave(String userID, String game, UnaryOperator<JSONObject> update){
		if(! Profile.games.contains(game))
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
			int hTID = (int)x.optJSONObject("save", new JSONObject()).optDouble("transid");
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
			var save = x.optJSONObject("save", new JSONObject());
			int hTID = (int)save.optDouble("transid", -1d);
			int hOffset = x.optInt("offset");
			//TODO: does hOffset need to be used at all? the client will use the lower one
			if((save==null || save == JSONObject.NULL || transid >= (hTID /*+ hOffset*/)) 
					//&& !Objects.equals(save.get("data"), data.get("data"))
					){
				x.put("save", new JSONObject(data).put("transid", Math.max(transid, hTID+1)))
					.put("lastSaved", System.currentTimeMillis());
			}
			return x;
		}).getJSONObject("save").getDouble("transid");
	}

}
 %>
 <%!
 
 %>