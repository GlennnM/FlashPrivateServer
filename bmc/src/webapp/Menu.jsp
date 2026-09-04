
<%@page import="java.util.function.Consumer"%>
<%@page import="xyz.hydar.bmc.AMFService.NKVerifyException"%>
<%@page import="org.json.JSONObject"%>
<%@page import="java.io.IOException"%>
<%@page import="java.nio.file.Path"%>
<%@page import="java.util.List"%>
<%@page import="xyz.hydar.bmc.FileObjectStore"%>
<%@page import="java.util.stream.Collectors"%>
<%@page import="xyz.hydar.bmc.Profile"%>	
<%@page import="java.time.Instant"%>
<%@page import="java.nio.file.attribute.FileTime"%>
<%!
static volatile FileObjectStore store;
static volatile List<String> keys;
static final String miniHydar = "<img src = https://hydar.xyz/images/notifhydar.png style='width:20px;height:20px;' alt='Hydar'>";
static final int tab = 0;
%>

<%
Consumer<Object> popup = (msg)->{
	%><script>
	document.addEventListener("DOMContentLoaded",()=>{
		document.getElementById("popup").hidden = null;
		//document.getElementById("overlay").hidden = null;
		document.getElementById("popup").innerText += ("<%= msg %>");
		setTimeout(hidePopups, 10000);
	});
	</script><%
};
//--> web with nodejs in?? params that are obtained through node????
//--> in-client webpage that sends api calls --> more secure
//-->-->counterpoint: nk uses webpage

// basically all the info we need is in the client anyways?
// -->-->problem: need to work with renderer.js which is annoying
//shows nk account using nk cookie stuff
//shows some kind of save status
//option to log in as a hydar account
//
// start with login.jsp probably
// URL params: nk username if present, 
if(store==null)
	try{
	
		String storeLocation = request.getServletContext().getInitParameter("STORE_LOCATION");
		store = FileObjectStore.of(Path.of(storeLocation));
		Profile.store = store;
		keys = store.list();
	}catch(IOException ioe){
		throw new RuntimeException(ioe);
	}

String username = request.getParameter("username");
String userID = request.getParameter("userID");
String token = request.getParameter("token");
boolean loggedIn = userID != null;
if(loggedIn){
	try{
		Profile.verifyNK(userID, token);
	}catch(Exception e){
		popup.accept("Invalid token found!! Try logging in again.");
	}
}
var profile = loggedIn ? Profile.get(userID) : null;
boolean isHydarLogin = loggedIn && token.startsWith("hyd");
boolean hasHydarID = loggedIn && profile.get("hydarUserID") != JSONObject.NULL;
String hydarUsername = hasHydarID ? profile.getString("hydarUsername") : null;
boolean hasNKID = loggedIn && profile.get("userID") != JSONObject.NULL;



// display NK status
// display which games are active using hydar server
// display hydar status
// state 1: no nk, no hydar -> show hydar login and register
// state 2: nk, no hydar -> show nk logged in, show hydar linking oiption
// state 3: nk, hydar linked, logged in NK -> mention hydar linking w/o prompt
// state 4: nk, hydar linked, logged in hydar -> show nk not logged in, hydar status, "a linked acc exist"
// state 5: hydar-only account logged in -> same as 4 but wi
//TODO: for friends support, need to create username->uid linkage in Profile.update, reconcile with registration
//client side cookie spec on how it will know how logged in?
//-->needs to be persistent
//-->if token starts in "hyd", function in hydar-only mode
//-->this now tells us how logged in here as well
//--> state 1: userid is null
//--> state 2: not null, not hydr, verifies, profile.hydarUserID null
//--> state 3: not null, not hydr, verifies, profile.hydarUserID not null
//--> state 4: not null, hydr, verifies, profile.nkUserID not null
//--> state 5: not null, hydr, verifies, profile.nkUserID null
//POST operations:
//--> log in to hydar
//--> register a hydar
//--> log out of hydar(client only?)
//--> add hydar login to nk
//--> change username(3,4,5)
//--> change password(3,4,5)
//--> change clan/avatar, add friends... (5.1 or smth probably)
//TODO: for friends support, intercept BMC friends api req
//TODO: settings ui stuff, add friend form, friend backend too
//TODO: smtp for reset
//TODO: show AP, clan, avatar
//TODO: show save failed status?
//placeholder stuff from index.jsp
%>
<!DOCTYPE html>
<html>
<head>
<meta charset="ISO-8859-1">
<title>Flash Private Server</title>
<link rel="icon" href="/favicon.ico"/>
<link rel="shorcut icon" href="/favicon.ico"/>



