package xyz.hydar.bmc;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openamf.AMFBody;
import org.openamf.AMFMessage;

import xyz.hydar.bmc.AMFService.NKVerifyException;

public class Profile {
	public static volatile ObjectStore store;
	public static final Pattern usernames = Pattern.compile("[\\w\\-_][\\w\\-_ ]{0,18}[\\w\\-_]");
	public static final Pattern avatars = Pattern.compile("[\\w\\-.]{1,20}");
	public static final int[] levelCutoffs = {0,50,100,150,200,250,325,400,500,600,750,900,1100,1300,1500,1700,1900,2100,2300,2500,2800,3100,
			3400,3700,4000,4400,4800,5200,5600,6000,6500,7000,7500,8500,9700,11000,12700,14500,16500,18500,20500,22500,24500,26500,
			28500,30500,32500,34500,36500,38500,40500,42500,44500,46500,48500,50500,53000,55500,58000};
	public static final List<String> clans = List.of("Black Cobras","Blue Wolves", "Dark Matter","Falcons","Iron Phoenix","Night Jackals","Red Storm","Scorpions","Shining Blade","The Watchers","Thunderbolts","White Tigers","XIII","Scorpions","Scorpions","Scorpions");

	static final JSONObject nk_ach = new JSONObject();
	public static final Set<String> games = Set.of("Battle Blocks Defense","Battle Panic","Battles","BSM2","BTD4","BTD5","Fortress: Destroyer","MonkeyCity","SAS TD","SAS3","SAS4","Tower Keepers");
	
