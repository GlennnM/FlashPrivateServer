<%@page import="java.util.Comparator"%>
<%@page import="java.util.concurrent.ThreadLocalRandom"%>
<%@page import="java.util.UUID"%>
<%@page import="java.util.concurrent.atomic.LongAdder"%>
<%@page import="javax.xml.crypto.Data"%>
<%@page import="java.util.concurrent.atomic.AtomicBoolean"%>
<%@page import="java.util.Scanner"%>
<%@page import="java.util.Spliterators"%>
<%@page import="java.util.stream.Collectors"%>
<%@page import="java.util.stream.Stream"%>
<%@page import="java.util.stream.IntStream"%>
<%@page import="java.util.function.UnaryOperator"%>
<%@page import="org.json.JSONArray"%>
<%@page import="java.util.zip.CRC32"%>
<%@page import="java.io.File"%>
<%@page import="java.util.concurrent.ConcurrentMap"%>
<%@page import="java.util.Base64"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.util.List"%>
<%@page import="java.io.IOException"%>
<%@page import="java.nio.file.Files"%>
<%@page import="java.nio.file.Path"%>
<%@page import="java.util.concurrent.ConcurrentHashMap"%>
<%@page import="java.util.concurrent.atomic.AtomicReference"%>
<%@page import="java.util.Objects"%>
<%@page import="java.net.URI"%>
<%@page import="java.net.http.HttpClient.Redirect"%>
<%@page import="java.net.http.HttpResponse"%>
<%@page import="java.net.http.HttpResponse.BodyHandlers"%>
<%@page import="java.net.http.HttpRequest.BodyPublishers"%>
<%@page import="java.net.http.HttpRequest"%>
<%@page import="java.net.http.HttpClient"%>
<%@page import="org.json.JSONObject"%>
<%@page import="static java.nio.charset.StandardCharsets.UTF_8"%>
<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ page
	import="javax.sql.*,javax.naming.InitialContext,javax.servlet.http.*,javax.servlet.*"%>
