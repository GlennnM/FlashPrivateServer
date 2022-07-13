import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Deflater;
import java.util.zip.InflaterOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.net.ServerSocket;

/**
 * @TODO idk
 */
 class FilePolicyServer extends Thread {
	/**
	 * Flash requests a "cross domain policy" xml whenever contacting a server. In
	 * SAS4 this is handled on port 843, then that connection is ended instantly
	 */
	public ServerSocket server;
	public int port;
	public volatile boolean alive = true;

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
		if(CoopServer.verbose)
			System.out.println("#OUT: file policy");
	}

	public FilePolicyServerThread(Socket client) {
		xml = "<?xml version=\"1.0\"?>\n<!DOCTYPE cross-domain-policy SYSTEM \n\"http://www.adobe.com/xml/dtds/cross-domain-policy.dtd\">\n<cross-domain-policy>\n";
		xml += "<site-control permitted-cross-domain-policies=\"master-only\"/>\n";
		xml += "<allow-access-from domain=\"*\" to-ports=\"*\"/>\n";
		xml += "</cross-domain-policy>\n";
		this.client = client;
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
			if(CoopServer.verbose)
				e.printStackTrace();
		}
	}
}
class ServerThread extends Thread {
	public Socket client = null;
	String session = null;
	OutputStream output;
	public boolean shouldQueue;
	public volatile boolean alive;
	Player player = null;
	public String code;
	/**
	1:<-game info
	2:->(join)code
	3:->(create)priv match info
	4:<-received priv
	7:->left lobby
	8:<-invalid code
	9:<-someone joined priv
	12:->modes selected for queue
	13:<-found quick match
	14:->left queue
	17:->user info
	18:<-received user info
	
	*/
	// constructor initializes socket
	private void doubleWrite(String s) throws IOException {
		this.output.write(s.getBytes(StandardCharsets.UTF_8));
		this.output.flush();
		if(CoopServer.verbose)
			System.out.println("OUT: " + s);
	}

	public ServerThread(Socket socket) throws IOException {
		this.client = socket;
		this.code="";
		this.alive = true;
		this.output = this.client.getOutputStream();
	}

