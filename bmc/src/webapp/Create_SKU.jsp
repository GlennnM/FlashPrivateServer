<%@page import="java.time.Month"%>
<%@page import="java.util.Set"%>
<%@page import="java.util.stream.Collectors"%>
<%@page import="java.util.Comparator"%>
<%@page import="java.security.NoSuchAlgorithmException"%>
<%@page import="java.util.Arrays"%>
<%@page import="java.math.BigInteger"%>
<%@page import="java.util.HexFormat"%>
<%@page import="java.nio.ByteBuffer"%>
<%@page import="java.nio.file.Path"%>
<%@page import="java.nio.file.Files"%>
<%@page import="java.io.IOException"%>
<%@page import="java.util.stream.IntStream"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.nio.charset.StandardCharsets"%>
<%@page import="java.util.HashMap"%>
<%@page import="xyz.hydar.ee.HydarEE.HttpServletRequest"%>
<%@page import="java.util.List"%>
<%@page import="java.time.ZoneOffset"%>
<%@page import="java.util.Map"%>
<%@page import="java.time.Instant"%>
<%@page import="org.json.JSONObject"%>
<%@page import="org.json.JSONArray"%>
<%@page import="java.sql.Connection"%>
<%@page import="java.security.MessageDigest"%>
<%@page import="javax.naming.InitialContext"%>
<%@page import="javax.sql.DataSource"%>
<%@page import="java.time.temporal.ChronoUnit"%>
<%@page import="java.util.Calendar"%>
<%@page import="java.util.TimeZone"%>
<%@page import="java.time.ZoneId"%>
<%@page import="java.time.LocalDateTime"%>
<%!private static final LocalDateTime FEB_3_2025;//sas4 origin
 private static final LocalDateTime APR_30_2025;//bmc origin
	private static final ZoneId MINUS8 = ZoneId.of("GMT-8");
	public static volatile JSONArray basicEvents7 = null;
	public static volatile JSONArray basicEvents14 = null;
	static {
		TimeZone MINUS8 = TimeZone.getTimeZone("GMT-8");
		Calendar c = Calendar.getInstance(MINUS8);
		
		c.set(2025, 1, 3, 0, 0, 0);
		FEB_3_2025 = LocalDateTime.ofInstant(c.toInstant(), ZoneId.of("GMT-8"));
		
		c.set(2025, 3, 30, 0, 0, 0);
		APR_30_2025 = LocalDateTime.ofInstant(c.toInstant(), ZoneId.of("GMT-8"));
	}

	public static int offset(LocalDateTime day, LocalDateTime origin) {
		//LocalDateTime now=LocalDateTime.now(MINUS8);
		return (int) ChronoUnit.DAYS.between(origin, day) * (day.isAfter(origin) ? 1 : -1);
	}

	//1. getEventMap give list of json objects ready to go for a day
	//2. if there were already events on that day, use those instead
	//3. we don't care about things older than before startDay at all, but will add them from old SKU regardless
	//new idea: "special events" file that will override what comes from here
	public static List<JSONObject> getEvents(HttpServletRequest request, int startDay, int endDay, int repeat, LocalDateTime origin, JSONArray basicEvents) {//ie -90, 14
		LocalDateTime startTime = LocalDateTime.now(MINUS8).plusDays(startDay);
		LocalDateTime endTime = LocalDateTime.now(MINUS8).plusDays(endDay);
		int modifyStartBy = offset(startTime, origin) / repeat;
		int modifyEndBy = offset(endTime, origin) / repeat;
		List<JSONObject> finalEvents = new ArrayList<>();
		for (int offsetBy = modifyStartBy - 1; offsetBy <= modifyEndBy + 1; offsetBy++) {
			for (int i = 0; i < basicEvents.length(); i++) {
				JSONObject event = new JSONObject(basicEvents.getJSONObject(i).toString());
				LocalDateTime eventStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getLong("start")), MINUS8)
						.plusDays(offsetBy * repeat);
				LocalDateTime eventEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getLong("end")), MINUS8)
						.plusDays(offsetBy * repeat);
				if (eventEnd.isAfter(startTime) && eventStart.isBefore(endTime)) {
					String id = event.getString("id");
					event.put("id", offsetBy == 0 ? id : "NEW" + offsetBy + "_" + id);
					event.put("start", eventStart.toInstant(MINUS8.getRules().getOffset(eventStart)).toEpochMilli());
					event.put("end", eventEnd.toInstant(MINUS8.getRules().getOffset(eventEnd)).toEpochMilli());
					finalEvents.add(event);
				}
			}
		}
		// need millis for all events that should start today
		// are alls tarts at gmt-8 0?
		return finalEvents;
		//short[] vs=EVENT_MAP_SETS[4];

		/**
		return switch(eventOffset % 21){
			case 4,10,15,17,19->vs[FlashUtils.xorRange(seed,vs.length)];
			case 14 -> 3;
			case 16 -> 2;
			case 18 -> 1;
			case 20 -> 0;
			default -> 0;
		};*/
	}
	//a

	private static int o333(int param1) {
		int _loc3_ = 0;
		int _loc2_ = param1;
		_loc3_ = 0;
		while (_loc3_ < 8) {
			if ((_loc2_ & 1) != 0) {
				_loc2_ >>= 1;
				_loc2_ ^= 3988292384l;
			} else {
				_loc2_ >>= 1;
			}
			_loc3_++;
		}
		return _loc2_;
	}

	private static String getHexCrc(String param1) {
		int _loc6_ = 0;
		int _loc2_ = 0;
		int _loc3_ = 0;
		int _loc7_ = 0;
		int _loc5_ = param1.length();
		_loc6_ = 0;
		while (_loc6_ < _loc5_) {
			_loc2_ = (param1.codePointAt(_loc6_));
			_loc3_ = ((_loc7_ ^ _loc2_) & 0xFF);
			_loc7_ = (_loc7_ >>> 8 & 0xFFFFFF ^ o333(_loc3_));
			_loc6_++;
		}
		if (_loc7_ < 0) {
			_loc7_ = (int) (4294967295l + _loc7_ + 1);
		}
		String loc4 = Integer.toHexString(_loc7_);
		while (loc4.length() < 8) {
			loc4 = "0" + loc4;
		}
		return loc4;
	}

	public static byte[] encrypt(String param0) {
		byte[] _param0 = param0.getBytes(StandardCharsets.UTF_8);
		byte[] destinationArray = new byte[14 + _param0.length];
		byte[] bytes = ("DGDATA" + getHexCrc(param0)).getBytes(StandardCharsets.UTF_8);
		System.out.println("DGDATA" + getHexCrc(param0));
		//System.ar((Array) bytes, (Array) destinationArray, bytes.Length);
		System.arraycopy(bytes, 0, destinationArray, 0, bytes.length);
		for (int index = 0; index < _param0.length; ++index)
			destinationArray[index + 14] = (byte) ((_param0[index] + 21 + index % 6) % 256);
		return destinationArray;
	}

	public static String decrypt(byte[] sku_enc) {
		var sku_dec = new byte[sku_enc.length];
		for (int i = 0; i < sku_enc.length; i++) {
			int offset = (i - 14) % 6;
			sku_dec[i] = (byte) (sku_enc[i] - (21 + offset));
		}
		return new String(sku_dec, 14, sku_dec.length - 14, StandardCharsets.UTF_8);
	}
	public static void createSKU(int id, String sig, HttpServletRequest request)
			throws IOException, NoSuchAlgorithmException {
		switch(id){
		case 7:
			createSKU(id,"/99d5c454171a3f5027a0563eb784a366.json", sig, -8, 8, 28, request, APR_30_2025, basicEvents7);
			break;
		case 14:
			createSKU(id,"/9a0a8254da299947492b4e1eb15291f1.json", sig, -30, 14, 63, request, FEB_3_2025, basicEvents14);
			break;
		}
	}
	/**
	* Create SKU file, will be saved to file given by URL
	* Should be run at least every few days on requests (use ctx.consumeSKU after)
	* not all requests since that would make lag
	*/
	public synchronized static void createSKU(int id, String url, String sig, int start, int end, int repeat, HttpServletRequest request, LocalDateTime origin, JSONArray basicEvents)
			throws IOException, NoSuchAlgorithmException {
		var sku_enc = request.getServletContext().getResourceAsStream("/" + id + "_ORIGIN.json").readAllBytes();
		var sku_dec = decrypt(sku_enc);
		String data3 = new String(sku_dec);
		System.out.println(data3.substring(0, data3.indexOf("{") + 1));
		var tmp = new JSONObject(data3.substring(data3.indexOf("{")));
		var sku_real = new JSONObject(tmp.getString("data"));
		var events = sku_real.getJSONObject("settings").getJSONArray("events");
		List<JSONObject> newEvents = getEvents(request, start, end, repeat, origin, basicEvents);
		newEvents.addAll(IntStream.range(0, events.length()).mapToObj(events::getJSONObject)
				.filter(x -> x.getLong("end") > System.currentTimeMillis()).toList());
		sku_real.getJSONObject("settings").put("events", new JSONArray(newEvents));

		if(id == 14){//sas4
			var h = (JSONObject) (sku_real.query("/settings/Settings/FestivalHoliday/Halloween"));
			var c = (JSONObject) (sku_real.query("/settings/Settings/FestivalHoliday/Christmas"));
			var e = (JSONObject) (sku_real.query("/settings/Settings/FestivalHoliday/Easter"));
			var ldt = LocalDateTime.now(ZoneId.of("America/New_York"));
			if(ldt.getMonth() == Month.OCTOBER){
				h.put("Active",true);
			}else if((ldt.getMonth() == Month.NOVEMBER && ldt.getDayOfMonth() > 28) ||
					ldt.getMonth() == Month.DECEMBER || 
					(ldt.getMonth() == Month.JANUARY && ldt.getDayOfMonth() < 3)){
				c.put("Active",true);
			}else if(ldt.getMonth() == Month.APRIL || 
					(ldt.getMonth() == Month.MARCH && ldt.getDayOfMonth() > 14)){
				e.put("Active",true);
			}
		}

		tmp.put("data", sku_real.toString());
		var MD = MessageDigest.getInstance("MD5");
		MD.update(sig.getBytes());
		MD.update(tmp.getString("data").getBytes(StandardCharsets.UTF_8));
		tmp.put("sig", HexFormat.of().formatHex(MD.digest()));

		byte[] sku_out = encrypt(tmp.toString());
		Files.write(Path.of(request.getServletContext().getRealPath(url)), sku_out);
		//Files.write(Path.of("test.json"),sku_out);
		//Files.writeString(Path.of("test2.json"),decrypt(sku_out));

		//EXPORT

	}%>
	<%