<%-- BMC DATA --%>
<%!
static{
	//VERY DUMB THING TO DO AN UPDATE THAT SHOULD HAPPEN ANYWAYS BUT isnt implemented FIXME:remove
	var hydar = xyz.hydar.ee.Hydar.hydars.get(0);
	if(hydar.ee.ctx.getAttribute("done")==null){
		new Thread(()->{
			try{
				Thread.sleep(100);
				hydar.ee.ctx.setAttribute("done", 1);
				hydar.ee.compile(Path.of("../src/webapp/BMC.jsp"));
				hydar.ee.ctx.setAttribute("done", null);
			}catch(InterruptedException e){
				Thread.currentThread().interrupt();
			}
		}).start();
	}
}
	static final long CT_QUEUE_TIME = 24L * 3600 * 1000 * 3;//time a new CT is joinable for
	/**does stuff like putCity(0,{},..)*/
	public static class BMCData{
		private final ObjectStore store;
		public BMCData(ObjectStore store){
			this.store = store;
		}
		%>
		<%-- CITIES --%>
		<%!
		public JSONObject getCityList(int userID){
			JSONArray ret = new JSONArray();
			for(int i=0;i<=1;i++){
				var info = getCityThing(userID, i, "info");
				if(info!=null){
					JSONObject newThing = new JSONObject();
					newThing
						.put("name", info.get("cityName"))
						.put("level", info.get("level"))
						.put("attacks", new JSONArray())
						.put("index", i);
					ret.put(newThing);
				}
			}
			return new JSONObject()
					.put("success", true)
					.put("cityList", ret);
		}
		public JSONObject getCities(int userID){
			return store.get("monkeyCity", ""+userID, "cities");
		}
		public boolean putCities(int userID, JSONObject payload){
			int cityID = payload.getJSONObject("cityInfo").getInt("cityIndex");
			return putCity(userID, cityID, payload);
			//return store.put(List.of("monkeyCity", ""+userID, "cities"), payload);
		}
		public JSONObject getCity(int userID, int cityID){
			var info = getCityThing(userID,cityID,"info");
			var content = getCityThing(userID,cityID,"content");
			var ct = getCT(userID,cityID);
			return info==null? null: 
				new JSONObject()
					.put("cityInfo",info)
					.put("content",content)
					.put("contestedTerritory", ct)
					.put("success", true);
		}
		//CONVERT TO NEW FORMAT
		public boolean putCity(int userID, int cityID, JSONObject payload){
			
			var info = payload.getJSONObject("cityInfo");
			var newInfo = new JSONObject()
					.put("index",cityID)
					.put("level",info.opt("cityLevel"))
					.put("cityName",info.get("name"))
					.put("xp",info.optInt("xp"))
					.put("xpDebt",info.optInt("xpDebt"))
					.put("userName",payload.get("userName"))
					.put("userClan",payload.get("userClan"))
					.putOpt("pacifistExpiresAt",payload.opt("pacifistExpiresAt"))
					;
			var newContent = new JSONObject()
					.put("tiles",payload.get("tiles"))
					.put("cityResources",payload.get("cityResources"))
					.put("worldSeed",payload.get("worldSeed"))
					.put("terrainData",payload.get("terrainData"))
					.put("cityQuests",payload.get("quests"));
			
			return store.put(List.of("monkeyCity", ""+userID, "cities", ""+cityID, "info"), newInfo)
				&& store.put(List.of("monkeyCity", ""+userID, "cities", ""+cityID, "content"), newContent);
			//return store.put(List.of("monkeyCity", ""+userID, "cities", ""+cityID), payload);
		}
		public JSONObject getCore(int userID){
			return store.get(List.of("monkeyCity", ""+userID, "core"), Util.BLANK_CORE);
		}
		public boolean updateCore(int userID, JSONObject payload) {
			return store.update(List.of("monkeyCity", "" + userID, "core"), x -> Util.mergeCore(x, payload)) != null;
		}

	
		public boolean updateContent(int userID, int cityID, JSONObject payload) {
			return store.update(List.of("monkeyCity", "" + userID, "cities", "" + cityID, "content"),
					x -> Util.mergeContent(x, payload)) != null;
		}
	
		public boolean updateInfo(int userID, int cityID, JSONObject payload) {
			return store.update(List.of("monkeyCity", "" + userID, "cities", "" + cityID, "info"),
					x -> Util.mergeInfo(x, payload)) != null;
		}

		public JSONObject getCityThing(int userID, int cityID, String thing) {
			return store.get("monkeyCity", "" + userID, "cities", "" + cityID, thing);
		}

		public boolean putCityThing(int userID, int cityID, String thing, JSONObject payload) {
			return switch (thing) {
			case "content" -> updateContent(userID, cityID, payload);
			case "info" -> updateInfo(userID, cityID, payload);
			default -> store.put(List.of("monkeyCity", "" + userID, "cities", "" + cityID, thing), payload);
			};
		}
		%>
		<%-- CRATES --%>
		<%!
		public boolean useCrate(int userID){
			return modifyCrates(userID, -1);
		}
		public boolean modifyCrates(int userID, int n){
			return store.update(List.of("monkeyCity", ""+userID, "core"),
				core->{
					var crates = core.optJSONObject("crates");
					if(crates==null)
						crates = Util.DEFAULT_CRATES();
					core.put("crates", crates.put("own",crates.optInt("own") + n));
					return core;
				}) != null;
		}
		public JSONObject getCrates(int userID){
			var crates =  getCore(userID).optJSONObject("crates");
			return crates==null ? Util.DEFAULT_CRATES() : crates;
		}
		%>
		<%-- CT - ROOMS --%>
		<%!
		//if main city data (/cities/x) contains ct data, this is used, otherwise it will check /history
		public JSONObject getCT(int userID, int cityID){
			JSONObject ret;//extracted room object
			var room = getOrArchiveRoomInfo(userID, cityID);
			if(room==null || room.optString("roomID").isEmpty()){
				return null;
			}else{
				String roomID = room.getString("roomID");
				ret = store.update(List.of("monkeyCity","contest",""+cityID,"rooms", roomID), r->{
					CTUtil.updateDurations(r);
					return r;
				});
			}
			return CTUtil.hideLeaderDuration(ret)
					.getJSONObject("contestedTerritory");
		}

		//TODO: make a consistent 'now' that is passed to findLeader and stuff
		public JSONObject joinCT(int userID, int cityID, JSONObject payload) {
			int level = payload.getInt("cityLevel");
			int tier = CTUtil.ctTier(level);
			long now = System.currentTimeMillis();
			JSONObject newRoom;
			String roomID;
			var roomInfo = getOrArchiveRoomInfo(userID, cityID);
			if (roomInfo == null || roomInfo.optString("roomID").isEmpty()) {
				//then check queue
				var ret = new AtomicReference<JSONObject>();
				var retID = new AtomicReference<String>();
				store.update(List.of("monkeyCity", "contest", "" + cityID, "queue"), queue -> {
					//we need to return the entire new queue object, while extracting the new/found room
					if (queue == null)
						queue = new JSONObject();
					var qRoom = queue.optJSONObject("" + tier);
					if (qRoom == null || 
							(now - qRoom.optLong("at")) > CT_QUEUE_TIME ||
							CTUtil.week(qRoom.optLong("at")) != CTUtil.week(now)) {
						//create the room
						ret.setPlain(CTUtil.newCTRoom(level, cityID, payload));
						String newRoomID = ret.getPlain().getJSONObject("contestedTerritory").getString("roomID");
						qRoom = new JSONObject().put("id", newRoomID).put("players", new JSONArray()).put("at",
								System.currentTimeMillis());
						queue.put("" + tier, qRoom);
					}
					retID.setPlain(qRoom.getString("id"));
					JSONArray players = qRoom.getJSONArray("players");
					if(!players.toList().contains(userID))
						players.put(userID);
					qRoom.put("players", players);
					if (players.length() >= 6)
						queue.remove("" + tier);
					//ret.setPlain(newRoom);
					return queue;
				});
				//if a new room was created, store it before adding the player
				roomID = retID.getPlain();
				if (ret.getPlain() != null) {//if a new room was made
					store.put(List.of("monkeyCity", "contest", "" + cityID, "rooms", roomID), ret.getPlain());
				} 
				
			} else {
				roomID = roomInfo.getString("roomID");
			}
			newRoom = addCTPlayerToRoom(userID, cityID, roomID, payload);
			CTUtil.updateDurations(newRoom);
	
			// user -> room id
			store.put(List.of("monkeyCity", "" + userID, "contest", "" + cityID),
					new JSONObject().put("roomID", roomID).put("at", now));
			return CTUtil.hideLeaderDuration(newRoom);
	
		}
		
		public JSONObject addCTPlayerToRoom(int userID, int cityID, String roomID, JSONObject payload){
			var ret = store.update(List.of("monkeyCity","contest",""+cityID,"rooms", roomID), room->{
				//we need to return the entire new queue object, while extracting the new/found room
				CTUtil.addCTPlayer(room, userID, payload);
				CTUtil.updateDurations(room);
				return room;
			});
			return CTUtil.hideLeaderDuration(ret);
		}
		%>
		<%-- CT - SCORES --%>
		<%! 
		
		public JSONObject updateCTScore(int userID, int cityID, String roomID, JSONObject payload){
			var ret = store.update(List.of("monkeyCity","contest",""+cityID,"rooms", roomID), room->{
				//we need to return the entire new queue object, while extracting the new/found room
				int score = payload.optInt("score");
				long time = payload.optLong("time");
				boolean pb = payload.optBoolean("isPersonalBest");
				double lootTimeOffset = payload.optDouble("lootTimeOffset");
				var ct = room.getJSONObject("contestedTerritory");
				int minRounds = ct.getInt("minRounds");
				long startTime = ct.optLong("startTime");
				if(CTUtil.week(startTime) != CTUtil.week(time)){
					return room;
				}
				var cities = ct.getJSONArray("cities");
				if(Util.jStream(cities).anyMatch(x->x.getInt("userID") == userID)){// verify if in ct room
					var scores = ct.getJSONObject("score");
					var myScore = scores.optJSONObject(""+userID, new JSONObject());
					int leader = CTUtil.findLeader(scores, minRounds);
					
					/**
					-->was not winner?
					---->can become winner?
					------>update time, current, durationTime
					------>for previous winner: add current duration into durationWithoutCurrent, reset time and current
					---->can't become winner?
					------>update current, don't update time or anything else
					-->already winner?
					---->if <current do nothing
					---->update time, current
					---->add current duration into durationWithoutCurrent
					---->don't update durationTime
					*/
					CTUtil.rollExtraTime(scores, startTime, minRounds);
					if(leader != userID){
						if(CTUtil.becomesLeader(scores, payload, minRounds)){
							System.out.println("NL -> L");
							ct.put("lastLootTime", time);
							ct.put("lootTimeOffset", lootTimeOffset);
							if(leader >= 0){
								System.out.println("Updating old leader "+leader);
								var oldLeader = scores.getJSONObject(""+leader);
								long durationWithoutCurrent = (time - oldLeader.getLong("time"))
										+ oldLeader.optLong("durationWithoutCurrent");
								oldLeader
									.put("durationWithoutCurrent", durationWithoutCurrent)
									.put("current", 0)
									.put("durationTime", 0)
									.put("time", 0);
							}
							myScore
								.put("durationTime", time)
								.put("time", time);
						}else{
							System.out.println("NL -> NL");
							//already handled below...
						}
					}else{
						System.out.println("L -> L");
						//get more time, but leave durationtime as is
						if(score > myScore.optInt("current")){
							System.out.println("L -> LL");
							long previousDuration = time - myScore.getLong("time");
							long durationWithoutCurrent = previousDuration + myScore.optLong("durationWithoutCurrent");
							ct.put("lootTimeOffset", lootTimeOffset);
							myScore
								.put("time", time)
								.put("durationTime", myScore.optLong("durationTime"))
								.put("durationWithoutCurrent", durationWithoutCurrent);//???????
							//current set below
						}
						else //do nothing
							score = myScore.optInt("current");
					}
					//ct.put("lootTimeOffset", lootTimeOffset)
					scores.put(""+userID, myScore);
					CTUtil.updateDurations(scores, startTime, minRounds);
					myScore
						.put("best",Math.max(score, myScore.optInt("best")))
						.put("current", score)
						.put("durationWithoutCurrent", myScore.optLong("durationWithoutCurrent"))
						.put("durationTime", myScore.optLong("durationTime"))
						.put("duration", myScore.optLong("duration"))
						.put("time", myScore.optLong("time"));	
				}
				return room;
			});
			return CTUtil.hideLeaderDuration(ret);
		}
		
		public JSONObject getCTScores(int userID, int cityID, String roomID){
			var room = store.get("monkeyCity","contest",""+cityID,"rooms", roomID);
			var ct = room.getJSONObject("contestedTerritory");
			var cities = ct.getJSONArray("cities");
			if(Util.jStream(cities).anyMatch(x->x.getInt("userID") == userID)){
				var scores = store.get("monkeyCity","contest",""+cityID,"rooms", roomID)
						.getJSONObject("contestedTerritory")
						.getJSONObject("score");
				//note - doesn't actually perform store update(just need client to see durations)
				CTUtil.updateDurations(room);
				return CTUtil.hideLeaderDuration(room);
			}
			return new JSONObject();
		}
		//lootTimeOffset is a 'claim reward', we set it and reset it after 1 claim, claiming from self included
		public JSONObject lootCT(int userID, int cityID, String roomID, JSONObject payload){
			var ret = store.update(List.of("monkeyCity","contest",""+cityID,"rooms", roomID), room->{
				var ct = room.getJSONObject("contestedTerritory");
				var cities = ct.getJSONArray("cities");
				//TODO: check if ended??
				long startTime = ct.optLong("startTime");
				if(Util.jStream(cities).anyMatch(x->x.getInt("userID") == userID)){// verify if in ct room
					ct.put("lastLootTime", payload.getLong("lootTime"))
					.put("lootTimeOffset", 0);
				}
				CTUtil.updateDurations(room);
				return room;
			});
			return CTUtil.hideLeaderDuration(ret);
		}
		%>
		<%-- CT - HISTORY --%>
		<%! 
		//gets CT room info, updating it to check if it expired first
		public JSONObject getOrArchiveRoomInfo(int userID, int cityID){
			var room = store.get("monkeyCity",""+userID,"contest",""+cityID);
			if(room == null){
				return null;
			} else {
				long at = room.optLong("at");
				if (at != 0 && CTUtil.week(at) != CTUtil.week(System.currentTimeMillis())) {
					JSONObject roomData = store.get("monkeyCity", "contest", "" + cityID, "rooms", room.getString("roomID"));
					CTUtil.updateDurations(roomData);
					var ct = roomData.get("contestedTerritory");
					room.put("history", new JSONObject().put("room",ct));
					room.remove("roomID");
					room.remove("at");
					store.put(List.of("monkeyCity",""+userID,"contest",""+cityID), room);
					//TODO: remove player from room and then delete it when no players left?
				}
			}
			return room;
		}
	
		public JSONObject getCTHistory(int userID, int cityID){
			var room = getOrArchiveRoomInfo(userID, cityID);
			JSONObject history;
			//contains room for previous thing
			//remove on /claim or /close?
			if(room==null || (history=room.optJSONObject("history")) == null){
				return new JSONObject();
			}
			return history;
		}
		
		public JSONObject closeCTHistory(int userID, int cityID, String roomID, String action){
			//action can be claim and close but claim is unused
			store.update(List.of("monkeyCity",""+userID,"contest",""+cityID), info->{
				//NOTE: does not use room id
				info.remove("history");
				return info;
			});
			return new JSONObject();
		}
		
		%>
<%!
	
	}
