import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.URI;
import java.net.InetAddress;
import java.net.URL;
import java.net.Socket;
import java.net.URLClassLoader;
import java.net.SocketTimeoutException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.Writer;
import java.io.IOException;
import java.io.File;
import java.lang.Character;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.text.FieldPosition;
import java.text.ParseException;
import java.util.TimeZone;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.net.ServerSocket;
import java.security.SecureRandom;
/**
@TODO
/done
/done
/done
no boosting in vs(or vs compatible boosting?)
feature: custom map for event. -> hard :(
also cycle events properly
/done
/done
*/
class ServerThread extends Thread {
	/**
	Client threads for "matchmaking" server. When a player joins an MP game, they send requests to here.
	The responses direct them to the individual game server instances(GameServers).
	See o4356
	*/
	public Socket client = null;
	OutputStream output;
	public volatile boolean alive;
	Player player=null;
	public int code=0;
	public int mode=0;
	// constructor initializes socket
	public ServerThread(Socket socket) throws IOException{
		this.client = socket;
		this.alive=true;
		this.output = this.client.getOutputStream();
	}
	@Override
	public void run() {
		try {
			this.alive=true;
			this.client.setSoTimeout(1000);
			SimpleDateFormat s = null;
			InputStream input = this.client.getInputStream();
			int size=1024;
			Date lastUpdate=null;
			int timeouts=0;
			byte[] buffer;
			int l4=0;
			while(this.alive){
				
				buffer=new byte[1024];
				try {
					l4=input.read(buffer,0,1024);
				} catch (java.net.SocketTimeoutException ste) {
					/**
					kill after multiple timeouts
					*/
				}
				if (l4>0) {
					
					/**incoming format:
						|2| bytes: 0x00ca(the number 202).
						|4| bytes: match code(int). If it was a string it is converted to an int.
							Yes this means multiple codes could point to the same lobby
						|2| bytes: player level(short).
						|2| bytes: game mode. 1=Normal 2=NM, 3/4/5/7=events, 100+ = contracts
						|4| bytes: amount of values in array.
						|2| bytes per value: the array. no idea what it does lol
						
						outgoing format:
						|2| bytes: ip length
						|^| (length) bytes: ip(game server)
						|2| bytes: port
						|2| bytes: mode
						|4| bytes: code
						
						See o4356.
						
						after this exchange this server isn't used as far as i'm aware
					*/
					//System.out.println("incoming:");
					timeouts=0;
					code = (((int)buffer[2]&0xff)<<24)|(((int)buffer[3]&0xff)<<16)|(((int)buffer[4]&0xff)<<8)|((int)buffer[5]&0xff);
					int length = ((int)buffer[10]&0xff)<<24+((int)buffer[11]&0xff)<<16+((int)buffer[12]&0xff)<<8+((int)buffer[13]&0xff);
					int[] data = new int[length];
					for(int i=0;i<length;i++)
						data[i]=((int)buffer[14+2*i]&0xff)<<8+((int)buffer[15+2*i]);
					
					short level = (short)(((buffer[6]&0xff)<<8)|(buffer[7]&0xff));
					mode = (((int)buffer[8]&0xff)<<8)|((int)buffer[9]&0xff);
					String strCode = ""+code+"\n"+mode;
					//System.out.println(strCode.hashCode());
					short autostart = (code==0)?(level):((short)-1);//quickmatch or event
					
					//Player player = new Player(buffer[7],data);
					GameServer g = S4Server.games.get(strCode);
					while(g!=null&&!g.allows(level)){
						code++;
						strCode = ""+code+"\n"+mode;
						g = S4Server.games.get(strCode);
					}
					if(g==null){
						g=new GameServer(S4Server.nextPort(),mode,code,autostart);
						S4Server.games.put(strCode,g);
						new Thread(g).start();
					}byte[] ip = "154.53.49.118".getBytes(StandardCharsets.UTF_8);
					byte[] pl =new byte[10+ip.length];
					pl[0]=(byte)(ip.length>>8);
					pl[1]=(byte)(ip.length&0xff);
					int i=0;
					int port = S4Server.games.get(strCode).port;
					for(i=0;i<ip.length;i++)
						pl[2+i]=ip[i];
					pl[i+2]=(byte)(port>>8);
					pl[i+3]=(byte)(port&0xff);
					pl[i+4]=(byte)(mode>>8);
					pl[i+5]=(byte)(mode&0xff);
					pl[i+6]=(byte)(code>>24);
					pl[i+7]=(byte)((code>>16)&0xff);
					pl[i+8]=(byte)((code>>8)&0xff);
					pl[i+9]=(byte)((code)&0xff);
					output.write(pl);
					//System.out.println("OUT: "+pl.length);
					for(int a=0;a<pl.length;a++)
							System.out.print(">"+pl[a]+" ");
					output.flush();
				}else{
					timeouts++;
					code=0;
					mode=0;
					if(timeouts>10){
						this.alive=false;
						output.close();
						input.close();
						this.client.close();
						return;
					}
				}
			}
		}catch(Exception ioe_){
			try{
				this.alive=false;
				output.close();
				this.client.close();
				ioe_.printStackTrace();
				return;	
			}catch(Exception e){
				e.printStackTrace();
			}return;
		}
	}
}
class FilePolicyServer extends Thread{
	/**
	Flash requests a "cross domain policy" xml whenever contacting a server.
	In SAS4 this is handled on port 843, then that connection is ended instantly
	*/
	public ServerSocket server;
	public int port;
	public boolean alive=true;
	public FilePolicyServer(int port){
		try{
			this.port=port;
			this.server = new ServerSocket(port);
			this.server.setSoTimeout(1000);
		}catch(IOException e){
			return;
		}
		this.alive=true;
	}
	@Override
	public void run(){
		while (this.alive) {
			Socket client = null;
			try {
				client = server.accept();
				FilePolicyServerThread connection = new FilePolicyServerThread(client);
				new Thread(connection).start();
			}catch(Exception e){
				//e.printStackTrace();
				continue;
			}
		}
	}
}
class FilePolicyServerThread extends Thread{
	/**
	Threads for individual connections; see FilePolicyServer
	*/
	public Socket client;
	public OutputStream output;
	public boolean alive;
	public void doubleWrite(String s) throws IOException{
		this.output.write((s+"\n").getBytes(StandardCharsets.UTF_8));
		this.output.flush();
		System.out.println("#OUT: file policy");
	}
	public FilePolicyServerThread(Socket client){
		this.client = client;
		try{
		this.output = client.getOutputStream();}catch(Exception e){e.printStackTrace();}
	}
	@Override
	public void run(){
		try{
			this.alive=true;
			this.client.setSoTimeout(1000);
			InputStream input = this.client.getInputStream();
			InputStreamReader ir = new InputStreamReader(input, Charset.forName("UTF-8"));
			int size=1024;
			int timeouts=0;
			String headers = "";
			String line="";
			while(this.alive){
				try {
					char[] buffer=new char[1024];
					int l4=ir.read(buffer,0,1024);
					line = new String(buffer).trim();
					headers = new String(line);
				} catch (java.net.SocketTimeoutException ste) {
					/**
					kill after multiple timeouts
					*/
					ste.printStackTrace();
				}
				if (line.length()>0) {
					timeouts=0;
					//System.out.println("#incoming: "+headers);
					if(headers.equals("<policy-file-request/>")){
						String xml = "<?xml version=\"1.0\"?>\n<!DOCTYPE cross-domain-policy SYSTEM \n\"http://www.adobe.com/xml/dtds/cross-domain-policy.dtd\">\n<cross-domain-policy>\n";
						xml += "<site-control permitted-cross-domain-policies=\"master-only\"/>\n";
						xml += "<allow-access-from domain=\"*\" to-ports=\"*\"/>\n";
						xml += "</cross-domain-policy>\n";
						doubleWrite(xml);
						this.alive=false;
						output.close();
						input.close();
						ir.close();
						this.client.close();
						return;
					}
				}else{
					//System.out.println("%%timeout");
					timeouts++;
					if(timeouts>10){
						this.alive=false;
						output.close();
						input.close();
						ir.close();
						this.client.close();
						return;
					}
					headers = "";
					line="";
					continue;					
				}
			}
		}catch(Exception e){
			this.alive=false;
				e.printStackTrace();
		}
	}
}
class Player{
	//header use is unknown; data is from the initial message to game server
	public int level;
	public int[] header;
	public byte[] data;
	public Player(int level, int[] header){
		this.level=level;
		this.header=header;
	}
	public void setData(byte[] data){
		this.data=data;
		
	}
}
class GameServer extends Thread{
	/**
	Individual game server; can have up to 4 clients/bots connected.
	Each one will have a GameServerThread
	Most of the multiplayer protocol is in here and GameServerThread.
	See o2144, o14519, o7788
	*/
	public ServerSocket server;
	public int port;
	public boolean alive=true;
	public ArrayList<GameServerThread> playerThreads;
	public int mode;
	public int code;
	public boolean init;
	public short map;
	public byte nextId;
	public byte host;
	public boolean started;
	public boolean autostart;
	public short minLvl;
	public short maxLvl;
	public int startTime;
	public int elapsedTime;
	public long lastTime;
	//public long startAt;
	public boolean allows(short lvl){
		return (!this.started)&&(this.playerThreads!=null)&&(this.playerThreads.size()<4)&&(lvl<=maxLvl)&&(lvl>=minLvl);
	}
	
