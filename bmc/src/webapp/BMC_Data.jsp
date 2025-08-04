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
<%@ page import="javax.sql.*,javax.naming.InitialContext,javax.servlet.http.*,javax.servlet.*"%>
<%!
public static class Defaults{
	public static JSONObject CORE = new JSONObject().put("core",new JSONObject());
}
/**does stuff like putCity(0,{},..)*/
public static class BMCData{
	private final ObjectStore store;
	public BMCData(ObjectStore store){
		this.store = store;
	}
	public JSONObject getCore(int userID){
		return store.get(List.of("monkeyCity", ""+userID, "core"), Defaults.CORE);
	}
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
		return info==null? null: 
			new JSONObject()
				.put("cityInfo",info)
				.put("content",content)
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
	public JSONObject getCore(int userID, int cityID){
		return store.get(List.of("monkeyCity", ""+userID, "core"), Defaults.CORE);
	}
	private static JSONObject DEFAULT_CRATES(){
		return new JSONObject()
				.put("own",0)
				.put("requested", new JSONArray())
				.put("sent", new JSONArray())
				.put("pending", new JSONArray())
				.put("received", new JSONArray());
	}
	public boolean useCrate(int userID){
		return modifyCrates(userID, -1);
	}
	public boolean modifyCrates(int userID, int n){
		return store.update(List.of("monkeyCity", ""+userID, "core"),
			core->{
				var crates = core.optJSONObject("crates");
				if(crates==null)
					crates=DEFAULT_CRATES();
				core.put("crates", crates.put("own",crates.optInt("own",0) + n));
				return core;
			});
	}
	public JSONObject getCrates(int userID){
		var crates =  getCore(userID).optJSONObject("crates");
		return crates==null ? DEFAULT_CRATES() : crates;
	}
	//TODO: implement
	public JSONObject getCTHistory(int userID, int cityID){
		//contains room.id and stuff of last week's event
		return new JSONObject();
	}
	public JSONObject newCTRoom(int level, int cityID, JSONObject payload){
		String roomID = "" + ThreadLocalRandom.current().nextLong();
		var newRoom = new JSONObject()
				.put("contestedTerritory", 
					new JSONObject()
						.put("cities", new JSONArray())
						.put("score",new JSONObject())
						.put("data",payload.get("data"))
						.put("roomID", roomID)
						.put("levelTier",ctTier(level))
						.put("minRounds",ctMinRound(level))
						.put("lastLootTime",System.currentTimeMillis())
				);
		return newRoom;
	}
	private static int week(long millis){
		return (int)((millis/(1000*60*60*24) - 4) /7);
	}
	//TODO: seems to always give 200 on game start due to sanity check failing
	public JSONObject lootCT(int userID, int cityID, String roomID, JSONObject payload){
		AtomicReference<JSONObject> ret = new AtomicReference<>();
		store.update(List.of("monkeyCity","contest",""+cityID,"rooms", roomID), room->{
			var ct = room.getJSONObject("contestedTerritory");
			var cities = ct.getJSONArray("cities");
			if(jStream(cities).anyMatch(x->x.getInt("userID") == userID)){// verify if in ct room
				ct.put("lastLootTime", payload.getLong("lootTime"));
			}
			ret.setOpaque(room);
			updateDurations(ct.getJSONObject("score"), ct.getInt("minRounds"));
			return room;
		});
		return ret.getOpaque();
	}
	//for cases where bloons retook while someone was leader
	//problem - what if they submitted bad scores during that time???
	public void rollExtraTime(JSONObject scores, int minRounds){
		long now = System.currentTimeMillis();
		int leader = findLeader(scores, minRounds);
		scores.keySet().stream()
			.filter(x -> {
				long time = scores.getJSONObject(x).optLong("time") ;
				return time > 0;
			})
			.filter(x -> !x.equals(""+leader))
			.forEach(id->{
				JSONObject score = scores.getJSONObject(""+id);
				long durationWithoutCurrent = Math.max(24*3600*1000, now - score.getLong("time"))
						+ score.optLong("durationWithoutCurrent");
				score
					.put("durationWithoutCurrent", durationWithoutCurrent)
					.put("current", 0)
					.put("time", 0);
			});
	}
	public void updateDurations(JSONObject scores, int minRounds){
		long now = System.currentTimeMillis();
		int leader = findLeader(scores, minRounds);
		for(String id: scores.keySet()){
			JSONObject score = scores.getJSONObject(""+id);
			long time = score.getLong("time");
			long durationTime = score.getLong("durationTime");
			long duration = score.optLong("durationWithoutCurrent") + 
					(id.equals(""+leader) ? (now - Math.max(time, durationTime)) : 0);
			score
				.put("duration", duration);
		}
	}
	//assumes newScore was not previously the leader
	public boolean becomesLeader(JSONObject scores, JSONObject newScore, int minRounds){
		int score = newScore.optInt("score");
		long time = newScore.optLong("time");
		long now = System.currentTimeMillis();
		if(score < minRounds || (now-time) >= 24 * 3600 * 1000) return false;
		int leader = findLeader(scores, minRounds);
		return leader < 0 || scores.getJSONObject(""+leader).optInt("current") < score;
	}
	//assumes time was already updated
	//now need a fn to determine if a new score would become the leader
	public int findLeader(JSONObject scores, int minRounds){
		long now = System.currentTimeMillis();
		return scores.keySet().stream()
				.filter(x -> {
					long time = scores.getJSONObject(x).optLong("time") ;
					return time > 0 && (now - time) < 24 * 3600 * 1000;
				})
				.filter(x -> {
					int round = scores.getJSONObject(x).optInt("current") ;
					return round >= minRounds;
				})
				.sorted(Comparator.comparing(x -> -scores.getJSONObject(x).optInt("current")))
				.mapToInt(Integer::parseInt)
				.findFirst()
				.orElse(-1);
	}
	//TODO: just use players list for verification instead of 2nd store check
	public JSONObject updateCTScore(int userID, int cityID, String roomID, JSONObject payload){
		AtomicReference<JSONObject> ret = new AtomicReference<>();
		store.update(List.of("monkeyCity","contest",""+cityID,"rooms", roomID), room->{
			//we need to return the entire new queue object, while extracting the new/found room
			int score = payload.optInt("score");
			long time = payload.optLong("time");
			boolean pb = payload.optBoolean("isPersonalBest");
			double lootTimeOffset = payload.optDouble("lootTimeOffset");
			var ct = room.getJSONObject("contestedTerritory");
			int minRounds = ct.getInt("minRounds");
			var cities = ct.getJSONArray("cities");
			if(jStream(cities).anyMatch(x->x.getInt("userID") == userID)){// verify if in ct room
				var scores = ct.getJSONObject("score");
				var myScore = scores.optJSONObject(""+userID, new JSONObject());
				int leader = findLeader(scores, minRounds);
				
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
				rollExtraTime(scores, minRounds);
				if(leader != userID){
					if(becomesLeader(scores, payload, minRounds)){
						System.out.println("NL -> L");
						myScore
							.put("durationTime", time)
							.put("time", time);
						//ct.put("lastLootTime", time);
						if(leader >= 0){
							var oldLeader = scores.getJSONObject(""+leader);
							long durationWithoutCurrent = (time - oldLeader.getLong("time"))
									+ oldLeader.optLong("durationWithoutCurrent");
							oldLeader
								.put("durationWithoutCurrent", durationWithoutCurrent)
								.put("current", 0)
								.put("time", 0);
						}
					}else{
						System.out.println("NL -> NL");
						//already handled below...
					}
				}else{
					System.out.println("L -> L");
					//get more time, update durationTime so that way we don't count too much
					if(score > myScore.optInt("current")){
						System.out.println("L -> LL");
						long previousDuration = time - myScore.getLong("durationTime");
						long durationWithoutCurrent = previousDuration + myScore.optLong("durationWithoutCurrent");
						myScore
							.put("time", time)
							.put("durationTime", time)
							.put("durationWithoutCurrent", durationWithoutCurrent);
						//current set below
					}
					else //do nothing
						score = myScore.optInt("current");
				}
				//ct.put("lootTimeOffset", lootTimeOffset)
				scores.put(""+userID, myScore);
				updateDurations(scores, minRounds);
				ct.put("lootTimeOffset", lootTimeOffset);
				myScore
					.put("best",Math.max(score, myScore.optInt("best")))
					.put("current", score)
					.put("durationWithoutCurrent", myScore.optLong("durationWithoutCurrent"))
					.put("durationTime", myScore.optLong("durationTime"))
					.put("duration", myScore.optLong("duration"))
					.put("time", myScore.optLong("time"));	
			}
			ret.setOpaque(room);
			return room;
		});
		return ret.getOpaque();
	}
	public JSONObject getCTScores(int userID, int cityID, String roomID){
		var room = store.get("monkeyCity","contest",""+cityID,"rooms", roomID);
		var ct = room.getJSONObject("contestedTerritory");
		var cities = ct.getJSONArray("cities");
		if(jStream(cities).anyMatch(x->x.getInt("userID") == userID)){
			var scores = store.get("monkeyCity","contest",""+cityID,"rooms", roomID)
					.getJSONObject("contestedTerritory")
					.getJSONObject("score");
			//note - doesn't actually perform store update(just need client to see durations)
			updateDurations(scores, ct.getInt("minRounds"));
			return ct;
		}
		return new JSONObject();
	}
	public void addCTPlayer(JSONObject room, int userID, JSONObject player){
		var cities = room.getJSONObject("contestedTerritory").getJSONArray("cities");
		if(jStream(cities).noneMatch(x->x.getInt("userID") == userID))
			cities
				.put(new JSONObject()
					.put("userName",player.get("userName"))
					.put("userID",userID)
					.put("cityLevel",player.get("cityLevel"))
					.put("cityName",player.get("cityName"))
				);
	}
	public JSONObject addCTPlayerToRoom(int userID, int cityID, String roomID, JSONObject payload){
		AtomicReference<JSONObject> ret = new AtomicReference<>();
		store.update(List.of("monkeyCity","contest",""+cityID,"rooms", roomID), room->{
			//we need to return the entire new queue object, while extracting the new/found room
			addCTPlayer(room, userID, payload);
			ret.setOpaque(room);
			return room;
		});
		return ret.getOpaque();
	}
	public JSONObject joinCT(int userID, int cityID, JSONObject payload){
		int level = payload.getInt("cityLevel");
		int tier = ctTier(level);
		AtomicReference<JSONObject> ret = new AtomicReference<>();//extracted room object
		AtomicReference<String> retString = new AtomicReference<>();//extracted room id
		//this should contain info about if the room in question is expired, etc
		var room = store.get(List.of("monkeyCity",""+userID,"contest",""+cityID));
		String roomID;
		if(room==null || week(room.optLong("at")) != week(System.currentTimeMillis())){
			//then check queue
			//TODO: queue needs to reset and store 
			store.update(List.of("monkeyCity","contest",""+cityID,"queue"),queue->{
				//we need to return the entire new queue object, while extracting the new/found room
				if(queue == null)
					queue=new JSONObject();
				var qRoom = queue.optJSONObject(""+tier);
				int players = 0;
				if(qRoom == null || week(qRoom.optLong("at")) != week(System.currentTimeMillis())){
					//create the room
					players = 0;
					JSONObject newRoom = newCTRoom(level, cityID, payload);
					ret.setOpaque(newRoom);
					String newRoomID = newRoom.getJSONObject("contestedTerritory").getString("roomID");
					qRoom = new JSONObject()
							.put("id",newRoomID)
							.put("players",0)
							.put("at", System.currentTimeMillis());
					queue.put(""+tier, qRoom);
				}
				String newRoomID = qRoom.getString("id");
				players = qRoom.optInt("players")+1;
				qRoom.put("players", players);
				if(players>=6)
					queue.remove(""+tier);
				retString.setOpaque(newRoomID);
				//ret.setOpaque(newRoom);
				return queue;
			});
			//if a new room was created, store it before adding the player
			if(ret.getOpaque() != null){
				roomID = ret.getOpaque().getJSONObject("contestedTerritory").getString("roomID");
				store.put(List.of("monkeyCity","contest",""+cityID,"rooms", roomID), ret.getOpaque());
			}
			//room was reused, extract id
			else if(retString.getOpaque() != null){
				roomID = retString.getOpaque();
			}else{
				throw new IllegalStateException("should have either ret or retString");
			}
		} else {
			roomID = room.getString("roomID");
		}
		ret.setOpaque(addCTPlayerToRoom(userID, cityID, roomID, payload));
		var ct = ret.getOpaque().getJSONObject("contestedTerritory");
		updateDurations(ct.getJSONObject("score"), ct.getInt("minRounds"));
				
		// user -> room id
		store.put(List.of("monkeyCity",""+userID,"contest",""+cityID),
				new JSONObject()
					.put("roomID", roomID)
					.put("at",System.currentTimeMillis())
				);
		return ret.getOpaque();
		//this would be the "create ct" thing, if no matching room was found
		
			
	}
	public static int ctMinRound(int level){
		int tier = ctTier(level);
		/**TODO: SET TO 1 FOR TESTING*/
		return 0==0?1:
			switch(tier){
			case 1, 2, 3, 4 -> 2 + tier * 4;
			case 5 -> 22;
			default -> 24 + (tier-6); //6-9
		};
	}
	public static int ctTier(int level){
		return Math.min(9, (level-5)/4 + 1);
	}
	public JSONObject getCityThing(int userID, int cityID, String thing){
		return store.get("monkeyCity", ""+userID, "cities", ""+cityID, thing);
	}
	public static Iterable<JSONObject> jIter(JSONArray array){
		return (Iterable<JSONObject>)(()->Spliterators.iterator(jStream(array).spliterator()));
	}
	public static Stream<JSONObject> jStream(JSONArray array){
		return IntStream.range(0,array.length())
		.mapToObj(array::getJSONObject);
	}
	private static long key(JSONObject tile){
		return ((long)tile.getInt("x")<<32)|(tile.getInt("y")&-1L);
	}
	public static JSONObject mergeContent(JSONObject content, JSONObject update){
		if(content==null)
			content = new JSONObject();
		var tiles = content.optJSONArray("tiles", new JSONArray());
		var newTiles = update.optJSONArray("tiles", new JSONArray());
		var updateContent = update.optJSONObject("content", new JSONObject());
		for(String key:updateContent.keySet()){
			if(!key.equals("tiles"))
				content.put(key,updateContent.get(key));
		}
		var tileMap = jStream(tiles).collect(Collectors.toMap(BMCData::key, x->x, (x,y)->y));
		for(var tile: jIter(newTiles)){
			long key = key(tile);
			var oldTile = tileMap.get(key);
			if(oldTile==null)
				tiles.put(tile);
			else
				oldTile.put("tileData",tile.getString("tileData"));
		}
		content.put("tiles",tiles);
		return content;
	}
	//cityName INDEX LEVEL XP
	public static JSONObject mergeInfo(JSONObject info, JSONObject update){
		if(info==null)
			info = new JSONObject();
		var change = update.getJSONObject("cityInfoChange");
		if(change!=null)
			info.put("cityName",update.get("cityName"))
				.put("level", update.get("cityLevel")) 
				.put("xp", info.optInt("xp") + change.optInt("xp"))
				.put("xpDebt", info.optInt("xpDebt") + change.optInt("xpDebt"))
				.put("honour", info.optInt("honour") + change.optInt("honour"));
		return info;
	}
	public static JSONObject mergeCore(JSONObject core, JSONObject update){
		if(core==null)
			core = new JSONObject();
		for(String topKey: List.of("core", "monkeyKnowledge", "crates")){
			JSONObject oldCore = core.optJSONObject(topKey, new JSONObject());
			JSONObject newCore = update.optJSONObject(topKey);
			if(newCore!=null){
				for(String key:newCore.keySet()){
					oldCore.put(key,newCore.get(key));
				}
			}
			core.put(topKey, oldCore);
		}
		return core;
	}
	public boolean updateCore(int userID, JSONObject payload){
		return store.update(List.of("monkeyCity", ""+userID, "core"), x->mergeCore(x, payload));
	}
	public boolean updateContent(int userID, int cityID, JSONObject payload){
		return store.update(List.of("monkeyCity", ""+userID, "cities", ""+cityID, "content"), x->mergeContent(x, payload));
	}
	public boolean updateInfo(int userID, int cityID, JSONObject payload){
		return store.update(List.of("monkeyCity", ""+userID, "cities", ""+cityID, "info"), x->mergeInfo(x, payload));
	}
	public boolean putCityThing(int userID, int cityID, String thing, JSONObject payload) {
		return switch (thing) {
			case "content" -> updateContent(userID, cityID, payload);
			case "info" -> updateInfo(userID, cityID, payload);
			default -> store.put(List.of("monkeyCity", "" + userID, "cities", "" + cityID, thing), payload);
		};
	}
}

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

	public default boolean update(Iterable<String> url, UnaryOperator<JSONObject> update) {
		return update(String.join("/", url), update);
	}

	public default boolean delete(String... url) {
		return delete(String.join("/", url));
	}

	public default boolean delete(Iterable<String> url) {
		return delete(String.join("/", url));
	}

	public JSONObject get(String url);

	public boolean has(String url);

	public boolean delete(String url);

	public boolean put(String url, JSONObject payload);

	public default boolean update(String url, UnaryOperator<JSONObject> update) {
		return put(url, update.apply(get(url)));
	}
}