<style>
			.header{
				color:gray; 
				font-size:18px; 
				font-family:calibri, arial; 
				text-align:center;
				position:relative;
				font-weight:bold;
				font-style:italic;
				text-decoration: none;
				left:0px;top:-3px;
				display:grid;
				grid-template-columns: 1fr 10px 1fr 10px 1.5fr 10px 1.2fr 10px 1fr;
				
			}
			a{
				text-decoration: none;
				color:inherit
			}
			a:hover{
				font-size:20px
			}
			#overlay{
			    position   : absolute;
			    top        : 0;
			    left       : 0;
			    width      : 100%;
			    height     : 100%;
			    background : #000;
			    opacity    : 0.0;
			    filter     : alpha(opacity=60);
			    z-index    : 5
			}
			.popup{
				position:absolute;
				top:25%;
				left:50%;
				width:450px;  
				height:30px;  
			    background : red;
			    opacity    : 0.4;
				margin-left:-180px; 
				text-align: center;
				 font-style: italic;
				font-family:calibri, arial;
				 font-size:20px;
				
				margin-top:-60px;
			    z-index    : 10
			}
</style>
			
<script>
function hidePopups(){
	//document.getElementById("overlay").hidden=1;
	[...document.getElementsByClassName("popup")].forEach(x=>x.hidden=1);
}
</script>
</head>
<style> 
	body{
		background-image:url('https://hydar.xyz/images/hydarface.png');
		background-repeat:no-repeat;
		background-attachment:fixed;
		background-size:100% 150%;
		background-color:rgb(51, 57, 63);
		background-position: 0% 50%;
	}
</style>
<style>
.images {
	height: 140%;
	width: calc(100% + 20px);
	position: absolute;
	overflow: hidden;
	top: -40%;
	left: -20px;
	opacity: 40%;
}

.textbox {
	position: absolute;
	top: 50%;
	left: 50%;
}

.textboxmove {
	background: rgb(51, 57, 63);
	width: 550px;
	height: 480px;
	display: block;
	position: absolute;
	top: -210px;
	left: -235px;
	box-shadow: 0 0 10px rgba(0, 0, 0, 20);
}

.hydarlogo {
	position: absolute;
	top: calc(50% - 160px);
	left: calc(50% - 220px);
	opacity: 100%;
}

.button3 {
	dsiplay: inline-block;
	background-color: rgb(41, 47, 53);
	color: white;
	border: none;
	padding: 8px 12px;
	position: relative;
	left: 0px;
	top: 4px;
	border-radius: 8px;
}

.button3:hover {
	background-color: rgb(61, 97, 183);
	cursor: pointer;
}

input {
	background-color: rgb(71, 77, 83);
	color: white;
	border: none;
	padding: 8px 10px;
	border-radius: 8px;
	position:relative;
}

input:-webkit-autofill {
	-webkit-box-shadow: 0 0 0 50px rgb(71, 77, 83) inset;
	-webkit-text-fill-color: White;
}

input:-webkit-autofill:focus {
	-webkit-box-shadow: 0 0 0 50px rgb(71, 77, 83) inset;
	-webkit-text-fill-color: White;
}
</style>
<body>
<div id="overlay" hidden=1 onclick= 'hidePopups'></div>

