
<%@page import="xyz.hydar.bmc.Profile"%>
<%@page import="java.time.Instant"%>
<%@page import="java.nio.file.attribute.FileTime"%>
<%@ include file="BMC_Data.jsp" %>
<!DOCTYPE html>
<html>
<head>
<%!
static volatile FileObjectStore store;
static volatile List<String> keys;
%>

<%
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
//--> 
//--> change username(3,4,5)
//--> change password(3,4,5)
//--> change clan/avatar, add friends... (5.1 or smth probably)
//TODO: for friends support, intercept BMC friends api req

//placeholder stuff from index.jsp
%>


<style>
			#overlay{
			    position   : absolute;
			    top        : 0;
			    left       : 0;
			    width      : 100%;
			    height     : 100%;
			    background : #000;
			    opacity    : 0.6;
			    filter     : alpha(opacity=60);
			    z-index    : 5
			}
			.popup{
				position:absolute;
				top:50%;
				left:50%;
				width:854px;  
				height:480px;  
			    background : white;
			    opacity    : 1;
				margin-left:-477px; 
				margin-top:-240px;
			    z-index    : 10
			}
</style>
			
<script>
class Viewer{
	Viewer(){
		this.keys = {};
		this.kd=this.keydown.bind(this);
		this.ku=this.keyup.bind(this);
		window.addEventListener('keydown',this.kd);
		window.addEventListener('keyup',this.ku);
	}
	keydown(e) {
		this.keys[e.keyCode] = true;
	}
	keyup(e) {
		//escape
		if(this.keys[27]){
			this.hidePopups();
		}
		this.keys[e.keyCode] = false;
	}
	editThing(e){
		if(e=="NEW"){
			this.willRefresh=true;
			e = document.getElementById("newNameArea").value;
		}
		this.editingThing=e;
		document.getElementById("editTextName").innerText = e;
		fetch("", {
			 method: "POST",
			  headers: {
			    "Content-Type": "application/x-www-form-urlencoded",
			  },
			  body: new URLSearchParams({ op: "get", key: e }),
		}).then(r=>{
			if (!r.ok) {
		      throw new Error(`error ${r.status}`);
		    }
			return r.json();
		}).then(json=>{
			document.getElementById("overlay").hidden=null;
			document.getElementById("popup").hidden=null;
			document.getElementById("editTextArea").value=JSON.stringify(json,null,2);
		});
		
	}

	deleteThing(e){
		fetch("", {
			 method: "POST",
			  headers: {
			    "Content-Type": "application/x-www-form-urlencoded",
			  },
			  body: new URLSearchParams({ op: "delete", key: e }),
		}).then(r=>{
			if (!r.ok) {
		      throw new Error(`error ${r.status}`);
		    }
			location.reload();
		})
		
	}
	editTextSave(){
		try{
			let obj = JSON.parse( document.getElementById("editTextArea").value);
			if (obj===null || typeof obj !== 'object' || Array.isArray(obj)){
		      throw new Error('Must be an object{}');
		    }
		}catch(e){
			document.getElementById("editTextName").innerText = e;
			return;
		}
		fetch("", {
			 method: "POST",
			  headers: {
			    "Content-Type": "application/x-www-form-urlencoded",
			  },
			  body: new URLSearchParams({ op: "put", key: this.editingThing, data: document.getElementById("editTextArea").value}),
		}).then(x=>{
			if(this.willRefresh && x.ok)
				location.reload();
			this.editTextDiscard();
		});
	}
	
	editTextDiscard(){
		this.willRefresh=false;
		this.editingThing=null;
		this.hidePopups();
	}
	hidePopups(){
		this.willRefresh=false;
		document.getElementById("overlay").hidden=1;
		[...document.getElementsByClassName("popup")].forEach(x=>x.hidden=1);
	}
	
}
var v = new Viewer();
let keys = <%=new JSONArray(keys)%>;
</script>
</head>
<body style='font-family:Calibri,Arial'>
<div id="overlay" hidden=1></div>
<div id="popup" class="popup" hidden=1>
	<h1>Edit <a id="editTextName">thing</a>:
		<a href='#' onclick="v.editTextDiscard()" style="float:right;padding-left:10px">[Discard]</a>
		<a href='#' onclick="v.editTextSave()" style="float:right">[Save]</a>	
	</h1>
	<textarea id="editTextArea" style="width:100%;height:100%;font-size:32"></textarea>
</div>
<div  id='entities'>


</div>
<script>

for(let key of [...keys, "NEW"]){
	let thing=document.createElement("div");
	
	let e_=key;
	let del=document.createElement("a");
	del.href='#';
	del.innerText="[DELETE]";
	del.onclick=()=>v.deleteThing(e_);
	
	let edit=document.createElement("a");
	edit.href='#';
	edit.innerText = key+"[EDIT]";
	edit.onclick=()=>v.editThing(e_);
	
	thing.appendChild(edit);
	thing.appendChild(del);
	document.getElementById("entities").appendChild(thing);
}
</script>

New thing name:<br><textarea id="newNameArea" style="width:250;height:100%;font-size:32"></textarea>	
</body>
</html>