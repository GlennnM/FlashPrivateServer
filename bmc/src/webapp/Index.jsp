
<%@ include file="BMC_Data.jsp" %>
<!DOCTYPE html>
<html>
<head>
<%!
static final FileObjectStore store;
static volatile List<String> keys;
static{
	try{
		store = new FileObjectStore(Path.of("./objects"));//TODO: obviously not .
		keys = store.list();
	}catch(IOException ioe){
		throw new RuntimeException(ioe);
	}
}

%>

<%
switch(request.getRemoteAddr()){
	case "127.0.0.1", "::1", "0:0:0:0:0:0:0:1":
		break;
	default: 
		throw new IllegalArgumentException("not loopback addr");
}
if(!("true".equals(request.getServletContext().getInitParameter("LOAD_INDEX")))){
	response.sendError(403);
	return;
}
String op = request.getParameter("op");
String key = request.getParameter("key");
if(op!=null){
	switch(op){
	case "delete":
		store.delete(key);
		return;
	case "get":
		response.resetBuffer();
		response.setContentType("application/json");
		out.print(store.get(key));
		return;
	case "put":
		store.put(key, new JSONObject(request.getParameter("data")));
		return;
	}
}
keys = store.list();
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