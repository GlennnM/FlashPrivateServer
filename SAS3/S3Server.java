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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.net.ServerSocket;

/**
 * @TODO idk
 */
 class FilePolicyServer extends Thread {
	/**
	 * Flash requests a "cross domain policy" xml whenever contacting a server. It is probably already started by the sas4 server(they both use port 843)
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
			if(S3Server.verbose)
				e.printStackTrace();
		}
	}
}
class ServerThread extends Thread {
	public Socket client = null;
	OutputStream output;
	public volatile String name;
	public volatile int rank;
	public int mode;
	public int nm;
	public volatile boolean ready;
	public volatile boolean alive;
	public volatile Room room;
	// constructor initializes socket
	private void doubleWrite(String s) throws IOException {
		this.output.write(s.getBytes(StandardCharsets.UTF_8));
		this.output.flush();
		if(S3Server.verbose)
			System.out.println("OUT: " + s);
	}

	public ServerThread(Socket socket) throws IOException {
		this.client = socket;
		this.alive = true;
		this.room=null;
		this.name=null;
		this.ready=false;
		mode=0;nm=0;
		this.rank=0;
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
			int sendAfter = 0;
			boolean hydar=false;
			int id=(int)(Math.random()*420);
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
					if(S3Server.verbose)
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
						//String[] msg = m.split(",");
						if(m.equals("<msg t='sys'><body action='verChk' r='0'><ver v='165' /></body></msg>"))
							toSend ="\0<msg t='sys'><body action='apiOK' r='0'><ver v='165'/></body></msg>\n";
						else if(m.equals("<msg t='sys'><body action='login' r='0'><login z='SAS3'><nick><![CDATA[]]></nick><pword><![CDATA[]]></pword></login></body></msg>"))
							toSend ="\0<msg t='sys'><body action='logOK' r='0'><login id='"+id+"' mod='0' n='SAS3'/></body></msg>\n";
						else if(m.equals("<msg t='sys'><body action='getRmList' r='-1'></body></msg>"))
							toSend ="\0<msg t='sys'><body action='rmList' r='-1'><rmList><rm></rm></rmList></body></msg>\n";
						else if(m.startsWith("%xt%SAS3%%")){
							List<String> msg = Arrays.asList(m.split("%"));
							int cmd = Integer.parseInt(msg.get(5));
							if(cmd==3){
								if(room==null){
									mode=Integer.parseInt(msg.get(12));
									nm=Integer.parseInt(msg.get(13));
									this.name=msg.get(7);
									this.rank=Integer.parseInt(msg.get(8));
									synchronized(S3Server.games){
										for(Room r:S3Server.games){
											if(r.mode==mode&&r.nm==nm&&r.players.size()<4){
												r.players.add(this);
												this.room=r;
												break;
											}
										}
									}
									if(this.room==null){
										this.room=new Room(this,mode,nm);
										S3Server.games.add(this.room);
									}
								}
								long time = new Date().getTime() / 1000;
								ArrayList<String> playerNames=new ArrayList<String>();
								ArrayList<Integer> playerRanks=new ArrayList<Integer>();
								ArrayList<Boolean> readyList=new ArrayList<Boolean>();
								room.players.forEach(x->{playerNames.add("\""+x.name+"\"");playerRanks.add(x.rank);readyList.add(x.ready);});
								if(!hydar){
									toSend="\0"+"%xt%15%"+time+"%-1%1%"+room.players.size()+"%0%"+playerNames+"%"+playerRanks+"%"+readyList+"%1%"+mode+"%"+(mode)+"%"+room.map;
											synchronized(room.players){
										for(ServerThread x:room.players){
											if(x!=this){
												try{
													x.doubleWrite(toSend);
												}catch(Exception e){
													e.printStackTrace();
												}
											}
										}
									}
									timeouts++;	//doubleWrite(toSend);+room.players.size()+"%0%"+playerNames+"%"+playerRanks+"%"+readyList+"%1%\""+mode+"\"%"+nm+"%%%%%"
								}else 
								hydar=true;
							}else if(cmd==26){
								long time = new Date().getTime() / 1000;
								ArrayList<String> playerNames=new ArrayList<String>();
								ArrayList<Integer> playerRanks=new ArrayList<Integer>();
								ArrayList<Boolean> readyList=new ArrayList<Boolean>();
								room.players.forEach(x->{playerNames.add("\""+x.name+"\"");playerRanks.add(x.rank);readyList.add(x.ready);});
								toSend="\0"+"%xt%25%"+time+"%-1%1%"+room.players.size()+"%0%"+playerNames+"%"+playerRanks+"%"+readyList+"%1%"+mode+"%"+(mode)+"%"+room.map+"%\""+room.mapURL+"\"%"+room.nm;
										synchronized(room.players){
									for(ServerThread x:room.players){
										if(x!=this){
											try{
												x.doubleWrite(toSend);
											}catch(Exception e){
												e.printStackTrace();
											}
										}
									}
								}
								
							}
							msg.set(2,""+cmd);
							long time = new Date().getTime() / 1000;
							msg.set(3,""+time);
							msg.set(4,""+-1);
							String pl = String.join("%",msg)+"%";
							synchronized(room.players){
								room.players.forEach(x->{
									if(x!=this){
										try{
											x.doubleWrite(pl);
										}catch(Exception e){
											e.printStackTrace();
										}
									}
								}
								);
							}
							
								//toSend="\0"+"%xt%3%"+time+"%-1%"+name+"%\""+rank+"\"%0%\""+mode+"\"%\""+room.map+"\"%\""+(mode+1)+"\"%"+nm;
							//toSend="\0"+"%xt%3%"+time+"%-1%"+"glenn m%50%0%%0%2%0%";
							//doubleWrite(toSend);
							//doubleWrite(toSend);
							//7 name
							//8 lvl
							//12 mode
							//13 NM
							//room.players.forEach(x->x.doubleWrite()
						}
						
					}
					try {
						Thread.sleep(1);
					} catch (InterruptedException ee) {
						Thread.currentThread().interrupt();
					}
				} else {
					timeouts++;
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
		this.alive = false;
		return;
	}
}

class Room {
	public volatile ArrayList<ServerThread> players;
	public int mode;
	public int nm;
	public int id;
	public int map;
	public String mapURL;
	public static final String[] MAP_URLS=new String[]{"http://sas3maps.ninjakiwi.com/sas3maps/FarmhouseMap.swf","http://sas3maps.ninjakiwi.com/sas3maps/AirbaseMap.swf","http://sas3maps.ninjakiwi.com/sas3maps/KarnivaleMap.swf","http://sas3maps.ninjakiwi.com/sas3maps/VerdammtenstadtMap.swf","http://sas3maps.ninjakiwi.com/sas3maps/BlackIsleMap.swf"};

	public Room(ServerThread p1, int mode, int nm) {
		players = new ArrayList<ServerThread>();
		players.add(p1);
		this.mode=mode;
		this.nm=nm;
		this.map=1+(int)(Math.random()*5);
		this.mapURL=MAP_URLS[map-1];
		this.id=(int)(Math.random()*999999999);
	}
	public void h(){
		
	}
}

//class for main method
public class S3Server {
	//
	public static volatile String ip="";
	public static boolean verbose=true;
	public static volatile ConcurrentHashMap<InetAddress, Integer> ipThreads;
	public static volatile ArrayList<Room> games;
	

	public static void main(String[] args) {
		S3Server.games = new ArrayList<Room>();
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
		try{
			FilePolicyServer fps = new FilePolicyServer(843);
			new Thread(fps).start();
		}catch(Exception e){
			System.out.println("policy server not started");
			//might already be running
		}
		try {
			ip=Files.readString(Paths.get("./config.txt")).trim();
			server = new ServerSocket(port);
		} catch (IOException e) {
			System.out.println("error. Please ensure the port is available and config.txt exists(put IP or \"localhost\" there)");
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
