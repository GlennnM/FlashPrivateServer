import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.net.InetAddress;
import java.net.Socket;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.net.ServerSocket;

/**
 * @TODO /done
 *		 /done /done
 *       /done /done
 *		/done
 *		/done
 *		/done /nvm
 *       /done /done
 */
class ServerThread extends Thread {
	/**
	 * Client threads for "matchmaking" server. When a player joins an MP game, they
	 * send requests to here. The responses direct them to the individual game
	 * server instances(GameServers). See o4356
	 */
	public Socket client = null;
	OutputStream output;
	public volatile boolean alive;
	Player player = null;
	public int code = 0;
	public int mode = 0;

	// constructor initializes socket
	public ServerThread(Socket socket) throws IOException {
		this.client = socket;
		this.alive = true;
		this.output = this.client.getOutputStream();
	}

	@Override
	public void run() {
		try {
			this.alive = true;
			this.client.setSoTimeout(1000);
			InputStream input = this.client.getInputStream();
			int timeouts = 0;
			byte[] buffer;
			int l4 = 0;
			while (this.alive) {

				buffer = new byte[1024];
				try {
					l4 = input.read(buffer, 0, 1024);
				} catch (java.net.SocketTimeoutException ste) {
					/**
					 * kill after multiple timeouts
					 */
				}
				if (l4 > 0) {

					/**
					 * incoming format: |2| bytes: 0x00ca(the number 202). |4| bytes: match
					 * code(int). If it was a string it is converted to an int. Yes this means
					 * multiple codes could point to the same lobby |2| bytes: player level(short).
					 * |2| bytes: game mode. 1=Normal 2=NM, 3/4/5/7=events, 100+ = contracts |4|
					 * bytes: amount of values in array. |2| bytes per value: the array. no idea
					 * what it does lol
					 * 
					 * outgoing format: |2| bytes: ip length |^| (length) bytes: ip(game server) |2|
					 * bytes: port |2| bytes: mode |4| bytes: code
					 * 
					 * See o4356.
					 * 
					 * after this exchange this server isn't used as far as i'm aware
					 */
					timeouts = 0;
					if(buffer[0]!=(byte)0x00||buffer[1]!=(byte)0xca){
						this.alive=false;
						break;
					}
					code = (((int) buffer[2] & 0xff) << 24) | (((int) buffer[3] & 0xff) << 16)
							| (((int) buffer[4] & 0xff) << 8) | ((int) buffer[5] & 0xff);
					if(code==2){
						code=(int)(Math.random()*899999999)+100000000;
						while(S4Server.games.get("" + code + "\n" + mode)!=null)
							code=(int)(Math.random()*899999999)+100000000;
					}
					int length = ((int) buffer[10] & 0xff) << 24 + ((int) buffer[11] & 0xff) << 16
							+ ((int) buffer[12] & 0xff) << 8 + ((int) buffer[13] & 0xff);
					int[] data = new int[length];
					for (int i = 0; i < length; i++)
						data[i] = ((int) buffer[14 + 2 * i] & 0xff) << 8 + ((int) buffer[15 + 2 * i]);

					short level = (short) (((buffer[6] & 0xff) << 8) | (buffer[7] & 0xff));
					if(code==1855064479&&level<99){
						code-=1000000000;
					}
					mode = (((int) buffer[8] & 0xff) << 8) | ((int) buffer[9] & 0xff);
					String strCode = "" + code + "\n" + mode;
					// System.out.println(strCode.hashCode());
					short autostart = (code == 0) ? (level) : ((short) -1);// quickmatch or event
					int originalCode = code;
					// Player player = new Player(buffer[7],data);
					GameServer g = S4Server.games.get(strCode);
					while (g != null && !g.allows(level)) {
						code++;
						strCode = "" + code + "\n" + mode;
						g = S4Server.games.get(strCode);
					}
					if (g == null) {
						g = new GameServer(S4Server.nextPort(), mode, code, autostart, originalCode);
						S4Server.games.put(strCode, g);
						new Thread(g).start();
					}
					byte[] ip = S4Server.ip.getBytes(StandardCharsets.UTF_8);
					
					byte[] pl = new byte[10 + ip.length];
					pl[0] = (byte) (ip.length >> 8);
					pl[1] = (byte) (ip.length & 0xff);
					int i = 0;
					int port = S4Server.games.get(strCode).port;
					for (i = 0; i < ip.length; i++)
						pl[2 + i] = ip[i];
					pl[i + 2] = (byte) (port >> 8);
					pl[i + 3] = (byte) (port & 0xff);
					pl[i + 4] = (byte) (mode >> 8);
					pl[i + 5] = (byte) (mode & 0xff);
					pl[i + 6] = (byte) ((code >> 24) & 0xff);
					pl[i + 7] = (byte) ((code >> 16) & 0xff);
					pl[i + 8] = (byte) ((code >> 8) & 0xff);
					pl[i + 9] = (byte) ((code) & 0xff);
					output.write(pl);
					//for (int a = 0; a < pl.length; a++)
					//	System.out.print(">" + pl[a] + " ");
					output.flush();
				} else {
					timeouts++;
					code = 0;
					mode = 0;
					if (timeouts > 10) {
						this.alive = false;
						output.close();
						input.close();
						this.client.close();
						return;
					}
				}
			}
		} catch (Exception ioe_) {
			try {
				this.alive = false;
				output.close();
				this.client.close();
				ioe_.printStackTrace();
				return;
			} catch (Exception e) {
				e.printStackTrace();
			}
			return;
		}
	}
}

class FilePolicyServer extends Thread {
	/**
	 * Flash requests a "cross domain policy" xml whenever contacting a server. In
	 * SAS4 this is handled on port 843, then that connection is ended instantly
	 */
	public ServerSocket server;
	public int port;
	public boolean alive = true;

	public FilePolicyServer(int port) {
		try {
			this.port = port;
			this.server = new ServerSocket(port);
			this.server.setSoTimeout(1000);
		} catch (IOException e) {
			this.alive = false;
			return;
		}
		this.alive = true;
	}

	@Override
	public void run() {
		while (this.alive) {
			Socket client = null;
			try {
				client = server.accept();
				FilePolicyServerThread connection = new FilePolicyServerThread(client);
				new Thread(connection).start();
			} catch (Exception e) {
				// e.printStackTrace();
				continue;
			}
		}
	}
}

class FilePolicyServerThread extends Thread {
	/**
	 * Threads for individual connections; see FilePolicyServer
	 */
	public Socket client;
	public OutputStream output;
	public boolean alive;
	private String xml;
	public void doubleWrite(String s) throws IOException {
		this.output.write((s + "\n").getBytes(StandardCharsets.UTF_8));
		this.output.flush();
		//System.out.println("#OUT: file policy");
	}