	@Override
	public void run() {
		try {
			this.alive = true;
			this.client.setSoTimeout(1000);
			InputStream input = this.client.getInputStream();
			InputStreamReader ir = new InputStreamReader(input, Charset.forName("UTF-8"));
			String headers = "";
			String line = "";
			int timeouts = 0;
			String toSend = "";
			String constraints = null;
			GameServer gs = null;
			String t1 = null;
			int sendAfter = 0;
			boolean failed=false;
			boolean queued=false;
			boolean finished=false;
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
					line = "";
					headers = "";
				}
				if (line.length() > 0) {
					timeouts = 0;
					toSend = "";
					if(CoopServer.verbose)
						System.out.println("incoming: "+headers);
					if (headers.equals("<policy-file-request/>")) {
						String xml = "<?xml version=\"1.0\"?>\n<!DOCTYPE cross-domain-policy SYSTEM \n\"http://www.adobe.com/xml/dtds/cross-domain-policy.dtd\">\n<cross-domain-policy>\n";
						xml += "<site-control permitted-cross-domain-policies=\"master-only\"/>\n";
						xml += "<allow-access-from domain=\"*\" to-ports=\"*\"/>\n";
						xml += "</cross-domain-policy>\n";
						doubleWrite(xml);
						this.alive = false;
						output.close();
						input.close();
						ir.close();
						this.client.close();
						return;
					}
					String[] msgs = headers.split("\n");
					for (String m : msgs) {
						String[] msg = m.split(",");
						toSend += "\n";
						if (msg[0].equals("12")) {
							//store data and add to q
							constraints=msg[1];
							if(player!=null)
								player.setConstraints(constraints);
							Player p = CoopServer.checkQueue(player);
							if(p==null&&!queued){
								synchronized(CoopServer.queue){
									CoopServer.queue.add(player);
								}
							}else if(!queued){
								synchronized(CoopServer.queue){
									CoopServer.queue.remove(p);
								}
								String ms=player.getMap(p);
								//System.out.println(ms);
								String map=ms.substring(0,ms.indexOf(","));
								int mode = Integer.parseInt(ms.substring(ms.indexOf(",")+1));
								int port = CoopServer.nextPort();
								gs=new GameServer(port,p,map,mode,(int)(Math.random()*2));
								CoopServer.games.add(gs);
								new Thread(gs).start();
								toSend += "13,"+"localhost"+","+gs.port+",843,13042641,"+p.id+","+p.name+","+gs.map+","+gs.mode+","+gs.reverse+",1";
								p.doubleWrite("13,"+"localhost"+","+gs.port+",843,13042641,"+player.id+","+player.name+","+gs.map+","+gs.mode+","+gs.reverse+",0");
								gs.p2=player;
								sendAfter=3;
							}else if(!finished){
								finished=true;
								toSend+="hydar";
								t1="13,"+"localhost"+","+gs.port+",843,13042641,"+player.id+","+player.name+","+gs.map+","+gs.mode+","+gs.reverse+",0";
							}
							queued=true;
							//toSend += "18";
						}
						if (msg[0].equals("17")) {
							//store data
							player = new Player(Integer.parseInt(msg[2]),msg[3],Integer.parseInt(msg[4]),Integer.parseInt(msg[5]),this.output);
							if(constraints!=null)
								player.setConstraints(constraints);
							toSend += "18";
						}
						if (msg[0].equals("3")) {
							//store data and check code
							String prevCode=""+code;
							code = msg[1];
							boolean repeat=(prevCode.equals(code));
								
							GameServer g = CoopServer.privateMatches.get(code);
							
							String map = msg[2];
							int mode = Integer.parseInt(msg[3]);
							int reverse = Integer.parseInt(msg[4]);
							int port;
							
							if(g==null){
								port=CoopServer.nextPort();
								g=new GameServer(port,player,map,mode,reverse);
								g.code=code;
								CoopServer.privateMatches.put(code,g);
								new Thread(g).start();
								toSend += "4,"+"localhost"+","+port+",843";
							}else if(!repeat){
								//g.p2=player;
								toSend += "5";
								//CoopServer.privateMatches.remove(code);
							}else{
								toSend += "hydar";
							}
						}if(msg[0].equals("7")||msg[0].equals("14")){
							failed=false;
							queued=false;
							CoopServer.queue.remove(this.player);
							CoopServer.privateMatches.remove(this.code);
						}if(msg[0].equals("2")){
							if(gs==null){
								gs = CoopServer.privateMatches.get(msg[1]);
								if(gs==null){
									if(!failed){
										toSend+="8";
										failed=true;
									}else{
										failed=false;
										toSend+="hydar";
									}
								}
								else{
									toSend += "1,"+"localhost"+","+gs.port+",843,13042641,"+gs.p1.id+","+gs.p1.name+","+gs.map+","+gs.mode+","+gs.reverse;
									t1="9,"+""+0+","+",0";
									gs.p2=player;
									CoopServer.privateMatches.remove(msg[1]);
									
								}
							}else{
								t1="9,"+""+0+","+",0";//t1+="1,"+"localhost"+","+gs.port+",843,13042641,"+0+","+","+gs.map+","+gs.mode+","+gs.reverse;
								toSend += "hydar";
							}
						}
						
					}
					try {
						Thread.sleep(1);
					} catch (InterruptedException ee) {
						Thread.currentThread().interrupt();
					}
				} else {
					timeouts++;
					if ((CoopServer.queue.contains(this.player))) {
						timeouts--;
					}
					if (timeouts > 45) {
						this.alive = false;
						output.close();
						input.close();
						ir.close();
						this.client.close();
						return;
					}
					headers = "";
					line = "";
				}
				if (t1 != null && gs != null && gs.g2 == null)
					gs.p1.doubleWrite(t1);
				if (toSend.trim().length() > 0 && timeouts < 2) {
					if (sendAfter == 0)
						doubleWrite(toSend);
					else {
						sendAfter--;
						timeouts--;
					}
				}
			}
		} catch (Exception ioe_) {
			
			try {
				
				output.close();
				this.client.close();
				ioe_.printStackTrace();
				return;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		CoopServer.queue.remove(this.player);
		CoopServer.privateMatches.remove(code);
		this.alive = false;
		return;
	}
}