<div id="popup" class="popup" hidden=1>
	
</div>
<div class = "textbox"><div class = "textboxmove">
<div class = "header" >
<a href="" <%if(tab==0){ %>style='color:white'<%} %>><%=miniHydar %>&nbsp; menu</a> 
/
<a href="" <%if(tab==1){ %>style='color:white'<%} %>><%=miniHydar %>&nbsp; profile</a> 
/
<a href="" <%if(tab==2){ %>style='color:white'<%} %>><%=miniHydar %>&nbsp; leaderboard</a>
/
 <a href="" <%if(tab==3){ %>style='color:white'<%} %>><%=miniHydar %>&nbsp; settings</a>
/
  <a href="" <%if(tab==4){ %>style='color:white'<%} %>><%=miniHydar %>&nbsp; about</a>
</div>
<div class = "hydarlogo" style = "color:rgb(255,255,255);font-family:calibri, arial;text-align:left;font-size:12px;left:0px">
<img src="https://hydar.xyz/images/hydar.png" alt="hydar" style="position:absolute;left:20px;top:-50px">
<br>
<p style = "position:absolute;left:20px;top:250px;right:200px;width:250px">
<%if(loggedIn){ %>
<a href="https://ninjakiwi.com/flash/logout" style="font-size:24px;color:blue;text-decoration: underline;">Log out...</a><br>
	 Games synced:&nbsp;<%= Profile.games.stream().filter(x->store.get("amf", userID, x, "ach") != null).collect(Collectors.joining(", "))%><br><%
	%> Not synced/never played:&nbsp;<%= Profile.games.stream().filter(x->store.get("amf", userID, x, "ach") == null).collect(Collectors.joining(", "))%><br>

<%}else{%>
*A new <%=miniHydar%> account will not be able to use saves from NK servers!!!
This option is only recommended if NK's save servers do not work.<br><br>
If you log in with NK (via main Archive menu), saves will go to both servers, and you can add a <%=miniHydar%> login to that linked account from here.
<%} %>
</p>
</div>

<p style = "color:rgb(255,255,255); font-family:calibri, arial; font-size:20px; z-index:1; position:absolute; text-align:left; left:50%; display:block; top:calc(50% - 220px);">

<b>
Flash Private Server</b><br><br>
<b style='color:red'>NK</b>:&nbsp;<%= loggedIn ? (hasNKID ? (isHydarLogin ? 
			"Linked, but "+miniHydar+" in use instead"+"<br><i style='font-size:9pt'>("+miniHydar+" doesn't store your NK token or password, so you<br>can't save to NK with only a "+miniHydar+" login. To save to NK, log out from "+miniHydar+" below, then log in with NK.)</i>" : username) 
		: "N/A ("+miniHydar+"-only account)") : "Not logged in" %>
<br>	
<%=miniHydar%>:&nbsp;<%= loggedIn ? (hasHydarID ? hydarUsername : "Linked with NK, no username yet") : "Not logged in" %>
<br>
Saving to&nbsp;<%=loggedIn ? (isHydarLogin ? miniHydar + " only" : "both") : "none" %>.
<br>
<%