	public FilePolicyServerThread(Socket client) {
		this.client = client;
		xml = "<?xml version=\"1.0\"?>\n<!DOCTYPE cross-domain-policy SYSTEM \n\"http://www.adobe.com/xml/dtds/cross-domain-policy.dtd\">\n<cross-domain-policy>\n";
		xml += "<site-control permitted-cross-domain-policies=\"master-only\"/>\n";
		xml += "<allow-access-from domain=\"*\" to-ports=\"*\"/>\n";
		xml += "</cross-domain-policy>\n";
		try {
			this.output = client.getOutputStream();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		try {
			this.alive = true;
			this.client.setSoTimeout(1000);
			InputStream input = this.client.getInputStream();
			InputStreamReader ir = new InputStreamReader(input, Charset.forName("UTF-8"));
			int timeouts = 0;
			String headers = "";
			String line = "";
			while (this.alive) {
				try {
					char[] buffer = new char[1024];
					int l4 = ir.read(buffer, 0, 1024);
					if (l4 == 1024) {
						ir.skip(999999999);
						continue;
					}
					line = new String(buffer).trim();
					headers = new String(line);
				} catch (java.net.SocketTimeoutException ste) {
					/**
					 * kill after multiple timeouts
					 */
					ste.printStackTrace();
				}
				if (line.length() > 0) {
					timeouts = 0;
					if (headers.equals("<policy-file-request/>")) {
						doubleWrite(xml);
						this.alive = false;
						output.close();
						input.close();
						ir.close();
						this.client.close();
						return;
					}
				} else {
					// System.out.println("%%timeout");
					timeouts++;
					if (timeouts > 10) {
						this.alive = false;
						output.close();
						input.close();
						ir.close();
						this.client.close();
						return;
					}
					headers = "";
					line = "";
					continue;
				}
			}
		} catch (Exception e) {
			this.alive = false;
			e.printStackTrace();
		}
	}
}

class Player {
	// header use is unknown; data is from the initial message to game server
	public volatile int level;
	public volatile int[] header;
	public volatile byte[] data;

	public Player(int level, int[] header) {
		this.level = level;
		this.header = header;
	}