	public GameServer(int port, int mode, int code, short auto){
		playerThreads = new ArrayList<GameServerThread>();
		this.nextId=0;
		this.host=0;
		this.startTime=-1;
		this.elapsedTime=0;
		this.lastTime=0;
		this.started=false;
		this.autostart=(auto>=0);
		if(this.autostart){
			this.minLvl=(short)(auto-10);
			this.maxLvl=(short)(auto+10);
		}else{
			this.minLvl=(short)-1000;
			this.maxLvl=(short)1000;
		}
		try{
			this.port=port;
			this.server = new ServerSocket(port);
			this.server.setSoTimeout(1000);
		}catch(IOException e){
			return;
		}
		this.mode=mode;
		this.code=code;
		//Pick a map based on mode; nm and event maps both have separate id's
		//playing normal on an NM map and vice versa is playable; playing normal on an event map does nothing
		if(this.mode==2){
			//NM
			this.map=(short)1102;
			this.map+=(short)(Math.random()*8);
			if(Math.random()<0.2)
				this.map=(short)1112;
		}else if(this.mode==1){
			//normal
			this.map=(new short[]{1008,1018,1067,1009,1054,1043,1016,1101,1110})[(int)(Math.random()*9)];
		}else if(this.mode<100){
			//event
			this.map=1100;
			//this.map=(new short[]{1092,1095,1094,1100,1093,1099,1096,1111,1113})[(int)(Math.random()*9)];
			//onsl pods sur ls vac po vip is md
		}
		//Otherwise it is a contract, but contract lobbies seem to work fine without having added the map.
		//I think the clients try to change the map after joining
		this.init=false;
		this.alive=true;
	}
	public void chat(String s){
		byte[] msg =S4Server.encode(s);
		this.writeAll(msg,0,msg.length);
	}
	//Add a bot, if possible.
	//Bots will leave as soon as building starts.
	public void boost(){
		if(playerThreads.size()>3){
			chat("Unable to boost.");
			return;
		}
		for(GameServerThread g:playerThreads)
			if(g.started){
				chat("Unable to boost.");
				return;
			}
		GameServerThread bot = new GameServerThread(null,this,this.nextId,this.host);
		this.nextId++;
		playerThreads.add(bot);
		newPlayer(bot.id);
		chat("Added 1 bot!");
		new Thread(bot).start();
	}
	//Remove a bot.
	public void unboost(){
		int i=0;
		for(i=0;i<this.playerThreads.size();i++)
			if(playerThreads.get(i).bot){
				chat("Removed 1 bot!");
				this.dropPlayer(playerThreads.get(i).id);
				i--;
				return;
			}
		chat("No bots to remove");
	}
	//Accept connections and route them to GameServerThreads.
	//Also use the loop for checks(start game=-11 packet when all built)
	@Override
	public void run(){
		while (this.alive) {
			long t = (new Date()).getTime();
			if(t-1000>lastTime){
				lastTime=t;
				if(this.startTime!=-1){
					this.elapsedTime++;
					if(autostart){
						this.minLvl--;
						this.maxLvl++;
						if(!this.started&&this.elapsedTime>=this.startTime){
							this.start();
							this.startTime=-1;
							this.elapsedTime=0;
						}
					}
				}
			}
			Socket client = null;
			int ready=0;
			for(GameServerThread g:playerThreads){
				if(g.built&&g.started&&!g.ingame)ready++;
			}if(ready==playerThreads.size()){
				long time = (new Date()).getTime()+3000;
				byte[] pl = new byte[5];
				pl[0]=(byte)-11;
				pl[1]=((byte)(time>>24));
				pl[2]=((byte)((time>>16)&0xff));
				pl[3]=((byte)((time>>8)&0xff));
				pl[4]=((byte)((time)&0xff));//time
				this.writeAll(pl,0,5);
				chat("All players built(hopefully)\nStarting game!");
				for(GameServerThread g:playerThreads)
					g.ingame=true;
			}
			try {
				client = server.accept();
				this.init=true;
				GameServerThread connection = new GameServerThread(client,this,this.nextId,this.host);
				this.nextId++;
				playerThreads.add(connection);
				new Thread(connection).start();
			}catch(Exception e){
				//e.printStackTrace();
				continue;
			}
		}
	}
	//Change the map, alerting all players. Packets start with -14.
	public void setMap(short map) throws IOException{
		//System.out.println(map);
		this.map=map;
		byte[] pl = new byte[9];
		pl[0]=((byte)(-14));
		pl[3]=((byte)((mode>>8)&0xff));
		pl[4]=(byte)(mode&0xff);
		long time = (new Date()).getTime();
		pl[1]=((byte)((map>>8)&0xff));
		pl[2]=(byte)(map&0xff);
		for(GameServerThread g:playerThreads)
			g.map=this.map;
		this.writeAll(pl,0,5);
	}
	//Forward a message to all players except the one specified by id.
	public void writeFrom(byte id,byte[] data){
		for(GameServerThread h:playerThreads)
			if(h.id!=id){
				try{
					h.output.write(data);
					h.output.flush();
				}catch(IOException e){
					continue;
				}
			}
	}
	//same function different arguments
	public void writeFrom(byte id,byte[] data,int offset,int length){
		for(GameServerThread h:playerThreads)
			if(h.id!=id){
				try{
					h.output.write(data,offset,length);
					h.output.flush();
				}catch(IOException e){
					continue;
				}
			}
	}	
	//Forward a message to all players.
	public void writeAll(byte[] data,int off, int length){
		for(GameServerThread g:playerThreads){
			try{
				//System.out.println(data.length);
				g.output.write(data,off,length);
				g.output.flush();
			}catch(IOException e){
				continue;
			}
		}
	}
	//Add a player, alert them of all the existing players and alert all the existing players of them.
	//See playerData() for protocol information.
	public void newPlayer(byte id){
		if(autostart){
			if(playerThreads.size()==2){
				this.startTime=(this.mode==5)?15:30;
				this.elapsedTime=0;
			}else if(playerThreads.size()==3&&this.startTime>10){
				this.startTime-=7;
			}else if(playerThreads.size()==4){
				this.startTime=0;
			}
			byte[] pl = new byte[5];
			pl[0]=(byte)-13;
			long st = 1000 * this.startTime;
			pl[1]=(byte)(st>>24);
			pl[2]=(byte)((st>>16)&0xff);
			pl[3]=(byte)((st>>8)&0xff);
			pl[4]=(byte)((st)&0xff);
			this.writeAll(pl,0,5);
		}
		for(GameServerThread g:playerThreads){
			try{
				if(g.id==id){
					for(GameServerThread h:playerThreads)
						if(h.id!=id){
							g.output.write(h.playerData((byte)-1));
							g.output.flush();
						}
				}else	
					for(GameServerThread h:playerThreads)
						if(h.id==id){
							g.output.write(h.playerData((byte)-1));
							g.output.flush();
						}
			}
			
			catch(IOException e){
				continue;
			}
		}
		for(GameServerThread g:playerThreads){
			if(g.id==id){
				try{
					g.output.write(S4Server.encode("Welcome to Flash Private Server!\nSee !help for a list of commands.\nReport bugs to Glenn M#9606"));
					g.output.flush();
				}catch(IOException e){
					
				}
			}
			fullLoad(g.id);
		}
	}
	//float value containing loading or building % for some player(0.0-1.0).
	public void loadingState(byte id, byte[] buffer){
		//System.out.println("HYDR");
		byte[] pl = new byte[11];
		pl[0]=(byte)-2;
		pl[4]=(byte)(0x06);
		pl[5]=(byte)7;
		pl[6]=id;
		pl[7]=buffer[10];
		pl[8]=buffer[11];
		pl[9]=buffer[12];
		pl[10]=buffer[13];
		this.writeFrom(id,pl);
				
	}
	//force building/loading finish(workaround for stuck lobbies)
	public void fullLoad(byte id){
		byte[] pl = new byte[11];
		pl[0]=(byte)-2;
		pl[4]=(byte)(0x06);
		pl[5]=(byte)7;
		pl[6]=id;
		pl[7]=(byte)0x3f;
		pl[8]=(byte)0x80;
		this.writeFrom(id,pl);
	}
	//remove a player/bot, alerting all other players(-6) and changing host(-7) if needed
	public void dropPlayer(byte id){
		playerThreads.removeIf(x->(x.id==id));
		int first=-1;
		if(playerThreads.size()==1){
			this.startTime=-1;
			this.elapsedTime=0;
		}
		for(int i=0;i<playerThreads.size();i++){
			if(!playerThreads.get(i).bot&&playerThreads.get(i).alive)
				first=i;
		}
		if(first==-1){
			this.alive=false;
			try{
				this.server.close();
			}catch(Exception e){}
			S4Server.games.remove(""+this.code+"\n"+this.mode);
			return;
		}
		if(this.host==id){
			//change host
			byte[] pl2 = new byte[]{(byte)-7,id,playerThreads.get(first).id};
			this.host=playerThreads.get(first).id;
			this.writeAll(pl2,0,3);
		}byte[] pl = new byte[]{(byte)-6,id};
		this.writeAll(pl,0,2);
		
	}
	//start building(start game button), or automatically in event/quickmatch
	public void start(){
		chat("Starting building...");
		System.out.println("-------STARTING GAME "+code+"-------");
		this.writeAll(new byte[]{(byte)-16},0,1);
		this.started=true;
		for(GameServerThread g:playerThreads){
			g.started=true;
			g.pingSinceBuild=0;
		}
	}
}
class GameServerThread extends Thread{
	//See GameServer
	public Socket client;
	public volatile OutputStream output;
	public boolean alive;
	public short map;
	public boolean init;
	public Player player;
	public GameServer parent;
	public byte id;
	public byte host;
	public boolean loaded;
	public boolean started;
	public boolean built;
	public boolean ingame;
	public int pingSinceLoad;
	private long lastPinged;
	public int pingSinceBuild;
	public boolean bot;
	/**
	-2 packet with operation -1 as interpreted in o14519; reflects most of the data from the (0x00ca) packet containing player data.
	Appended to this is a byte which goes from 0-100(loaded percentage).
	Each starting opcode corresponds to a different function; however -2 is special in that it will have a "sub-operation" such as -1. Format:
	|1| operation
	|4| length IF type=bytearray
	|1| suboperation IF type -1
	|data| - usually includes id and level early on
	See o14519, o2144.
	*/
	public byte[] playerData(byte opcode){
		int len = this.player.data.length;
		if(opcode==-1)
			len++;
		byte[] pl;
		pl = new byte[len+5];
		if(this.bot){
			pl[len+4]=(byte)100;
		}
		try{
			pl[0]=(byte)-2;
			pl[1]=(byte)(len>>24);
			pl[2]=(byte)((len>>16)&0xff);
			pl[3]=(byte)((len>>8)&0xff);
			pl[4]=(byte)((len)&0xff);
			pl[5]=(byte)-1;
			pl[6]=this.id;
			short nameLen = (short)((((short)this.player.data[0]&0xff)<<8)|((short)this.player.data[1]&0xff));
			
			//nameLen|=((short)(this.player.data[1])&0xff);
			//System.out.println("%%%NAMELEN "+nameLen+"//"+this.player.data[0]+"//"+this.player.data[1]);
			for(int i=7;i<10+nameLen;i++)
				pl[i]=this.player.data[i-7];
			pl[10+nameLen]=(byte)((this.player.level>>8)&0xff);
			//System.out.println("pl%%%"+this.player.level);
			pl[11+nameLen]=(byte)(this.player.level&0xff);
		for(int i=12+nameLen;i<this.player.data.length+5;i++)
			pl[i]=this.player.data[i-5];
		}catch(Exception e){
			e.printStackTrace();
		}
		return pl;
		
	}
	//deprecated(but works)
	public byte[] getPowerups(){
		short nameLen = (short)((((short)this.player.data[0]&0xff)<<8)|((short)this.player.data[1]&0xff));
		int dataLen = (((int)this.player.data[nameLen+2]&0xff)<<24)|(((int)this.player.data[nameLen+3]&0xff)<<16)|(((int)this.player.data[nameLen+4]&0xff)<<8)|((int)this.player.data[nameLen+5]&0xff);
		byte gearCount = this.player.data[nameLen+7];
		int gearBytes=0;
		for(int i=0;i<gearCount;i++){
			gearBytes+=18+(this.player.data[nameLen+gearBytes+20]*2);
		}
		//System.out.println("gearCount: "+gearCount+"\ngearBytes: "+gearBytes);
		int dictionaryBytes=this.player.data[nameLen+gearBytes+8]*2+1;
		//System.out.println("dictionaryBytes: "+dictionaryBytes);
		byte powerupBytes = this.player.data[nameLen+gearBytes+24+dictionaryBytes];
		//System.out.println("powerupBytes: "+powerupBytes);
		byte[] powerups = new byte[powerupBytes];
		for(int i=0;i<powerupBytes;i++)
			powerups[i]=this.player.data[nameLen+gearBytes+25+dictionaryBytes+i];
		int len = (powerupBytes+1)*4+1;
		byte[] pl = new byte[len+5];
		pl[0]=(byte)-2;
		pl[1]=(byte)(len>>24);
		pl[2]=(byte)((len>>16)&0xff);
		pl[3]=(byte)((len>>8)&0xff);
		pl[4]=(byte)((len)&0xff);
		pl[5]=(byte)11;
		pl[9]=this.id;
		for(int i=0;i<powerupBytes;i++)
			pl[13+4*i]=powerups[i];
		return pl;
		
	}
	