%>
<%-- Util --%>
<%!
public static class Util{


	private static long key(JSONObject tile) {
		return ((long) tile.getInt("x") << 32) | (tile.getInt("y") & -1L);
	}

	public static JSONObject BLANK_CORE = new JSONObject().put("core",new JSONObject());
	public static Iterable<JSONObject> jIter(JSONArray array) {
		return (Iterable<JSONObject>) (() -> Spliterators.iterator(jStream(array).spliterator()));
	}

	public static Stream<JSONObject> jStream(JSONArray array) {
		return IntStream.range(0, array.length()).mapToObj(array::getJSONObject);
	}


	public static JSONObject mergeContent(JSONObject content, JSONObject update) {
		if (content == null)
			content = new JSONObject();
		var tiles = content.optJSONArray("tiles", new JSONArray());
		var newTiles = update.optJSONArray("tiles", new JSONArray());
		var updateContent = update.optJSONObject("content", new JSONObject());
		for (String key : updateContent.keySet()) {
			if (!key.equals("tiles"))
				content.put(key, updateContent.get(key));
		}
		var tileMap = jStream(tiles).collect(Collectors.toMap(Util::key, x -> x, (x, y) -> y));
		for (var tile : jIter(newTiles)) {
			long key = key(tile);
			var oldTile = tileMap.get(key);
			if (oldTile == null)
				tiles.put(tile);
			else
				oldTile.put("tileData", tile.getString("tileData"));
		}
		content.put("tiles", tiles);
		return content;
	}

