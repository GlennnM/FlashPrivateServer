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
		case "Black Cobras","Shining Blade"->"yellow";
		case "Dark Matter"->"purple";
		case "Iron Phoenix"->"orange";
		case "Night Jackals","Blue Wolves"->"blue";
		case "Thunderbolts","Falcons"->"goldenrod";
		case "The Watchers"-> "white";
		case "XIII"->"green";
		case "White Tigers"->"cyan";
		case "Red Storm","Scorpions"->"red";
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
Games:
</p>
<p style = "color:rgb(255,255,255); font-family:calibri, arial; font-size:20px; z-index:1; position:absolute; text-align:left; left:50%; display:block; top:calc(50% - 220px);">

<%}else{ %>
<b>Not logged in...</b>
<%}%>
</p>
</html>