%>
<%if(!loggedIn){ %>
<br>
<form method="post" action=""  >
<p style = "color:rgb(255,255,255); font-family:calibri, arial; z-index:1; position:fixed; position:absolute; text-align:left; left:50%; display:block; top:calc(50% - 70px);">

<%=miniHydar%> login<br>	
<input  type="text" name="loginU" size = "20px" style="" placeholder = "Username" autofocus><br>
 

<input type="password" name="loginP" size = "20px" style="top:4px;" placeholder = "Password">
<input type="submit" name="submit" value = "Go" class= "button3">
<input type="text" hidden=1 name="op" value = "login">
<br><br>
</form>
<br>
<br>
<form method="post" action=""  >
<p style = "color:rgb(255,255,255); font-family:calibri, arial; z-index:1; position:fixed; position:absolute; text-align:left; left:50%; display:block; top:calc(50% + 20px);">

-or-
<br>
Register new <%=miniHydar%> account*:
<br>
<input  type="text" name="loginU" size = "20px" style="" placeholder = "Username" autofocus><br>
 
<input type="text" name="email" size = "20px" style="top:4px;" placeholder = "Email (recovery only)"><br>

<input type="password" name="loginP" size = "20px" style="top:8px;" placeholder = "Password"><br> 
<input type="password" name="loginP2" size = "20px" style="top:12px;" placeholder = "Confirm password">
<input type="submit" name="submit" value = "Go" class= "button3" style="top:12px"><br><br>

<input type="hidden" name="op" value = "register">
</form>
<%}else if(!hasHydarID){ %>
<form method="post" action=""  >
<p style = "color:rgb(255,255,255); font-family:calibri, arial; z-index:1; position:fixed; position:absolute; text-align:left; left:50%; display:block; top:calc(50% - 50px);">

<br>
Add <%=miniHydar%> credentials to your NK account:
<br>
<input  type="text" name="loginU" size = "20px" style="" placeholder = "Username" autofocus><br>
 
<input type="text" name="email" size = "20px" style="top:4px;" placeholder = "Email (recovery only)"><br>

<input type="password" name="loginP" size = "20px" style="top:8px;" placeholder = "Password"><br>
<input type="password" name="loginP2" size = "20px" style="top:12px;" placeholder = "Confirm password">

<input type="submit" name="submit" value = "Go" class= "button3" style="top:12px"><br><br>
<input type="hidden" name="op" value = "link">
</form>
</div></div>
<%}%>

</body>
<%
if(request.getMethod().equals("POST")){
	String op = request.getParameter("op");
	try{
	switch(op){
	
		case "login":
			var login = Profile.login(request.getParameter("loginU"), request.getParameter("loginP"));
			%>
			<script>
			let login = <%=login%>;
			window.nkarchive.sendUserData(login);
			login.userID = login.id;
			window.location = "?"+new URLSearchParams(login).toString();
			</script>
			<%
			//window.nkarchive.sendUserData(flashvars);
			break;
		case "register":
			var reg = Profile.registerNew(request.getParameter("loginU"), request.getParameter("email"),request.getParameter("loginP"), request.getParameter("loginP2"));
			%>
			<script>
			let login = <%=reg%>;
			window.nkarchive.sendUserData(login);
			login.userID = login.id;
			window.location = "?"+new URLSearchParams(login).toString();
			</script>
			<%
			break;
		case "link":
			var link = Profile.linkNewNK(request.getParameter("loginU"), request.getParameter("email"), request.getParameter("loginP"), request.getParameter("loginP2"), userID, token);
			%>
			<script>
			let usp = window.location.search;
			usp.op="";
			window.location="?"+usp;
			</script>
			<%
			break;
		case "changeUsername":
			Profile.changeUsername(username, token, request.getParameter("loginU"));
			break;
		case "changePassword":
			var newToken = Profile.changePassword(userID, request.getParameter("loginP"), token, request.getParameter("newP"));
			break;
		case "changeClan":
			Profile.changeClan(userID, token, Integer.parseInt(request.getParameter("clan")));
			break;
		case "changeAvatar":
			Profile.changeAvatar(userID, token, request.getParameter("avatar"));
			break;
		case "addFriend":
		//	Profile.changeAvatar(userID, token, request.getParameter("addFriend"));
			break;
		}
	}catch(Exception e){
		popup.accept(e instanceof NKVerifyException ? 
				e.getMessage().replaceAll("[^\\w -]", "").toLowerCase() :
				e.getClass());
	}
}
%>
</html>