if (basicEvents7 == null)
	basicEvents7 = new JSONArray(
	new String(request.getServletContext().getResourceAsStream("/7_basic_events.json").readAllBytes(),
			StandardCharsets.UTF_8));
if (basicEvents14 == null)
	basicEvents14 = new JSONArray(
	new String(request.getServletContext().getResourceAsStream("/14_basic_events.json").readAllBytes(),
			StandardCharsets.UTF_8));
	%>
<%--

//createSKU(14,request);
List<JSONObject> finalEvents = getEvents(request, -120, 120, 63, FEB_3_2025, basicEvents14);
out.println(finalEvents);

//out.println("<br>");
//for(var j:finalEvents){ 
createSKU(7,"",request);
byte[] sku_enc = request.getServletContext().getResourceAsStream("/99d5c454171a3f5027a0563eb784a366.json").readAllBytes();
var sku_dec = decrypt(sku_enc);
String data3 = new String(sku_dec);
var tmp = new JSONObject(data3.substring(data3.indexOf("{")));
var sku_real = new JSONObject(tmp.getString("data"));
var events = sku_real.getJSONObject("settings").getJSONArray("events");


var eventList = IntStream.range(0,events.length())
	.mapToObj(events::getJSONObject)
	.sorted(Comparator.comparing(x->x.getLong("start")))
	.toList();