	//cityName INDEX LEVEL XP
	public static JSONObject mergeInfo(JSONObject info, JSONObject update) {
		if (info == null)
			info = new JSONObject();
		var change = update.getJSONObject("cityInfoChange");
		if (change != null)
			info.put("cityName", update.get("cityName")).put("level", update.get("cityLevel"))
					.put("xp", info.optInt("xp") + change.optInt("xp"))
					.put("xpDebt", info.optInt("xpDebt") + change.optInt("xpDebt"))
					.put("honour", info.optInt("honour") + change.optInt("honour"));
		return info;
	}

	public static JSONObject mergeCore(JSONObject core, JSONObject update) {
		if (core == null)
			core = new JSONObject();
		for (String topKey : List.of("core", "monkeyKnowledge", "crates")) {
			JSONObject oldCore = core.optJSONObject(topKey, new JSONObject());
			JSONObject newCore = update.optJSONObject(topKey);
			if (newCore != null) {
				for (String key : newCore.keySet()) {
					oldCore.put(key, newCore.get(key));
				}
			}
			core.put(topKey, oldCore);
		}
		return core;
	}

	private static JSONObject DEFAULT_CRATES(){
		return new JSONObject()
				.put("own",0)
				.put("requested", new JSONArray())
				.put("sent", new JSONArray())
				.put("pending", new JSONArray())
				.put("received", new JSONArray());
	}
}
%>
<%-- CTUtil --%>
<%!
public static class CTUtil {