	/**
	Constructor. If client isn't a real socket we will create a bot instead
	parent is a reference to the GameServer we are connected to
	*/
	public GameServerThread(Socket client, GameServer parent, byte id, byte host){
		this.parent = parent;
		this.map=parent.map;
		this.alive=true;
		this.init=false;
		this.player=null;
		this.loaded=false;
		this.started=false;
		this.built=false;
		this.ingame=false;
		this.bot=false;
		this.lastPinged=0;
		this.pingSinceLoad=0;
		this.pingSinceBuild=0;
		this.id=id;
		this.host=host;
		this.client = client;
		try{
			this.output = client.getOutputStream();
		}catch(Exception e){
			this.bot=true;
			this.output = new NullOutputStream();
			this.player = new Player((short)100,new int[]{});
			this.player.setData(S4Server.bot);
		}
	}
	/**
	handle packets.
	incoming first bytes:
	0,202 start -> player data
	-14->change map
	-9->ping
	-10->loading state
	-15->building state
	-2-> game data, various suboperations
	see o14519,o2144
	*/
	public void parsePacket(byte[] buffer, int actualSize) throws IOException{
		System.out.print((buffer[0]));
		if(buffer[0]==(byte)0&&buffer[1]==(byte)202){
			int length = (((int)buffer[10]&0xff)<<24)|(((int)buffer[11]&0xff)<<16)|(((int)buffer[12]&0xff)<<8)|((int)buffer[13]&0xff);
			int[] header = new int[length];
			int i;
			for(i=0;i<length;i++)
				header[i]=(((int)buffer[14+2*i]&0xff)<<8)|((int)buffer[15+2*i]&0xff);
			this.player=new Player(buffer[9],header);
			this.player.setData(Arrays.copyOfRange(buffer,14+2*i,actualSize));
			this.parent.newPlayer(this.id);
			/**
			auto boost codes(zzz, boost, 400)
			*/
			if(this.parent.code==254486284){
				this.parent.boost();
			}if(this.parent.code==193514003){
				this.parent.boost();
			}if(this.parent.code==400){
				this.parent.boost();
				this.parent.boost();
				this.parent.boost();
			}
			
		}
		else if(buffer[0]==(byte)(-14)){
			this.map=(short)((((short)buffer[1]&0xff)<<8)|((short)buffer[2]&0xff));
			parent.setMap(this.map);
		}else if(buffer[0]==(byte)(-9)){
			if((new Date()).getTime()-lastPinged>1000){
				this.pingSinceLoad++;
				this.pingSinceBuild++;
				lastPinged=(new Date()).getTime();
			}
			if(!this.started&&!this.loaded&&this.pingSinceLoad>10){
				this.loaded=true;
				parent.fullLoad(this.id);
			}if(!this.built&&!this.ingame&&this.started&&this.pingSinceBuild>20){
				this.built=true;
				parent.fullLoad(this.id);
			}
		}
		else if(buffer[0]==(byte)(-10)&&!loaded){
			this.pingSinceLoad=0;
			parent.loadingState(this.id,buffer);
		}else if(buffer[0]==(byte)(-3)){
			byte[] pl = new byte[5];
			pl[0]=((byte)(-3));
			long time = (new Date()).getTime();
			pl[1]=((byte)(time>>24));
			pl[2]=((byte)((time>>16)&0xff));
			pl[3]=((byte)((time>>8)&0xff));
			pl[4]=((byte)((time)&0xff));
			output.write(pl,0,5);
			output.flush();
		}else if(buffer[0]==(byte)(-2)){
			System.out.print("."+buffer[6]);
			byte[] pl;
			//pl[1]=buffer[1];
			//pl[2]=buffer[2];
			//pl[3]=buffer[3];
			//pl[4]=buffer[4];
				
			if(actualSize<6)return;
			pl = new byte[actualSize-1];
			int len = actualSize-6;
			
			if(buffer[6]==(byte)0x05){
				pl = new byte[actualSize];
				len = actualSize-5;
			}
			pl[0]=(byte)-2;
			pl[1]=(byte)(len>>24);
			pl[2]=(byte)((len>>16)&0xff);
			pl[3]=(byte)((len>>8)&0xff);
			pl[4]=(byte)((len)&0xff);
			pl[5]=(byte)buffer[6];
			if(buffer[6]==(byte)0x05){
				
				//pl[6]=this.id;
				pl[6]=this.id;
				if(actualSize>25){
					String msg=new String(Arrays.copyOfRange(buffer,24,actualSize),StandardCharsets.UTF_8);
					//System.out.println("MSG:"+msg);
					if(msg.startsWith("!boost")){
						parent.boost();
					}if(msg.startsWith("!unboost")){
						parent.unboost();
					}if(msg.startsWith("!source")){
						parent.chat("https://github.com/GlennnM/NKFlashServers");
					}if(msg.startsWith("!help")){
						parent.chat("Flash Private Server by Glenn M#9606.\nCommands:\n!boost !unboost !source");
					}
				}
			}
			if(buffer[6]==(byte)0x07){
				parent.fullLoad(this.id);
				this.built=true;
			}
			else{
				//if((byte)(buffer[8]>>4&0xff)==(byte)0x0c){
				//	parent.writeFrom(this.id,buffer,0,actualSize);
				//}else{
					//if(buffer[6]==(byte)0x05){
				//		for(int i=7;i<pl.length;i++)
				//			pl[i]=buffer[i];
				//		parent.writeFrom(this.id,pl,0,pl.length);
				//	}else{
						for(int i=7;i<actualSize;i++)
							pl[i-1]=buffer[i];
						parent.writeFrom(this.id,pl,0,pl.length);
					//}
				//}
			}
		}else if(!parent.autostart&&buffer[0]==(byte)(-17)){
			
			parent.start();
		}else if(buffer[0]==(byte)(-15)&&!built){
			this.pingSinceBuild=0;
			parent.loadingState(this.id,buffer);
		}
	}
	@Override
	public void run() {
		try {
			this.alive=true;
			InputStream input=null;
			if(!this.bot){
				this.client.setSoTimeout(1000);
				input = this.client.getInputStream();
			}
			int size=1024;
			int timeouts=0;
			byte[] buffer;
			int l4=0;
			/**
			-4 packet will load the game lobby; map is also included along with a lot of other info that i have left blank
			there are other values here which might matter and aren't implemented
			see o14519, o2144
			*/
			if(!this.init)
				{
					this.init=true;
					byte[] pl = new byte[17];
					pl[0]=((byte)(-4));
					pl[1]=host;
					
					pl[2]=id;
					long time = (new Date()).getTime();
					
					pl[3]=((byte)(time>>24));
					pl[4]=((byte)((time>>16)&0xff));
					pl[5]=((byte)((time>>8)&0xff));
					pl[6]=((byte)((time)&0xff));//time
					
					pl[7]=((byte)((map>>8)&(short)0xff));
					pl[8]=(byte)(map&(short)0xff);//map
					//04 54 is last stand
				//	pl[13]=((byte)0x00);
				//	pl[14]=((byte)0x04);//?????
					
					pl[9]=((byte)((parent.mode>>8)&0xff));
					pl[10]=(byte)(parent.mode&0xff);
				//	pl[15]=((byte)0x00);
				//	pl[16]=((byte)0x04);
					
				//	pl[17]=((byte)0x00);
				//	pl[18]=((byte)0x00);
				//	pl[19]=((byte)0x00);
				//	pl[20]=((byte)0x00);
					output.write(pl,0,17);
					output.flush();
				}
			while(this.alive){
				if(this.bot){
					Thread.sleep(1000);
					if(this.started){
						parent.dropPlayer(this.id);
						this.alive=false;
						break;
					}
					continue;
				}
				int max=1024;
				int actualSize=0;
				buffer=new byte[1024];
				try {
					l4=input.read(buffer,0,1024);
					actualSize+=l4;
				} catch (java.net.SocketTimeoutException ste) {
					//
				}
				if(l4==max){
					this.client.setSoTimeout(1);
					try {
						
						while(l4>0){
							max+=l4;
							buffer = Arrays.copyOf(buffer,max);
							l4=input.read(buffer,max-1024,1024);
							actualSize+=l4;
						}
					} catch (java.net.SocketTimeoutException seee) {
						
					}
					this.client.setSoTimeout(1000);
				}
				if (actualSize>0) {
					timeouts=0;
					/**
					split incoming message into game packets(see parsePacket)
					we know the length of the message based on the "opcode"(1st byte)
					*/
					int x=0;
					System.out.print("[");
					while(x<actualSize){
						byte opcode = buffer[x];
						int length=1;
						switch(opcode){
							case -9:
								length+=4;
								break;
							case -3:
							case -17:
								length+=0;
								break;
							case -14:
								length+=4;
								break;
							case -10:
								length+=21;
								break;
							case -15:
								length+=21;
								break;
							case -2:
								length+=(((int)buffer[x+1]&0xff)<<24)|(((int)buffer[x+2]&0xff)<<16)|(((int)buffer[x+3]&0xff)<<8)|((int)buffer[x+4]&0xff)+4;
								break;
							default:
								length=(actualSize-x);
								break;
						}
						try{
							System.out.print("("+length+"/"+actualSize+")");
							parsePacket(Arrays.copyOfRange(buffer,x,x+length),length);
						}catch(Exception e){
							e.printStackTrace();
							System.out.print("!");
							break;
						}x+=length;
						if(x<actualSize){
							System.out.print(",");
							S4Server.window++;
						}
					}System.out.print("] ");
					S4Server.window++;
					if(S4Server.window%20==0)
						System.out.println();
				}else{
					timeouts++;
					
					if(timeouts>120){
						this.alive=false;
						output.close();
						input.close();
						this.client.close();
					}
				}
			}
		}catch(Exception ioe_){
			this.alive=false;
			try{
				output.close();
				this.client.close();
				ioe_.printStackTrace();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		this.parent.dropPlayer(this.id);
		return;
	}
}
//fake output stream for bots
class NullOutputStream extends OutputStream {
  @Override
  public void write(int b) throws IOException {
  }
}
//class for main method
public class S4Server {
	public static byte[] bot;
	public static volatile ConcurrentHashMap<InetAddress,Integer> ipThreads;
	public static volatile ConcurrentHashMap<String,GameServer> games;
	public static volatile ConcurrentHashMap<Integer,ArrayList<String>> history;
	public static volatile String log="";
	public static volatile int nextPort = 8118;
	public static long window=0;
	private static boolean checkPort(int p){
		for(String s:games.keySet())
			if(games.get(s).port==p)
				return true;
		return false;
	}
	public static int nextPort(){
		do{
			nextPort+=10;
			if(nextPort>65000)
				nextPort=8128;
		}while(checkPort(nextPort));
		return nextPort;
		
	}
	//makes a chat message packet
	public static byte[] encode(String s){
		//System.out.println("chat:\n"+s);
		String s2="\n[BEGINFONT size='17' color='#00FF00'CLOSEFONT]"+s+"[ENDFONT]";
		byte[] msg = s2.getBytes(StandardCharsets.UTF_8);
		int mod=0;
		if(msg[msg.length-1]==(byte)0x00){
			mod=1;
		}
		if(s.length()>255)
			return new byte[]{};
		byte[] pl = new byte[msg.length+31];
		int len = msg.length+26;
		long time = (new Date()).getTime();
		pl[0]=(byte)-2;
		pl[1]=((byte)(len>>24));
		pl[2]=((byte)((len>>16)&0xff));
		pl[3]=((byte)((len>>8)&0xff));
		pl[4]=((byte)((len)&0xff));
		pl[5]=(byte)0x05;
		pl[6]=((byte)(time>>24));
		pl[7]=((byte)((time>>16)&0xff));
		pl[8]=((byte)((time>>8)&0xff));
		pl[9]=((byte)((time)&0xff));
		pl[10]=(byte)0x00;
		pl[11]=(byte)0x00;
		pl[12]=(byte)0x03;
		pl[13]=(byte)0xe7;
		pl[14]=(byte)0xff;
		pl[15]=(byte)0xff;
		pl[16]=(byte)0xff;
		pl[17]=(byte)0xff;
		pl[18]=(byte)0x01;
		pl[19]=(byte)0x00;
		pl[20]=(byte)0x01;
		pl[21]=(byte)0x00;
		pl[22]=(byte)(msg.length&0xff-mod);
		for(int i=0;i<msg.length;i++)
			pl[23+i]=msg[i];
		return pl;
	}
	public static void main(String[] args) {
		try{
			bot=Files.readAllBytes(Paths.get("bot.bin"));
		}catch(Exception e){
			bot=new byte[]{};
		}
		games = new ConcurrentHashMap<String,GameServer>();
		//checks if a port is specified
		if (args.length == 0) {
			System.out.println("No port specified");
			System.exit(0);
		}
		int port = Integer.parseInt(args[0]);
		//checks if port is valid
		if (port < 1 || port > 65535) {
			System.out.println("Invalid port");
			System.exit(0);
		}
		ServerSocket server;
		FilePolicyServer fps = new FilePolicyServer(843);
		try{
			server = new ServerSocket(port);
			new Thread(fps).start();
		}catch(IOException e){
			return;
		}
		
		//server loop(only ends on ctrl-c)
		ArrayList<ServerThread> threads = new ArrayList<ServerThread>();
		try{
			server.setSoTimeout(1000);
		}catch(Exception eeeeeee){
			System.out.println("???");
		}while (true) {
			
			Socket client = null;
			try {
				client = server.accept();
				System.out.println("accept???");
				//client.setTcpNoDelay(true);
				ipThreads = new ConcurrentHashMap<InetAddress,Integer>();
				//for (ServerThread l:threads){System.out.println(l.isWebSocket);}
				for (int i = 0; i < threads.size(); i++) {
					if (threads.get(i)!=null&&(threads.get(i).alive||threads.get(i).isAlive())&&threads.get(i).client!=null) {
				ipThreads.put(threads.get(i).client.getInetAddress(),(ipThreads.get(threads.get(i).client.getInetAddress())==null)?1:ipThreads.get(threads.get(i).client.getInetAddress())+1);}
				if(threads.get(i)!=null&&!threads.get(i).alive&&!threads.get(i).isAlive()){
						threads.set(i,null);
					}
				}//fix all this garbage
				if(client!=null&&client.getInetAddress()!=null&&ipThreads.get(client.getInetAddress())!=null&&ipThreads.get(client.getInetAddress())>40){
					OutputStream output = client.getOutputStream();
					output.write("ServerMessage,Service unavailable. Please report".getBytes());
					output.flush();
					output.close();
					client.close();
					continue;
				}
				ServerThread connection = new ServerThread(client);
				int alives = 0;
				int index = -1;
				boolean run = false;
				//find dead threads and replace them
				for (int i = 0; i < threads.size(); i++) {
					if (threads.get(i)==null||(!threads.get(i).alive&&!threads.get(i).isAlive())) {
						if(index<0){
							index = i;
							threads.set(i, connection);
						}
					} else
						alives++;
				}
				//all threads are dead -> reset threadpool
				//System.out.println("ALIVE: "+alives+", EXIST: ");
				if (alives == 0&&index==-1) {
					threads = new ArrayList<ServerThread>();
					threads.add(connection);
					index = 0;
					run = true;
					new Thread(threads.get(index)).start();
					continue;
				}
				
				else if(index>-1){
					//at least 1 thread is dead, so just replace it
					run = true;
					threads.set(index,connection);
					new Thread(threads.get(index)).start();
					continue;
				}
				//expand threadpool, or give 505 if already 256+
				if (!run) {
					if (!run && alives < 256) {
						threads.add(connection);
						index = threads.size() - 1;
						run = true;
						new Thread(threads.get(index)).start();
						continue;
					} else {
						try {
							OutputStream output = client.getOutputStream();
							output.flush();
							try {
								Thread.sleep(1);
							} catch (InterruptedException ee) {
								Thread.currentThread().interrupt();
							}
							output.close();
							client.close();

						} catch (IOException e) {
						}

					}

				}
			} catch (Exception e) {
			}
			

		}
	}

}
