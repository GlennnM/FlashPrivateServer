<%@page import="org.json.JSONArray"%>
<%@page import="java.util.LinkedHashMap"%>
<%@page import="xyz.hydar.bmc.Util"%>
<%int tab = 3; boolean bigHydar = true;%>
<%@include file = "BaseMenu.jsp"%>

<p style = "color:rgb(255,255,255); font-family:calibri, arial; font-size:20px; z-index:1; position:absolute; text-align:left; left:50%; display:block; top:calc(50% - 220px);">

<b>
Settings -&nbsp;<%=username%></b><br><br>
<%if(!loggedIn){ %>
<br>Not logged in...
<%}else if(hasHydarID){ %>
<form method="post" action=""  >
<p style = "color:rgb(255,255,255); font-family:calibri, arial; z-index:1; position:fixed; position:absolute; text-align:left; left:50%; display:block; top:calc(50% - 200px);">

<br>
Change <%=miniHydar%> username:
<br>
<input id="loginU0" type="text" name="loginU" size = "20px" style="" placeholder = "username" autofocus>
<input id="loginP0" type="password" name="loginP" size = "20px" placeholder = "password" style="top:4px">
<input type="submit" name="submit" value = "Go" class= "button3" style="top:4px"><br>
<input type="hidden" name="op" value = "changeUsername">
</form>
<form method="post" action=""  >
<p style = "color:rgb(255,255,255); font-family:calibri, arial; z-index:1; position:fixed; position:absolute; text-align:left; left:50%; display:block; top:calc(50% - 110px);">

<br>
Change email:
<br>
<input id="email" type="text" name="email" size = "20px" style="" placeholder = "email">
<input type="submit" name="submit" value = "Go" class= "button3" style="top:0px"><br>
<input type="hidden" name="op" value = "changeEmail">
</form><form method="post" action=""  >
<p style = "color:rgb(255,255,255); font-family:calibri, arial; z-index:1; position:fixed; position:absolute; text-align:left; left:50%; display:block; top:calc(50% - 60px);">

<br>
Change password:
<br>
<input id="loginP" type="password" name="loginP" size = "20px" placeholder = "old password" style="top:0px">
<br>
<input id="loginP2" type="password" name="loginP2" size = "20px" placeholder = "new password" style="top:4px">
<input type="submit" name="submit" value = "Go" class= "button3" style="top:4px"><br>
<input type="hidden" name="op" value = "changePassword">
</form>
</div></div>
<%}else{
out.print("Requires Hydar login...");

}%>

</body>
<%
if(request.getMethod().equals("POST")){
	String op = request.getParameter("op");
	try{
	switch(op){
	
		case "changeUsername":
			toJS.accept(request.getParameter("loginP"), 1);
			JSONObject data = Profile.changeUsername(hydarUsername, request.getParameter("loginP"), token, request.getParameter("loginU"));
			
			%>
			<script>
			let data = <%=data%>;
			let usp = new URLSearchParams(window.location.search);
			usp.set("op","");
			if(token.startsWith("hyd")){
				usp.set("username",data.username);
				usp.set("token",data.token);
			}
			window.location.search = usp.toString();
			</script>
			<%
			break;
		case "changePassword":
			var newToken = Profile.changePassword(userID, request.getParameter("loginP"), token, request.getParameter("loginP2"));
			%>
			<script>
			let usp = new URLSearchParams(window.location.search);
			usp.set("op","");
			if(token.startsWith("hyd"))
				usp.set("token","<%=newToken%>");
			window.location.search = usp.toString();
			</script>
			<%
			break;
		case "changeEmail":
			Profile.changeEmail(userID, token, request.getParameter("email"));
			break;
		}
	}catch(Exception e){
		e.printStackTrace();
		popup.accept(e instanceof NKVerifyException ? 
				e.getMessage().replaceAll("[^\\w -,]", "").toLowerCase() :
				e.getClass());
		%>
			<script>
			if(window["jsp1"])
				$("loginU0").value=jsp1;
			</script>	
		<%		
	}
}
%>
</html>