	public static JSONObject newCTRoom(int level, int cityID, JSONObject payload) {
		String roomID = "" + ThreadLocalRandom.current().nextLong();
		var newRoom = new JSONObject().put("contestedTerritory",
				new JSONObject().put("cities", new JSONArray()).put("score", new JSONObject())
						.put("data", payload.get("data")).put("roomID", roomID).put("levelTier", ctTier(level))
						.put("minRounds", ctMinRound(level)).put("lastLootTime", System.currentTimeMillis())
						.put("startTime", System.currentTimeMillis()));
		return newRoom;
	}


	public static void addCTPlayer(JSONObject room, int userID, JSONObject player){
		var cities = room.getJSONObject("contestedTerritory").getJSONArray("cities");
		if(Util.jStream(cities).noneMatch(x->x.getInt("userID") == userID))
			cities
				.put(new JSONObject()
					.put("userName",player.get("userName"))
					.put("userID",userID)
					.put("cityLevel",player.get("cityLevel"))
					.put("cityName",player.get("cityName"))
				);
	}
	
	private static int week(long millis) {
		return (int) ((millis / (1000 * 60 * 60 * 24) - 4) / 7);
	}

	//for cases where bloons retook while someone was leader
	//problem - what if they submitted bad scores during that time???
	public static void rollExtraTime(JSONObject scores, long roomStartTime, int minRounds) {
		long now = System.currentTimeMillis();
		long endOfWeek = (week(roomStartTime) + 1L) * 24 * 3600 * 1000 * 7;
		int leader = findLeader(scores, minRounds);
		scores.keySet().stream().filter(x -> {
			long time = scores.getJSONObject(x).optLong("time");
			return time > 0;
		}).filter(x -> !x.equals("" + leader)).forEach(id -> {
			JSONObject score = scores.getJSONObject("" + id);
			long durationWithoutCurrent = Math.max(24 * 3600 * 1000,
					Math.min(endOfWeek, now) - score.getLong("time")) + score.optLong("durationWithoutCurrent");
			score.put("durationWithoutCurrent", durationWithoutCurrent).put("current", 0).put("time", 0);
		});
	}

