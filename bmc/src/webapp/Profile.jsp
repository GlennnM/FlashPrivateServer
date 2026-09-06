<%@page import="org.json.JSONArray"%>
<%@page import="java.util.LinkedHashMap"%>
<%@page import="xyz.hydar.bmc.Util"%>
<%int tab = 1; boolean bigHydar = false;%>
<%@include file = "BaseMenu.jsp"%>
<%!
static final String color(int lvl){
	return 
			lvl<10?"skyblue":
			lvl<20?"green":
			lvl<30?"yellow":
			lvl<40?"orange":
			lvl<50?"red":
			"gold";
}static final String color(String clan_or_game){
	return switch(clan_or_game){
		case "Black Cobras","Shining Blade","BTD5"->"yellow";
		case "Dark Matter"->"purple";
		case "Iron Phoenix","Tower Keepers","BSM2"->"orange";
		case "Night Jackals","Blue Wolves","Fortress: Destroyer"->"blue";
		case "Thunderbolts","Falcons"->"goldenrod";
		case "The Watchers", "BTD4"-> "white";
		case "MonkeyCity","Battle Panic"->"lightgreen";
		case "XIII","SAS TD"->"green";
		case "White Tigers","Battles"->"cyan";
		case "Red Storm","Scorpions","SAS3"->"red";
		case "SAS4"->"darkred";
		default->"red";
	};
}
static final String image(String clan){
	var c = URLEncoder.encode(clan.replace(" ","-").toLowerCase(), UTF_8);
	return "<img width=20px height=20px alt='%s' src='https://cdn.nkstatic.com/clans/shields/%s/thumb/%s.png' />"
			.formatted(c, c, c.replace("jackals","jackal").replace("scorp","the-scorp"));
}
%><script>function color(clan_or_game) {
    switch (clan_or_game) {
        case "Black Cobras":case "Shining Blade":case "BTD5":
            return "yellow";
        case "Dark Matter":
            return "purple";
        case "Iron Phoenix":case "Tower Keepers":case "BSM2":
            return "orange";
        case "Night Jackals":case "Blue Wolves": case "Fortress: Destroyer":
            return "blue";
        case "Thunderbolts":case "Falcons":
            return "goldenrod";
        case "The Watchers":case "BTD4":
            return "white";
        case "MonkeyCity":
        	return "lime";
        case "Battle Panic":
            return "lightgreen";
        case "XIII":case "SAS TD":
            return "green";
        case "White Tigers":case "Battles":
            return "cyan";
        case "SAS3":
        	return "salmon";
        case "Red Storm":case "Scorpions":case "SAS4":
            return "red";
        default:return "red";
    }
}</script><%
%>
<%if(loggedIn){
String target = request.getParameter("target");
String searchUser = request.getParameter("targetUsername");
String targetUserID, targetUsername;
if(searchUser!=null){
	toJS.accept(searchUser,1);
	targetUserID = Profile.updateIndex(x->x).optString(searchUser, userID);
	targetUsername = userID.equals(targetUserID) ? username : searchUser;
	profile = Profile.get(targetUserID);
}else if(target!=null && !target.isBlank()){
	targetUserID = request.getParameter("target");
	profile = Profile.get(targetUserID);
	searchUser = profile.optString("hydarUsername", profile.optString("username"));
	targetUsername = Profile.isValid(searchUser) ? searchUser: "invalid";
	toJS.accept(targetUsername,1);
}else{
	targetUserID = userID;
	targetUsername = username;
}
int ap = profile.optInt("ap");
int level = Profile.getLevel(ap);
String clan = Profile.clans.get(profile.optInt("clan"));
String avatar = profile.optString("avatar");
if(avatar==null)avatar = "nk-monkey.png";
%>
<p class="hydarLogo" id="leftCol" style="color:rgb(255,255,255);font-family:calibri, arial; font-size:20px;margin:15px">
<img style='float:left;margin-right:10px;border-radius: 50%;object-fit: cover;' src = "https://avatars.nkstatic.com/large/<%=avatar%>" />
<b><a style="color:<%=color(level)%>">[<%=level%>]</a> 
<a><%=targetUsername%></a>
<br> 
<%=miniHydar%>&nbsp;<%=ap%></b><br>
<b style = "color:<%=color(clan) %>"><%=image(clan)%>&nbsp;<%=clan%></b>
<br><br>
Games:<br>

</p>
<script>
let achProgress = <%=
	new JSONObject(Profile.games.stream().collect(
			Collectors.toMap(x->x, x->Profile.getAchProgress(x, targetUserID)/*, (x,y)->x, ()->new LinkedHashMap<>()*/)
		)
	)
%>;
const gamesInOrder = ["BTD4","BTD5","Battles","BSM2","MonkeyCity","SAS3","SAS TD","SAS4","Battle Blocks Defense","Battle Panic","Fortress: Destroyer","Tower Keepers"];
async function loadAch(){
	for(let game of gamesInOrder/*Object.keys(achProgress).sort()*//*.map(x=>Object.values(achProgress[x]).sum())*/){
		//(async ()=>{
			try{
				let progress = achProgress[game];
				let r = await fetch(`amf_data/ach/${encodeURIComponent(game.replace(":",""))}.json`);
				let achs = JSON.parse(await r.text());
				let totalAP=0, totalA=0, myAP=0, myA = 0;
				console.log(achs);
				for(let ach of achs){
					//out.print(ach);
					if(ach.id == 457)continue;
					totalA+=1;
					totalAP+=ach.points; 
					if(progress[ach.id]>=100){
						myA+=1;
						myAP+=ach.points;
					}
				}
				
				if(myA==0){
					$("leftCol").innerHTML += `<i style="color:gray;font-size:15px">${game}: (no data)</i><br>`;
				}else{
					$("leftCol").innerHTML += `<a style="color:${color(game)};font-size:15px"> ${game}:</a> <a style="color:${myA == totalA?"cyan":"white"};font-size:15px">
						${myA}/${totalA}, ${myAP}/${totalAP} <%=miniHydar2%></a> <br>`;
				}
			}catch(e){
				$("leftCol").innerHTML += `<i style="color:gray;font-size:15px">${game}: (error)</i><br>`;
			}
		//})();
	}
}
loadAch();
</script>



<div style = "color:rgb(255,255,255); font-family:calibri, arial; font-size:18px; z-index:1; position:absolute; text-align:left; left:50%; display:block; top:40px;">
	<form method="post" action=""  >
	<%
	if(!targetUserID.equals(userID)){
		%>
			<a style='color:skyblue;text-decoration:underline;position:absolute;top:-20px' href="#" onclick = 'redirParam("target",null)'>
			&lt;&lt;Back to your profile...
			</a>
		<%
	}
	%>
	Add friend:
	<br>
	<input id="friend" type="text" name="friend" size = "20px" style="" placeholder = "Username" autofocus>
	 
	<input type="submit" name="submit" value = "Go" class= "button3" style="top:0px"><br>
	<input type="hidden" name="op" value = "friend">
	</form>
	
	<form method="post" action=""  >
	View profile:
	<br>
	<input id="targetUsername" type="text" name="targetUsername" size = "20px" style="" placeholder = "Username" autofocus>
	<input type="hidden" name="target" value = "">
	<input type="submit" name="submit" value = "Go" class= "button3" style="top:0px"><br>
	</form>
	<br> 
	<%var empty = new JSONArray(); 
	Consumer<JSONArray> printFriends = friends->{
		for(String s: Util.jIterS(friends)){
			var fp = Profile.get(s);
			var fa = fp.optString("avatar","nk_monkey.png");
			var fn = fp.optString("hydarUsername");
			if(fn.isEmpty()) fn = fp.optString("username");
			%><a title='<%=fn%>' href='#' onclick='redirParam("target","<%=s%>")'>
			<img src = 'https://avatars.nkstatic.com/small/<%=fa%>' class='inline20'/>
			</a> <%
		}
	};
	
	%>
	Friends:
	<%printFriends.accept(profile.optJSONArray("friends", empty));%>
	<br>
	Following:
	<%printFriends.accept(profile.optJSONArray("following", empty));%>
	<br>
	Followers:
	<%printFriends.accept(profile.optJSONArray("followers", empty));%>
	<br>
</div>
<%}else{ %>
<b style = "color:rgb(255,255,255); font-family:calibri, arial; font-size:20px;">Not logged in...</b>
<%}%>
<%
if(request.getMethod().equals("POST")){
	String op = request.getParameter("op");
	if(op!=null)
		try{
		switch(op){
			case "friend":
				toJS.accept(request.getParameter("friend"),1);
				boolean success = Profile.addFriend(userID, token, request.getParameter("friend"));
				if(success){
					%> window.location="";<%
				}else{
					throw new NKVerifyException("Already following or friends");
				}
				break;
			}
		}catch(Exception e){
			e.printStackTrace();
			popup.accept(e instanceof NKVerifyException ? 
					e.getMessage().replaceAll("[^\\w -,]", "").toLowerCase() :
					e.getClass());
		}
}

%>
	<script>
	if(window["jsp1"])
		$("friend").value=jsp1;
	</script>	
<%		
%>
</body>
</html>