class GameServer extends Thread {
	public volatile GameServerThread g1;
	public volatile GameServerThread g2;
	public volatile ServerSocket server;
	public volatile Player p1;
	public volatile Player p2;
	public volatile String code;
	public volatile int port;
	public volatile boolean alive;
	public volatile String map;
	public volatile int mode;
	public volatile int reverse;
	public GameServer(int port,Player p1,String map,int mode,int reverse) {
		this.g2 = null;
		this.p1 = p1;
		this.map=map;
		this.mode=mode;
		this.reverse=reverse;
		try {
			this.port = port;
			this.server = new ServerSocket(port);
			this.server.setSoTimeout(1000);
		} catch (IOException e) {
			return;
		}
		this.alive = true;
	}
	@Override
	public void run() {
		while (this.alive) {
			Socket client = null;
			if (g1 != null && g2 != null && !g1.alive && g2.alive && g1.state > 0 && g2.state > 0) {
				try {
					p2.doubleWrite("107");
				} catch (IOException e) {
					e.printStackTrace();
					g2.alive=false;
				}
			}
			if (g1 != null && g2 != null && g1.alive && !g2.alive && g1.state > 0 && g2.state > 0) {
				try {
					p1.doubleWrite("107");
				} catch (IOException e) {
					e.printStackTrace();
					g1.alive=false;
				}
			}if(g1 != null && g2 != null && !g1.alive && !g2.alive && g1.state > 0 && g2.state > 0){
				this.alive=false;
				try{
					server.close();
				}catch(Exception e){
					e.printStackTrace();
				}
				CoopServer.games.remove(this);
				return;
			}
			try {
				client = server.accept();
				System.out.println("GST accepted");
				GameServerThread connection = new GameServerThread(client);
				if (this.g1 == null) {
					g1 = connection;
					g1.opponent = p2;
					g1.side = 0;
					// g2.opponent.output=client.getOutputStream();
					g1.output = client.getOutputStream();
					//p1.output = client.getOutputStream();
					new Thread(g1).start();
				}

				else {
					g2 = connection;
					g2.opponent = p1;
					g2.side = 1;
					p1.output=g1.output;
					p2=new Player(0,"",0,0,client.getOutputStream());
					g1.player=p1;
					g2.player=p2;
					g1.opponent=p2;
					g1.opponent.output = client.getOutputStream();
					//p2.output = client.getOutputStream();
					g2.output = client.getOutputStream();
					g2.init = true;
					g1.init = true;
					new Thread(g2).start();
				}
			} catch (Exception e) {
				//e.printStackTrace();
				continue;
			}
		}
	}
}

class GameServerThread extends Thread {
	public Player player;
	public Player opponent;
	public Socket client;
	public OutputStream output;
	public boolean alive;
	public int side;
	public int state = 0;
	public int map;
	public String welcome;
	public int time = 0;
	public boolean init = true;
	public void doubleWrite(String s) throws IOException {
		this.output.write((s + "\n").getBytes(StandardCharsets.UTF_8));
		this.output.flush();
		if(CoopServer.verbose)
			System.out.println("#OUT: " + s);
	}

