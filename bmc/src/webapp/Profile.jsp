<%@page import="xyz.hydar.bmc.Util"%>
<%int tab = 1; boolean bigHydar = false;%>
<%@include file = "BaseMenu.jsp"%>
<%!
static final String color(int lvl){
	return 
			lvl<10?"cyan":
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
%>

<%if(loggedIn){ 
int ap = profile.optInt("ap");
int level = Profile.getLevel(ap);
String clan = Profile.clans.get(profile.optInt("clan"));
String avatar = profile.optString("avatar");
if(avatar==null)avatar = "nk-monkey.png";
%>
<p class="hydarLogo" style="color:rgb(255,255,255);font-family:calibri, arial; font-size:20px;margin:15px">
<img style='float:left;margin-right:10px' src = "https://avatars.nkstatic.com/large/<%=avatar%>" />
<b><a style="color:<%=color(level)%>">[<%=level%>]</a> 
<a><%=username%></a>
<br> 
<%=ap%><%=miniHydar %></b><br>
<b style = "color:<%=color(clan) %>"><%=clan%></b>
<br><br>
Games:<br>
<%
for(String game: Profile.games){
	Path achDataPath = Path.of(request.getServletContext().getRealPath("/amf_data/ach"));
	var achs = Profile.getMyAchievements(game, userID, achDataPath);
	IO.println(achs.length());
	int totalAP=0, totalA=0, myAP=0, myA = 0;
	for(var ach:Util.jIter(achs)){
		//out.print(ach);
		if(ach.getInt("id") == 457)continue;
		totalA+=1;
		totalAP+=ach.getInt("points");
		if(ach.getInt("perc")>=100){
			myA+=1;
			myAP+=ach.getInt("points");
		}
	}
	if(myA==0){
		%><i style="color:gray"><%=game%> (no data)</i><br><%
	}else{
		%><a style="color:<%=color(game)%>"> <%=game%>:</a> <a style="color:<%=myA == totalA?"cyan":"white"%>">
			<%=myA%>/<%=totalA%>, <%=myAP%>/<%=totalAP%> <%=miniHydar%></a> <br><%
	}
}

%>
</p>
<p style = "color:rgb(255,255,255); font-family:calibri, arial; font-size:20px; z-index:1; position:absolute; text-align:left; left:50%; display:block; top:calc(50% - 220px);">

<%}else{ %>
<b>Not logged in...</b>
<%}%>
</p>
</html>