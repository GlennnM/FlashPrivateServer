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
				hydar.ee.compile(Path.of("../src/webapp/BMC.jsp"));
				hydar.ee.ctx.setAttribute("done", null);
			}catch(InterruptedException e){
				Thread.currentThread().interrupt();
			}
		}).start();
	}
}
%><%!
	static final long CT_QUEUE_TIME = 24L * 3600 * 1000 * 3;//time a new CT is joinable for
	/**does stuff like putCity(0,{},..)*/
	public static class BMCData{
		private final ObjectStore store;
		private volatile Set<Integer> noScoreUpdate;
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
			updateCrates(userID, myCrates);
			updateCrates(friendID, friendCrates);
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
							.put("honour", info.getInt("honour"))
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
			var tmpTarget = updateAndGetAttack(userID, cityID, attackID, x->x).getJSONObject("target");
			int defHonor = getCityThing(tmpTarget.getInt("userID"), tmpTarget.getInt("cityIndex"), "info")
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
						var wasHc = resolution.optBoolean("wasHardcore");
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
						((isSender ^ recall) ? sender : target).put("resolutionSeen", System.currentTimeMillis());
					}
					return attack;
				}
			);
			// response structure: sender IF we are the target, otherwise no sender
			// .honour, .senderCity, 
			var sender = a.getJSONObject("sender");
			var target = a.getJSONObject("target");
			boolean isSender = sender.getInt("userID") == userID;
			//TODO: attacker doesn't see as themself
			//if(!isSender)
			//	updateAttack(sender.getInt("userID"), sender.getInt("cityIndex"), attackID, discard->a);
			if(!isSender && (sender.getInt("userID") != target.getInt("userID"))){
				resolveAttack(sender.getInt("userID"), sender.getInt("cityIndex"), attackID,
						resolution, true);
			}
			var senderCity = getFriend(sender.getInt("userID"), sender.getInt("cityIndex"));
			
			return new JSONObject(10)
					.put("honour", (isSender ? target : sender).getInt("honour"))
					.putOpt("sender", isSender ? null : sender)
					.put("target", target)
					.put("senderCity", senderCity);
		}
		

		public JSONObject closeAttack(int userID, int cityID, String attackID){
			int[] opp = new int[1];
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
				return attack;
			});
			updateAttack(opp[0], cityID, attackID, attack->{//TODO: assumes same city id(this is fine)
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

		%>
		<%-- PVP - QUEUE/SEND --%>
		<%!public JSONObject sendAttack(int userID, int cityID, JSONObject payload) {
			var sender = payload.getJSONObject("sender");
			var target = payload.getJSONObject("target");
			exitPacifist(userID, cityID);
			addToQueue(userID, cityID, sender.getInt("cityLevel"), sender.getInt("honour"));
			long now = System.currentTimeMillis();
			payload.put("attackID", "" + ThreadLocalRandom.current().nextLong())
				.put("timeLaunched", now)
				.put("status", AttackStatus.NEW_SENT)
				.put("attack", payload.get("attackDefinition"))
				.put("expireAt", now + 24l*3600*1000*30)
				.remove("attackDefinition");
				;
			sender.put("userID", ""+userID)//MUST BE STRING!!!
				.put("cityIndex", cityID);
			addAttack(target.getInt("userID"), target.getInt("cityIndex"), new JSONObject(payload.toString()));
			addAttack(userID, cityID, payload);
			//TODO: verify if attack should happen? ie attacked recently, city level, ...
			//then add attack to pvp core for both sender and target(target first)
			//add fields: attackID, status, timeLaunched, ...
			//update sender timeUntilPacifist?
			return new JSONObject(8).put("success", true);
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
				var eCore = getPVPCore(eID, cityID);
				if(now - eCore.optLong("exitedPacifistAt") > 24l*3600*1000*3 && e.getInt("honor") <= 1000)
					continue;
				var eAttacks = eCore.getJSONArray("attacks");
				int nAttacks = (int) Util.jStream(eAttacks)
						.filter(x -> x.getJSONObject("target").getInt("userID") == eID)
						.filter(x -> x.getInt("status") < AttackStatus.RESOLVED)
						.count();
				long alreadyAttackedAt = Util.jStream(eAttacks)
						.filter(x -> x.getJSONObject("target").getInt("userID") == eID)
						.filter(x -> x.getJSONObject("sender").getInt("userID") == userID)
						.mapToLong(x -> x.getLong("timeLaunched")).max().orElse(0);
				if (nAttacks > 5 || now - alreadyAttackedAt < 24l * 3600 * 1000)
					continue;
				//TODO: only do this if attack actually sent
				matchedID = dequeue(eID, cityID, true) ? eID : -1;
				if (matchedID>=0) break;
			}
			if (matchedID < 0)
				return new JSONObject(8).put("success", false);
			var match = getFriend(matchedID, cityID);

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
				if (requeue){//update level
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
				q.put(new JSONObject(3).put("userID", userID)
						.put("level", level)
						.put("honor", honor)
					);
				//attackedBy or something? or just get that while taking from queue
				//probably the latter
				queue.put("queue", q);
				return queue;
			});
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
		if(friend && w>1500 && l>1500)
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

	private static JSONObject DEFAULT_CRATES() {
		return new JSONObject(5).put("own", 0).put("requested", new JSONArray()).put("sent", new JSONArray())
				.put("pending", new JSONArray()).put("received", new JSONArray());
	}
}%>
<%-- CTUtil --%>
<%!
public static class CTUtil {

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
		return (int) ((millis / (1000 * 60 * 60 * 24) - 4) / 7);
	}

	private static long endOfWeek(long roomStartTime) {
		return ((week(roomStartTime) + 1L) * 7 + 4) * 24 * 3600 * 1000;
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
			long durationWithoutCurrent = Math.min(24 * 3600 * 1000,
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
		return 
		//		0 == 0 ? 1 : 
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
		
		public default boolean has(String... url) {
			return has(String.join("/", url));
		}
	
		public default boolean has(Iterable<String> url) {
			return has(String.join("/", url));
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
	/**
		allows you to specify that an update() did nothing. 
		update() will still return your input value with any mutations
	*/
	public static final JSONObject UNCHANGED = new JSONObject();
	//stored in cache if something is deleted or not found(in constrast, null = 'not cached')
	public static final JSONObject NOT_PRESENT = new JSONObject();
	
	public static final ConcurrentMap<Path, FileObjectStore> INSTANCES = new ConcurrentHashMap<>();

	public final int maxCacheSize;
	private final ConcurrentMap<String, JSONObject> cache;
	private final Queue<String> order = new ConcurrentLinkedQueue<>();
	private final NavigableSet<String> modif = new ConcurrentSkipListSet<>();
	private final LongAdder cacheSize = new LongAdder();
	private volatile boolean delayedFlush = false;
	
	private FileObjectStore(Path root, int maxCacheSize) throws IOException {
		this.maxCacheSize = maxCacheSize;
		this.cache = new ConcurrentHashMap<>(maxCacheSize);
		if (!Files.exists(root))
			Files.createDirectories(root);
		if (!Files.isDirectory(root))
			throw new IllegalArgumentException("Not a dir: " + root);
		this.root = root;
	}
	public static FileObjectStore of(Path root) throws IOException {
		return of(root, 100000);
	}
	public static FileObjectStore of(Path root, int maxCacheSize) throws IOException {
		return INSTANCES.computeIfAbsent(root, x->{
			try{
				return new FileObjectStore(root, maxCacheSize);
			}catch(IOException ioe){
				throw new RuntimeException(ioe);
			}
		});
	}
	/**
	* Activates delayed flush. Only ever call once and before ever calling compute().
	* In order to prevent recompiling leakage, runs then removes previous shutdown hooks.
	* Then kills flusher services.
	*/
	public FileObjectStore bind(HttpServletRequest request, long flushInterval){
		if(delayedFlush)
			return this;
		var flusher = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
		var flusherHook = new Thread(this::flush);
		
		@SuppressWarnings("unchecked")
		var hooks =  (List<Thread>)(request.getServletContext().getAttribute("OBJECT_HOOKS_"+root.toString()));
		if(hooks == null)
			hooks = new ArrayList<Thread>();
		hooks.forEach(x->x.start());
		hooks.forEach(x->{
			try{
				x.join();
			}catch(InterruptedException ie){
				Thread.currentThread().interrupt();
			}
		});
		hooks.forEach(Runtime.getRuntime()::removeShutdownHook);
		hooks.clear();
		hooks.add(flusherHook);
		Runtime.getRuntime().addShutdownHook(flusherHook);
		request.getServletContext().setAttribute("OBJECT_HOOKS_"+root.toString(), hooks);
		

		@SuppressWarnings("unchecked")
		var flushers =  (List<ScheduledExecutorService>)(request.getServletContext().getAttribute("OBJECT_FLUSHERS_"+root.toString()));
		if(flushers == null)
			flushers = new ArrayList<ScheduledExecutorService>();
		flushers.forEach(ScheduledExecutorService::shutdown);
		flushers.clear();
		flushers.add(flusher);
		request.getServletContext().setAttribute("OBJECT_FLUSHERS_"+root.toString(), flushers);
		
		this.delayedFlush = true;
		flusher.scheduleAtFixedRate(this::flush, flushInterval, flushInterval, TimeUnit.MILLISECONDS);
		return this;
	}
	/**
	*	Perform an update, while hiding the cache.
	*	update can accept null and return UNCHANGED,
	*	but should not return or expect NOT_PRESENT.
	*		
	*	This method itself can return NOT_PRESENT but not UNCHANGED.
	*/
	private JSONObject compute(String unmappedKey, UnaryOperator<JSONObject> update){
		//automatically do the cache updates here
		var CHANGED = new AtomicBoolean(true);
		Path p = map(unmappedKey);
		String key = p.toString();
		//outside the update(non-reentrant), but before it so it must succeed before continuing
		tryEvict();
		var result = cache.compute(p.toString(),(k,v)->{
			if(v == null){
				//System.err.println("Cache MISS "+key);
				cacheSize.increment();
				try{
					v = Files.exists(p) ? new JSONObject(Files.readString(p)) : NOT_PRESENT;
				}catch(IOException ioe){
					throw new RuntimeException(ioe);
				}
			}
			var ret =  update.apply(v == NOT_PRESENT ? null : v);
			CHANGED.setPlain(ret != UNCHANGED);
			return ret == null ? NOT_PRESENT : 
				ret == UNCHANGED ? v : ret;
		});
		//update LRU order
		order.remove(key);
		order.add(key);
		if(CHANGED.getPlain())
			modif.add(key);
		if(!delayedFlush)
			flush();
		return result;
	}
	//Evict if cache size is greater than MAX_CACHE_SIZE. Fails if write-through fails.
	public void tryEvict(){
		//System.err.println("Cache size " + cacheSize.sum());
		if(cacheSize.sum() > maxCacheSize){
			String oldest = order.poll();
			if(oldest!=null){
				//System.err.println("Evicting "+oldest);
				if(modif.contains(oldest)){
					flushOne(oldest);
				}
				cache.remove(oldest);
				cacheSize.decrement();
			}
		}
	}
	//Write all modified entries to disk.
	public void flush(){
		//System.err.println("Flushing entries. "+modif);
		var failures = new ArrayList<String>();
		modif.forEach(key->{
			try{
				writeThrough(key);
			}catch(IOException e){
				failures.add(key);
			}
		});
		modif.clear();
		if(!failures.isEmpty()){
			//System.err.println("WARNING: Write failures "+failures);
			modif.addAll(failures);
		}
	}
	//Used on evict. Must succeed or evict will not happen.
	public void flushOne(String key){
		//System.err.println("Flushing entry. "+key);
		try{
			writeThrough(key);
			modif.remove(key);
		}catch(IOException e){
			throw new RuntimeException(e);//not a warning, since data could be lost
		}
	}
	private void writeThrough(String key) throws IOException{
		var val = cache.get(key);
		var path = Path.of(key);
		if(val == NOT_PRESENT){
			Files.deleteIfExists(path);
		}else{
			Files.createDirectories(path.getParent());
			Files.writeString(path, val.toString());
		}
	}
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
		var res =  compute(url, x -> UNCHANGED);
		return res == NOT_PRESENT ? null : res;
	}

	@Override
	public boolean has(String url) {
		return compute(url, x -> UNCHANGED) != NOT_PRESENT;
	}

	@Override
	public boolean put(String url, JSONObject payload) {
		compute(url, x->payload);
		return true;
	}
	
	@Override
	public JSONObject update(String url, UnaryOperator<JSONObject> update) {
		var res =  compute(url, update);
		return res == NOT_PRESENT ? null : res;
	}

	@Override
	public boolean delete(String url) {
		AtomicBoolean PRESENT = new AtomicBoolean();
		var res =  compute(url, x ->{
			PRESENT.setPlain(x != null);
			return null;
		});
		return PRESENT.getPlain();
	}
}
//public static class DBObjectStore ?!?!!
//public static class S3ObjectStore ?!???!?!?!?!?!

%>