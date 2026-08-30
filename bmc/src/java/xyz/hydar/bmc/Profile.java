package xyz.hydar.bmc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openamf.AMFBody;
import org.openamf.AMFMessage;

import xyz.hydar.bmc.AMFService.NKVerifyException;

public class Profile {
	public static volatile ObjectStore store;
	static final Pattern usernames = Pattern.compile("[\\w\\-_ ]*");

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

	 public static JSONObject blank(String userID){
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
			.put("currencies", new JSONObject())
			.put("following", new JSONArray())
			.put("friends", new JSONArray());
	 }
	 public static JSONObject linkExistingNK(String nkUserID, String token, String hydarUserID, String hydarToken) {
		 verifyNK(nkUserID, token);
		 //problem: they will have different UIDs
		 //solution: don't allow this at all
		 return null;
	 }
	 public static JSONObject linkNewNK(String username, String password, String nkUserID, String token) {
		//if we don't save NK token, how does game determine which one to use???
		 //--> always use hashed NK token
		 //--> logging out of linked NK account will also log you out of hydar account
		 //--> logging in from hydar will use the hydar token, and not log you in on NK
		 verifyNK(nkUserID, token);
		 if(!isValid(username))throw new NKVerifyException();
		 if(password.length()<8)throw new NKVerifyException();
		 var success = new AtomicBoolean();
		 updateIndex(x->{
				if(x.has(username))
					return x;
				if(x.toMap().values().stream().anyMatch(nkUserID::equals))
					return x;
				x.put(username, nkUserID);
				success.setOpaque(true);
				return x;
		}).optString("username");
		 if(!success.getOpaque())throw new NKVerifyException();
		 //set uid in index to nkuserid
		 var profile = update(nkUserID, x->{
			 return x.put("hydarUserID", nkUserID)
					 .put("password", Util.hash(username+password))
					 .put("hydarToken", newToken())
					 .put("hydarUsername", username);
		 });
		 return new JSONObject()
				 .put("userID", profile.get("hydarUserID"))
				 .put("token", profile.get("hydarToken"))
				 .put("username", profile.get("hydarUsername")); 
	 }
	 public static JSONObject registerNew(String username, String password) {
		 var success = new AtomicBoolean();
		 if(!isValid(username))throw new NKVerifyException();
		 if(password.length()<8)throw new NKVerifyException();
		 var uid = updateIndex(x->{
			if(x.has(username))
				return x;
			long maxUID = x.toMap().values().stream()
					.mapToLong(id->Long.parseLong((String)id)).max().orElse((int)1E8);
			maxUID = Math.max((int)1E8, maxUID);
			x.put(username, ""+(maxUID+1));
			
			success.setOpaque(true);
			return x;
		 }).optString("username");
		 if(!success.getOpaque())throw new NKVerifyException();
		 var profile = update(uid, x->{
			 return x.put("hydarUserID", uid)
					 .put("password", Util.hash(username+password))
					 .put("hydarToken", newToken())
					 .put("hydarUsername", username);
		 });
		 return new JSONObject()
				 .put("userID", profile.get("hydarUserID"))
				 .put("token", profile.get("hydarToken"))
				 .put("username", profile.get("hydarUsername"));
	 }
	 public static JSONObject login(String username, String password) {
		 var uid = updateIndex(x->x).optString("username");
		 if(username == null)throw new NKVerifyException();
		 var profile = get(uid);
		 var hydarPW = profile.optString("password");
		 if(hydarPW == null || !hydarPW.equals(Util.hash(username+password)))
			 throw new NKVerifyException();
		 return new JSONObject()
				 .put("userID", profile.get("hydarUserID"))
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
						x.put("nkToken", Util.hash(token));
						success[0] = true;
					}else if(isHydarToken(userID, token)) {
						success[0] = true;
					}
				}else
					success[0] = true;
				return x;
			});
			if(!success[0])
				throw new NKVerifyException();
		}
}
