import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
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
			int sendAfter = 0;
			while (this.alive) {
				// System.out.println("alive");
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
					// System.out.println("incoming: "+headers);
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
						if (msg[0].equals("Hello")) {
							toSend += "ServerMessage";
						}
						if (msg[0].equals("FindMeAQuickBattle")) {
							toSend += "GimmeUrPlayerInfo";
							shouldQueue = true;
							assault = (Integer.parseInt(msg[3]) == 0);
							// doubleWrite(toSend);toSend="";
						}
						if (msg[0].equals("HeresMyPlayerInfo")) {
							this.player = new Player(Integer.parseInt(msg[1]), msg[2], Integer.parseInt(msg[3]), 0, 0,
									msg[5], this.output, Integer.parseInt(msg[4]));
							// set player info, add to queue
							if (shouldQueue)
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
										gs.p2 = this.player;
										if (assault)
											gs.p1 = FlashServer.queue1.poll();
										else
											gs.p1 = FlashServer.queue2.poll();
										t1 = "FoundYouAGame,154.53.49.118," + gs.port + "," + 0 + ",4480\n";
										toSend += "\nFoundYouAGame,154.53.49.118," + gs.port + "," + 0 + ",4480\n";
										sendAfter = 3;
										gs.p1.doubleWrite(t1);
										FlashServer.games.add(gs);
										new Thread(gs).start();
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
										gs.p1 = this.player;
										FlashServer.privateMatches.put(code, gs);
										toSend += "FindingYouAMatch," + code;
									} else if (joinCode != null) {
										if (FlashServer.privateMatches.containsKey(joinCode)) {
											toSend += "FindingYouAMatch," + joinCode;
											//
											toSend += "\nFoundYouAGame,154.53.49.118,"
													+ FlashServer.privateMatches.get(joinCode).port + "," + joinCode
													+ ",4480\n";
											gs = FlashServer.privateMatches.get(joinCode);
											t1 = "FoundYouAGame,154.53.49.118,"
													+ FlashServer.privateMatches.get(joinCode).port + "," + joinCode
													+ ",4480\n";
											gs.p2 = this.player;
											sendAfter = 3;
											gs.p1.doubleWrite(t1);
											FlashServer.privateMatches.put(joinCode, gs);
											// doubleWrite(toSend);toSend="";
											new Thread(FlashServer.privateMatches.get(joinCode)).start();
										} else {
											toSend += "CouldntFindYourCustomBattle";
										}
									} else {
										toSend += "ServerMessage";
									}

								}
							}
						}
						if (msg[0].equals("ILeftGame")) {
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
						}
						if (msg[0].equals("CreateMeACustomBattle")) {
							shouldQueue = false;
							toSend += "GimmeUrPlayerInfo,";

							// toSend+="FoundYouAGame,localhost,"+p+","+1234567+",4480\n";
						}
						if (msg[0].equals("FindMyCustomBattle")) {
							// System.out.println(FlashServer.privateMatches.keySet());
							shouldQueue = false;
							// remove from queue

							if (FlashServer.privateMatches.containsKey(msg[2])) {
								joinCode = msg[2];
								toSend += "GimmeUrPlayerInfo,";
							} else {
								toSend += "CouldntFindYourCustomBattle";
							}

						}
						if (msg[0].equals("FindMyGame")) {
							// int p=4481+87*FlashServer.games.size();
							// System.out.println("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

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
				if (t1 != null && gs != null && gs.g2 == null)
					gs.p1.doubleWrite(t1);
				if (t2 != null && gs != null && gs.p2 != null)
					gs.p2.doubleWrite(t2);
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
	public GameServerThread g1;
	public GameServerThread g2;
	public ServerSocket server;
	public Player p1;
	public Player p2;
	public String code;
	public int port;
	public boolean alive;
	public int round;
	public int map;
	public int seed;

	public GameServer(int port) {
		this.round = 1;
		this.g2 = null;
		this.p1 = null;
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
				GameServerThread connection = new GameServerThread(client, this.map, this.seed);
				if (this.g1 == null) {
					g1 = connection;
					g1.opponent = p2;
					g1.side = 0;
					// g2.opponent.output=client.getOutputStream();
					g1.output = client.getOutputStream();
					p1.output = client.getOutputStream();
					new Thread(g1).start();
				}

				else {
					g2 = connection;
					g2.opponent = p1;
					g2.side = 1;
					g1.opponent.output = client.getOutputStream();
					p2.output = client.getOutputStream();
					g2.output = client.getOutputStream();
					g2.init = true;
					g1.init = true;
					new Thread(g2).start();
				}
			} catch (Exception e) {
				// e.printStackTrace();
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
	public Player player;
	public Player opponent;
	public Socket client;
	public OutputStream output;
	public boolean alive;
	public int side;
	public boolean r;
	public int relays;
	public int state = 0;
	public int seed = 0;
	public int map;
	public int win;
	public String reason = "Player disconnected";
	public int time = 0;
	public boolean init = false;

	public void doubleWrite(String s) throws IOException {
		this.output.write((s + "\n").getBytes(StandardCharsets.UTF_8));
		this.output.flush();
		if(FlashServer.verbose)
			System.out.println("#OUT: " + s);
	}

	public GameServerThread(Socket client, int map, int seed) {
		this.r = false;
		this.relays = 0;
		this.player = null;
		this.opponent = null;
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
			int timeouts = 0;
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
				if (state == 0) {
					if (FlashServer.getProfile(opponent.id) != null)
						opponent.profile = FlashServer.getProfile(opponent.id);
					doubleWrite("FoundYourGame," + opponent.id + "," + opponent.name + "," + opponent.bs + ","
							+ opponent.decal + "," + opponent.profile + "," + this.seed + "," + this.side + ","
							+ this.map + "," + opponent.w + "," + opponent.l);
					state = 1;
				}
				if (this.player == null) {
					doubleWrite("GimmeUrPlayerInfo,h");
				}
				if (lastTest != null && ((new Date()).getTime() - lastTest.getTime()) > 5000) {
					doubleWrite("RelayMsg,SentChatMsg," + FlashServer.encode("Lag test results: The game ran at "
							+ ((((double) ((int) (1.0 / (((double) ((new Date()).getTime() - lastTest.getTime()))
									/ ((double) (time * 1000 - lastTime * 1000))) * 10000))) / 100.0))
							+ "% speed over about 5 seconds"));
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
				}
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
							state = 1;
							this.player = new Player(Integer.parseInt(msg[1]), msg[2], Integer.parseInt(msg[3]), 0, 0,
									msg[5], this.output, Integer.parseInt(msg[4]));
						} else {
							String tail;

							if (m.indexOf(',') >= 0)
								tail = m.substring(m.indexOf(','));
							else
								tail = "";
							if (msg[0].equals("IChangedMyTowerLoadout")) {
								toSend += "OpponentChangedTowerLoadout" + tail;
							}
							if (msg[0].equals("IRequestYourTowerLoadout")) {
								toSend += "OpponentRequestsMyTowerLoadout" + tail;
							}
							if (msg[0].equals("IChangedBattleOptions")) {
								toSend += "OpponentChangedBattleOptions" + tail;
							}
							if (msg[0].equals("MyReadyToPlayStatus")) {
								toSend += "OpponentReadyStatus" + tail;
							}
							if (msg[0].equals("MyGameIsLoaded")) {
								System.out.println("player ready");
								toSend += "OpponentHasLoaded" + tail;
							}
							if (msg[0].equals("GimmeOpponentSyncData")) {
								toSend += "OpponentRequestsSync" + tail;
							}
							if (msg[0].equals("IBuiltATower")) {
								toSend += "OpponentBuiltATower" + tail;
							}
							if (msg[0].equals("ISoldATower")) {
								toSend += "OpponentSoldATower" + tail;
							}
							if (msg[0].equals("IUpgradedATower")) {
								toSend += "OpponentUpgradedATower" + tail;
							}
							if (msg[0].equals("ISentABloonWave")) {
								/**if (tail.contains("Cerem")) {
									doubleWrite("RelayMsg,SentChatMsg," + FlashServer.encode("they WILL die,"));
									opponent.doubleWrite(
											"RelayMsg,SentChatMsg," + FlashServer.encode("they WILL die,"));
								}*/
								toSend += "OpponentSentABloonWave" + tail;
							}
							if (msg[0].equals("IRemovedABloonWave")) {
								toSend += "OpponentRemovedABloonWave" + tail;
							}
							if (msg[0].equals("ILived")) {
								this.win = 2;
								toSend += "OpponentLived" + tail;
							}
							if (msg[0].equals("IDied")) {
								System.out.println("game ended probably");
								this.win = 1;
								this.reason = "Player died";
								toSend += "OpponentDied" + tail;
							}
							if (msg[0].equals("ISurrender")) {
								System.out.println("game ended probably");
								this.win = 1;
								this.reason = "Player surrendered";
								toSend += "OpponentSurrendered" + tail;
							}
							if (msg[0].equals("IChangedATowerTarget")) {
								toSend += "OpponentTowerTargetChanged" + tail;
							}
							if (msg[0].equals("IChangedAcePath")) {
								toSend += "OpponentChangedAcePath" + tail;
							}
							if (msg[0].equals("IChangedTargetReticle")) {
								toSend += "OpponentChangedTargetReticle" + tail;
							}
							if (msg[0].equals("IUsedAnAbility")) {
								toSend += "OpponentUsedAnAbility" + tail;
							}
							if (msg[0].equals("YouDidntRespondToMySyncs")) {
								// toSend+="OpponentDidntGetMySyncResponses"+tail;
								toSend += "OpponentRequestsSync" + tail;

							}
							if (msg[0].equals("RelayMsg")) {
								this.relays++;
								if (msg.length > 1 && msg[1].equals("SentChatMsg")) {
									try {
										String[] cmd = FlashServer.decode(msg[2]).trim().split(" ");
										String param = null;
										if (cmd.length > 1)
											param = FlashServer.decode(msg[2]).trim()
													.substring(FlashServer.decode(msg[2]).trim().indexOf(' ') + 1);
										if (cmd[0].equals("!help"))
											doubleWrite("RelayMsg,SentChatMsg," + FlashServer.encode(
													"\nCommands:\n!help - displays this\n!history - displays your most recent 10 matches\n!source - view the source code on GitHub\n!profile <url> - set a profile picture for your opponents to see(140x140)\n!random <count> - generates random tower(s)\n!map <count> - generates random map(s)\n!lagtest - tests game timer vs real time\n!bugs - displays known bugs"));
										else if (cmd[0].equals("!history"))
											doubleWrite("RelayMsg,SentChatMsg,"
													+ FlashServer.encode(FlashServer.getHistory(player.id)));
										else if (cmd[0].equals("!source"))
											doubleWrite("RelayMsg,SentChatMsg,"
													+ FlashServer.encode("https://github.com/GlennnM/NKFlashServers"));
										else if (cmd[0].equals("!profile")) {
											if (cmd.length == 1)
												doubleWrite("RelayMsg,SentChatMsg,"
														+ FlashServer.encode("Current profile picture: "
																+ FlashServer.getProfile(player.id)));
											else {
												doubleWrite("RelayMsg,SentChatMsg,"
														+ FlashServer.encode("Set profile picture to: " + param));
												FlashServer.setProfile(player.id, param);
											}
										} else if (cmd[0].equals("!random")) {
											int count = 0;
											if (param == null)
												count = 4;
											else
												count = Math.min(Integer.parseInt(param), 10);
											ArrayList<String> x = new ArrayList<String>();
											for (int n = 0; n < count; n++) {
												int q = (int) (Math.random() * FlashServer.towers.length);
												while (x.contains(FlashServer.towers[q]))
													q = (int) (Math.random() * FlashServer.towers.length);
												x.add(FlashServer.towers[q]);
											}
											doubleWrite("RelayMsg,SentChatMsg," + FlashServer
													.encode(x.toString().substring(1, x.toString().length() - 1)));
										} else if (cmd[0].equals("!map")) {
											int count = 0;
											if (param == null)
												count = 1;
											else
												count = Math.min(Integer.parseInt(param), 10);
											ArrayList<String> x = new ArrayList<String>();
											for (int n = 0; n < count; n++) {
												int q = (int) (Math.random() * FlashServer.maps.length);
												while (x.contains(FlashServer.maps[q]))
													q = (int) (Math.random() * FlashServer.maps.length);
												x.add(FlashServer.maps[q]);
											}
											doubleWrite("RelayMsg,SentChatMsg," + FlashServer
													.encode(x.toString().substring(1, x.toString().length() - 1)));
										} else if (cmd[0].equals("!bugs"))
											doubleWrite("RelayMsg,SentChatMsg," + FlashServer.encode(
													"Known issues:\n-game disconnects sometimes\n-wins and losses aren't shown\n-wins, losses, battlescore don't update"));
										else if (cmd[0].equals("!lagtest")) {
											doubleWrite("RelayMsg,SentChatMsg,"
													+ FlashServer.encode("Starting lag test..."));
											lastTest = new Date();
											lastTime = this.time;
										} else
											toSend += m;
									} catch (Exception e) {
										doubleWrite("RelayMsg,SentChatMsg," + FlashServer.encode(
												"An error occurred while processing a chat message or command."));
										e.printStackTrace();
									}
								} else
									toSend += m;
							}
							if (msg[0].equals("HeresMySyncData")) {
								if (msg.length > 1) {
									toSend += "OpponentSyncRetrieved" + tail;
									time = (int) (Double.parseDouble(msg[1]));
								}
							}
							if (msg[0].equals("ImReadyToStartARound")) {
								this.r = true;
							}
							if (msg[0].equals("ILeftGame")) {
								this.win = 1;
								toSend += "OpponentDisconnected";
							}

							if (msg[0].equals("GiveMeDaBalance")) {
								doubleWrite("HeresDaBalance," + FlashServer.balance);
							}
							// ImReadyToStartARound
						}
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
				else if (toSend.length() > 0 && this.init) {

					opponent.doubleWrite(toSend);
					// opponent.doubleWrite("ServerMessage");
					headers = "";
					line = "";
					toSend = "";
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
	public OutputStream output;
	public int decal;
	public String profile;
	public boolean r;

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
	public static String[] towers = new String[] { "Banana Farm", "Bomb Tower", "Boomerang Thrower", "Dart Monkey",
			"Dartling Gun", "Glue Gunner", "Ice Tower", "Monkey Ace", "Monkey Apprentice", "Monkey Buccaneer",
			"Monkey Village", "Mortar Tower", "Ninja Monkey", "Sniper Monkey", "Spike Factory", "Super Monkey",
			"Tack Shooter" };
	public static String[] maps = new String[] { "Park", "Temple", "Yin Yang", "Cards", "Rally", "Mine", "Hydro Dam",
			"Pyramid Steps", "Patch", "Battle Park", "Ice Flow", "Yellow Brick Road", "Swamp", "Battle River",
			"Mondrian", "Zen Garden", "Volcano", "Water Hazard", "Indoor Pools", "A-Game", "Ink Blot", "Snowy Castle" };
	public static String balance = "baseVals;STARTING_ENERGY=50;ENERGY_COST_PER_GAME=5;ENERGY_COST_SPY=3;ENERGY_COST_EXTRA_TOWER=6;ENERGY_REGEN_TIME=720;MEDS_FOR_WIN=5;MEDS_FOR_LOSS=2;BATTLESCORE_FOR_WIN=10;BATTLESCORE_FOR_LOSS=2;SURRENDER_ROUND_REWARD=7;COST_MULTIPLIER_REGEN=1.8;COST_MULTIPLIER_CAMO=2.5;UNLOCK_ROUND_REGEN=8;UNLOCK_ROUND_CAMO=12;STARTING_CASH=650;STARTING_HEALTH=150;CASH_PER_TICK_MINIMUM=0;CASH_PER_TICK_STARTING=250;CASH_PER_TICK_STARTING_DEFEND=25;CASH_PER_TICK_MAXIMUM_DEFEND=3000;GIVE_CASH_TIME=6;FIRST_ROUND_START_TIME=6;ROUND_EXPIRY_TIME_BASE=8;ROUND_EXPIRY_TIME_MUL=1.5;PREMATCH_SCREEN_TIME=30;MAX_BLOON_QUE_SIZE=10;baseValsEnd;bloonSetsid=RedGroup1;type=0;name=Grouped Reds;hotkey=1;quantity=8;interval=0.1;cost=25;incomeChange=1;unlockRound=2;,id=SpaceBlue1;type=1;name=Spaced Blues;hotkey=Shift+1;quantity=6;interval=0.33;cost=25;incomeChange=1;unlockRound=2;,id=BlueGroup1;type=1;name=Blue Bloon;hotkey=2;quantity=6;interval=0.1;cost=42;incomeChange=1.7;unlockRound=4;,id=SpacedPink1;type=4;name=Spaced Pink;hotkey=Shift+2;quantity=3;interval=0.5;cost=42;incomeChange=1.7;unlockRound=4;,id=GreenGroup1;type=2;name=Grouped Greens;hotkey=3;quantity=5;interval=0.08;cost=60;incomeChange=2.4;unlockRound=6,id=SpacedBlack1;type=5;name=Spaced Black;hotkey=Shift+3;quantity=3;interval=0.6;cost=60;incomeChange=2.4;unlockRound=6,id=YellowGroup1;type=3;name=Grouped Yellows;hotkey=4;quantity=5;interval=0.06;cost=75;incomeChange=3;unlockRound=8,id=SpaceWhite1;type=6;name=Spaced White;hotkey=Shift+4;quantity=4;interval=0.5;cost=75;incomeChange=3;unlockRound=8,id=PinkGroup1;type=4;name=Grouped Pink;hotkey=5;quantity=3;interval=0.05;cost=90;incomeChange=3.6;unlockRound=10,id=SpaceLead1;type=7;name=Spaced Leads;hotkey=Shift+5;quantity=2;interval=1.5;cost=90;incomeChange=3.6;unlockRound=10,id=WhiteGroup1;type=6;name=Group White;hotkey=6;quantity=3;interval=0.15;cost=125;incomeChange=5;unlockRound=11,id=SpaceZebra1;type=8;name=Space Zebra;hotkey=Shift+6;quantity=3;interval=0.6;cost=125;incomeChange=5;unlockRound=11,id=BlackGroup1;type=5;name=Black Bloon;hotkey=7;quantity=3;interval=0.15;cost=150;incomeChange=6;unlockRound=12,id=SpaceRainbowGroup1;type=9;name=Space Rainbow;hotkey=Shift+7;quantity=1;interval=1;cost=150;incomeChange=6;unlockRound=12,id=ZebraGroup1;type=8;name=Zebra Bloon;hotkey=8;quantity=3;interval=0.18;cost=200;incomeChange=6;unlockRound=13,id=RainbowGroup1;type=9;name=Rainbow Bloon;hotkey=Shift+8;quantity=3;interval=0.18;cost=450;incomeChange=3;unlockRound=13,id=LeadGroup1;type=7;name=Grouped Leads;hotkey=9;quantity=4;interval=0.2;cost=200;incomeChange=6;unlockRound=15,id=SpaceCeremic1;type=10;name=Space Ceremic;hotkey=Shift+9;quantity=1;interval=1;cost=300;incomeChange=0;unlockRound=15,id=CeremicGroup1;type=10;name=Fast Ceremic;hotkey=0;quantity=1;interval=0.25;cost=450;incomeChange=-5;unlockRound=18,id=SpaceMOABGroup1;type=11;name=Space MOAB;hotkey=Shift+0;quantity=1;interval=5;cost=1500;incomeChange=-60;unlockRound=18,id=MOABGroup1;type=11;name=Fast MOAB;hotkey=O;quantity=1;interval=0.5;cost=1500;incomeChange=-140;unlockRound=20,id=BFBGroup1;type=12;name=BFB;hotkey=Shift+O;quantity=1;interval=4;cost=2500;incomeChange=-350;unlockRound=20,id=FastBFBGroup1;type=12;name=Fast BFB;hotkey=P;quantity=1;interval=0.6;cost=2500;incomeChange=-350;unlockRound=22,id=ZOMGGroup1;type=13;name=ZOMG;hotkey=Shift+P;quantity=1;interval=6;cost=9000;incomeChange=-1500;unlockRound=22;bloonSetsEnd";
	//
	public static volatile ConcurrentHashMap<InetAddress, Integer> ipThreads;
	public static volatile ConcurrentLinkedQueue<Player> queue1;// assault
	public static volatile ConcurrentLinkedQueue<Player> queue2;// defend
	public static volatile ArrayList<GameServer> games;
	public static volatile ConcurrentHashMap<String, GameServer> privateMatches;
	public static volatile ConcurrentHashMap<Integer, String> profiles;// might become settings object
	public static volatile ConcurrentHashMap<Integer, ArrayList<String>> history;
	public static volatile String log = "";
	public static volatile int nextPort = 8119;

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
				privateMatches.remove(x);
				System.out.println("game finished");
			}
		for (GameServer q : games)
			if (!q.alive) {
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