	public static void updateDurations(JSONObject room) {
		var ct = room.getJSONObject("contestedTerritory");
		updateDurations(ct.getJSONObject("score"), ct.optLong("startTime"), ct.getInt("minRounds"));
	}
	//when the ct is active, the client would add calculated duration to the regular duration
	//only hidden from the client, the actual durations need that value
	//but add in the time before capture extended
	public static JSONObject hideLeaderDuration(JSONObject room) {
		var scores = room.getJSONObject("contestedTerritory").getJSONObject("score");
		for (String id : scores.keySet()) {
			var score = scores.getJSONObject(id);
			if(score.optLong("time") > 0)
				//why is this a thing
				score.put("duration", (score.optLong("time") - score.optLong("durationTime")));
		}
		return room;
	}
	public static void updateDurations(JSONObject scores, long roomStartTime, int minRounds) {
		long now = System.currentTimeMillis();
		long endOfWeek = (week(roomStartTime) + 1L) * 24 * 3600 * 1000 * 7;
		for (String id : scores.keySet()) {
			JSONObject score = scores.getJSONObject("" + id);
			long time = score.getLong("time");
			long durationTime = score.getLong("durationTime");
			long duration = score.optLong("durationWithoutCurrent") +
			//if time > 0, you were the last leader(even though you would no longer be)
					(time > 0 ? (Math.min(endOfWeek, now) - Math.max(time, durationTime)) : 0);
			score.put("time", time).put("durationTime", durationTime).put("duration", duration);
		}
	}

