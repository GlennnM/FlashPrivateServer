<%int tab = 0; %>
<%@include file = "BaseMenu.jsp"%>
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