<%@page import="xyz.hydar.bmc.FileObjectStore"%>
<%@page import="xyz.hydar.bmc.ObjectStore"%>
<%@page import="java.util.zip.DeflaterOutputStream"%>
<%@page import="java.nio.charset.StandardCharsets"%>
<%@page import="java.util.zip.InflaterInputStream"%>
<%@page import="java.io.ByteArrayInputStream"%>
<%@page import="java.io.ByteArrayOutputStream"%>
<%@page import="java.util.zip.InflaterOutputStream"%>
<%@page import="java.time.ZoneId"%>
<%@page import="java.time.Instant"%>
<%@page import="java.time.LocalDateTime"%>
<%@page import="java.util.Collections"%>
<%@page import="java.util.Arrays"%>
<%@page import="java.util.Set"%>
<%@page import="xyz.hydar.ee.HydarEE.HttpServletRequest"%>
<%@page import="java.util.concurrent.TimeUnit"%>
<%@page import="java.util.concurrent.Executors"%>
<%@page import="java.util.concurrent.ScheduledExecutorService"%>
<%@page import="java.util.Queue"%>
<%@page import="java.util.concurrent.ConcurrentLinkedQueue"%>
<%@page import="java.util.NavigableSet"%>
<%@page import="java.util.concurrent.ConcurrentSkipListSet"%>
<%@page import="java.nio.file.NoSuchFileException"%>
<%@page import="java.io.FileNotFoundException"%>
<%@page import="java.util.Comparator"%>
<%@page import="java.util.concurrent.ThreadLocalRandom"%>
<%@page import="java.util.concurrent.atomic.LongAdder"%>
<%@page import="java.util.concurrent.atomic.AtomicBoolean"%>
<%@page import="java.util.Spliterators"%>
<%@page import="static java.util.stream.Collectors.*"%>
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
				if(Files.exists(Path.of("../src/webapp/BMC.jsp")))
					hydar.ee.compile(Path.of("../src/webapp/BMC.jsp"));
				hydar.ee.ctx.setAttribute("done", null);
			}catch(InterruptedException e){
				Thread.currentThread().interrupt();
			}
		}).start();
	}
}
%><%!
	static final int MAX_CLOSED_ATTACKS = 20;
	static final int BOT_ID = 1;
	static final int MAX_FRIEND_HONOR = Integer.MAX_VALUE; //above this value friend attacks give no honor(default 1500)
	static final long CT_QUEUE_TIME = 24L * 3600 * 1000 * 3;//time a new CT is joinable for
	/**does stuff like putCity(0,{},..)*/
	public static class BMCData{
		private final ObjectStore store;
		private volatile Set<Integer> noScoreUpdate = Set.of();
		public BMCData(ObjectStore store){
			this.store = store;
		}
		//save initial achievements for a user
		public void saveAchIfNew(int userID, JSONArray ach){
			store.update(List.of("monkeyCity",""+userID,"achievements"), (old)->{
				if(old == null){//compact(we only need %)
					return new JSONObject(Util.jStream(ach).collect(toMap(x->x.getInt("id"), x->x.optInt("perc"))));
				}
				return FileObjectStore.UNCHANGED;
			});
		}
		%>
		<%-- CITIES --%>
		<%!
		public JSONObject getCityList(int userID){
			JSONArray ret = new JSONArray();
			for(int i=0;i<=1;i++){
				var info = getCityThing(userID, i, "info");
				if(info!=null){
					JSONObject newThing = new JSONObject(4);
					newThing
						.put("name", info.get("cityName"))
						.put("level", info.get("level"))
						.put("attacks", Util.jStream(getPVPCore(userID, i, true).getJSONArray("attacks"))
								.filter(a->a.getJSONObject("target").getInt("userID") == userID)
								.filter(a->a.getInt("status") < AttackStatus.RESOLVED)
								//.peek(a->a.put("timeLeft", a.getLong("expireAt") - System.currentTimeMillis()))
								.toList()
							)
						.put("index", i);
					ret.put(newThing);
				}
			}
			return new JSONObject(6)
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
				new JSONObject(8)
					.put("cityInfo",info)
					.put("content",content)
					.put("contestedTerritory", ct)
					.put("success", true);
		}
		//CONVERT TO NEW FORMAT
		public boolean putCity(int userID, int cityID, JSONObject payload){
			
			var info = payload.getJSONObject("cityInfo");
			var newInfo = new JSONObject(8)
					.put("index",cityID)
					.put("level",info.opt("cityLevel"))
					.put("cityName",info.get("name"))
					.put("xp",info.optInt("xp"))
					.put("honour",info.optInt("honour"))
					.put("xpDebt",info.optInt("xpDebt"))
					.put("userName",payload.get("userName"))
					.put("userClan",payload.get("userClan"))
					.putOpt("pacifistExpiresAt",payload.opt("pacifistExpiresAt"))
					;
			var newContent = new JSONObject(10)
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
					if(crates==null || !crates.has("sent")){
						var theCrates = Util.DEFAULT_CRATES();
						if(crates!=null)
							theCrates.putOpt("own",crates.opt("own"));
						crates = theCrates;
					}
					
					tryResetCrates(crates);
					core.put("crates", crates.put("own",crates.optInt("own") + n));
					return core;
				}) != null;
		}
		public boolean sendCrates(int userID, JSONArray friendIDs){
			return Util.jStreamI(friendIDs).mapToObj(x->sendCrate(userID, x)).reduce((x,y)->x&&y).orElse(true);
		}
		private JSONObject tryResetCrates(JSONObject myCrates){
			long lastReset = myCrates.optLong("lastReset");
			if(Util.isBeforeStartOfTodayUTC(lastReset)){
				lastReset = System.currentTimeMillis();
				myCrates.getJSONArray("sent").clear();
				myCrates.getJSONArray("requested").clear();
				myCrates.getJSONArray("pending").clear();
				myCrates.getJSONArray("received").clear();
			}
			return myCrates.put("lastReset", lastReset);
		}
		public boolean requestCrates(int userID, JSONArray friendIDs){
			return Util.jStreamI(friendIDs).mapToObj(x->requestCrate(userID, x)).reduce((x,y)->x&&y).orElse(true);
		}
		public boolean sendCrate(int userID, int friendID){
			return sendOrRequestCrate(userID, friendID, true);
		}
		public boolean requestCrate(int userID, int friendID){
			return sendOrRequestCrate(userID, friendID, false);
		}
		private boolean sendOrRequestCrate(int userID, int friendID, boolean isSend){
			var myCrates = getCrates(userID);
			var friendCrates = getCrates(friendID);
			var rng = ThreadLocalRandom.current();
			//clear out sent and requested every 24h???
			tryResetCrates(myCrates);
			
			var sent = myCrates.getJSONArray(isSend ? "sent" : "requested");
			var success = Util.jStream(sent).noneMatch(x->x.getString("receiver").equals(""+friendID))
					&& Util.jStream(sent).filter(x->x.getString("sender").equals(""+userID)).count() < 3;
			if(success){
				var newCrate = new JSONObject(4).put("id", ""+rng.nextLong())
						.put("sender",""+userID)
						.put("receiver",""+friendID)
						.put("senderName",getCityThing(userID,0,"info").optString("userName","hydar"))
						;
				friendCrates.getJSONArray(isSend ? "received": "pending").put(newCrate);
				if(isSend)
					modifyCrates(friendID, 1);
				sent.put(newCrate);
			}
			return updateCrates(userID, myCrates) && success && updateCrates(friendID, friendCrates);
		}
		public JSONObject getCrates(int userID){
			var crates = getCore(userID).optJSONObject("crates");
			if(crates==null || !crates.has("sent")){
				var theCrates = Util.DEFAULT_CRATES();
				if(crates!=null)
					theCrates.putOpt("own",crates.opt("own"));
				crates = theCrates;
			}
			return crates;
		}
		public boolean updateCrates(int userID, JSONObject payload){
			return updateCore(userID, new JSONObject(1).put("crates",payload));
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
					return FileObjectStore.UNCHANGED;
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
						qRoom = new JSONObject(3).put("id", newRoomID).put("players", new JSONArray()).put("at",
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
					new JSONObject(2).put("roomID", roomID).put("at", now));
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
			long now = System.currentTimeMillis();
			AtomicReference<JSONObject> clonedRoom = new AtomicReference<>();
			var ret = store.update(List.of("monkeyCity","contest",""+cityID,"rooms", roomID), room->{
				//we need to return the entire new queue object, while extracting the new/found room
				boolean clone = (noScoreUpdate!=null && noScoreUpdate.contains(userID));
				if(clone)
					room = new JSONObject(room.toString());
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
					var myScore = scores.optJSONObject(""+userID);
					if(myScore==null)
						myScore = new JSONObject(6);
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
							ct.put("lastLootTime", now);
							ct.put("lootTimeOffset", lootTimeOffset);
							if(leader >= 0){
								System.out.println("Updating old leader "+leader);
								var oldLeader = scores.getJSONObject(""+leader);
								long durationWithoutCurrent = (now - oldLeader.getLong("time"))
										+ oldLeader.optLong("durationWithoutCurrent");
								oldLeader
									.put("durationWithoutCurrent", durationWithoutCurrent)
									.put("current", 0)
									.put("durationTime", 0)
									.put("time", 0);
							}
							myScore
								.put("durationTime", now)
								.put("time", now);
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
								.put("time", now)
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
				if(clone)
					clonedRoom.setPlain(room);
				return clone ? FileObjectStore.UNCHANGED : room;
			});
			ret = clonedRoom.getPlain() == null ? ret : clonedRoom.getPlain();
			updateCTLevels(ret, cityID);
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
			return new JSONObject(8);
		}
		public BMCData skipScoreUpdate(String skip){
			if(skip != null && !skip.trim().isEmpty())
				noScoreUpdate = Arrays.stream(skip.split(",",0)).map(Integer::parseInt).collect(toSet());
			return this;
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

		public void updateCTLevels(JSONObject room, int cityID){
			var ct = room.getJSONObject("contestedTerritory");
			var levels = Util.jStream(ct.getJSONArray("cities"))
				.collect(toMap(
					city -> city.getInt("userID"),
					city -> getCityThing(city.getInt("userID"), cityID, "info")
						.getInt("level")
					));
			//update instead of direct put in case a new city was added we don't know about
			store.update(List.of("monkeyCity","contest",""+cityID,"rooms", ct.getString("roomID")),newRoom->{
				var newCT = newRoom.getJSONObject("contestedTerritory");
				Util.jStream(newCT.getJSONArray("cities")).forEach(city->{
					city.putOpt("cityLevel", levels.get(city.getInt("userID")));
				});
				return newRoom;
			});
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
					room.put("history", new JSONObject(1).put("room",ct));
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
				return new JSONObject(8);
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
			return new JSONObject(8);
		}
		
		%>
		<%-- PVP - CORE --%>
		<%!

		public static class AttackStatus{
			public static final int INVALID = 0, NEW_SENT = 1, DELIVERED = 2, LINKED = 3, STARTED = 4, RESOLVED = 5,
					CLOSED = 6;
		}
		public JSONObject getFriend(int userID, int cityID){
			return Util.jStream(
					getFriends(new JSONArray().put(userID)
							)
						.getJSONArray("friends").getJSONObject(0)
						.getJSONArray("cities")
						)
					.filter(x -> x.getInt("cityIndex") == cityID)
					.findFirst()
					.orElseThrow()
				;
		}
		public JSONObject getFriends(JSONArray friendIDs){
			var friends =  new JSONArray();
			var friendData = new JSONObject(2)
				.put("friends", friends)
				.put("friendIDs", friendIDs);
			for(int id: Util.jIterI(friendIDs)){
				var cities = new JSONArray();
				friends.put(
					new JSONObject(2)
						.put("userID", id)
						.put("cities", cities)
					);
				for(int index: List.of(0,1)){
					var info = getCityThing(id, index, "info");
					if(info == null)
						continue;
					cities.put(new JSONObject(6)
							.put("cityIndex", index)
							.put("level", info.getInt("level"))
							.put("honour", info.optInt("honour"))
							.put("name", info.getString("userName"))
							.put("clan", info.getString("userClan"))
							.put("youHaveAlreadyAttacked", false)//TODO: attacks.contains thing...
							//.put("quickMatchID", id)
						);
				}
			}
			return friendData;
		}
		public JSONObject getPVPCore(int userID, int cityID){
			return getPVPCore(userID, cityID, false);
		}
		public JSONObject getPVPCore(int userID, int cityID, boolean fromDirectOrCityList){
			updateBotAttacks(userID, cityID);
			//attack status updates might have to happen here?
			//-->find which state the expire countdown would start in
			long now = System.currentTimeMillis();
			List<JSONObject> toResolveAsSender = new ArrayList<>();
			var ret = store.update(List.of("monkeyCity", ""+userID, "pvp", ""+cityID, "core"),core->{
				if(core==null)
					core = new JSONObject(2).put("attacks",new JSONArray()).put("timeUntilPacifist", 0);
				if(core.has("pacifist") && core.getBoolean("pacifist") == false)//legacy key
					core.put("exitedPacifistAt", now).remove("pacifist");
				long sinceExit = now - core.optLong("exitedPacifistAt");
				long timeUntilPacifist = Math.max(0, 24l*3600*1000*3 - sinceExit);
				//TODO: hide or totally remove resolved attacks over some amount
				for(var attack: Util.jIter(core.getJSONArray("attacks"))){
					int status = attack.getInt("status");
					boolean expired = attack.getLong("expireAt") < now;
					if(status == AttackStatus.NEW_SENT && fromDirectOrCityList && 
							attack.getJSONObject("target").getInt("userID") == userID){
						attack.put("status",AttackStatus.DELIVERED);
						if(!expired)
							attack.put("expireAt", now + 24l*3600*1000);
					}
					if(attack.getJSONObject("sender").optInt("userID") == userID){
						timeUntilPacifist = Math.max(timeUntilPacifist, 24l*3600*1000*3 + now - attack.getLong("timeLaunched"));
						if(status == AttackStatus.NEW_SENT && expired){
							attack.put("status",AttackStatus.DELIVERED);//TODO: allow attacker to resolve?
							toResolveAsSender.add(attack);
						}
					}
					
				}
				return core.put("timeUntilPacifist", timeUntilPacifist);
			});
			for(JSONObject att: toResolveAsSender){
				//nothing you put here works - maybe try setting resolutionSeen for defender, but only in attacker data
				/**
				var res = new JSONObject()
						.put("resolution","win")
						.put("attackSucceeded", true)
						.put("wasHardcore", false)
						.put("info","");
				resolveAttack(userID, cityID, att.getString("attackID"), res);*/
			}
			return ret;
		}
		public JSONObject updatePVPCore(int userID, int cityID, UnaryOperator<JSONObject> update){
			if(userID==BOT_ID)
				return null;
			return store.update(List.of("monkeyCity", ""+userID, "pvp", ""+cityID, "core"),core->{
				if(core==null)
					core = new JSONObject(2).put("attacks",new JSONArray()).put("timeUntilPacifist", 0);
				return update.apply(core);
			});
		}
		public JSONObject addAttack(int userID, int cityID, JSONObject attack){
			return updatePVPCore(userID, cityID, core->{
				core.getJSONArray("attacks").put(attack);
				return core;
			});
		}

		public JSONObject updateAndGetAttack(int userID, int cityID, String attackID, UnaryOperator<JSONObject> update){
			return Util.jStream(updateAttack(userID, cityID, attackID, update)
				.getJSONArray("attacks"))
				.filter(x->x.getString("attackID").equals(attackID))
				.findFirst().orElseThrow();
		}
		
		public JSONObject updateAttack(int userID, int cityID, String attackID, UnaryOperator<JSONObject> update){
			return updatePVPCore(userID, cityID, core->{
				var attacks = core.getJSONArray("attacks");
				int index = IntStream.range(0, core.getJSONArray("attacks").length())
					.filter(x->attacks.getJSONObject(x).getString("attackID").equals(attackID))
					.findFirst().orElseThrow();
				attacks.put(index, update.apply(attacks.getJSONObject(index)));
				return core;
			});
		}

		%>
		<%-- PVP - LIFECYCLE --%>
		<%!

		public JSONObject linkAttack(int userID, int cityID, String attackID, JSONObject payload) {
			updateAttack(userID, cityID, attackID, attack->{
				if(attack.getJSONObject("target").getInt("userID") == userID
						&& attack.getInt("status") == AttackStatus.DELIVERED
						){	
					if(payload.getString("action").equals("linkToTile")){
						attack.put("linkedTile",new JSONObject(2)
							.put("x", payload.getInt("tileX"))
							.put("y", payload.getInt("tileY"))
						).put("status",AttackStatus.LINKED)
						//done when DELIVERED state added now
						//.put("expireAt", System.currentTimeMillis() + 24l*3600*1000)
						;
					}//other actions if those exist...
				}
				return attack;
			});
			return new JSONObject(8).put("success", true);
		}
		//also IO error but no stacktrace when clicking the attack 
		public JSONObject startAttack(int userID, int cityID, String attackID) {
			
			return updateAttack(userID, cityID, attackID, attack->{
			if(attack.getJSONObject("target").getInt("userID") == userID
					&& attack.getInt("status") == AttackStatus.LINKED
				){	
					attack.put("status", AttackStatus.STARTED);
				}
				return attack;
			});
		}
		public JSONObject resolveAttack(int userID, int cityID, String attackID, JSONObject resolution){
			return resolveAttack(userID, cityID, attackID, resolution, false);
		}
		public JSONObject resolveAttack(int userID, int cityID, String attackID, JSONObject resolution, boolean recall) {
			int[] changes = new int[2];
			var tmpAtk = updateAndGetAttack(userID, cityID, attackID, x->x);
			var tmpTarget = tmpAtk.getJSONObject("target");
			var tmpSender = tmpAtk.getJSONObject("sender");
			var targetID = tmpTarget.getInt("userID");
			int defHonor = targetID == BOT_ID ? 
					Util.getBotCity(
							cityID,
							tmpSender.getInt("cityLevel"),
							tmpSender.getInt("honour")
						).getInt("honour"):
					getCityThing(targetID, tmpTarget.getInt("cityIndex"), "info")
						.getInt("honour");
			var a =  updateAndGetAttack(userID, cityID, attackID, attack->{
				var sender = attack.getJSONObject("sender");
				var target = attack.getJSONObject("target");
				boolean isSender = sender.getInt("userID") == userID;
				boolean isTarget = target.getInt("userID") == userID;
				if((isSender || isTarget)
						&& attack.getInt("status") < AttackStatus.RESOLVED
					){//problem: not only the target can resolve the attack	
						target.put("honour", defHonor);
						var attSucc = resolution.getBoolean("attackSucceeded");
						var wasHc = resolution.optBoolean("hardcore");
						var isFriend = attack.getBoolean("isFriend");//will be used for honor calc
						attack.put("status", AttackStatus.RESOLVED)
							.put("resolution", resolution.getString("resolution"))
							.put("wasHardcore", wasHc)
							.put("attackSucceeded", attSucc)
							.put("info", resolution.getString("info"))
							.put("timeResolved", System.currentTimeMillis())
							;
						int att = sender.getInt("honour");
						int def = target.getInt("honour");
						changes[0] = attSucc ? Util.winHonor(att, def, attSucc, false, isFriend) : 
								Util.lossHonor(def, att, attSucc, isFriend);
						changes[1] = attSucc ? Util.lossHonor(att, def, attSucc, isFriend) : 
							Util.winHonor(def, att, attSucc, wasHc, isFriend);
						
						sender.put("honourChange", changes[0]);
						target.put("honourChange", changes[1]);
						//started with target: FF->TT, started with sender TF->FT
						var targetBot = target.getInt("userID") == BOT_ID;
						(((isSender && !targetBot) ^ recall) ? sender : target).put("resolutionSeen", System.currentTimeMillis());
					}
					return attack;
				}
			);
			// response structure: sender IF we are the target, otherwise no sender
			// .honour, .senderCity, 
			var sender = a.getJSONObject("sender");
			var target = a.getJSONObject("target");
			int senderID = sender.getInt("userID");
			int senderCityIndex = sender.getInt("cityIndex");
			boolean isSender = senderID == userID;
			//TODO: attacker doesn't see as themself
			//if(!isSender)
			//	updateAttack(sender.getInt("userID"), sender.getInt("cityIndex"), attackID, discard->a);
			if(!isSender){
				if(senderID != BOT_ID)
					resolveAttack(senderID, senderCityIndex, attackID, resolution, true);
				else 
					closeAttack(userID, cityID, attackID);//bot doesn't need to see the resolution
			}
			var senderCity = senderID == BOT_ID ? 
					Util.getBotCity(senderCityIndex, sender.getInt("level"), 0)
						.put("honour",sender.getInt("honour")) :
					getFriend(senderID, senderCityIndex);
			return new JSONObject(10)
					.put("honour", (isSender ? target : sender).getInt("honour"))
					.putOpt("sender", isSender ? null : sender)
					.put("target", target)
					.put("senderCity", senderCity);
		}
		
		//NOTE: can be run as either sender or target
		public JSONObject closeAttack(int userID, int cityID, String attackID){
			int[] opp = new int[2];
			var now = System.currentTimeMillis();
			updateAttack(userID, cityID, attackID, attack->{
				var sender = attack.getJSONObject("sender");
				var target = attack.getJSONObject("target");
				var targetID = target.getInt("userID");
				var senderID = sender.getInt("userID");
				if((targetID == userID || senderID == userID)
						&& attack.getInt("status") == AttackStatus.RESOLVED
						){	
						attack.put("status",AttackStatus.CLOSED);
						target.put("resolutionSeen",target.optLong("resolutionSeen", now));
						sender.put("resolutionSeen",sender.optLong("resolutionSeen", now));
					}
				opp[0] = userID==targetID ? senderID : targetID;
				opp[1] = (userID==targetID ? sender : target).getInt("cityIndex");
				return attack;
			});
			if(opp[0] != BOT_ID)
				updateAttack(opp[0], opp[1], attackID, attack->{
					if(attack.getInt("status") == AttackStatus.RESOLVED){	
						var target = attack.getJSONObject("target");
						var sender = attack.getJSONObject("sender");
						attack.put("status",AttackStatus.CLOSED);
						target.put("resolutionSeen",target.optLong("resolutionSeen", now));
						sender.put("resolutionSeen",sender.optLong("resolutionSeen", now));
					}
					return attack;
				});
			return new JSONObject(8).put("success", true);
		}
		public void pruneAttacks(int userID, int cityID){
			updatePVPCore(userID, cityID, core->{
				var attacks = core.getJSONArray("attacks");
				if(attacks.length() > MAX_CLOSED_ATTACKS){
					int closedAttacks = 0;
					for(int i=attacks.length()-1; i>=0; i--){
						if(attacks.getJSONObject(i).getInt("status")  == AttackStatus.CLOSED
								&& ++closedAttacks > MAX_CLOSED_ATTACKS){
							int cutoff = i;
							IntStream.range(0, cutoff)
								.map(x-> cutoff - x)
								.filter(x-> attacks.getJSONObject(x).getInt("status") == AttackStatus.CLOSED)
								.forEach(attacks::remove);
							break;
						}
					}
				}
				return core;
			});
		}
		%>
		<%-- PVP - QUEUE/SEND --%>
		<%!public JSONObject sendAttack(int userID, int cityID, JSONObject payload) {
			var sender = payload.getJSONObject("sender");
			var target = payload.getJSONObject("target");
			if(userID != BOT_ID){
				exitPacifist(userID, cityID);
				addToQueue(userID, cityID, sender.getInt("cityLevel"), sender.getInt("honour"));
				pruneAttacks(userID, cityID);
			}
			long now = System.currentTimeMillis();
			
			var eID = target.getInt("userID");
			var eCityID = target.getInt("cityIndex");
			if(!payload.getBoolean("revenge")){
				var eInfo = getCityThing(eID, eCityID, "info");
				String canAttack = eInfo == null ?
						(eID==BOT_ID ? canAttackBot(userID, cityID) : "no_city"):
						canAttack(userID, eID, eInfo.optInt("honour"), eCityID);
				if(!"yes".equals(canAttack))
					return new JSONObject(3).put("success",false).put("state",canAttack).put("error","bmc_game");
			}//canAttackBot
			payload.put("attackID", "" + ThreadLocalRandom.current().nextLong())
				.put("timeLaunched", now)
				.put("status", AttackStatus.NEW_SENT)
				.put("attack", payload.get("attackDefinition"))
				.put("expireAt", now + 24l*3600*1000*30)
				.remove("attackDefinition");
				;
			sender.put("userID", ""+userID)//MUST BE STRING!!!
				.put("cityIndex", cityID);
			if(!noScoreUpdate.contains(userID) && eID != BOT_ID)
				addAttack(eID, eCityID, new JSONObject(payload.toString()));
			if(userID != BOT_ID)
				addAttack(userID, cityID, payload);
			return new JSONObject(8).put("success", true);
		}
		private String canAttack(int userID, int eID, int eHonor, int eCityID){
			long now = System.currentTimeMillis();
			var eCore = getPVPCore(eID, eCityID);
			if(now - eCore.optLong("exitedPacifistAt") > 24l*3600*1000*3 && eHonor <= 1000)
				return "pacifist";
			var eAttacks = eCore.getJSONArray("attacks");
			int nAttacks = (int) Util.jStream(eAttacks)
					.filter(x -> x.getJSONObject("target").getInt("userID") == eID)
					.filter(x -> x.getInt("status") < AttackStatus.RESOLVED)
					.count();
			long alreadyAttackedAt = Util.jStream(eAttacks)
					.filter(x -> x.getJSONObject("target").getInt("userID") == eID)
					.filter(x -> x.getJSONObject("sender").getInt("userID") == userID)
					.mapToLong(x -> x.getLong("timeLaunched")).max().orElse(0);
			if (nAttacks > 4)
				return "maxAttacks";
			if(now - alreadyAttackedAt < 24l * 3600 * 1000)
				return "already";
			return "yes";
		}
		public JSONObject exitPacifist(int userID, int cityID){
			return updatePVPCore(userID, cityID, x->
				x.put(
						//doesn't work because it gets overwritten
					"timeUntilPacifist", 24l*3600*3*1000 //no 1000 honor check(in client)
				).put(
					"exitedPacifistAt", System.currentTimeMillis()
				)
			);
		}
		public JSONObject quickMatch(int userID, int cityID, int level, int honor){
			return findMatch(userID, cityID, level, honor);
		}
		public JSONObject findMatch(int userID, int cityID, int level, int honor){
			addToQueue(userID, cityID, level, honor);
			exitPacifist(userID, cityID);
			List<JSONObject> candidates = new ArrayList<>();
			long now = System.currentTimeMillis();
			store.update(List.of("monkeyCity","pvp",""+cityID,"queue"),queue->{
				if(queue==null)
					queue=new JSONObject(1);
				JSONArray q = queue.getJSONArray("queue");
				//TODO: scales badly since basically everyone is in the same queue
				for(int i=0;i<q.length();i++){
					var e = q.getJSONObject(i);
					int eLevel = e.getInt("level");
					int range = (int) (Math.max(level,eLevel)*0.2);
					
					if(e.getInt("userID") == userID || Math.abs(e.getInt("level") - level) > range)
						continue;
					candidates.add(e);
				}
				//attackedBy or something? or just get that while taking from queue
					//probably the latter
				return queue;
			});
			//this makes the whole queue ordering thing pointless and makes it same opp every time
			/*Collections.sort(candidates, 
					Comparator.comparingInt((JSONObject e) -> Math.abs(e.getInt("level") - level))
						.thenComparing(Comparator.comparingInt(e -> Math.abs(e.getInt("honor") - honor))
						)
				);*/
			int matchedID = -1;
			for (var e : candidates) {
				int eID = e.getInt("userID");
				if(!"yes".equals(canAttack(userID, eID, e.getInt("honor"), cityID)))
						continue;
				//TODO: only do this if attack actually sent
				matchedID = dequeue(eID, cityID, true) ? eID : -1;
				if (matchedID>=0) break;
			}
			if (matchedID < 0){
				// attempt to attack bot
				if(!"yes".equals(canAttackBot(userID, cityID)))
					return new JSONObject(8).put("success", false);
				matchedID=BOT_ID;
			}
			var match = matchedID==BOT_ID ? Util.getBotCity(cityID, level, honor) : getFriend(matchedID, cityID);

			return new JSONObject(8).put("matchedOpponent",
					new JSONObject(6).put("userID", matchedID).put("quickMatchID", matchedID)//TODO: find out what these do
							.put("name", match.get("name")).put("clan", match.get("clan"))
							.put("honour", match.get("honour")).put("city", match))
					.put("success", true);

		}

		private boolean dequeue(int userID, int cityID, boolean requeue) {
			var success = new AtomicBoolean();
			var newData = getFriend(userID, cityID);
			store.update(List.of("monkeyCity", "pvp", "" + cityID, "queue"), queue -> {
				if (queue == null)
					queue = new JSONObject(1);
				JSONArray q = queue.optJSONArray("queue", new JSONArray());
				int index = IntStream.range(0, q.length()).filter(x -> q.getJSONObject(x).getInt("userID") == userID)
						.findFirst().orElse(-1);
				if (index < 0)
					return queue;
				success.setPlain(true);
				var e_ = q.remove(index);
				if (requeue && !noScoreUpdate.contains(userID)){//update level
					q.put(new JSONObject(3).put("userID", userID)
							.put("level", newData.getInt("level"))
							.put("honor", newData.getInt("honour"))
						);
				}
				queue.put("queue", q);
				return queue;
			});
			return success.getPlain();
		}

		public void addToQueue(int userID, int cityID, int level, int honor) {
			dequeue(userID, cityID, false);
			store.update(List.of("monkeyCity", "pvp", "" + cityID, "queue"), queue -> {
				if (queue == null)
					queue = new JSONObject(1);
				JSONArray q = queue.optJSONArray("queue", new JSONArray());
				if(!noScoreUpdate.contains(userID))
					q.put(new JSONObject(3).put("userID", userID)
							.put("level", level)
							.put("honor", honor)
						);
				//attackedBy or something? or just get that while taking from queue
				//probably the latter
				queue.put("queue", q);
				return queue;
			});
		}
	%>
		<%-- PVP - BOTS --%>
	<%!
		private String canAttackBot(int userID, int cityID){
			long now = System.currentTimeMillis();
			var core = getPVPCore(userID, cityID);
			var attacks = core.getJSONArray("attacks");
			int nAttacks = (int) Util.jStream(attacks)
					.filter(x -> x.getJSONObject("target").getInt("userID") == BOT_ID)
					.filter(x -> x.getInt("status") < AttackStatus.RESOLVED)
					.count();
			long alreadyAttackedAt = Util.jStream(attacks)
					.filter(x -> x.getJSONObject("target").getInt("userID") == BOT_ID)
					.mapToLong(x -> x.getLong("timeLaunched")).max().orElse(0);
			if (nAttacks > 4)
				return "maxAttacks";
			if(nAttacks > 0 && now - alreadyAttackedAt < 8l * 3600 * 1000)
				return "already";
			return "yes";
		}
		// "tick" the bot attacks
		// resolve after 12-24 hours
		// need to decode attack :(
		public void updateBotAttacks(int userID, int cityID){
			List<Runnable> toResolve = new ArrayList<>();
			updatePVPCore(userID, cityID, core->{
				var attacks = core.getJSONArray("attacks");
				for(var attack: Util.jIter(attacks)){
					var sender = attack.getJSONObject("sender");
					var target = attack.getJSONObject("target");
					if(target.getInt("userID") == BOT_ID && attack.getInt("status") < AttackStatus.RESOLVED){
						var id = attack.getString("attackID");
						var resolveDelta = (480l + (Math.abs(Long.parseLong(id)) % 480)) * 60 * 1000;//defend the attack after 8-16h
						var launchTime = attack.getLong("timeLaunched");
						var now = System.currentTimeMillis();
						if(now > launchTime + resolveDelta){
							toResolve.add(()->{
								resolveAttack(userID, cityID, id, new JSONObject()
										.put("attackSucceeded", false)
										.put("hardcore", false)
										.put("info","eNpdjcEOgjAQBc/yFaRnYgApokf1YmI0Qe9koRtAodVSMMb475Y2RuN15u3s05mQnte3HrebLMvI0p2HnmYiP2OhTo8rWkoOBhDP1TJvhOBHJZGXqiKutjsEthqpHSgJxWUPLRqZ1gNKag0opRVKM1aC132b4h0kG5e+PRYKmlT0nHUjDBJD5T/4lNbQVd9EQH1b6aAZoET282j0Mz+MplEcJzTyQxrRhfN6A03jS9g=")
										.put("resolution","loss")
									);
							});
							//now revenge
							toResolve.add(()->{
								var newCityInfo = getCityThing(userID, cityID, "info");
								var x = new JSONObject(Util.unpack(attack.getString("attack")))
									.put("isRevenge", true)
									.put("defenderUserName", sender.getString("name"))
									.put("defenderUserClan", sender.getString("clan"))
									.put("defenderCityIndex", cityID)
									.put("attackerID", ""+BOT_ID)
									.put("defenderID", ""+userID)
									.put("quickMatchID", ""+userID)
									.put("messageToOpponent","")
									.put("defenderCityLevel", newCityInfo.getInt("level"));
								var n = new JSONObject(7)
										.put("action","attack")
										.put("isFriend",false)
										.put("revenge",true)
										.put("quickMatchID",""+userID)
										.put("attackDefinition",Util.pack(x.toString()))
										.put("target", getFriend(userID, cityID).put("userID", userID).put("cityIndex", cityID))
										.put("sender", Util.getBotCity(
												cityID, 
												newCityInfo.getInt("level"), 
												newCityInfo.getInt("honour")
											)
										);
								sendAttack(BOT_ID, cityID, n);
							});
							
						}
					}
				}
				return core;
			});
			toResolve.forEach(Runnable::run);
		}%>
<%!
	
	}