/**uses b64's of the urls so it is always in the same folder*/
public static class FileObjectStore implements ObjectStore {
	private final Path root;
	//we lock using this, without ever adding to it
	private final ConcurrentMap<String, Void> urlLock = new ConcurrentHashMap<>(1024*1024);

	public FileObjectStore(Path root) throws IOException {
		if (!Files.exists(root))
			Files.createDirectories(root);
		if (!Files.isDirectory(root))
			throw new IllegalArgumentException("Not a dir: " + root);
		this.root = root;
	}

	public List<String> dump(){
		try{
			return Files.walk(root, 2).filter(Files::isRegularFile)//.peek(System.out::println)
				.map(x -> {
					try{
					return x.getParent().getFileName().toString() + "->"
						+ new String(Base64.getDecoder().decode(x.getFileName().toString().trim()), UTF_8)
						+ " -> "
						+ Files.readString(x);
					}catch(IOException e){
						return "";
					}
				}).toList();
		}catch(IOException e){throw new RuntimeException(e);}
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
				holder.setOpaque(new JSONObject(Files.readString(path)));
			} catch (IOException e) {
				holder.setOpaque(null);
			}
			return null;
		});
		return holder.getOpaque();
	}

	@Override
	public boolean has(String url) {
		Path path = map(url);
		AtomicReference<Boolean> holder = new AtomicReference<>();//for stupid lambda thing
		urlLock.compute(path.toString(), (k, v) -> {
			holder.setOpaque(Files.exists(path));
			return null;
		});
		return holder.getOpaque();
	}

	@Override
	public boolean put(String url, JSONObject payload) {
		Path path = map(url);
		AtomicReference<Boolean> holder = new AtomicReference<>();//for stupid lambda thing
		urlLock.compute(path.toString(), (k, v) -> {
			try {
				Files.createDirectories(path.getParent());
				Files.writeString(path, payload.toString());
				holder.setOpaque(true);
			} catch (IOException e) {
				holder.setOpaque(false);
			}
			return null;
		});
		return holder.getOpaque();
	}

	@Override
	public boolean update(String url, UnaryOperator<JSONObject> update) {
		Path path = map(url);
		AtomicReference<Boolean> holder = new AtomicReference<>();//for stupid lambda thing
		System.out.println("-->"+path);
		urlLock.compute(path.toString(), (k, v) -> {
			try {
				JSONObject input = Files.exists(path) ? new JSONObject(Files.readString(path)) : null;
				Files.createDirectories(path.getParent());
				Files.writeString(path, update.apply(input).toString());
				holder.setOpaque(true);
			} catch (IOException e) {
				holder.setOpaque(false);
			}
			return null;
		});
		return holder.getOpaque();
	}

	@Override
	public boolean delete(String url) {
		Path path = map(url);
		AtomicReference<Boolean> holder = new AtomicReference<>();//for stupid lambda thing
		urlLock.compute(path.toString(), (k, v) -> {
			try {
				Files.delete(path);
				holder.setOpaque(true);
			} catch (IOException e) {
				holder.setOpaque(false);
			}
			return null;
		});
		return holder.getOpaque();
	}
}
//public static class DBObjectStore ?!?!!
//public static class S3ObjectStore ?!???!?!?!?!?!
%>
<%

var fos = new FileObjectStore(Path.of("./objects"));
%><%=
fos.map("monkeyCity/24095321/cities")
%><%=
fos.get("monkeyCity/24095321/cities/0/content")
%><%=
fos.dump()
%>