//for(int i=0;i<events.length();i++){ 
//	JSONObject j = new JSONObject(events.getJSONObject(i).toString());
			//new JSONObject(((HashMap<String,Object>)basicEvents.getJSONObject(i).toMap()).clone() );
	//System.out.println(j);
/**Set<String> ids = Arrays.stream(
		"m8wtyl0h,m8wtyl39,m8wtyks5,m8wtykux,m8wtykml,m8wtykpd,m8wtykjt,m8wtyke9,m8wtykh1,m8wtyk8p,m8wtykbh,m8wtyk5x,m8wtyk0d,m8wtyk35,m8wtyjup,m8wtyjxl,m967sw6u,m967sw9m,m967sw1a,m967sw42,m967svyi,m967svsy,m967svvq,m967svne,m967svq6,m967svhu,m967svkm,m967svca,m967svf2,m967sv6q,m967sv9i,m967sv16,m967sv3y,m967susu,m967suvm,m967suye,m967suna,m967suq2,m967suhq,m967suki,m967suey,m967su9e,m967suc6,m967su3u,m967su6m,m967su12,m967stvi,m967stya,m967stpy,m967stsq,m967sthm,m967stke,m967stn6,m967steu,m967st9a,m967stc2,m967st6i,m967st0y,m967st3q,m967ssve,m967ssy6,m967sspu,m967sssm,m967sska,m967ssn2,m967sshi,m967ss96"
		//"m3y9ckvt,m3y9ckt1,m3y9cknh,m3y9cknh_MedalsEarned,m3y9ckq9,m3y9ckkp,m3y9ckhx,m3y9ckf5,m3y9ckcd,m3y9ck9l,m3y9ck6t,m3y9ck41,m3y9ck19,m3y9cjyh,m3y9cjsx,m3y9cjvp,m3y9cjnd,m3y9cjq5,m3y9cjht,m3y9cjkl,m3y9cjf1,m3y9cjc9,m3y9cj6p,m3y9cj9h,m3y9cj3x,m3y9cj15,m3y9civl,m3y9ciyd,m3y9ciq1,m3y9cist,m3y9cin9,m3y9cihp,m3y9cikh,m3y9ciex,m3y9cic5,m3y9ci6l,m3y9ci9d,m3y9ci9d_MedalsEarned,m3y9ci3t,m3y9ci11,m3y9chy9,m3y9chvh,m3y9chsp,m3y9chpx,m3y9chn5,m3y9chkd,m3y9chhl,m3y9chc1,m3y9chet,m3y9ch6h,m3y9ch99,m3y9ch0x,m3y9ch3p,m3y9cgy5,m3y9cgvd,m3y9cgpt,m3y9cgsl,m3y9cgn1,m3y9cgk9,m3y9cgep,m3y9cghh,m3y9cg95,m3y9cgbx,m3y9cg6d,m3y9cg0t,m3y9cg3l,m3y9cfy1,m3y9cfuz,m821gzyx,m821gzyx_MedalsEarned,m821gzw5,m821gztd,m821gzql,m821gznt,m821gzl1,m821gzi9,m821gzfh,m821gzcp,m821gz9x,m821gz75,m821gz1l,m821gz4d,m821gyw1,m821gyyt,m821gyqh,m821gyt9,m821gynp,m821gykx,m821gyfd,m821gyi5,m821gycl,m821gy9t,m821gy49,m821gy71,m821gxyp,m821gy1h,m821gxvx,m821gxqd,m821gxt5,m821gxnl,m821gxkt"
				.split(",")).collect(Collectors.toSet());*/
//response.resetBuffer();
for(var j:eventList){ 		 
	Instant eventStart = Instant.ofEpochMilli(j.getLong("start"));
	Instant eventEnd = Instant.ofEpochMilli(j.getLong("end"));
	out.println(j.getString("id"));
	out.println(j.getString("type"));
	out.println(j.getJSONObject("metadata").getString("dataID"));
	out.println(LocalDateTime.ofInstant(eventStart,ZoneId.of("GMT-8")));
	out.println(LocalDateTime.ofInstant(eventEnd,ZoneId.of("GMT-8"))+"<br>");
	//if(ids.contains(j.getString("id")))
	//	out.print(j+",");
}
--%>