	//assumes time was already updated
	//now need a fn to determine if a new score would become the leader
	public static int findLeader(JSONObject scores, int minRounds) {
		long now = System.currentTimeMillis();
		return scores.keySet().stream().filter(x -> {
			long time = scores.getJSONObject(x).optLong("time");
			return time > 0 && (now - time) < 24 * 3600 * 1000;
		}).filter(x -> {
			int round = scores.getJSONObject(x).optInt("current");
			return round >= minRounds;
		}).sorted(Comparator.comparing(x -> -scores.getJSONObject(x).optInt("current"))).mapToInt(Integer::parseInt)
				.findFirst().orElse(-1);
	}
	
	//assumes newScore was not previously the leader
	public static boolean becomesLeader(JSONObject scores, JSONObject newScore, int minRounds) {
		int score = newScore.optInt("score");
		long time = newScore.optLong("time");
		long now = System.currentTimeMillis();
		if (score < minRounds || (now - time) >= 24 * 3600 * 1000 || week(now) != week(time))
			return false;
		int leader = findLeader(scores, minRounds);
		return leader < 0 || scores.getJSONObject("" + leader).optInt("current") < score;
	}

	public static int ctMinRound(int level) {
		int tier = ctTier(level);
		/**TODO: SET TO 1 FOR TESTING*/
		return 0 == 0 ? 1 : switch (tier) {
		case 1, 2, 3, 4 -> 2 + tier * 4;
		case 5 -> 22;
		default -> 24 + (tier - 6); //6-9
		};
	}

	public static int ctTier(int level) {
		return Math.min(9, (level - 5) / 4 + 1);
	}
}
%>
<%-- for folding - Window > Preferences >Web> JSP Files > Editor > templates > declaration > type = 'All JSP' --%>
<%-- ObjectStore --%>

<%!
	/**stuff like put(url, ..., ...)*/
	public static interface ObjectStore {
		public default JSONObject get(String... url) {
			return get(String.join("/", url));
		}
	
		public default JSONObject get(Iterable<String> url) {
			return get(String.join("/", url));
		}
	
		public default JSONObject get(String url, JSONObject fallback) {
			var ret = get(url);
			return ret == null ? fallback : ret;
		}
	
		public default JSONObject get(Iterable<String> url, JSONObject fallback) {
			var ret = get(url);
			return ret == null ? fallback : ret;
		}
	
		public default boolean put(Iterable<String> url, JSONObject payload) {
			return put(String.join("/", url), payload);
		}
	
		public default JSONObject update(Iterable<String> url, UnaryOperator<JSONObject> update) {
			return update(String.join("/", url), update);
		}
	
		public default boolean delete(String... url) {
			return delete(String.join("/", url));
		}
	
		public default boolean delete(Iterable<String> url) {
			return delete(String.join("/", url));
		}

		public List<String> list();
		
		public JSONObject get(String url);
	
		public boolean has(String url);
	
		public boolean delete(String url);
	
		public boolean put(String url, JSONObject payload);
	
		public default JSONObject update(String url, UnaryOperator<JSONObject> update) {
			var input = get(url);
			put(url, update.apply(get(url)));
			return input;
		}
	}
%>
<%-- FileObjectStore --%>
<%!
/**
uses b64's of the urls so it is always in the same folder
*/
public static class FileObjectStore implements ObjectStore {
	private final Path root;
	//we lock using this, without ever adding to it
	private final ConcurrentMap<String, Void> urlLock = new ConcurrentHashMap<>(1024 * 1024);

