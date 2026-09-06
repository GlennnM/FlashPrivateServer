<%@page import="org.json.JSONArray"%>
<%@page import="java.util.LinkedHashMap"%>
<%@page import="xyz.hydar.bmc.Util"%>
<%int tab = 1; boolean bigHydar = false;%>
<%@include file = "BaseMenu.jsp"%>
<style>
.selector{
	position:absolute;
	top:30%;
	left:40%;
	width:470px; 
	height:300px;
    background : gray;
    color:white;
	margin-left:-180px; 
	text-align: center;
	 font-style: italic;
	font-family:calibri, arial;
	 font-size:20px;
	margin-top:-60px;
    z-index: 30;
	display:grid;
	grid-template-columns: repeat(9,50px);
	overflow-y:scroll
}
#clanSelector{
	left:55%;
	padding-left:20px;
	overflow-y:hidden;
	width:270px; 
	grid-template-columns: repeat(5,50px);
}
.selItem{
width:40px;
height:40px;
margin:5px;
float: left;
  display: inline-block;
  background-size:cover;
}
.selItemFade{
text-align:center;
opacity:0.5;
color:white;
font-size:32px;
font-weight:bold;
vertical-align:center;
pointer-events: none;
}
#myClan:hover{
font-size:22px;
}
#myAvatar:hover{
width:84px;
height:84px
}
.selItem:hover{
width:50px;
height:50px;
margin:0px;
}
.locked {
	left:0%;
    text-align:center;
    top: 30px;
}
</style>
<div id="avatarSelector" class="selector" hidden=1>
<a style="grid-column: 1 / -1;">Select avatar...<br></a>

</div>
<div id="clanSelector" class="selector" hidden=1>
<a style="grid-column: 1 / -1;">Select clan...<br></a>

</div>

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
		case "MonkeyCity"->"lime";
		case "Battle Panic"->"lightgreen";
		case "XIII","SAS TD"->"green";
		case "White Tigers","Battles"->"cyan";
		case "Red Storm","Scorpions","SAS4"->"red";
		case "SAS3"->"salmon";
		default->"red";
	};
}
static final String image(String clan){
	var c = URLEncoder.encode(clan.replace(" ","-").toLowerCase(), UTF_8);
	return "<img width=20px height=20px alt='%s' src='https://cdn.nkstatic.com/clans/shields/%s/thumb/%s.png' />"
			.formatted(c, c, c.replace("jackals","jackal").replace("scorp","the-scorp").replace("thund","Thund").replace("xiii","XIII"));
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
	if(!searchUser.equals(username))toJS.accept(searchUser,1);
	targetUserID = Profile.updateIndex(x->x).optString(searchUser, userID);
	targetUsername = userID.equals(targetUserID) ? username : searchUser;
	profile = Profile.get(targetUserID);
}else if(target!=null && !target.isBlank()){
	targetUserID = request.getParameter("target");
	profile = Profile.get(targetUserID);
	searchUser = profile.optString("hydarUsername", profile.optString("username"));
	targetUsername = Profile.isValid(searchUser) ? searchUser: "invalid";
	if(!userID.equals(targetUserID) && profile!=null)
		toJS.accept(targetUsername,1);
}else{
	targetUserID = userID;
	targetUsername = username;
}
boolean isMe = targetUserID.equals(userID);
int ap = profile.optInt("ap");
int level = Profile.getLevel(ap);
String clan = Profile.clans.get(profile.optInt("clan"));
String avatar = profile.optString("avatar");
if(avatar==null)avatar = "nk-monkey.png";
%>
<p class="hydarLogo" id="leftCol" style="color:rgb(255,255,255);font-family:calibri, arial; font-size:20px;margin:15px">
<a href='#' onclick='selectAvatar()'>
<img id="myAvatar" style='float:left;margin-right:10px;border-radius: 50%;object-fit: cover;' src = "https://avatars.nkstatic.com/large/<%=avatar%>" />
</a>
<b><a style="color:<%=color(level)%>">[<%=level%>]</a> 
<a><%=targetUsername%></a>
<br> 
<%=miniHydar%>&nbsp;<%=ap%></b><br>
<a onclick='selectClan()' id="myClan" href="#">
<b style = "color:<%=color(clan) %>"><%=image(clan)%>&nbsp;<%=clan%></b>
</a>
<br><br>
Games:<br>

</p>
<script type="text/javascript">
const avatars = <%= new JSONArray(Profile.avatarURLs) %>;
const clans = <%=new JSONArray(Profile.uniqueClans) %>
let loaded = {"avatar":false,"clan":false};
function selectThing(thing){
	let sel = $(thing==="avatar" ? "avatarSelector" : "clanSelector");
	sel.hidden=null;
	$("overlay").hidden=null;
	if(!loaded[thing]){
		for(let ava of (thing=="avatar" ? avatars : clans)){
			//let img = document.createElement("img");
			//img.setAttribute("src",);
			let link = document.createElement("a");
			link.href="#";
			link.classList.add("selItem");
			let clan1 = encodeURIComponent(ava.replace(" ","-").toLowerCase());
			link.style.backgroundImage = 
				thing=="avatar"?
				`url('https://avatars.nkstatic.com/small/${ava}')`:
				`url('https://cdn.nkstatic.com/clans/shields/${clan1}/thumb/${clan1.replace("jackals","jackal").replace("scorp","the-scorp").replace("thund","Thund").replace("xiii","XIII")}.png')`
			;
				
			
			sel.appendChild(link);
			let req = thing!="avatar" ? -1 : ava.includes("-") ? parseInt(ava.split("-")[0]) : -1;
			if(req > <%=level%>){
				link.classList.add("selItemFade");
				let div = document.createElement("div");
				div.classList.add("locked");
				div.innerText=req;
				link.appendChild(div);
			}else
				link.onclick = ()=>redirParam(thing=="avatar" ? "newAvatar" : "newClan",ava);
		}
	}
	loaded[thing]=true;
}
function selectAvatar(){
	if(<%=isMe%>)selectThing("avatar");
}
function selectClan(){
	if(<%=isMe%>)selectThing("clan");
}
if(!<%=isMe%>){
	$("myAvatar").style.pointerEvents="none";
	$("myClan").style.pointerEvents="none";
}
</script>
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
		try{
			if(request.getMethod().equals("POST")){
				String op = request.getParameter("op");
				if(op!=null)
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
			}else if(request.getParameter("newAvatar")!=null){
				Profile.changeAvatar(userID, token, request.getParameter("newAvatar"));
				%> <script>redirParam("newAvatar" ,null);</script><%
			}else if(request.getParameter("newClan")!=null){
				Profile.changeClan(userID, token, Profile.clans.indexOf(request.getParameter("newClan")));
				%> <script>redirParam("newClan" ,null);</script><%
			}
		}catch(Exception e){
			e.printStackTrace();
			popup.accept(e instanceof NKVerifyException ? 
					e.getMessage().replaceAll("[^\\w -,]", "").toLowerCase() :
					e.getClass());
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