	public GameServerThread(Socket client) {
		welcome="106,214,"+CoopServer.encode("\nWelcome to Flash Private Server!\nType !help for a list of commands, and report any bugs to Glenn M#9606");
		this.player = null;
		this.opponent = null;
		this.client = client;
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
			String toSend = "";
			Date lastTest = null;
			double lastTime = 0;
			
			state=1;
			while (this.alive) {
				int l4=0;
				int actualSize=0;
				int max=1024;
				char[] buffer = new char[1024];
				if (lastTest != null && ((new Date()).getTime() - lastTest.getTime()) > 5000) {
					String chat = "106,214," + CoopServer.encode("\"Lag test\" results: The game ran at "
							+ ((((double) ((int) (1.0 / (((double) ((new Date()).getTime() - lastTest.getTime()))
									/ ((double) (time * 1000 - lastTime * 1000))) * 10000))) / 100.0))
							+ "% speed over about 5 seconds");
					doubleWrite(chat);
					opponent.doubleWrite(chat);
					lastTest = null;
				}
				try {
					l4 = ir.read(buffer, 0, max);
					actualSize+=l4;
				} catch (java.net.SocketTimeoutException ste) {
					/**
					 * kill after multiple timeouts
					 */
					//ste.printStackTrace();
				}
				if (l4 == max) {
					this.client.setSoTimeout(1);
					try {

						while (l4 > 0) {
							max += l4;
							buffer = Arrays.copyOf(buffer, max);
							l4 = ir.read(buffer, max - 1024, 1024);
							actualSize += l4;
						}
					} catch (java.net.SocketTimeoutException seee) {

					}
					this.client.setSoTimeout(1000);
				}
				if (actualSize > 0) {
					line = new String(buffer).trim();
					headers = new String(line);
					timeouts = 0;
					if(CoopServer.verbose)
						System.out.println("#incoming: " + headers);
					String[] msgs = headers.split("\n");
					for (String m : msgs) {
						String[] msg = m.split(",");
						// if(msgs.length>0&&!msgs.
						if (!toSend.equals(""))
							toSend += "\n";
						
						String tail;

						if (m.indexOf(',') >= 0)
							tail = m.substring(m.indexOf(','));
						else
							tail = "";
						if (msg[0].equals("101")) {
							//this.player=
							//String tail2=
							toSend+="102"+tail.substring(tail.substring(1).indexOf(',')+1);
							//doubleWrite("102,"+)
						}else if (msg[0].equals("103")) {
							toSend+="104";
							//doubleWrite("102,"+)
						}else{
							if(msg.length>1&&msg[0].equals("106")){
								if(player!=null&&opponent!=null&&!player.welcomed&&!opponent.welcomed&&msg[1].equals("203")){
									player.welcomed=true;
									opponent.welcomed=true;
									
									doubleWrite(welcome);
									opponent.doubleWrite(welcome);
								}
								if(msg[1].equals("214")&&msg.length>2){
									String message=m.substring(m.indexOf(",")+1);
									message=CoopServer.decode(message.substring(message.indexOf(",")+1));
									//message=CoopServer.
									if(message.startsWith("!help")){
										doubleWrite("106,214,"+CoopServer.encode("\nCommands:\n!help !source !lagtest"));
									}if(message.startsWith("!source")){
										doubleWrite("106,214,"+CoopServer.encode("\nhttps://github.com/GlennnM/NKFlashServers"));
									}if(message.startsWith("!lagtest")){
										String chat="106,214,"+CoopServer.encode("\nStarting lag test...");
										doubleWrite(chat);
										opponent.doubleWrite(chat);
										lastTest = new Date();
										lastTime=time;
									}
								}if(msg[1].equals("207")&&msg.length>2){
									time = (int) (Double.parseDouble(msg[2]));
									
								}
							}
							try{
								opponent.doubleWrite(m);
							}catch(Exception e){
								continue;
							}
						}
							// ImReadyToStartARound
							//107
						
					}
				} else {
					timeouts++;
					if (timeouts > 60) {
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

				if (toSend.length() > 0 && state == 0)
					doubleWrite(toSend);
				else if (toSend.length() > 0) {
					headers = "";
					line = "";
					try{
					opponent.doubleWrite(toSend);
					toSend = "";
					}catch(Exception e){
						
					}
				}
			}
		} catch (Exception e) {
			
			e.printStackTrace();
		}this.alive = false;
	}
}

class Player {
	public String name;
	public int id;
	public int rank;
	public int clan;
	public OutputStream output;
	public boolean welcomed;
	public ArrayList<Integer> constraints;
	public Player(int id, String name, int rank, int clan, OutputStream o) {
		this.name = name;
		this.id = id;
		this.output = o;
		this.welcomed = false;
		this.constraints = new ArrayList<Integer>(Arrays.asList(new Integer[]{1,1,1,1,1,1,1}));
	}
	public void setConstraints(String constraints){
		this.constraints = new ArrayList<Integer>();
		Arrays.asList(constraints.split(":")).forEach((x->this.constraints.add(Integer.parseInt(x))));
	}
	public String getMap(Player p){
		return CoopServer.getMap(this.constraints,p.constraints);
	}
	public void doubleWrite(String s) throws IOException {
		if(CoopServer.verbose)
			System.out.println(output);
		this.output.write((s + "\n").getBytes(StandardCharsets.UTF_8));
		this.output.flush();
		if(CoopServer.verbose)
			System.out.println("player.OUT: " + s);
	}
	@Override
	public String toString() {
		return "" + id + name;
	}

	@Override
	public boolean equals(Object o) {
		return this.output.equals(((Player) o).output);
	}
}

//class for main method
public class CoopServer {
	public static final String[] BEGINNER = new String[] {"AlpineLake","ZFactor","PumpkinPatch","SantasWorkshop","ExpressShipping","SnowyBackyard","RabbitHoles"};
	public static final String[] INTERMEDIATE = new String[] {"Hearthside","SnakeRiver","HauntedSwamp","Jungle","CountryRoad","LavaFields","WaterHazard","SixFeet","TrickOrTreat"};
	public static final String[] ADVANCED = new String[]{"TheEye","Dollar","TheGreatDivide","ScorchedEarth","RinkRevenge","DuneSea","CryptKeeper","Candyland"};
	public static final String[] EXPERT = new String[]{"Castle","Spider","TreeTops","Runway","DownTheDrain","DarkForest"};
	
