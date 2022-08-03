import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.net.ServerSocket;
import java.security.SecureRandom;

/**
 * @TODO string builder?? no /done map, loadout, custom time?? no
 */
class ServerThread extends Thread {
	public Socket client = null;
	String session = null;
	OutputStream output;
	public boolean shouldQueue;
	public volatile boolean alive;
	Player player = null;

	// constructor initializes socket
	private void doubleWrite(String s) throws IOException {
		this.output.write(s.getBytes(StandardCharsets.UTF_8));
		this.output.flush();
		if(FlashServer.verbose)
			System.out.println("OUT: " + s);
	}

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
			InputStreamReader ir = new InputStreamReader(input, Charset.forName("UTF-8"));
			String headers = "";
			String line = "";
			String code = "";
			String joinCode = null;
			int timeouts = 0;
			String toSend = "";
			GameServer gs = null;
			String t1 = null;
			String t2 = null;
			boolean matched = false;
			boolean queued = false;
			boolean assault = false;
			Player opp=null;
			int sendAfter = 0;
			while (this.alive) {
				// headers = "";
				// line="";
				/*
				 * if(lastUpdate!=null&&(newTime-lastUpdate.getTime()>2000)){ lastUpdate = new
				 * Date(); doubleWrite("ServerMessage,"); }
				 */
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
					// System.out.println("timeout: "+line);
					// return;

					line = "";
					headers = "";
				}
				if (line.length() > 0) {
					timeouts = 0;
					toSend = "";
					if(FlashServer.verbose)
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
						// if(msgs.length>0&&!msgs.
						// if(!toSend.equals(""))
						toSend += "\n";
						switch (msg[0]) {
						case "Hello":
							toSend += "ServerMessage";
							break;
						case "FindMeAQuickBattle":
							toSend += "GimmeUrPlayerInfo";
							shouldQueue = true;
							assault = (Integer.parseInt(msg[3]) == 0);
							// doubleWrite(toSend);toSend="";
							break;
						case "HeresMyPlayerInfo":
							this.player = new Player(Integer.parseInt(msg[1]), msg[2], Integer.parseInt(msg[3]), 0, 0,
									msg[5], this.output, Integer.parseInt(msg[4]));
							// set player info, add to queue
							if (shouldQueue&&!matched)
								toSend += "FindingYouAMatch";
							if (!matched) {
								//
								if (shouldQueue && !queued) {
									// int p=4481+87*FlashServer.privateMatches.size();
									// FlashServer.games.add(new GameServer(p));
									if ((assault && FlashServer.queue1.size() == 0)
											|| (!assault && FlashServer.queue2.size() == 0) && !queued) {
										if (assault)
											FlashServer.queue1.add(player);
										else
											FlashServer.queue2.add(player);
										queued = true;
									} else if (!queued) {
										gs = new GameServer(FlashServer.nextPort());
										//Player opp;
										if (assault)
											opp = FlashServer.queue1.poll();
										else
											opp = FlashServer.queue2.poll();
										opp.doubleWrite("\nFoundYouAGame,"+FlashServer.ip+"," + gs.port + "," + 0 + ",4480");
										t1="\nFoundYouAGame,"+FlashServer.ip+"," + gs.port + "," + 0 + ",4480";
										toSend += "\nFoundYouAGame,"+FlashServer.ip+"," + gs.port + "," + 0 + ",4480\n";
										//sendAfter = 3;
										//gs.p1.doubleWrite(t1);
										FlashServer.games.add(gs);
										new Thread(gs).start();
										matched=true;
										queued = true;
									}
								} else if (!queued) {

									// remove from queue
									char[] id = new char[8];
									if (code.length() == 0) {
										shouldQueue = false;
										SecureRandom rng = new SecureRandom();
										for (int i = 0; i < id.length; i++)
											id[i] = (char) ('G' + rng.nextInt(20));
										code = new String(id);
										while (FlashServer.privateMatches.containsKey("B2GARB" + code)) {
											for (int i = 0; i < id.length; i++)
												id[i] = (char) ('G' + rng.nextInt(20));
											code = new String(id);
										}
										code = "B2GARB" + code;
										gs = new GameServer(FlashServer.nextPort());
										gs.code=code;
										FlashServer.privateMatches.put(code, gs);
										FlashServer.privateHosts.put(code, player);
										gs.p1=player;
										
										toSend += "FindingYouAMatch," + code;
									} else if (joinCode != null) {
										if (FlashServer.privateMatches.containsKey(joinCode)) {
											new Thread(FlashServer.privateMatches.get(joinCode)).start();
											toSend+="FindingYouAMatch," + joinCode+ "\nFoundYouAGame,"+FlashServer.ip+","
													+ FlashServer.privateMatches.get(joinCode).port + "," + joinCode
													+ ",4480\n";
											gs = FlashServer.privateMatches.get(joinCode);
											sendAfter = 5;
											//if(gs.p1==null){
											opp=FlashServer.privateHosts.remove(joinCode);
											opp.doubleWrite("FoundYouAGame,"+FlashServer.ip+","
													+ FlashServer.privateMatches.get(joinCode).port + "," + joinCode
													+ ",4480");
											t1="\nFoundYouAGame,"+FlashServer.ip+","
													+ FlashServer.privateMatches.get(joinCode).port + "," + joinCode
													+ ",4480\n";
											gs.p2=player;
											matched=true;
											//}
											//gs.p1.doubleWrite(t1);
											//FlashServer.privateMatches.put(joinCode, gs);
											// doubleWrite(toSend);
										} else {
											toSend += "CouldntFindYourCustomBattle";
										}
									} else {
										toSend += "ServerMessage";
									}

								}
							}
							break;
						case "ILeftGame":
							// remove from queue
							shouldQueue = false;
							queued = false;
							matched = false;
							doubleWrite("ServerMessage");
							FlashServer.queue1.remove(this.player);
							FlashServer.queue2.remove(this.player);
							this.alive = false;
							output.close();
							input.close();
							ir.close();
							this.client.close();
							return;
						case "CreateMeACustomBattle":
							shouldQueue = false;
							toSend += "GimmeUrPlayerInfo,";
							break;
						case "FindMyCustomBattle":
							// System.out.println(FlashServer.privateMatches.keySet());
							shouldQueue = false;
							// remove from queue
							if (FlashServer.privateMatches.containsKey(msg[2])) {
								joinCode = msg[2];
								toSend += "GimmeUrPlayerInfo,";
							} else {
								toSend += "CouldntFindYourCustomBattle";
							}
							break;
						case "FindMyGame":
							break;
						}
					}
					try {
						Thread.sleep(1);
					} catch (InterruptedException ee) {
						Thread.currentThread().interrupt();
					}
				} else {
					// if(toSend.startsWith("FindingYouAMatch"))
					// doubleWrite("ServerMessage");
					// if(timeouts==0&&)
					// timeouts-=2;
					timeouts++;
					if (queued && shouldQueue
							&& (FlashServer.queue1.contains(this.player) || FlashServer.queue2.contains(this.player))) {
						timeouts--;
					}
					if (timeouts > 10) {
						doubleWrite("ServerMessage");
						FlashServer.queue1.remove(this.player);
						FlashServer.queue2.remove(this.player);
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
				try{
					if (t1 != null && gs != null && opp != null)
						opp.doubleWrite(t1);
					if (t2 != null && gs != null && gs.p2 != null)
						gs.p2.doubleWrite(t2);
				}catch(IOException e){
					if(FlashServer.verbose)
						e.printStackTrace();
				}
				if (toSend.trim().length() > 0 && timeouts < 2) {
					if (sendAfter == 0)
						doubleWrite(toSend);
					else if(gs!=null&&gs.g1!=null&&gs.g1.player!=null&&gs.g1.player.init){
						doubleWrite(toSend);
						sendAfter=0;
					}else {
						sendAfter--;
						timeouts--;
					}
				}
			}
		} catch (Exception ioe_) {
			ioe_.printStackTrace();
			FlashServer.queue1.remove(this.player);
			FlashServer.queue2.remove(this.player);
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

class GameServer extends Thread {
	public volatile GameServerThread g1;
	public volatile GameServerThread g2;
	public ServerSocket server;
	public volatile Player p1;
	public volatile Player p2;
	public volatile String code;
	public int port;
	public boolean alive;
	public int round;
	public volatile int map;
	public int seed;
	public GameServer(int port) {
		this.round = 1;
		this.g2 = null;
		this.p1 = null;
		this.code=null;
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
		this.map = (int) (Math.random() * 22);
		this.seed = (int) (Math.random() * 20000);
		while (this.alive) {
			Socket client = null;
			if (g1 != null && g2 != null && g1.r && g2.r) {
				try {
					if (round == 1) {
						String welcome = FlashServer.encode(
								"Welcome to Flash Private Server! Please report any bugs to Glenn M#9606 on discord, and use !help for a list of commands.");
						g1.doubleWrite("RelayMsg,SentChatMsg," + welcome);
						g2.doubleWrite("RelayMsg,SentChatMsg," + welcome);
					}
					if (round == 39) {
						g1.doubleWrite("ServerMessage,SYNCING...");
						g2.doubleWrite("ServerMessage,SYNCING...");
					}
					g1.doubleWrite("ServerStartingARound," + round);
					g2.doubleWrite("ServerStartingARound," + round);
					round += 1;
					g1.r = false;
					g2.r = false;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (g1 != null && g2 != null && !g1.alive && g2.alive && g1.state > 0 && g2.state > 0) {
				try {
					g2.doubleWrite("OpponentDisconnected");
					g2.doubleWrite("ServerMessage");
					if (g2.win == 0 && g1.win == 0) {
						g2.win = 2;
						g1.win = 1;
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (g1 != null && g2 != null && g1.alive && !g2.alive && g1.state > 0 && g2.state > 0) {
				try {
					g1.doubleWrite("OpponentDisconnected");
					g1.doubleWrite("ServerMessage");
					if (g2.win == 0 && g1.win == 0) {
						g1.win = 2;
						g2.win = 1;
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (g1 != null && g2 != null && !g1.alive && !g2.alive && g1.state > 0 && g2.state > 0) {
				try {
					server.close();
					this.alive = false;
					return;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (g1 != null && g2 != null && this.round < 2 && g1.relays > 14 && g2.relays == 0) {
				try {
					g1.relays = 0;
					g2.doubleWrite("OpponentHasLoaded");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (g1 != null && g2 != null && this.round < 2 && g1.relays == 0 && g2.relays > 14) {
				try {
					g2.relays = 0;
					g1.doubleWrite("OpponentHasLoaded");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			try {
				client = server.accept();
				System.out.println("GS accept");
				GameServerThread connection = new GameServerThread(client, this.map, this.seed,this);
				if (this.g1 == null) {
					g1 = connection;
					g1.opponent = p2;
					g1.side = 0;
					// g2.opponent.output=client.getOutputStream();
					g1.output = client.getOutputStream();
					p1=g1.player;
					new Thread(g1).start();
				}

				else if(this.g2==null){
					g2 = connection;
					g2.opponent = p1;
					g2.side = 1;
					g2.output = client.getOutputStream();
					g2.init = true;
					g1.init = true;
					p2=g2.player;
					g1.opponent=p2;
					new Thread(g2).start();
				}
			} catch (Exception e) {
				//e.printStackTrace();
				continue;
			}
		}
	}

	@Override
	public String toString() {
		if (g1.win == 1) {
			return g2.player.name + "," + g2.player.id + "," + "WIN\n" + g1.player.name + "," + g1.player.id + ","
					+ "LOSE\nRound: " + (round - 1) + ", Approx time: " + g1.time / 60 + "m" + g1.time % 60
					+ "s, Cause: " + g1.reason;
		} else if (g2.win == 1) {
			return g1.player.name + "," + g1.player.id + "," + "WIN\n" + g2.player.name + "," + g2.player.id + ","
					+ "LOSE\nRound: " + (round - 1) + ", Approx time: " + g2.time / 60 + "m" + g2.time % 60
					+ "s, Cause: " + g2.reason;
		}
		return null;
	}
}

class GameServerThread extends Thread {
	public volatile Player player;
	public volatile Player opponent;
	public Socket client;
	public volatile OutputStream output;
	public volatile boolean alive;
	public int side;
	public boolean r;
	public int relays;
	public int state = 0;
	public int timeouts = 0;
	public int seed = 0;
	public volatile int map;
	public int win;
	public String reason = "Player disconnected";
	public int time = 0;
	public volatile boolean init = false;
	public volatile GameServer parent;
	public void doubleWrite(String s) throws IOException {
		this.output.write((s + "\n").getBytes(StandardCharsets.UTF_8));
		this.output.flush();
		if(this.parent!=null&&this.parent.round<1)
			this.timeouts=0;
		if(FlashServer.verbose)
			System.out.println("#OUT: " + s);
	}

	public GameServerThread(Socket client, int map, int seed, GameServer parent) {
		this.parent=parent;
		this.r = false;
		this.relays = 0;
		this.client = client;
		this.map = map;
		this.win = 0;
		this.seed = seed;
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
			String headers = "";
			String line = "";
			String toSend = "";
			Date lastTest = null;
			double lastTime = 0;
			while (this.alive) {
				// System.out.println("SIDE"+this.side);
				// System.out.println("GST alive");
				// ask for player info
				// System.out.println(opponent);
				// if(!this.init)
				// continue;
				if (state<2&&opponent!=null&&((this==parent.g1)?(parent.g2):(parent.g1))!=null) {
					if (FlashServer.getProfile(opponent.id) != null)
						opponent.profile = FlashServer.getProfile(opponent.id);
					doubleWrite("FoundYourGame," + opponent.id + "," + opponent.name + "," + opponent.bs + ","
							+ opponent.decal + "," + opponent.profile + "," + this.seed + "," + this.side + ","
							+ parent.map + "," + opponent.w + "," + opponent.l);
					if(parent.code!=null){
						doubleWrite("OpponentChangedBattleOptions,InkBlot,0");
						opponent.doubleWrite("OpponentChangedBattleOptions,InkBlot,0");
					}
					state+=1;
				}
				if (this.player == null||!this.player.init) {
					doubleWrite("GimmeUrPlayerInfo,h");
				}
				if (lastTest != null && ((new Date()).getTime() - lastTest.getTime()) > 5000) {
					String lt="RelayMsg,SentChatMsg," + FlashServer.encode("Lag test results: The game ran at "
							+ ((((double) ((int) (1.0 / (((double) ((new Date()).getTime() - lastTest.getTime()))
									/ ((double) (time * 1000 - lastTime * 1000))) * 10000))) / 100.0))
							+ "% speed over about 5 seconds");
					doubleWrite(lt);
					opponent.doubleWrite(lt);
					lastTest = null;
				}
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
					 headers="";
					 line="";
				}
				int count;
				ArrayList<String> x;
				if (line.length() > 0) {
					timeouts = 0;
					
					toSend = "";
					if(FlashServer.verbose)
						System.out.println("#incoming: " + headers);
					String[] msgs = headers.split("\n");
					for (String m : msgs) {
						String[] msg = m.split(",");
						// if(msgs.length>0&&!msgs.
						if (!toSend.equals(""))
							toSend += "\n";
						if (msg[0].equals("HeresMyPlayerInfo")) {
							this.player = new Player(Integer.parseInt(msg[1]), msg[2], Integer.parseInt(msg[3]), 0, 0,
									msg[5], this.output, Integer.parseInt(msg[4]));
							this.player.init=true;
							
								if(this==parent.g1){
									if(parent.g2!=null)
										parent.g2.opponent=this.player;
									parent.p1=this.player;
								}else{
									if(parent.g1!=null)
										parent.g1.opponent=this.player;
									parent.p2=this.player;
								}
							//state=1;
						} else {
							String tail;

							if (m.indexOf(',') >= 0)
								tail = m.substring(m.indexOf(','));
							else
								tail = "";
							switch (msg[0]) {
							case "IChangedMyTowerLoadout":
								toSend += "OpponentChangedTowerLoadout" + tail;
								break;
							case "IRequestYourTowerLoadout":
								toSend += "OpponentRequestsMyTowerLoadout" + tail;
								break;
							case "IChangedBattleOptions":
								toSend += "OpponentChangedBattleOptions" + tail;
								parent.map = FlashServer.MAPIDS.indexOf(msg[1]);
								break;
							case "MyReadyToPlayStatus":
								toSend += "OpponentReadyStatus" + tail;
								break;
							case "MyGameIsLoaded":
								System.out.println("player ready");
								toSend += "OpponentHasLoaded" + tail;
								break;
							case "GimmeOpponentSyncData":
								toSend += "OpponentRequestsSync" + tail;
								break;
							case "IBuiltATower":
								toSend += "OpponentBuiltATower" + tail;
								break;
							case "ISoldATower":
								toSend += "OpponentSoldATower" + tail;
								break;
							case "IUpgradedATower":
								toSend += "OpponentUpgradedATower" + tail;
								break;
							case "ISentABloonWave":
								if (tail.contains("Cerem")) {
									doubleWrite("RelayMsg,SentChatMsg,"
											+ FlashServer.encode("they WILL die,\n(only you can see this)"));
								}
								toSend += "OpponentSentABloonWave" + tail;
								break;
							case "IRemovedABloonWave":
								toSend += "OpponentRemovedABloonWave" + tail;
								break;
							case "ILived":
								this.win = 2;
								toSend += "OpponentLived" + tail;
								break;
							case "IDied":
								System.out.println("game ended probably");
								this.win = 1;
								this.reason = "Player died";
								toSend += "OpponentDied" + tail;
								break;
							case "ISurrender":
								System.out.println("game ended probably");
								this.win = 1;
								this.reason = "Player surrendered";
								toSend += "OpponentSurrendered" + tail;
								break;
							case "IChangedATowerTarget":
								toSend += "OpponentTowerTargetChanged" + tail;
								break;
							case "IChangedAcePath":
								toSend += "OpponentChangedAcePath" + tail;
								break;
							case "IChangedTargetReticle":
								toSend += "OpponentChangedTargetReticle" + tail;
								break;
							case "IUsedAnAbility":
								toSend += "OpponentUsedAnAbility" + tail;
								break;
							case "YouDidntRespondToMySyncs":
								// toSend+="OpponentDidntGetMySyncResponses"+tail;
								toSend += "OpponentRequestsSync" + tail;
								break;
							case "RelayMsg":
								this.relays++;
								if (msg.length > 1 && msg[1].equals("SentChatMsg")) {
									try {
										String[] cmd = FlashServer.decode(msg[2]).trim().split(" ");
										String param = null;
										if (cmd.length > 1)
											param = FlashServer.decode(msg[2]).trim()
													.substring(FlashServer.decode(msg[2]).trim().indexOf(' ') + 1);
										switch (cmd[0]) {
										case "!help":
											doubleWrite("RelayMsg,SentChatMsg," + FlashServer.encode(
													"\nCommands:\n!help - displays this\n!history - displays your most recent 10 matches\n!source - view the source code on GitHub\n!profile <url> - set a profile picture for your opponents to see(140x140)\n!random <count> - generates random tower(s)\n!map <count> - generates random map(s)\n!lagtest - tests game timer vs real time\n!bugs - displays known bugs"));
											break;
										case "!history":
											doubleWrite("RelayMsg,SentChatMsg,"
													+ FlashServer.encode(FlashServer.getHistory(player.id)));
											break;
										case "!source":
											doubleWrite("RelayMsg,SentChatMsg,"
													+ FlashServer.encode("https://github.com/GlennnM/NKFlashServers"));
											break;
										case "!profile":
											if (cmd.length == 1)
												doubleWrite("RelayMsg,SentChatMsg,"
														+ FlashServer.encode("Current profile picture: "
																+ FlashServer.getProfile(player.id)));
											else {
												doubleWrite("RelayMsg,SentChatMsg,"
														+ FlashServer.encode("Set profile picture to: " + param));
												FlashServer.setProfile(player.id, param);
											}
											break;
										case "!random":
											count = 0;
											if (param == null)
												count = 4;
											else
												count = Math.min(Integer.parseInt(param), 10);
											x = new ArrayList<String>();
											for (int n = 0; n < count; n++) {
												int q = (int) (Math.random() * FlashServer.TOWERS.length);
												while (x.contains(FlashServer.TOWERS[q]))
													q = (int) (Math.random() * FlashServer.TOWERS.length);
												x.add(FlashServer.TOWERS[q]);
											}
											doubleWrite("RelayMsg,SentChatMsg," + FlashServer
													.encode(x.toString().substring(1, x.toString().length() - 1)));
											break;
										case "!map":
											count = 0;
											if (param == null)
												count = 1;
											else
												count = Math.min(Integer.parseInt(param), 10);
											x = new ArrayList<String>();
											for (int n = 0; n < count; n++) {
												int q = (int) (Math.random() * FlashServer.MAPS.length);
												while (x.contains(FlashServer.MAPS[q]))
													q = (int) (Math.random() * FlashServer.MAPS.length);
												x.add(FlashServer.MAPS[q]);
											}
											doubleWrite("RelayMsg,SentChatMsg," + FlashServer
													.encode(x.toString().substring(1, x.toString().length() - 1)));
											break;
										case "!bugs":
											doubleWrite("RelayMsg,SentChatMsg," + FlashServer.encode(
													"Known issues:\n-game disconnects sometimes\n-wins and losses aren't shown\n-wins, losses, battlescore don't update"));
											break;
										case "!lagtest":
											doubleWrite("RelayMsg,SentChatMsg,"
													+ FlashServer.encode("Starting lag test..."));
											opponent.doubleWrite("RelayMsg,SentChatMsg,"
													+ FlashServer.encode("Starting lag test..."));
											lastTest = new Date();
											lastTime = this.time;
											break;
										default:
											toSend += m;
											break;
										}
									} catch (Exception e) {
										doubleWrite("RelayMsg,SentChatMsg," + FlashServer.encode(
												"An error occurred while processing a chat message or command."));
										e.printStackTrace();
									}
								} else
									toSend += m;
								break;
							case "HeresMySyncData":
								if (msg.length > 1) {
									toSend += "OpponentSyncRetrieved" + tail;
									time = (int) (Double.parseDouble(msg[1]));
								}
								break;
							case "ImReadyToStartARound":
								this.r = true;
								break;
							case "ILeftGame":
								this.win = 1;
								toSend += "OpponentDisconnected";
								break;
							case "GiveMeDaBalance":
								doubleWrite("HeresDaBalance," + FlashServer.BALANCE);
								break;
							}
						}
					}
				} else {
					timeouts++;
					if (timeouts > 60) {
						System.out.println("GST timeout");
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
					try{
						doubleWrite(toSend);
						}catch(IOException e){
						e.printStackTrace();
					}
				else if (toSend.length() > 0 && this.init) {
					try{
						opponent.doubleWrite(toSend);
						headers = "";
						line = "";
						toSend = "";
					}catch(IOException e){
						e.printStackTrace();
						
					}
					// opponent.doubleWrite("ServerMessage");
				}
			}
		} catch (Exception e) {
			this.alive = false;
			e.printStackTrace();
		}
	}
}

class Player {
	public String name;
	public int id;
	public int bs;
	public int w;
	public int l;
	public volatile OutputStream output;
	public int decal;
	public String profile;
	public volatile boolean r;
	public volatile boolean init=false;
	public Player(int id, String name, int bs, int w, int l, String profile, OutputStream o, int decal) {
		this.name = name;
		this.id = id;
		this.bs = bs;
		this.w = w;
		this.l = l;
		this.output = o;
		this.profile = profile;
		this.r = false;
		this.decal = decal;
	}

	public void doubleWrite(String s) throws IOException {
		this.output.write((s + "\n").getBytes(StandardCharsets.UTF_8));
		this.output.flush();
		if(FlashServer.verbose)
		System.out.println("player.OUT: " + s);
	}

	@Override
	public String toString() {
		return "" + id + name + bs + "" + w + "" + l + "" + profile;
	}

	@Override
	public boolean equals(Object o) {
		return this.output.equals(((Player) o).output);
	}
}

//class for main method
public class FlashServer {
	public static final String[] TOWERS = new String[] { "Banana Farm", "Bomb Tower", "Boomerang Thrower", "Dart Monkey",
			"Dartling Gun", "Glue Gunner", "Ice Tower", "Monkey Ace", "Monkey Apprentice", "Monkey Buccaneer",
			"Monkey Village", "Mortar Tower", "Ninja Monkey", "Sniper Monkey", "Spike Factory", "Super Monkey",
			"Tack Shooter" };
	public static final String[] MAPS = new String[] { "Park", "Temple", "Yin Yang", "Cards", "Rally", "Mine", "Hydro Dam",
			"Pyramid Steps", "Patch", "Battle Park", "Ice Flow", "Yellow Brick Road", "Swamp", "Battle River",
			"Mondrian", "Zen Garden", "Volcano", "Water Hazard", "Indoor Pools", "A-Game", "Ink Blot", "Snowy Castle" };
	public static final List<String> MAPIDS = Arrays.asList(new String[] { "Park", "Temple", "YinYang", "Cards", "Rally", "Mine", "HydroDam",
			"PyramidSteps", "Patch", "BattlePark", "IceFlowBattle", "YellowBrick", "Swamp", "BattleRiver",
			"Mondrian", "ZenGarden", "Volcano", "WaterHazard", "IndoorPools", "Agame", "InkBlot", "SnowyCastle" });
	
	public static final String BALANCE = "baseVals;STARTING_ENERGY=50;ENERGY_COST_PER_GAME=5;ENERGY_COST_SPY=3;ENERGY_COST_EXTRA_TOWER=6;ENERGY_REGEN_TIME=720;MEDS_FOR_WIN=5;MEDS_FOR_LOSS=2;BATTLESCORE_FOR_WIN=10;BATTLESCORE_FOR_LOSS=2;SURRENDER_ROUND_REWARD=7;COST_MULTIPLIER_REGEN=1.8;COST_MULTIPLIER_CAMO=2.5;UNLOCK_ROUND_REGEN=8;UNLOCK_ROUND_CAMO=12;STARTING_CASH=650;STARTING_HEALTH=150;CASH_PER_TICK_MINIMUM=0;CASH_PER_TICK_STARTING=250;CASH_PER_TICK_STARTING_DEFEND=25;CASH_PER_TICK_MAXIMUM_DEFEND=3000;GIVE_CASH_TIME=6;FIRST_ROUND_START_TIME=6;ROUND_EXPIRY_TIME_BASE=8;ROUND_EXPIRY_TIME_MUL=1.5;PREMATCH_SCREEN_TIME=30;MAX_BLOON_QUE_SIZE=10;baseValsEnd;bloonSetsid=GroupRed1;type=0;name=Grouped Reds;hotkey=1;quantity=8;interval=0.1;cost=25;incomeChange=1;unlockRound=2;,id=SpacedBlue1;type=1;name=Spaced Blues;hotkey=Shift+1;quantity=6;interval=0.33;cost=25;incomeChange=1;unlockRound=2;,id=GroupBlue1;type=1;name=Blue Bloon;hotkey=2;quantity=6;interval=0.1;cost=42;incomeChange=1.7;unlockRound=4;,id=SpacedPink1;type=4;name=Spaced Pink;hotkey=Shift+2;quantity=3;interval=0.5;cost=42;incomeChange=1.7;unlockRound=4;,id=GroupGreen1;type=2;name=Grouped Greens;hotkey=3;quantity=5;interval=0.08;cost=60;incomeChange=2.4;unlockRound=6,id=SpacedBlack;type=5;name=Spaced Black;hotkey=Shift+3;quantity=3;interval=0.6;cost=60;incomeChange=2.4;unlockRound=6,id=GroupYellow1;type=3;name=Grouped Yellows;hotkey=4;quantity=5;interval=0.06;cost=75;incomeChange=3;unlockRound=8,id=SpaceWhite1;type=6;name=Spaced White;hotkey=Shift+4;quantity=4;interval=0.5;cost=75;incomeChange=3;unlockRound=8,id=GroupPink1;type=4;name=Grouped Pink;hotkey=5;quantity=3;interval=0.05;cost=90;incomeChange=3.6;unlockRound=10,id=SpaceLead1;type=7;name=Spaced Leads;hotkey=Shift+5;quantity=2;interval=1.5;cost=90;incomeChange=3.6;unlockRound=10,id=GroupWhite1;type=6;name=Group White;hotkey=6;quantity=3;interval=0.15;cost=125;incomeChange=5;unlockRound=11,id=SpaceZebra1;type=8;name=Space Zebra;hotkey=Shift+6;quantity=3;interval=0.6;cost=125;incomeChange=5;unlockRound=11,id=GroupBlack1;type=5;name=Black Bloon;hotkey=7;quantity=3;interval=0.15;cost=150;incomeChange=6;unlockRound=12,id=SpaceRainbow1;type=9;name=Space Rainbow;hotkey=Shift+7;quantity=1;interval=1;cost=150;incomeChange=6;unlockRound=12,id=GroupZebra1;type=8;name=Zebra Bloon;hotkey=8;quantity=3;interval=0.18;cost=200;incomeChange=6;unlockRound=13,id=GroupRainbow1;type=9;name=Rainbow Bloon;hotkey=Shift+8;quantity=3;interval=0.18;cost=450;incomeChange=3;unlockRound=13,id=GroupLead1;type=7;name=Grouped Leads;hotkey=9;quantity=4;interval=0.2;cost=200;incomeChange=6;unlockRound=15,id=SpaceCerem1;type=10;name=Space Ceremic;hotkey=Shift+9;quantity=1;interval=1;cost=300;incomeChange=0;unlockRound=15,id=CeremicGroup1;type=10;name=Fast Ceremic;hotkey=0;quantity=1;interval=0.25;cost=450;incomeChange=-5;unlockRound=18,id=SpaceMOABGroup1;type=11;name=Space MOAB;hotkey=Shift+0;quantity=1;interval=5;cost=1500;incomeChange=-60;unlockRound=18,id=Moab2;type=11;name=Fast MOAB;hotkey=O;quantity=1;interval=0.5;cost=1500;incomeChange=-140;unlockRound=20,id=BFB1;type=12;name=BFB;hotkey=Shift+O;quantity=1;interval=4;cost=2500;incomeChange=-350;unlockRound=20,id=FastBFBGroup1;type=12;name=Fast BFB;hotkey=P;quantity=1;interval=0.6;cost=2500;incomeChange=-350;unlockRound=22,id=ZOMG1;type=13;name=ZOMG;hotkey=Shift+P;quantity=1;interval=6;cost=9000;incomeChange=-1500;unlockRound=22;bloonSetsEnd";
	//
	public static volatile ConcurrentHashMap<InetAddress, Integer> ipThreads;
	public static volatile ConcurrentLinkedQueue<Player> queue1;// assault
	public static volatile ConcurrentLinkedQueue<Player> queue2;// defend
	public static volatile ArrayList<GameServer> games;
	public static volatile ConcurrentHashMap<String, GameServer> privateMatches;
	public static volatile ConcurrentHashMap<String, Player> privateHosts;
	public static volatile ConcurrentHashMap<Integer, String> profiles;// might become settings object
	public static volatile ConcurrentHashMap<Integer, ArrayList<String>> history;
	public static volatile String log = "";
	public static volatile String ip = "";
	public static volatile int nextPort = 8129;
	public static boolean verbose=false;
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
			nextPort += 5;
			if (nextPort > 32000)
				nextPort = 8129;
		} while (checkPort(nextPort));
		return nextPort;

	}

	public static String encode(String data) {

		try {
			ByteArrayOutputStream out1 = new ByteArrayOutputStream(0);
			DeflaterOutputStream out = new DeflaterOutputStream(out1);
			out.write((byte) ((data.length() & 0xFF00) >> 8));
			out.write((byte) (data.length() & 0x00FF));
			out.write(data.getBytes(StandardCharsets.UTF_8));
			out.finish();
			byte[] x = out1.toByteArray();
			String q = new String(Base64.getEncoder().encodeToString(x));
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

	public static void updateServer() throws IOException {
		String filename = "FlashLog.txt";
		FileWriter fw = new FileWriter(filename, true);
		for (String x : privateMatches.keySet())
			if (!privateMatches.get(x).alive) {
				if(privateMatches.get(x).round>1){
					int id1 = privateMatches.get(x).g1.player.id;
					int id2 = privateMatches.get(x).g2.player.id;
					ArrayList<String> entries1;
					if (history.get(id1) == null)
						entries1 = new ArrayList<String>();
					else
						entries1 = new ArrayList<String>(history.get(id1));
					if (entries1.size() > 9)
						entries1.remove(entries1.get(0));
					entries1.add(privateMatches.get(x).toString() + "\nType: Private\n");
					history.put(id1, entries1);
					if (id2 != id1)
						history.put(id2, entries1);
					fw.write(privateMatches.get(x).toString() + "\nType: Private\n");
					fw.flush();
				}
				privateMatches.remove(x);
				privateHosts.remove(x);
				System.out.println("game finished");
			}
		for (GameServer q : games)
			if (!q.alive) {
				if(q.round>1){
					int id1 = q.g1.player.id;
					int id2 = q.g2.player.id;
					ArrayList<String> entries1;
					if (history.get(id1) == null)
						entries1 = new ArrayList<String>();
					else
						entries1 = new ArrayList<String>(history.get(id1));
					if (entries1.size() > 9)
						entries1.remove(entries1.get(0));
					entries1.add(q.toString() + "\nType: Quick\n");
					history.put(id1, entries1);
					if (id2 != id1)
						history.put(id2, entries1);
					fw.write(q.toString() + "\nType: Quick\n");
					fw.flush();
				}
				games.remove(q);
				System.out.println("game finished");
			}
		fw.close();
		// log=Files.readString(Paths.get("FlashLog.txt"), StandardCharsets.UTF_8);

	}

	public static String getHistory(int uid) {
		// String[] lines = log.split("\n");
		String h = "\n====================\n";

		if (history.get(uid) == null)
			return h;
		ArrayList<String> entries = history.get(uid);
		ArrayList<String> compact = new ArrayList<String>();
		for (String e : entries) {
			String[] lines = e.split("\n");
			int i = 0;
			if (lines[i].contains("" + uid)) {

				compact.add(lines[i + 3] + "|win|" + lines[i + 2] + "|" + "Opponent: "
						+ lines[i + 1].substring(0, lines[i + 1].indexOf(',')) + '\n');
			} else if (lines[i + 1].contains("" + uid)) {
				compact.add(lines[i + 3] + "|lose|" + lines[i + 2] + "|" + "Opponent: "
						+ lines[i].substring(0, lines[i].indexOf(',')) + '\n');
			}
		}
		for (int i = Math.max(0, compact.size() - 10); i < compact.size(); i++) {
			//System.out.println(i);
			h += compact.get(i);
		}
		return h;
	}

	public static String getProfile(int uid) {
		return profiles.get(uid);
	}

	public static void setProfile(int uid, String profile) {
		profiles.put(uid, profile);
	}

	public static void main(String[] args) {

		queue1 = new ConcurrentLinkedQueue<Player>();
		queue2 = new ConcurrentLinkedQueue<Player>();
		privateMatches = new ConcurrentHashMap<String, GameServer>();
		privateHosts = new ConcurrentHashMap<String, Player>();
		profiles = new ConcurrentHashMap<Integer, String>();
		history = new ConcurrentHashMap<Integer, ArrayList<String>>();
		Date lastUpdate = new Date();
		FlashServer.games = new ArrayList<GameServer>();
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
			ip=Files.readString(Paths.get("./config.txt")).trim();
			server = new ServerSocket(port);
		} catch (IOException e) {
			System.out.println("Error. Please ensure the port is available and config.txt exists(put IP or \"localhost\" there)");
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
				long newTime = new Date().getTime();
				if (newTime - lastUpdate.getTime() > 2000) {
					lastUpdate = new Date();
					try {
						updateServer();
					} catch (IOException a) {
						a.printStackTrace();
					}
				}
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
