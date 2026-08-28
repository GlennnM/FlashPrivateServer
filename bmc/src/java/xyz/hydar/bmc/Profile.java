package xyz.hydar.bmc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.json.JSONObject;
import org.openamf.AMFBody;
import org.openamf.AMFMessage;

import xyz.hydar.bmc.AMFService.NKVerifyException;

public class Profile {
	public static volatile ObjectStore store;
	public static void setStore(ObjectStore store) {
		Profile.store = store;
	}
	public static JSONObject get(String userID){
		return update(userID, x->FileObjectStore.UNCHANGED);
	}
	public static JSONObject update(String userID, UnaryOperator<JSONObject> update){
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
	 public static void verifyNK(String userID, String token){
			
			String hash = Util.hash(token);
			boolean[] success = {false};
			update(userID, x->{
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
}