	public FileObjectStore(Path root) throws IOException {
		if (!Files.exists(root))
			Files.createDirectories(root);
		if (!Files.isDirectory(root))
			throw new IllegalArgumentException("Not a dir: " + root);
		this.root = root;
	}
	//TODO: web formatting to make this explorable since everything is json
	public List<String> dump() {
		try {
			return Files.walk(root, 2).filter(Files::isRegularFile)//.peek(System.out::println)
					.map(x -> {
						try {
							return x.getParent().getFileName().toString() + "->"
									+ new String(Base64.getDecoder().decode(x.getFileName().toString().trim()), UTF_8)
									+ " -> " + Files.readString(x);
						} catch (IOException e) {
							return "";
						}
					}).toList();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	@Override
	public List<String> list() {
		try {
			return Files.walk(root, 2).filter(Files::isRegularFile)//.peek(System.out::println)
					.map(x -> new String(Base64.getDecoder().decode(x.getFileName().toString().trim()), UTF_8))
					.sorted()
					.toList();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	public Path map(String url) {
		String newURL = Base64.getEncoder().encodeToString(url.getBytes(UTF_8));
		CRC32 crc = new CRC32();
		crc.update(url.getBytes(UTF_8));
		int bucket = (int) (crc.getValue()) & 0x7ff;
		return root.resolve(new StringBuilder().append(bucket).append(File.separatorChar).append(newURL).toString());
	}

	@Override
	public JSONObject get(String url) {
		Path path = map(url);
		AtomicReference<JSONObject> holder = new AtomicReference<>();//for stupid lambda thing
		urlLock.compute(path.toString(), (k, v) -> {
			try {
				holder.setPlain(new JSONObject(Files.readString(path)));
			} catch (IOException e) {
				holder.setPlain(null);
			}
			return null;
		});
		return holder.getPlain();
	}

	@Override
	public boolean has(String url) {
		Path path = map(url);
		AtomicReference<Boolean> holder = new AtomicReference<>();//for stupid lambda thing
		urlLock.compute(path.toString(), (k, v) -> {
			holder.setPlain(Files.exists(path));
			return null;
		});
		return holder.getPlain();
	}

	@Override
	public boolean put(String url, JSONObject payload) {
		Path path = map(url);
		AtomicReference<Boolean> holder = new AtomicReference<>();//for stupid lambda thing
		urlLock.compute(path.toString(), (k, v) -> {
			try {
				Files.createDirectories(path.getParent());
				Files.writeString(path, payload.toString());
				holder.setPlain(true);
			} catch (IOException e) {
				holder.setPlain(false);
			}
			return null;
		});
		return holder.getPlain();
	}

	@Override
	public JSONObject update(String url, UnaryOperator<JSONObject> update) {
		Path path = map(url);
		AtomicReference<JSONObject> holder = new AtomicReference<>();//for stupid lambda thing
		System.out.println("-->" + path);
		urlLock.compute(path.toString(), (k, v) -> {
			try {
				JSONObject input = Files.exists(path) ? new JSONObject(Files.readString(path)) : null;
				Files.createDirectories(path.getParent());
				JSONObject output = update.apply(input);
				Files.writeString(path, output.toString());
				holder.setPlain(output);
			} catch (IOException e) {
				holder.setPlain(null);
			}
			return null;
		});
		return holder.getPlain();
	}

	@Override
	public boolean delete(String url) {
		Path path = map(url);
		AtomicReference<Boolean> holder = new AtomicReference<>();//for stupid lambda thing
		urlLock.compute(path.toString(), (k, v) -> {
			try {
				Files.delete(path);
				holder.setPlain(true);
			} catch (IOException e) {
				holder.setPlain(false);
			}
			return null;
		});
		return holder.getPlain();
	}
}
//public static class DBObjectStore ?!?!!
//public static class S3ObjectStore ?!???!?!?!?!?!
%>