	public void setData(byte[] data) {
		this.data = data;

	}
	public void setLevel(byte level){
		this.level=level;
		
	}
}

class GameServer extends Thread {
	/**
	 * Individual game server; can have up to 4 clients/bots connected. Each one
	 * will have a GameServerThread Most of the multiplayer protocol is in here and
	 * GameServerThread. See o2144, o14519, o7788
	 */
	public volatile ServerSocket server;
	public volatile int port;
	public volatile boolean alive = true;
	public volatile CopyOnWriteArrayList<GameServerThread> playerThreads;
	public volatile int mode;
	public volatile int code;
	public volatile int actualCode;
	public volatile boolean init;
	public volatile short seed;
	public volatile short map;
	public volatile byte nextId;
	public volatile byte host;
	public volatile boolean started;
	public volatile boolean autostart;
	public volatile short minLvl;
	public volatile short maxLvl;
	public volatile int startTime;
	public volatile boolean autoFlush;
	public volatile int elapsedTime;
	public volatile long lastTime;
	public volatile boolean tcpNoDelay;
	public volatile int mapSet=0;
	// public long startAt;
	public boolean allows(short lvl) {
		return (!this.started) && (this.playerThreads != null) && (this.playerThreads.size() < 4) && (lvl <= maxLvl)
				&& (lvl >= minLvl);
	}
	public boolean can101(){
		try{
			for(GameServerThread g:playerThreads){
				if(!g.bot&&g.player.level<96)
					return false;
			}
		}catch(Exception e){
			
		}return true;
	}
	public void flushAll(){
		for(GameServerThread g:playerThreads){
			try{
				g.flushAll();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		
	}
	
	public void toggleNoDelay(){
		
			this.tcpNoDelay=!this.tcpNoDelay;
			for(GameServerThread g:playerThreads){
				try{
				g.client.setTcpNoDelay(this.tcpNoDelay);
				}catch(Exception e){continue;}
			}
		
	}
	
	public GameServer(int port, int mode, int code, short auto, int actualCode) {
		playerThreads = new CopyOnWriteArrayList<GameServerThread>();
		this.nextId = 0;
		this.host = 0;
		this.startTime = -1;
		this.elapsedTime = 0;
		this.lastTime = 0;
		this.actualCode=actualCode;
		this.started = false;
		this.autostart = (auto >= 0);
		this.tcpNoDelay=false;
		this.autoFlush=true;
		if (this.autostart) {
			this.minLvl = (short) (auto - 10);
			this.maxLvl = (short) (auto + 10);
		} else {
			this.minLvl = (short) -1000;
			this.maxLvl = (short) 1000;
		}
		try {
			this.port = port;
			this.server = new ServerSocket(port);
			this.server.setSoTimeout(1000);
		} catch (IOException e) {
			return;
		}
		this.mode = mode;
		this.code = code;
		// Pick a map based on mode; nm and event maps both have separate id's
		// playing normal on an NM map and vice versa is playable; playing normal on an
		// event map does nothing
		this.seed = (short) (Math.random() * 32768);
		if (this.mode == 2) {
			// NM
			this.map = (short) 1102;
			this.map += (short) (Math.random() * 8);
			if (Math.random() < 0.2)
				this.map = (short) 1112;
		} else if (this.mode == 1) {
			// normal
			this.map = (new short[] { 1008, 1018, 1067, 1009, 1054, 1043, 1016, 1101, 1110 })[(int) (Math.random()
					* 9)];
		} else if (this.mode < 100) {
			// event
			this.map=S4Server.getEventMap();
			if(this.map<1000){
				this.mapSet=map;
				map=S4Server.EVENT_MAP_SETS[mapSet][(int) (Math.random() * S4Server.EVENT_MAP_SETS[mapSet].length)];
			}else this.mapSet=-1;
			//if(this.map==-1)
			//	this.map=(new short[]{1092,1095,1094,1100,1093,1099,1096,1111,1113})[(int)(Math.random()*9)];
			// onsl pods sur ls vac po vip is md
		}
		// Otherwise it is a contract, but contract lobbies seem to work fine without
		// having added the map.
		// I think the clients try to change the map after joining
		this.init = false;
		this.alive = true;
	}
	public void memory() {
		int mb = 1024 * 1024;
		// get Runtime instance
		Runtime instance = Runtime.getRuntime();
		String usage = "";
		// available memory
		usage+=("Games: " +S4Server.games.size()+", Players: "+S4Server.getPlayerCount());
		usage+=(", Threads: " +Thread.activeCount()+"\n");
		// used memory
		usage+=("Used Memory: "
				+ (instance.totalMemory() - instance.freeMemory()) / mb+" MB, ");
		// Maximum available memory
		usage+="Allocated: " + instance.totalMemory() / mb+" MB";
		usage+=("\nMax Memory: " + instance.maxMemory() / mb+" MB");
		chat(usage);
		chat("Connections since last restart: "+S4Server.connections+"\nGames started: "+S4Server.gamesStarted);
	}
	public void chat(String s) {
		byte first=(byte)-1;
		byte[] msg;
			for(GameServerThread g:playerThreads){
				if(first==(byte)-1){
					first=g.id;
					byte second=(byte)-1;
					for(GameServerThread h:playerThreads)
						if(h.id!=g.id)
							second=h.id;
					if(second==-1)
						return;
					msg = S4Server.encode(s, second);
				}else{
					if(first==-1)
						return;
					msg = S4Server.encode(s, first);
					
				}
				try{
					g.output.write(msg,0,msg.length);
					//g.output.flush();
				}catch(IOException e){continue;}
			}
		
		flushAll();
		//this.writeAll(msg, 0, msg.length);
	}

	// Add a bot, if possible.
	// Bots will leave as soon as building starts.
	public void boost(byte level,int vs) {
	if (level>101||(level==101&&!can101())||playerThreads.size() > 3||this.started||(this.mode>2&&level<1)||(vs>1&&this.mode!=7)) {
			chat("Unable to boost.");
			return;
		}
		GameServerThread bot = new GameServerThread(null, this, this.nextId, this.host, vs);
		bot.player.setLevel(level);
		this.nextId++;
		playerThreads.add(bot);
		newPlayer(bot.id);
		chat((vs==1)?("Added 1 VS bot!"):((vs==0)?"Added 1 bot!":"Added 1 deadtab!"));
		new Thread(bot).start();
	}
	// Remove a bot.
	public void unboost() {
		int i = 0;
		
			for (i = 0; i < this.playerThreads.size(); i++)
				if (playerThreads.get(i).bot) {
					chat("Removed 1 bot!");
					this.dropPlayer(playerThreads.get(i).id);
					i--;
					return;
				}
		
		//chat("No bots to remove");
	}
	// Accept connections and route to GameServerThreads.
	// Also use the loop for checks(start game=-11 packet when all built)
	@Override
	public void run() {
		while (this.alive) {
			long t = (new Date()).getTime();
			if (t - 1000 > lastTime) {
				this.minLvl--;
				this.maxLvl++;
				lastTime = t;
				if (this.startTime != -1) {
					this.elapsedTime++;
					if (autostart) {
						if (this.allLoaded()&&!this.started && this.elapsedTime >= this.startTime) {
							this.start();
							this.startTime = -1;
							this.elapsedTime = 0;
						}
					}
				}
				int first=-1;
				/**
				if(this.init){
				for (int i = 0; i < playerThreads.size(); i++) {
					if (!playerThreads.get(i).bot && playerThreads.get(i).alive)
						first = i;
					}
					if (first == -1) {
						this.alive = false;
						System.out.println("Game cleaned up");
						try {
							this.server.close();
						} catch (Exception e) {
						}
						S4Server.games.remove("" + this.code + "\n" + this.mode);
						return;
					}
				}*/
			}
			Socket client = null;
			int ready = 0;
			
				for (GameServerThread g : playerThreads) {
					if ((g.built && g.started && !g.ingame)||g.vs>0)
						ready++;
				}
			
			if (ready == playerThreads.size()) {
				long time = (new Date()).getTime()/1000 + 3;
				byte[] pl = new byte[5];
				pl[0] = (byte) -11;
				pl[1] = ((byte) (time >> 24));
				pl[2] = ((byte) ((time >> 16) & 0xff));
				pl[3] = ((byte) ((time >> 8) & 0xff));
				pl[4] = ((byte) ((time) & 0xff));// time
				this.writeAll(pl, 0, 5);
				chat("All players built(hopefully)\nStarting game!");
				long hydar = (new Date()).getTime();
				
					for (GameServerThread g : playerThreads){
						g.ingame = true;
						g.ingameSince=hydar;
					}
				
			}
			try {
				client = server.accept();
				client.setTcpNoDelay(this.tcpNoDelay);
				//this.init = true;
				GameServerThread connection = new GameServerThread(client, this, this.nextId, this.host, 0);
				
				new Thread(connection).start();
			} catch (Exception e) {
				// e.printStackTrace();
				continue;
			}
		}
	}

	// Change the map, alerting all players. Packets start with -14.
	public void setMap(short map) throws IOException {
		// System.out.println(map);
		this.map = map;
		byte[] pl = new byte[9];
		pl[0] = ((byte) (-14));
		pl[3] = ((byte) ((mode >> 8) & 0xff));
		pl[4] = (byte) (mode & 0xff);
		pl[1] = ((byte) ((map >> 8) & 0xff));
		pl[2] = (byte) (map & 0xff);
		
			for (GameServerThread g : playerThreads)
				g.map = this.map;
		
		this.writeAll(pl, 0, 5);
	}

	// Forward a message to all players except the one specified by id.
	public void writeFrom(byte id, byte[] data) {
		
		
			for (GameServerThread h : playerThreads)
				if (h.id != id) {
					try {
						h.output.write(data);
						//h.output.flush();
					} catch (IOException e) {
						continue;
					}
				}
		
	}

	// same function different arguments
	public void writeFrom(byte id, byte[] data, int offset, int length) {
		
		
			for (GameServerThread h : playerThreads)
				if (h.id != id) {
					try {
						h.output.write(data, offset, length);
						//h.output.flush();
					} catch (IOException e) {
						continue;
					}
				}
		
	}

	// Forward a message to all players.
	public void writeAll(byte[] data, int off, int length) {
		
		
			for (GameServerThread g : playerThreads) {
				try {
					// System.out.println(data.length);
					g.output.write(data, off, length);
					//g.output.flush();
				} catch (IOException e) {
					continue;
				}
			}
		
	}

	// Add a player, alert them of all the existing players and alert all the
	// existing players of them.
	// See playerData() for protocol information.
	public void newPlayer(byte id) {
		
			for (GameServerThread g : playerThreads) {
				try {
					if (g.id == id) {
						for (GameServerThread h : playerThreads){
							if (h.id != id) {
								g.output.write(h.playerData((byte) -1));
								//g.output.flush();
								if(!g.welcomed){
									g.output.write(S4Server.encode("Welcome to SAS4 Private Server!\n!help for a list of commands.",h.id));
									g.welcomed=true;
								}
								//g.output.flush();
							}
								
						}
					} else
						for (GameServerThread h : playerThreads)
							if (h.id == id) {
								g.output.write(h.playerData((byte) -1));
								if(!g.welcomed){
									g.output.write(S4Server.encode("Welcome to SAS4 Private Server!\n!help for a list of commands.",h.id));
									g.welcomed=true;
								}
								//g.output.flush();
							}
				}

				catch (IOException e) {
					continue;
				}
			}
		
		this.flushAll();
		//for (GameServerThread g : playerThreads) {
			
			//fullLoad(id);
		//}
	}
	private void autoTick(){
		if(this.allLoaded()){
			if (playerThreads.size() == 2) {
					this.startTime = (this.mode == 5) ? 8 : 15;
					this.elapsedTime = 0;
				} else if (playerThreads.size() == 3) {
					if(this.startTime > 4)
						this.startTime -= 3;
					else this.startTime=4;
				} else if (playerThreads.size() == 4) {
					this.startTime = 0;
				}
				if (autostart&&!this.started) {
			
					byte[] pl = new byte[5];
					pl[0] = (byte) -13;
					int st = 1000 * Math.max(0,this.startTime-this.elapsedTime);
					pl[1] = (byte) (st >> 24);
					pl[2] = (byte) ((st >> 16) & 0xff);
					pl[3] = (byte) ((st >> 8) & 0xff);
					pl[4] = (byte) ((st) & 0xff);
					this.writeAll(pl, 0, 5);
				}
				
		}else{
			this.startTime=-1;
			this.elapsedTime=0;
		}
	}
	// float value containing loading or building % for some player(0.0-1.0).
	public void loadingState(byte id, byte[] buffer) {
		byte[] pl = new byte[11];
		pl[0] = (byte) -2;
		pl[4] = (byte) (0x06);
		pl[5] = (byte) 7;
		pl[6] = id;
		pl[7] = buffer[10];
		pl[8] = buffer[11];
		pl[9] = buffer[12];
		pl[10] = buffer[13];
		this.writeFrom(id, pl);
		this.flushAll();
		if(autostart){
			autoTick();
			this.flushAll();
		}
		
	}

	// force building/loading finish(workaround for stuck lobbies)
	public void fullLoad(byte id) {
		byte[] pl = new byte[11];
		pl[0] = (byte) -2;
		pl[4] = (byte) (0x06);
		pl[5] = (byte) 7;
		pl[6] = id;
		pl[7] = (byte) 0x3f;
		pl[8] = (byte) 0x80;
		this.writeFrom(id, pl);
		this.flushAll();
		if(autostart){
			autoTick();
			this.flushAll();
		}
	}

	// remove a player/bot, alerting all other players(-6) and changing host(-7) if
	// needed
	public void dropPlayer(byte id) {
		
			int prev = playerThreads.size();
			playerThreads.removeIf(x -> (x.id == id));
			if(playerThreads.size()==prev)
				return;
			int first = -1;
			if (playerThreads.size() == 1 && this.autostart) {
				this.startTime = -1;
				this.elapsedTime = 0;
			}
			for (int i = 0; i < playerThreads.size(); i++) {
				if (!playerThreads.get(i).bot && playerThreads.get(i).alive)
					first = i;
			}
			if (first == -1) {
				this.alive = false;
				System.out.println("Game ended");
				try {
					this.server.close();
				} catch (Exception e) {
				}
				S4Server.games.remove("" + this.code + "\n" + this.mode);
				return;
			}
			byte[] pl = new byte[] { (byte) -6, id };
			this.writeAll(pl, 0, 2);
			this.flushAll();
			if (this.host == id) {
				// change host
				byte[] pl2 = new byte[] { (byte) -7, id, playerThreads.get(first).id };
				this.host = playerThreads.get(first).id;
				this.writeAll(pl2, 0, 3);
			}
		
	}

	// start building(start game button), or automatically in event/quickmatch
	public void start() {
		chat("Starting building...");
		System.out.println("-------STARTING GAME " + code + "@"+new Date()+"-------");
		S4Server.gamesStarted++;
		this.writeAll(new byte[] { (byte) -16 }, 0, 1);
		this.flushAll();
		this.started = true;
		
			for (GameServerThread g : playerThreads) {
				g.started = true;
				g.pingSinceBuild = 0;
			}
		
	}public boolean allLoaded(){
		for(GameServerThread g:playerThreads)
			if(!g.loaded)
				return false;
		return true;
	}
}

class GameServerThread extends Thread {
	// See GameServer
	public volatile Socket client;
	public volatile OutputStream rawOutput;
	public volatile BufferedOutputStream output;
	public volatile boolean alive;
	public volatile short map;
	public volatile Player player;
	public volatile GameServer parent;
	public volatile byte id;
	public volatile byte host;
	//game state(started means building started, ingame means actually started)
	public volatile boolean init;
	public volatile boolean welcomed;
	public volatile boolean loaded;
	public volatile boolean started;
	public volatile boolean built;
	public volatile boolean ingame;
	
	public volatile int pingSinceLoad;
	private volatile long lastPinged;
	public volatile int pingSinceBuild;
	public volatile long ingameSince;
	
	public volatile boolean bot;
	public volatile int vs;
	public volatile int actualCode;
	public volatile float load;
	public volatile float build;
	public volatile float tickTime;
	public volatile long lastTick;
	public void flushAll() throws IOException{
		this.output.flush();
		this.rawOutput.flush();
	}
	/**
	 * -2 packet with operation -1 as interpreted in o14519; reflects most of the
	 * data from the (0x00ca) packet containing player data. Appended to this is a
	 * byte which goes from 0-100(loaded percentage). Each starting opcode
	 * corresponds to a different function; however -2 is special in that it will
	 * have a "sub-operation" such as -1. Format: |1| operation |4| length IF
	 * type=bytearray |1| suboperation IF type -1 |data| - usually includes id and
	 * level early on See o14519, o2144.
	 */
	
	public byte[] playerData(byte opcode) {
		int len = this.player.data.length;
		if (opcode == -1)
			len++;
		byte[] pl;
		pl = new byte[len + 5];
		if (this.bot) {
			pl[len + 4] = (byte) 100;
		}else
			pl[len + 4] = (byte) (this.load*100);
		
		try {
			pl[0] = (byte) -2;
			pl[1] = (byte) (len >> 24);
			pl[2] = (byte) ((len >> 16) & 0xff);
			pl[3] = (byte) ((len >> 8) & 0xff);
			pl[4] = (byte) ((len) & 0xff);
			pl[5] = (byte) opcode;
			pl[6] = this.id;
			short nameLen = (short) ((((short) this.player.data[0] & 0xff) << 8)
					| ((short) this.player.data[1] & 0xff));

			// nameLen|=((short)(this.player.data[1])&0xff);
			// System.out.println("%%%NAMELEN
			// "+nameLen+"//"+this.player.data[0]+"//"+this.player.data[1]);
			for (int i = 7; i < 10 + nameLen; i++)
				pl[i] = this.player.data[i - 7];
			pl[10 + nameLen] = (byte) ((this.player.level >> 8) & 0xff);
			// System.out.println("pl%%%"+this.player.level);
			pl[11 + nameLen] = (byte) (this.player.level & 0xff);
			for (int i = 12 + nameLen; i < this.player.data.length + 5; i++)
				pl[i] = this.player.data[i - 5];
		} catch (Exception e) {
			e.printStackTrace();
		}
		return pl;

	}

	// deprecated(but works)
	public byte[] getPowerups() {
		short nameLen = (short) ((((short) this.player.data[0] & 0xff) << 8) | ((short) this.player.data[1] & 0xff));
		// int dataLen =
		// (((int)this.player.data[nameLen+2]&0xff)<<24)|(((int)this.player.data[nameLen+3]&0xff)<<16)|(((int)this.player.data[nameLen+4]&0xff)<<8)|((int)this.player.data[nameLen+5]&0xff);
		byte gearCount = this.player.data[nameLen + 7];
		int gearBytes = 0;
		for (int i = 0; i < gearCount; i++) {
			gearBytes += 18 + (this.player.data[nameLen + gearBytes + 20] * 2);
		}
		// System.out.println("gearCount: "+gearCount+"\ngearBytes: "+gearBytes);
		int dictionaryBytes = this.player.data[nameLen + gearBytes + 8] * 2 + 1;
		// System.out.println("dictionaryBytes: "+dictionaryBytes);
		byte powerupBytes = this.player.data[nameLen + gearBytes + 24 + dictionaryBytes];
		// System.out.println("powerupBytes: "+powerupBytes);
		byte[] powerups = new byte[powerupBytes];
		for (int i = 0; i < powerupBytes; i++)
			powerups[i] = this.player.data[nameLen + gearBytes + 25 + dictionaryBytes + i];
		int len = (powerupBytes + 1) * 4 + 1;
		byte[] pl = new byte[len + 5];
		pl[0] = (byte) -2;
		pl[1] = (byte) (len >> 24);
		pl[2] = (byte) ((len >> 16) & 0xff);
		pl[3] = (byte) ((len >> 8) & 0xff);
		pl[4] = (byte) ((len) & 0xff);
		pl[5] = (byte) 11;
		pl[9] = this.id;
		for (int i = 0; i < powerupBytes; i++)
			pl[13 + 4 * i] = powerups[i];
		return pl;

	}

	/**
	 * Constructor. If client isn't a real socket we will create a bot instead
	 * parent is a reference to the GameServer we are connected to
	 */
	public GameServerThread(Socket client, GameServer parent, byte id, byte host, int vs) {
		this.parent = parent;
		this.welcomed=false;
		this.tickTime=0.05f;
		this.map = parent.map;
		this.alive = true;
		this.init = false;
		this.player = null;
		this.loaded = false;
		this.started = false;
		this.built = false;
		this.ingame = false;
		this.bot = false;
		this.vs=vs;
		this.load=0.0f;
		this.build=0.0f;
		this.lastPinged = 0;
		this.pingSinceLoad = 0;
		this.pingSinceBuild = 0;
		this.ingameSince=(new Date().getTime())*2;
		this.id = id;
		this.host = host;
		this.client = client;
		try {
			this.rawOutput = client.getOutputStream();
		} catch (Exception e) {
			this.bot = true;
			this.built=true;
			this.loaded=true;
			this.rawOutput = new NullOutputStream();
			this.player = new Player((short) 100, new int[] {});
			switch(this.vs){
				case 1:
					this.player.setData(S4Server.vsbot);
					break;
				case 2:
					this.player.setData(S4Server.deadtab);
					break;
				default:
					this.player.setData(S4Server.bot);
					break;
			}
		}
		this.output = new BufferedOutputStream(rawOutput);
	}

	/**
	 * handle packets. incoming first bytes: 0,202 start -> player data -14->change
	 * map -9->ping -10->loading state -15->building state -2-> game data, various
	 * suboperations see o14519,o2144
	 */
	public void parsePacket(byte[] buffer, int actualSize) throws IOException {
		byte[] pl;
		long time;
		if(S4Server.verbose)
			System.out.print((buffer[0]));
		switch((int)buffer[0]){
			case 0:
				if(!parent.started&&buffer.length>13&&buffer[1] == (byte) 202){
					parent.nextId++;
					parent.init=true;
					parent.playerThreads.add(this);
					int length = (((int) buffer[10] & 0xff) << 24) | (((int) buffer[11] & 0xff) << 16)
						| (((int) buffer[12] & 0xff) << 8) | ((int) buffer[13] & 0xff);
					int[] header = new int[length];
					int i;
					for (i = 0; i < length; i++)
						header[i] = (((int) buffer[14 + 2 * i] & 0xff) << 8) | ((int) buffer[15 + 2 * i] & 0xff);
					this.player = new Player(buffer[9], header);
					this.player.setData(Arrays.copyOfRange(buffer, 14 + 2 * i, actualSize));
					this.parent.newPlayer(this.id);
					/**
					 * auto boost codes(zzz, boost, 400)
					 */
					 /**
					if (this.parent.actualCode == 254486284) {
						this.parent.boost((byte)100, 0);
					}
					else if (this.parent.actualCode == 193514003) {
						this.parent.boost((byte)100, 0);
					}
					else if (this.parent.actualCode == 1855064479&&this.parent.playerThreads.size()<2) {
						this.parent.boost((byte)100, 0);
					}
					else if (this.parent.actualCode == 2&&this.parent.playerThreads.size()<2) {
						this.parent.boost((byte)100, 0);
					}else if (this.parent.actualCode == 0&&this.parent.playerThreads.size()<2) {
						this.parent.boost((byte)100, 0);
					}
					*/
					if(this.parent.playerThreads.size()<2){
						if(this.parent.mode!=7){
							this.parent.boost((byte)100, 0);
							if (this.parent.actualCode == 400||Math.abs(this.parent.actualCode-2089076591)<20) {
								this.parent.boost((byte)100, 0);
								this.parent.boost((byte)100, 0);
							}
						}else{
							this.parent.boost((byte)100, 2);
							//this.parent.boost((byte)100, 1);
							//this.parent.boost((byte)100, 1);
						}
					}
				}
			break;
			case -14:
				if(!parent.started){
					this.map = (short) ((((short) buffer[1] & 0xff) << 8) | ((short) buffer[2] & 0xff));
					parent.setMap(this.map);
				}
			break;
			case -9:
				if ((new Date()).getTime() - lastPinged > 1000) {
					this.pingSinceLoad++;
					this.pingSinceBuild++;
					pl = new byte[5];
					pl[0] = ((byte) (-3));
					time = (new Date()).getTime()/1000;
					pl[1] = ((byte) (time >> 24));
					pl[2] = ((byte) ((time >> 16) & 0xff));
					pl[3] = ((byte) ((time >> 8) & 0xff));
					pl[4] = ((byte) ((time) & 0xff));
					output.write(pl, 0, 5);
					lastPinged = (new Date()).getTime();
				}
				if (!this.started && !this.loaded && this.pingSinceLoad > 10) {
					this.loaded = true;
					this.load=1.0f;
					parent.fullLoad(this.id);
				}
				if (!this.built && !this.ingame && this.started && this.pingSinceBuild > 40) {
					this.built = true;
					this.load=1.0f;
					parent.fullLoad(this.id);
				}
			break;
			case -10:
				this.pingSinceLoad = 0;
				this.load = Float.intBitsToFloat(( buffer[10]&0xff )| ((buffer[11]&0xff)<<8 ) | ((buffer[12]&0xff)<<16 ) | ((buffer[13] &0xff)<<24 ));
				if(Math.abs(1f-this.load)<0.0001){
					this.load=1.0f;
					parent.fullLoad(this.id);
					this.loaded=true;
				}else 
					parent.loadingState(this.id, buffer);
			break;
			case -3:
			if (this.ingame&&(new Date()).getTime() - lastPinged > 1000) {
				pl = new byte[5];
				pl[0] = ((byte) (-3));
				time = (new Date()).getTime()/1000;
				pl[1] = ((byte) (time >> 24));
				pl[2] = ((byte) ((time >> 16) & 0xff));
				pl[3] = ((byte) ((time >> 8) & 0xff));
				pl[4] = ((byte) ((time) & 0xff));
				lastPinged = (new Date()).getTime();
				output.write(pl, 0, 5);
			}
			break;
			case -2:
				if(S4Server.verbose)
					System.out.print("." + buffer[6]);

				if (actualSize < 6)
					return;
				
				pl = new byte[actualSize - 1];
				int len = actualSize - 6;

				pl[0] = (byte) -2;
				pl[1] = (byte) ((len >> 24)& 0xff);
				pl[2] = (byte) ((len >> 16) & 0xff);
				pl[3] = (byte) ((len >> 8) & 0xff);
				pl[4] = (byte) ((len) & 0xff);
				pl[5] = (byte) buffer[6];
				if (buffer[6] == (byte) 0x05) {

					// pl[6]=this.id;
					pl[6] = this.id;
					if (actualSize > 25&&buffer[13]==(byte)0x03) {
						String msg = "";
						byte chat_length=0;
						try{
							chat_length = buffer[23];
							msg = new String(Arrays.copyOfRange(buffer, 24, 24+chat_length), StandardCharsets.UTF_8);
							if (msg.startsWith("!boost")) {
							if(msg.indexOf(" ")==-1)
								parent.boost((byte)100, 0);
							else parent.boost(Byte.parseByte(msg.substring(msg.indexOf(" ")+1)), 0);
							}else if (msg.startsWith("!vsboost")) {
								if(msg.indexOf(" ")==-1)
									parent.boost((byte)100, 1);
								else parent.boost(Byte.parseByte(msg.substring(msg.indexOf(" ")+1)), 1);
							}else if (msg.startsWith("!deadtab")) {
								if(msg.indexOf(" ")==-1)
									parent.boost((byte)100, 2);
								else parent.boost(Byte.parseByte(msg.substring(msg.indexOf(" ")+1)), 2);
							}
							else if (msg.startsWith("!unboost")) {
								parent.unboost();
							}
							else if (msg.startsWith("!source")) {
								parent.chat("https://github.com/GlennnM/NKFlashServers");
							}else if (msg.startsWith("!help tickrate")) {
								parent.chat("!tickrate (number). \nThis determines the maximum number of packets the server can send to you per second.\nNo effect unless autoflush is off.\nDefault is 0.\nOnly affects you.");
							}else if (msg.startsWith("!help")) {
								parent.chat("Flash Private Server by Glenn M#9606.\nCommands:\n!boost <lvl>, !vsboost, !deadtab, !unboost\n !source, !seed, !stats, !code, !range, !disconnect");
							}
							else if (msg.startsWith("!seed")) {
								parent.chat("Current seed: "+parent.seed+"\nMap ID: "+parent.map+"\nMode: "+parent.mode);
							}
							else if (msg.startsWith("!code")) {
								parent.chat("Current code: "+parent.actualCode+"\nMatch ID: "+parent.code+"\nMap ID: "+parent.map+"\nMode: "+parent.mode);
							}
							else if (msg.startsWith("!stats")) {
								parent.memory();
							}
							else if (msg.startsWith("!range")) {
								parent.chat("Accepting levels "+parent.minLvl+"-"+parent.maxLvl);
							}
							else if (msg.startsWith("!tickrate")) {
								if(msg.indexOf(" ")==-1)
									this.tickTime=0.0f;
								float q=Float.parseFloat(msg.substring(msg.indexOf(" ")+1));
								if(q==0)
									this.tickTime=0.0f;
								else this.tickTime=1.0f/q;
								parent.chat("Tick rate is now "+q);
							}
							else if (msg.startsWith("!map")) {
								if(msg.indexOf(" ")!=-1&&!parent.started){
									int q=Integer.parseInt(msg.substring(msg.indexOf(" ")+1));
									if(q>0&&parent.mode>2&&parent.mode<7&&q<=S4Server.EVENT_MAPS.length){
										if(parent.mapSet==-1)
											parent.chat("Invalid map");
										else{
											short m=S4Server.getEventMap(q-1);
											for(short s:S4Server.EVENT_MAP_SETS[parent.mapSet])
												if(m==s){
													parent.setMap(m);
													m=-1;
													break;
												}
											if(m>=0){
												parent.chat("Map not allowed today. Allowed:\n"+S4Server.EVENT_MAP_DESC[parent.mapSet]);
											}
										}
									}else
										parent.chat("Invalid map/not Apoc or LMS");
								}else
									parent.chat("Specify a map: 1-9 = normal maps, 10+ = contract maps");
								parent.flushAll();
							}else if(msg.startsWith("!autoflush")){
								this.parent.autoFlush=!this.parent.autoFlush;
								parent.chat("Auto flush set to "+this.parent.autoFlush);
							}else if(msg.startsWith("!tcpnodelay")){
								this.parent.toggleNoDelay();
								parent.chat("TCP nodelay set to "+this.parent.tcpNoDelay);
							}else if(msg.startsWith("!disconnect")){
								parent.chat("Disconnecting player...");
								this.alive=false;
							}
						}catch(Exception e){
							//not a chat message, probably
						}
						// System.out.println("MSG:"+msg);
						
					}
				}
				
				if (buffer[6] == (byte) 0x07) {
					this.build=1.0f;
					parent.fullLoad(this.id);
					this.built = true;
				}
				
				for (int i = 7; i < actualSize; i++)
					pl[i - 1] = buffer[i];
				if(actualSize>10&&buffer[6] != (byte) 0x07){
					time = (new Date()).getTime()/1000;
					pl[6] = ((byte) (time >> 24));
					pl[7] = ((byte) ((time >> 16) & 0xff));
					pl[8] = ((byte) ((time >> 8) & 0xff));
					pl[9] = ((byte) ((time) & 0xff));
				}
				parent.writeFrom(this.id, pl, 0, pl.length);
			break;
			case -17:
			if(buffer[0] == (byte) (-17) && !parent.started)
				parent.start();
			break;
			case -15:
			if(!built){
				this.pingSinceBuild = 0;
			
				this.build=Float.intBitsToFloat(( buffer[10]&0xff )| ((buffer[11]&0xff)<<8 ) | ((buffer[12]&0xff)<<16 ) | ((buffer[13] &0xff)<<24 ));
				if(Math.abs(1f-this.build)<0.0001){
					this.build=1.0f;
					parent.fullLoad(this.id);
					this.built=true;
				}else 
					parent.loadingState(this.id, buffer);
			}
			break;
		}
		if(buffer[0]<(byte)0&&parent.autoFlush)
			parent.flushAll();
	}

	@Override
	public void run() {
		try {
			int overflow=0;
			this.alive = true;
			InputStream input = null;
			if (!this.bot) {
				this.client.setSoTimeout(1000);
				input = this.client.getInputStream();
			}
			int timeouts = 0;
			byte[] buffer;
			byte[] prev=new byte[0];
			int l4 = 0;
			/**
			 * -4 packet will load the game lobby; map is also included along with a lot of
			 * other info that i have left blank there are other values here which might
			 * matter and aren't implemented see o14519, o2144
			 */
			if (!this.init) {
				this.init = true;
				byte[] pl = new byte[17];
				pl[0] = ((byte) (-4));
				pl[1] = host;

				pl[2] = id;
				long time = (new Date()).getTime()/1000;

				pl[3] = ((byte) (time >> 24));
				pl[4] = ((byte) ((time >> 16) & 0xff));
				pl[5] = ((byte) ((time >> 8) & 0xff));
				pl[6] = ((byte) ((time) & 0xff));// time

				pl[7] = ((byte) ((map >> 8) & (short) 0xff));
				pl[8] = (byte) (map & (short) 0xff);// map
				// 04 54 is last stand
				// pl[13]=((byte)0x00);
				// pl[14]=((byte)0x04);//?????

				pl[9] = ((byte) ((parent.mode >> 8) & 0xff));
				pl[10] = (byte) (parent.mode & 0xff);
				pl[11] = ((byte) ((parent.seed >> 8) & (short)0xff));
				pl[12] = (byte) (parent.seed & (short)0xff);
				/**
				next 4 bytes are time until start in ms i think
				 */
				rawOutput.write(pl, 0, 17);
				rawOutput.flush();
				//this.flushAll();
				if(this.bot){
					this.load=1.0f;
					this.build=1.0f;
					parent.fullLoad(this.id);
				}
			}
			while (this.alive) {
				if(!this.parent.alive){
					this.alive=false;
					break;
				}
				if (this.bot) {
					try{
						Thread.sleep(1000);
					}catch(InterruptedException e){
						Thread.currentThread().interrupt();
					}
					if (this.started&&this.vs==0) {
						parent.dropPlayer(this.id);
						this.alive = false;
						break;
					}else if(this.vs==1&&(new Date().getTime())-this.ingameSince>6000){
						parent.dropPlayer(this.id);
						this.alive = false;
						break;
					}else if(this.vs==2&&(new Date().getTime())-this.ingameSince>240000){
						parent.dropPlayer(this.id);
						this.alive = false;
						break;
					}
					continue;
				}
				int max = 1024;
				int actualSize = 0;
				buffer = new byte[1024];
				try {
					l4 = input.read(buffer, 0, 1024);
					actualSize += l4;
				} catch (java.net.SocketTimeoutException ste) {
					//
				}
				if (l4 == max) {
					this.client.setSoTimeout(1);
					try {

						while (l4 > 0) {
							max += l4;
							buffer = Arrays.copyOf(buffer, max);
							l4 = input.read(buffer, max - 1024, 1024);
							actualSize += l4;
						}
					} catch (java.net.SocketTimeoutException seee) {

					}
					this.client.setSoTimeout(1000);
				}
				if (actualSize > 0) {
					timeouts = 0;
					/**
					 * split incoming message into game packets(see parsePacket) we know the length
					 * of the message based on the "opcode"(1st byte)
					 */
					int x = 0;
					if(S4Server.verbose)
						System.out.print("[");
					if(overflow>0){
						
						int read = Math.min(overflow,actualSize);
						overflow-=read;
						x+=read;
						int j=0;
						prev = Arrays.copyOf(prev,prev.length+read);
						for(j=prev.length-read;j<prev.length;j++)
							prev[j]=buffer[j-prev.length+read];
						if(overflow==0){
							if(S4Server.verbose)
								System.out.println("{"+prev.length+"/"+actualSize+"}");
							try{
								parsePacket(Arrays.copyOfRange(prev, 0, prev.length), prev.length);
								if((new Date()).getTime()-lastTick>tickTime*1000){
									parent.flushAll();
									lastTick=(new Date()).getTime();
								}
							}catch(Exception e){
								//System.out.print("*");
							}
							
						}else{
							overflow=0;
							prev = new byte[]{};
							continue;
						}
					}
					while (x < actualSize) {
						
						byte opcode = buffer[x];
						if(this.player==null&&opcode<(byte)0)
							opcode=(byte)1;
						int length = 1;
						switch (opcode) {
							case -9:
								length += 4;
								break;
							case -3:
							case -17:
								length += 0;
								break;
							case -14:
								length += 4;
								break;
							case -10:
								length += 21;
								break;
							case -15:
								length += 21;
								break;
							case -2:
								length += (((int) buffer[x + 1] & 0xff) << 24) | (((int) buffer[x + 2] & 0xff) << 16)
										| (((int) buffer[x + 3] & 0xff) << 8) | ((int) buffer[x + 4] & 0xff) + 4;
								break;
							case 0:
								length = (actualSize - x);
								break;
							default://print something maybe
								length = (actualSize - x);
								timeouts=(120+timeouts)/2;
								timeouts+=10;
								break;
						}if(buffer.length>x+6&&buffer[x+6]==(byte)0x07&&buffer[x]==(byte)-2){
							length+=2;
						}
						if(x+length<=actualSize){
							try {
								if(S4Server.verbose)
									System.out.print("(" + length + "/" + actualSize + ")");
									
								
								parsePacket(Arrays.copyOfRange(buffer, x, x + length), length);
								if(!parent.autoFlush && (new Date()).getTime()-lastTick>tickTime*1000){
									parent.flushAll();
									lastTick=(new Date()).getTime();
								}
							} catch (Exception e) {
								e.printStackTrace();
								overflow=0;
								prev = new byte[]{};
								//System.out.print("!");
								break;
							}
						}else{
							overflow = x+length-actualSize;
							if(S4Server.verbose)
								System.out.print(":"+(actualSize-x)+"+"+overflow);
							prev = Arrays.copyOfRange(buffer,x,actualSize);
						}
						x += length;
						if (S4Server.verbose&&x < actualSize) {
							System.out.print(",");
							S4Server.window++;
						}
					}
					if(S4Server.verbose){
						System.out.print("] ");
					S4Server.window++;
					if (S4Server.window % 20 == 0)
						System.out.println();
					}
				} else {
					timeouts++;

					if (timeouts > 120) {
						this.alive = false;
						output.close();
						input.close();
						this.client.close();
					}
				}
			}
		} catch (Exception ioe_) {
			ioe_.printStackTrace();
			
		}
		this.alive = false;
		try {
			output.close();
			this.client.close();
		} catch (Exception e) {
			if(S4Server.verbose)
				e.printStackTrace();
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
	public static byte[] vsbot;
	public static byte[] deadtab;
	public static volatile ConcurrentHashMap<InetAddress, Integer> ipThreads;
	public static volatile ConcurrentHashMap<String, GameServer> games;
	public static volatile String log = "";
	public static volatile int nextPort = 8118;
	public static volatile int connections=0;
	public static volatile int gamesStarted=0;
	public static volatile long window = 0;
	public static boolean verbose=false;//printing
	public static volatile String ip="";
	public static final short[] EVENT_MAPS=new short[]{1092,1093,1094,1095,1096,1099,1100,1111,1113,1114,1115,1116,1117,1118,1119};
	public static final short[][] EVENT_MAP_SETS=new short[][]{EVENT_MAPS,new short[]{1092,1093,1094,1095,1099,1100,1113,1114,1116,1117,1118,1119},new short[]{1092,1093,1095,1099,1113,1114,1116,1117,1119},new short[]{1092,1093,1099,1116},new short[]{1092,1094,1095,1099,1100,1114,1119}};
	public static String[] EVENT_MAP_DESC=new String[]{"All","All except Ice(8),VIP(5),Highway(11)","1, 2, 4, 6, 9, 10, 12, 13, 15","Ons(1), Vac(2), PO(6), Crash Site(12)","??? VS maps or something"};
	
	//	this.map=(new short[]{1092,1095,1094,1100,1093,1099,1096,1111,1113})[(int)(Math.random()*9)];
	// onsl pods sur ls vac po vip is md
	
	public static int getPlayerCount(){
		int x=0;
		for(String s:games.keySet())
			x+=games.get(s).playerThreads.size();
		return x;
	}
	public static short getEventMap(int index){
		return EVENT_MAPS[index];
	}
	public static short getEventMap(){
		//return getEventMap((int)(Math.random()*eventMaps.length));
		Calendar event = Calendar.getInstance(TimeZone.getTimeZone("GMT-8"));
		Calendar now = Calendar.getInstance(TimeZone.getTimeZone("GMT-8"));
		Random x = new Random(33888522196117857l);
		now.setTime(new Date());
		event.set(2022,1,16,0,0,0);
		int eventOffset=0;
		while(now.get(Calendar.YEAR)!=event.get(Calendar.YEAR)||now.get(Calendar.DAY_OF_YEAR)!=event.get(Calendar.DAY_OF_YEAR)){
			event.add(Calendar.HOUR,24);
			x.nextInt();
			eventOffset++;
		}
		switch(eventOffset % 21){
			/**case 0:
			case 1:
				return -1;
			case 2:
				return 1094;
			case 3:
				return 1093;
			case 4:
				return 1113;
			case 5:
			case 6:
				return -1;
			case 7:
				return 1111;
			case 8:
				return -1;
			case 9:
				return 1096;
			case 10:
				return 1111;
			case 11:
				return 1095;
			case 12:
			case 13:
				return -1;
			case 14:
				return 1093;
			case 15:
				return 1095;
			case 16:
				return 1100;
			case 17:
				return 1092;
			case 18:
				return 1111;
			case 19:
				return 1100;
			case 20:
				return 1096;*/
			case 4:
			case 10:
			case 15:
			case 17:
			case 19:
				return EVENT_MAP_SETS[4][(int)(x.nextDouble()*EVENT_MAP_SETS[4].length)];
			case 14:
				return 3;
			case 16:
				return 2;
			case 18:
				return 1;
			case 20:
				return 0;
			default:
				return 0;
				
			
		}
		// short[]{1092,1095,1094,1100,1093,1099,1096,1111,1113})[(int)(Math.random()*9)];
			// onsl pods sur ls vac po vip is md
			//1092,1093,1094,1095,1096,1099,1100,1111,1113,1114,1115,1116,1117,1118,1119
			//
		//System.out.println(c);
		//System.out.println(c.getTime());
		//return 0;
		
	}
	private static boolean checkPort(int p) {
		for (String s : games.keySet())
			if (games.get(s).port == p)
				return true;
		return false;
	}

	public static int nextPort() {
		do {
			nextPort += 5;
			if (nextPort > 32000)
				nextPort = 8128;
		} while (checkPort(nextPort));
		return nextPort;

	}
	// makes a chat message packet
	public static byte[] encode(String s, byte src) {
		// System.out.println("chat:\n"+s);
		String s2 = "\n[BEGINFONT face='Consolas' size='16' color='#21d91f'CLOSEFONT]" + s + "[ENDFONT]";
		byte[] msg = s2.getBytes(StandardCharsets.UTF_8);
		int mod = 0;
		if (msg[msg.length - 1] == (byte) 0x00) {
			mod = 1;
		}
		if (s.length() > 255)
			return new byte[] {};
		byte[] pl = new byte[msg.length + 31];
		int len = msg.length + 26;
		long time = (new Date()).getTime()/1000;
		pl[0] = (byte) -2;
		pl[1] = ((byte) (len >> 24));
		pl[2] = ((byte) ((len >> 16) & 0xff));
		pl[3] = ((byte) ((len >> 8) & 0xff));
		pl[4] = ((byte) ((len) & 0xff));
		pl[5] = (byte)0x05;
		pl[6] = ((byte) (time >> 24));
		pl[7] = ((byte) ((time >> 16) & 0xff));
		pl[8] = ((byte) ((time >> 8) & 0xff));
		pl[9] = ((byte) ((time) & 0xff));
		pl[10] = (byte) 0x00;
		pl[11] = (byte) 0x00;
		pl[12] = (byte) 0x03;
		pl[13] = (byte) 0xe7;
		pl[14] = (byte) 0xff;
		pl[15] = (byte) 0xff;
		pl[16] = (byte) 0xff;
		pl[17] = (byte) 0xff;
		pl[18] = (byte) 0x01;
		pl[19] = (byte) 0x00;
		pl[20] = src;
		pl[21] = (byte) 0x00;
		pl[22] = (byte) (msg.length & 0xff - mod);
		for (int i = 0; i < msg.length; i++)
			pl[23 + i] = msg[i];
		return pl;
	}

	public static void main(String[] args) {
		//first 9 are normal evnt maps rest are contracts
		try {
			bot = Files.readAllBytes(Paths.get("bot.bin"));
			vsbot = Files.readAllBytes(Paths.get("vs.bin"));
			deadtab = Files.readAllBytes(Paths.get("deadtab.bin"));
		} catch (Exception e) {
			bot = new byte[] {};
			vsbot = new byte[] {};
			deadtab = new byte[] {};
		}
		games = new ConcurrentHashMap<String, GameServer>();
		// checks if a port is specified
		if (args.length == 0) {
			System.out.println("No port specified");
			System.exit(0);
		}
		int port = Integer.parseInt(args[0]);
		// checks if port is valid
		if (port < 1 || port > 65535) {
			System.out.println("Invalid port");
			System.exit(0);
		}
		ServerSocket server;
		try {
			ip=new String(Files.readAllBytes(Paths.get("./config.txt")));
			server = new ServerSocket(port);
		} catch (IOException e) {
			System.out.println("error. Please ensure the port is available and config.txt exists(put IP or \"localhost\" there)");
			return;
		}
		try {
			FilePolicyServer fps = new FilePolicyServer(843);
			new Thread(fps).start();
		} catch (Exception e) {
			
		}
		// server loop(only ends on ctrl-c)
		List<ServerThread> threads = new ArrayList<ServerThread>();
		try {
			server.setSoTimeout(1000);
		} catch (Exception eeeeeee) {
			System.out.println("???");
			return;
		}
		System.out.println("SAS4 Server started! Listening for connections...");
		while (true) {
			Socket client = null;
			try {
				client = server.accept();
				connections++;
				System.out.println("accept???");
				// client.setTcpNoDelay(true);
				ipThreads = new ConcurrentHashMap<InetAddress, Integer>();
				// for (ServerThread l:threads){System.out.println(l.isWebSocket);}
				for (int i = 0; i < threads.size(); i++) {
					if (threads.get(i) != null && (threads.get(i).alive || threads.get(i).isAlive())
							&& threads.get(i).client != null) {
						ipThreads.put(threads.get(i).client.getInetAddress(),
								(ipThreads.get(threads.get(i).client.getInetAddress()) == null) ? 1
										: ipThreads.get(threads.get(i).client.getInetAddress()) + 1);
					}
					if (threads.get(i) != null && !threads.get(i).alive && !threads.get(i).isAlive()) {
						threads.set(i, null);
					}
				} // fix all this garbage
				if (client != null && client.getInetAddress() != null && ipThreads.get(client.getInetAddress()) != null
						&& ipThreads.get(client.getInetAddress()) > 40) {
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
				// find dead threads and replace them
				for (int i = 0; i < threads.size(); i++) {
					if (threads.get(i) == null || (!threads.get(i).alive && !threads.get(i).isAlive())) {
						if (index < 0) {
							index = i;
							threads.set(i, connection);
						}
					} else
						alives++;
				}
				// all threads are dead -> reset threadpool
				// System.out.println("ALIVE: "+alives+", EXIST: ");
				if (alives == 0 && index == -1) {
					threads = new ArrayList<ServerThread>();
					threads.add(connection);
					index = 0;
					run = true;
					new Thread(threads.get(index)).start();
					continue;
				}

				else if (index > -1) {
					// at least 1 thread is dead, so just replace it
					run = true;
					threads.set(index, connection);
					new Thread(threads.get(index)).start();
					continue;
				}
				// expand threadpool, or give 505 if already 256+
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