%>
<%-- Util --%>
<%!public static class Util{

	public static boolean isBeforeStartOfTodayUTC(long time){
		var startOfToday = LocalDateTime.ofInstant(Instant.now(), ZoneId.of("UTC")).toLocalDate().atStartOfDay();
		return startOfToday.isAfter(LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.of("UTC")));
	}
	private static long key(JSONObject tile) {
		return ((long) tile.getInt("x") << 32) | (tile.getInt("y") & -1L);
	}
	/**
1-8 = 8
101 = 14 10
301 = 17 17
601 = 18 24
1001 = 20 
1501 = 21
2101 = 23 
2801 = 24
3601 = 26
4501 = 27
5501 = 28
7501 = 28
10001 = 29
vs 10001, there is no additional tiered effect
diff 9 = 1 3+27
diff 36 = 2 6+45
diff 81 = 3 9+ 63
diff 144 = 4 12+

but then something else emerges??? imprecision, differs even when difference not different???
9125 = 32 
10301 = 34 
tier 1

w=0 - 16 until 0,197
w=1 - 16 until 1,198
w=2 - 16 until 1,199
w=6 - 16 until 6,203
w=7 - 16 until 7,205 -->increased by 1, why??????
w=8 - 16 until 8,206 
w=9 - 16 until 9,207 
w=10 - 16 until 10,208, d=198
w=15 - 16 until 15,214, d=199 -->increased by 1, why??????
w=23 - 16 until 23,223, d=200 -->increased by 1, why??????
w=32 - 15 until 102, 16 until 32,232 
w=33 - 15 until 103, 16 until 33,234, d=201 -->increased by 1, why??????
w=43 - 15 until ??, 16 until 43,245, d=202 -->increased by 1, why??????
w=54 - 15 until ??, 16 until 54,257, d=203 -->increased by 1, why??????
w=66 - 15 until ??, 16 until 66,270, d=204 -->increased by 1, why??????
w=80 - 15 until ??, 16 until 80,285, d=205 -->increased by 1, why??????
w=95 - 15 until ??, 16 until 95,301, d=206 -->increased by 1, why??????
		
		
w=100 - 14 until 100,109 then 15 until 100,174
off by a small but increasing amount, inconsistent direction, dependent on w
each tier seems to have a different calculation based on the sqrt
--> so we add sqrt(diff)/3 to the tiered effect
but the tiered effect also depends on the diff???
below 100 has different behavior
	*/
	//achievement tier - used in loss honor
	public static double lossFactor(int honor){
		int[] tiers = {10000,7500,5500,4500,3600,2800,2100,1500,1000,600,300,100};
		double[] facs = {0.3, 0.5, 0.6, 0.65, 0.7, 0.75, 0.8, 0.85, 0.9, 0.95, 0.975, 0.99, 1};
		for(int i=0;i<tiers.length;i++){
			if(honor>tiers[i])
				return facs[tiers.length-i];
		}
		return facs[0];
	}
	public static double baseHonor(int w, int l, boolean attackSuccess, boolean friend){
		if(friend && w>MAX_FRIEND_HONOR && l>MAX_FRIEND_HONOR)
			return 0;
		double d = Math.abs(w-l);
		double base = (attackSuccess ? 29 : 28);
		return Math.max(1, (base + (w < l ? 1 : -1) * (Math.sqrt(d)+ d/(l+1.0))/3+1));
	}
	public static int lossHonor(int w, int l, boolean attackSuccess, boolean friend){
		return Math.min(l, 1 - (int)(lossFactor(l) * baseHonor(w, l, attackSuccess, friend)));
	}
	public static int winHonor(int w, int l, boolean attackSuccess, boolean hc, boolean friend){
		return (int)((hc? 2:1) * baseHonor(w,l,attackSuccess, friend));
	}
	
	static String honor(int w, int l, boolean a) {
		return a ? "" + winHonor(w, l, a, false, false) + ", " + lossHonor(w, l, a, false)
				: "" + lossHonor(w, l, a, false) + ", " + winHonor(w, l, a, false, false);
	}

	public static JSONObject BLANK_CORE = new JSONObject(2).put("core", new JSONObject()).put("crates", DEFAULT_CRATES());

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

	public static JSONObject mergeContent(JSONObject content, JSONObject update) {
		if (content == null)
			content = new JSONObject(10);
		var tiles = content.optJSONArray("tiles", new JSONArray());
		var newTiles = update.optJSONArray("tiles", new JSONArray());
		var updateContent = update.optJSONObject("content", new JSONObject(10));
		for (String key : updateContent.keySet()) {
			if (!key.equals("tiles"))
				content.put(key, updateContent.get(key));
		}
		var tileMap = jStream(tiles).collect(toMap(Util::key, x -> x, (x, y) -> y));
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
			info = new JSONObject(6);
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
			core = new JSONObject(3);
		for (String topKey : List.of("core", "monkeyKnowledge", "crates")) {
			JSONObject oldCore;
			if(core.has(topKey))
				oldCore = core.getJSONObject(topKey);
			else
				oldCore = topKey.equals("crates") ? Util.DEFAULT_CRATES() : new JSONObject();
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

	public static String unpack(String data){
		try(var in1 = new ByteArrayInputStream(data.getBytes(StandardCharsets.ISO_8859_1));
			var b64=Base64.getDecoder().wrap(in1);
			var inf = new InflaterInputStream(b64);){
				return new String(inf.readNBytes(1024000), StandardCharsets.UTF_8);
		}catch(IOException ioe){return null;}
	}
	public static String pack(String data){
		var baos = new ByteArrayOutputStream(data.length());
		try(var b64=Base64.getEncoder().wrap(baos);
			var def = new DeflaterOutputStream(b64);){
			def.write(data.getBytes(StandardCharsets.UTF_8));
		}catch(IOException ioe){return null;}
		return baos.toString(StandardCharsets.ISO_8859_1);
	}
	private static JSONObject DEFAULT_CRATES() {
		return new JSONObject(5).put("own", 0).put("requested", new JSONArray()).put("sent", new JSONArray())
				.put("pending", new JSONArray()).put("received", new JSONArray());
	}

	public static JSONObject getBotCity(int cityID, int level, int honor){
		return new JSONObject(8)
				.put("userID", ""+BOT_ID)
				.put("cityIndex", cityID)
				.put("level", level)
				.put("cityLevel", level)
				.put("honour", (int) Math.min(honor*0.75, 10000))
				.put("name", "sam ninjakiwi")
				.put("clan", "kong")
				.put("youHaveAlreadyAttacked", false);
	}
}%>
<%-- CTUtil --%>
<%!
public static class CTUtil {
	public static long DAY = 24*3600*1000l;
	public static JSONObject newCTRoom(int level, int cityID, JSONObject payload) {
		String roomID = "" + ThreadLocalRandom.current().nextLong();
		var newRoom = new JSONObject(8).put("contestedTerritory",
				new JSONObject(8).put("cities", new JSONArray()).put("score", new JSONObject())
						.put("data", payload.get("data")).put("roomID", roomID).put("levelTier", ctTier(level))
						.put("minRounds", ctMinRound(level)).put("lastLootTime", System.currentTimeMillis())
						.put("startTime", System.currentTimeMillis()));
		return newRoom;
	}

	public static void addCTPlayer(JSONObject room, int userID, JSONObject player){
		var cities = room.getJSONObject("contestedTerritory").getJSONArray("cities");
		if(Util.jStream(cities).noneMatch(x->x.getInt("userID") == userID))
			cities
				.put(new JSONObject(6)
					.put("userName",player.get("userName"))
					.put("userID",userID)
					.put("cityLevel",player.get("cityLevel"))
					.put("cityName",player.get("cityName"))
				);
	}
	
	private static int week(long millis) {
		return (int) ((millis / (DAY) - 4) / 7);
	}

	private static long endOfWeek(long roomStartTime) {
		return ((week(roomStartTime) + 1L) * 7 + 4) * DAY;
	}
	//for cases where bloons retook while someone was leader
	//problem - what if they submitted bad scores during that time???
	public static void rollExtraTime(JSONObject scores, long roomStartTime, int minRounds) {
		long now = System.currentTimeMillis();
		long endOfWeek = endOfWeek(roomStartTime);
		int leader = findLeader(scores, minRounds);
		scores.keySet().stream().filter(x -> {
			long time = scores.getJSONObject(x).optLong("time");
			return time > 0;
		}).filter(x -> !x.equals("" + leader)).forEach(id -> {
			JSONObject score = scores.getJSONObject("" + id);
			long durationWithoutCurrent = Math.min(DAY,
					Math.min(endOfWeek, now) - score.getLong("time")) + score.optLong("durationWithoutCurrent");
			score.put("durationWithoutCurrent", durationWithoutCurrent).put("current", 0).put("time", 0);
		});
	}
	/**
	* For some reason when someone overtakes you in CT it requires you to beat their best score and not current score
	* Therefore we hide the best score but only on score put request
	*/
	public static JSONObject hideBest(JSONObject room) {
		var scores = room.getJSONObject("contestedTerritory").getJSONObject("score");
		for(String key:scores.keySet()){
			var score = scores.getJSONObject(key);
			score.put("best",score.optInt("current"));
		}
		return room;
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
				score.put("duration", score.optLong("durationWithoutCurrent"));//(score.optLong("time") - score.optLong("durationTime")));
		}
		return room;
	}
	public static void updateDurations(JSONObject scores, long roomStartTime, int minRounds) {
		long now = System.currentTimeMillis();
		long endOfWeek = endOfWeek(roomStartTime);
		for (String id : scores.keySet()) {
			JSONObject score = scores.getJSONObject("" + id);
			long time = score.optLong("time");
			long durationTime = score.optLong("durationTime");
			long duration = score.optLong("durationWithoutCurrent") +
			//if time > 0, you were the last leader(even though you would no longer be)
					(time > 0 ? (Math.min(DAY, Math.min(endOfWeek, now) - time)) : 0);
			score.put("time", time).put("durationTime", durationTime).put("duration", duration);
		}
	}
	//assumes time was already updated
	//now need a fn to determine if a new score would become the leader
	public static int findLeader(JSONObject scores, int minRounds) {
		long now = System.currentTimeMillis();
		return scores.keySet().stream().filter(x -> {
			long time = scores.getJSONObject(x).optLong("time");
			return time > 0 && (now - time) < DAY;
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
		if (score < minRounds || (now - time) >= DAY || week(now) != week(time))
			return false;
		int leader = findLeader(scores, minRounds);
		return leader < 0 || scores.getJSONObject("" + leader).optInt("current") < score;
	}

	public static int ctMinRound(int level) {
		int tier = ctTier(level);
		return 
				//0 == 0 ? 1 : 
		switch (tier) {
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