	static final SecureRandom rng=new SecureRandom();
	public static void setStore(ObjectStore store) {
		Profile.store = store;
	}
	public static JSONObject get(String userID){
		return update(userID, x->FileObjectStore.UNCHANGED);
	}
	public static JSONObject update(String userID, UnaryOperator<JSONObject> update){
		return store.update(List.of("amf", userID, "info"),x->{
			if(x==null){
				x = blank(userID);
			}
			if(!x.has("currencies"))
				x.put("currencies", new JSONObject());
			var res = update.apply(x);
			return res == FileObjectStore.UNCHANGED ? x : res;
		});
	}
	 public static boolean isNKToken(String userID, String token){
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
				Thread.sleep(2000);
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
	 public static int getLevel(int ap) {
		 int lvl = Arrays.binarySearch(levelCutoffs, ap);
		 return lvl<0 ? -(lvl + 2) : lvl;
	 }
	 public static JSONObject blank(String userID){
		 return new JSONObject()
			.put("hydarUsername",JSONObject.NULL)
			.put("nkUsername",JSONObject.NULL)
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
			.put("currencies", new JSONObject())
			.put("following", new JSONArray())
			.put("friends", new JSONArray());
	 }
	 public static JSONObject linkNewNK(String username, String email, String password,String password2, String nkUserID, String token) {
		//if we don't save NK token, how does game determine which one to use???
		 //--> always use hashed NK token
		 //--> logging out of linked NK account will also log you out of hydar account
		 //--> logging in from hydar will use the hydar token, and not log you in on NK
		 verifyNK(nkUserID, token);
		 if(!isValid(username))throw new NKVerifyException("Username contains bad characters or length");
		 if(password.length()<8)throw new NKVerifyException("Password must be at least 8 characters");
		 if(!password.equals(password2))throw new NKVerifyException("Passwords do not match");
		 var success = new AtomicBoolean();
		 updateIndex(x->{
				if(x.has(username))
					throw new NKVerifyException("Username taken");
				if(x.toMap().values().stream().anyMatch(nkUserID::equals))
					throw new NKVerifyException("Already linked");
				x.put(username, nkUserID);
				success.setOpaque(true);
				return x;
		});
		 if(!success.getOpaque())throw new NKVerifyException("Link failure");
		 //set uid in index to nkuserid
		 var profile = update(nkUserID, x->{
			 return x.put("hydarUserID", nkUserID)
					 .put("password", Util.hash(username+password))
					 .putOpt("email", (email!=null && email.trim().length() != 0) ? email : null)
					 .put("hydarToken", newToken())
					 .put("hydarUsername", username);
		 });
		 return new JSONObject()
				 .put("id", profile.get("hydarUserID"))
				 .put("token", profile.get("hydarToken"))
				 .put("username", profile.get("hydarUsername")); 
	 }
	 public static JSONObject registerNew(String username, String email, String password, String password2) {
		 var success = new AtomicBoolean();
		 //TODO: make email unique
		 if(!isValid(username))throw new NKVerifyException("Username contains bad characters or length");
		 if(password.length()<8)throw new NKVerifyException("Password must be at least 8 characters");
		 if(!password.equals(password2))throw new NKVerifyException("Passwords do not match");
		 Util.sleep(1500);
		 var uid = updateIndex(x->{
			if(x.has(username))
				throw new NKVerifyException("Username taken");
			long maxUID = x.toMap().values().stream()
					.mapToLong(id->Long.parseLong((String)id)).max().orElse((int)1E8);
			maxUID = Math.max((int)1E8, maxUID);
			x.put(username, ""+(maxUID+1));
			
			success.setOpaque(true);
			return x;
		 }).optString(username);
		 if(!success.getOpaque())throw new NKVerifyException();
		 var profile = update(uid, x->{
			 return x.put("hydarUserID", uid)
					 .putOpt("email", (email!=null && email.trim().length() != 0) ? email : null)
					 .put("password", Util.hash(username+password))
					 .put("hydarToken", newToken())
					 .put("hydarUsername", username);
		 });
		 return new JSONObject()
				 .put("id", profile.get("hydarUserID"))
				 .put("token", profile.get("hydarToken"))
				 .put("username", profile.get("hydarUsername"));
	 }
	 public static boolean addFriend(String password, String token, String newPassword) {
		 //TODO: how get from username to an ID if not on hydar?
		 //-->when getInventory called, create thing, then change profile methods to use other methods of verifying hydar
		 //-->no, just require hydar login to add friend
		 return true;
	 }
	 //remove ava and clan sync(not needed now)
	 public static boolean changeClan(String userID, String token, int newClan) {
		 verifyNK(userID, token);
		 if(newClan>=0 && newClan<17) {
			update(userID, x->x.put("clan", newClan));
		 	return true;
		 }
		 return false;
	 }

	 public static boolean changeAvatar(String userID, String token, String avatar) {
		 verifyNK(userID, token);
		 if(avatars.matcher(avatar).matches()) {
			update(userID, x->x.put("avatar", avatar));
		 	return true;
		 }
		 return false;
	 }
	 public static String changePassword(String userID, String password, String token, String newPassword) {
		 if(password.length()<8)throw new NKVerifyException("Password must have at least 8 characters");
		 verifyNK(userID, token);
		 //on client: if token same -> failed
		 return update(userID, x->{
			 var hydarPW = x.optString("password");
			 var newPW = Util.hash(x.getString("hydarUsername")+password);
			 if(hydarPW==null || !hydarPW.equals(newPW))
				 return x;
			 return x.put("password", newPW)
					 .put("hydarToken", newToken());
		 }).getString("hydarToken");
	 }
	 public static boolean changeUsername(String username, String token, String newUsername) {
		 var uid = updateIndex(x->x).optString(username);
		 if(username == null || !isValid(newUsername) || !token.startsWith("hyd"))throw new NKVerifyException();
		 verifyNK(uid, token);
		 update(uid, x->{
			 return x.put("hydarUsername", newUsername);
		 });
		 return false;
	 }
	 public static JSONObject login(String username, String password) {
		 var uid = updateIndex(x->x).optString(username);
		 if(username == null)throw new NKVerifyException("User or password incorrect");
		 var profile = get(uid);
		 var hydarPW = profile.optString("password");
		 Util.sleep(500);
		 if(hydarPW == null || !hydarPW.equals(Util.hash(username+password)))
			 throw new NKVerifyException("User or password incorrect");
		 return new JSONObject()
				 .put("id", profile.get("hydarUserID"))
				 .put("token", profile.get("hydarToken"))
				 .put("username", profile.get("hydarUsername"));
		 
		 //if(Util.hash(username+password).equals())
	 }
	 public static String newToken() {
		 byte[] token = new byte[16];
		 rng.nextBytes(token);
		 return "hyd"+HexFormat.of().formatHex(token)+"r";
	 }
	 public static boolean isValid(String username) {
		 return usernames.matcher(username).matches() && username.length() < 20;
	 }
	 public static JSONObject updateIndex(UnaryOperator<JSONObject> update) {
		 //separate index for NK, otherwise someone could take your ign?
		 //solution: no adding NK acc as friend, since those usernames can be changed anyways
		return store.update("usernames", x->{
			 if(x==null)x= new JSONObject();
			 return update.apply(x);
		 });
	 }
	 public static boolean isHydarToken(String userID, String token) {
		 return token!=null && token.length()>30 && token.equals(get(userID).get("hydarToken"));
	 }
	 public static void verifyNK(String userID, String token){
			String hash = Util.hash(token);
			boolean[] success = {false};
			Long.parseLong(userID);
			update(userID, x->{
				if(x.get("nkToken") == JSONObject.NULL || !hash.equals(x.getString("nkToken"))){
					if(!token.startsWith("hyd") && isNKToken(userID, token)){ 
						x.put("nkToken", Util.hash(token))
							.put("userID", userID);
						success[0] = true;
					}else if(isHydarToken(userID, token)) {
						success[0] = true;
					}
				}else
					success[0] = true;
				return x;
			});
			if(!success[0])
				throw new NKVerifyException("Invalid token");
		}

		public static JSONArray getAchievements(String game, Path dataPath){
			if(nk_ach.isEmpty()){
				synchronized(nk_ach){
					for(String g:  Profile.games){
						try{
							Path f = dataPath.resolve(g.replace(":","") +".json");
							nk_ach.put(g, new JSONArray(Files.readString(f)));
						}catch(IOException ioe){
							ioe.printStackTrace();
						}
					}
				}
			}
			return nk_ach.getJSONArray(game);
		}
		public static JSONObject getAchProgress(String game, String userID) {
			if(!Profile.games.contains(game))
				return null;
			return store.get("amf", userID, game, "ach");
		}
		public static JSONArray getMyAchievements(String game, String userID, Path dataPath){
			if(!Profile.games.contains(game))
				return null;
			JSONArray j = new JSONArray(getAchievements(game, dataPath).toString());
			JSONObject myAch = store.get("amf", userID, game, "ach");
			
			for(var x: Util.jIter(j)){
				int perc = myAch==null ? 0 : myAch.optInt(""+x.getInt("id"));
				x.put("perc",(double) perc)
					.put("credited",perc>100)
					.put("userid",Double.parseDouble(userID));
			}//IO.println(j);
			return j;
		}
		public static JSONArray setAchievement(String userID, String token, String game, double ach_id, double perc, Path dataPath){
			if(!Profile.games.contains(game))
				return null;
			verifyNK(userID, token);
			JSONArray j = getAchievements(game, dataPath);
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
		private static void addAP(String userID, int ap){
			Profile.update(userID, x->x.put("ap",x.getInt("ap")+ap));
		}
}