	//
	public static boolean verbose=false;
	public static volatile ConcurrentHashMap<InetAddress, Integer> ipThreads;
	public static volatile ArrayList<Player> queue;
	public static volatile ArrayList<GameServer> games;
	public static volatile ConcurrentHashMap<String, GameServer> privateMatches;
	public static volatile int nextPort = 8117;
	public static Player checkQueue(Player x){
		Player n=null;
		try{
			synchronized(queue){
				for(Player q:queue){
					if(x.getMap(q)!=null){
						n=q;
						break;
					}
				}
			}
		}catch(Exception e){
		}
		return n;
	}
	public static String getMap(ArrayList<Integer> c1, ArrayList<Integer> c2){
		ArrayList<Integer> diffs = new ArrayList<Integer>();
		ArrayList<Integer> modes = new ArrayList<Integer>();
		for(int i=0;i<4;i++){
			if(c1.get(i)==1&&c2.get(i)==1)
				diffs.add(i);
		}for(int i=4;i<7;i++){
			if(c1.get(i)==1&&c2.get(i)==1)
				modes.add(i-4);
		}
		if(diffs.size()==0||modes.size()==0)
			return null;
		int difficulty = diffs.get((int)(Math.random()*diffs.size()));
		int mode = modes.get((int)(Math.random()*modes.size()));
		String map=null;
		switch(difficulty){
			case 0:
				map=BEGINNER[(int)(Math.random()*BEGINNER.length)];
				break;
			case 1:
				map=INTERMEDIATE[(int)(Math.random()*INTERMEDIATE.length)];
				break;
			case 2:
				map=ADVANCED[(int)(Math.random()*ADVANCED.length)];
				break;
			case 3:
				map=EXPERT[(int)(Math.random()*EXPERT.length)];
				break;
		}
		return map+","+mode;
	}
	private static boolean checkPort(int p) {
		for (GameServer g : games)
			if (g.port == p)
				return true;
		for (String s : privateMatches.keySet())
			if (privateMatches.get(s).port == p)
				return true;
		return false;
	}

	public static int nextPort() {
		do {
			nextPort += 10;
			if (nextPort > 65000)
				nextPort = 8127;
		} while (checkPort(nextPort));
		return nextPort;

	}

	public static String encode(String data) {

		try {
			ByteArrayOutputStream out1 = new ByteArrayOutputStream(0);
			DeflaterOutputStream out = new DeflaterOutputStream(out1, new Deflater(9));
			byte[] x=data.getBytes(StandardCharsets.UTF_8);
			out.write((byte) ((x.length & 0xFF00) >> 8));
			out.write((byte) (x.length & 0x00FF));
			out.write(x);
			out.finish();
			byte[] n = out1.toByteArray();
			String q = new String(Base64.getEncoder().encodeToString(n));
			//q="eNp"+q.substring(3);
			out1.close();
			out.close();
			return q;
		} catch (IOException e) {
			return null;
		} // not possible
	}

	public static String decode(String data) {
		try {
			ByteArrayOutputStream out1 = new ByteArrayOutputStream(0);
			byte[] x = Base64.getDecoder().decode(data.getBytes(StandardCharsets.UTF_8));
			InflaterOutputStream out = new InflaterOutputStream(out1);
			out.write(x);
			out.finish();
			String q = new String(Arrays.copyOfRange(out1.toByteArray(), 2, out1.toByteArray().length),
					StandardCharsets.UTF_8);
			out1.close();
			out.close();
			return q;
		} catch (IOException e) {
			return null;
		} // not possible
	}


	public static void main(String[] args) {
		
		queue = new ArrayList<Player>();
		privateMatches = new ConcurrentHashMap<String, GameServer>();
		CoopServer.games = new ArrayList<GameServer>();
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
		FilePolicyServer fps = new FilePolicyServer(843);
		try{
			new Thread(fps).start();
		}catch(Exception e){
			//might already be running
		}
		try {
			server = new ServerSocket(port);
		} catch (IOException e) {
			return;
		}

		// server loop(only ends on ctrl-c)
		ArrayList<ServerThread> threads = new ArrayList<ServerThread>();
		try {
			server.setSoTimeout(1000);
		} catch (Exception eeeeeee) {
			System.out.println("???");
		}
		while (true) {

			Socket client = null;
			try {
				client = server.accept();
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
					//output.write("ServerMessage,Service unavailable. Please report".getBytes());
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
