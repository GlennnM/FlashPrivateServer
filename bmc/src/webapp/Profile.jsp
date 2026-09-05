<%@page import="java.util.LinkedHashMap"%>
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
        case "MonkeyCity":case "Battle Panic":
            return "lightgreen";
        case "XIII":case "SAS TD":
            return "green";
        case "White Tigers":case "Battles":
            return "cyan";
        case "Red Storm":case "Scorpions":case "SAS3":
            return "red";
        case "SAS4":return "darkred";
        default:return "red";
    }
}</script><%
%>

<%if(loggedIn){ 
int ap = profile.optInt("ap");
int level = Profile.getLevel(ap);
String clan = Profile.clans.get(profile.optInt("clan"));
String avatar = profile.optString("avatar");
if(avatar==null)avatar = "nk-monkey.png";
%>
<p class="hydarLogo" id="leftCol" style="color:rgb(255,255,255);font-family:calibri, arial; font-size:20px;margin:15px">
<img style='float:left;margin-right:10px' src = "https://avatars.nkstatic.com/large/<%=avatar%>" />
<b><a style="color:<%=color(level)%>">[<%=level%>]</a> 
<a><%=username%></a>
<br> 
<%=miniHydar%>&nbsp;<%=ap%></b><br>
<b style = "color:<%=color(clan) %>"><%=clan%></b>
<br><br>
Games:<br>

<script>
let achProgress = <%=
	new JSONObject(Profile.games.stream().collect(
			Collectors.toMap(x->x, x->Profile.getAchProgress(x, userID)/*, (x,y)->x, ()->new LinkedHashMap<>()*/)
		)
	)
%>;
async function loadAch(){
	for(let game of Object.keys(achProgress)/*.map(x=>Object.values(achProgress[x]).sum())*/){
		(async ()=>{
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
					$("leftCol").innerHTML += `<i style="color:gray">${game}> (no data)</i><br>`;
				}else{
					$("leftCol").innerHTML += `<a style="color:${color(game)}"> ${game}:</a> <a style="color:${myA == totalA?"cyan":"white"}">
						${myA}/${totalA}, ${myAP}/${totalAP} <%=miniHydar%></a> <br>`;
				}
			}catch(e){
				$("leftCol").innerHTML += `<i style="color:gray">${game}> (error)</i><br>`;
			}
		})();
	}
}
loadAch();
</script>



</p>
<p style = "color:rgb(255,255,255); font-family:calibri, arial; font-size:20px; z-index:1; position:absolute; text-align:left; left:50%; display:block; top:calc(50% - 220px);">

</p>
<%}else{ %>
<b style = "color:rgb(255,255,255); font-family:calibri, arial; font-size:20px;">Not logged in...</b>
<%}%>
</html>