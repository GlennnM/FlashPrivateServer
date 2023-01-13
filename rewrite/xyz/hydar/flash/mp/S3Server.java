package xyz.hydar.flash.mp;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
*	SAS3 Multiplayer Server
*	For some reason this game is more "server sided" than any other NK game... the server is not just used as a relay and for matchmaking but also has to:
*	-spawn mobs(purge nests/skeletons too) itself
*	-track which mobs are alive to end waves
*	-manually kill mobs hit by AoE weapons
*	-spawn mini mamushkas and worms when those are killed
*	-track XP, kills, etc of all players
*	-handle targeting of mobs(retarget on death/getting hit)
*	So as a result it is probably much more complicated and laggy than any of the other servers.
*	
*	Ninja kiwi's servers for this game used smartfoxserver(https://www.smartfoxserver.com/) - it supports HTTP tunneling which is why the game was able to be played
*	behind firewalls, but also has a weird disconnect system that never actually kicks people
*	
*	 @TODO 
*	 bots-not needed since it doesnt actually affect difficulty for now lol
*	 barriers->xp
*	 fix density-"done??? the original functionality isnt replicated, particularly in purge/apoc, but hopefully its close
*	 fix xp-the calculations are kinda random rn(loosely based on sp)
*	 reduce lag more - too many zombies :(
*	 notify player(kick? send something to see if still alive?) when timed out in lobby
*	 
*	 purge-"done"
*	 apoc-"done"
*	 dev attacks-done
*	 targeting-done
*	 death/revive-done 
*	 game end-done
*	 performance-done(for now)
*	 test mp-done(for now)
*	 leaving-done
*	 make mgl work-done 
*	 winning/xp-done
*	 powerups-done
 */

class S3ServerThread extends Thread {
	/**
	 * Server threads handle smartfox setup, matchmaking, and then are grouped into
	 * rooms for ingame stuff. (unlike the other servers which use separate ports
	 * for game instances)
	 */
	public Socket client = null;
	OutputStream output;
	public volatile boolean xt = false;
	public volatile String name;
	public volatile int rank;
	public int mode;
	public int nm;
	public volatile boolean ready;
	public volatile boolean alive;// thread alive/dead
	public volatile Room room;
	public volatile int id;
	public volatile int myPlayerNum;

	public volatile boolean dead = false;// INGAME alive/dead
	public volatile int kills;
	public volatile int deaths;
	public volatile int xp;
	public volatile int damage;
	public volatile int revives;
	public volatile int cash;
	public volatile long startTime;

	public void doubleWrite(String s) throws IOException {
		this.output.write(s.getBytes(StandardCharsets.UTF_8));
		// add a dummy message after, otherwise it gets ignored(idk why)
		if (xt) {
			if (startTime != 0)
				this.output.write(("\0" + "%xt%13%-1%" + (new Date().getTime() - startTime) + "%0%")
						.getBytes(StandardCharsets.UTF_8));
			else
				this.output.write(("\0" + "%xt%13%-1%" + 0 + "%0%").getBytes(StandardCharsets.UTF_8));

		}
		this.output.flush();
		if (S3Server.verbose)
			System.out.println("OUT: " + s);
	}

	// constructor initializes socket
	public S3ServerThread(Socket socket) throws IOException {
		this.client = socket;
		this.alive = true;
		this.room = null;
		this.name = null;
		this.ready = false;
		myPlayerNum = -1;
		mode = 0;
		nm = 0;
		kills = 0;
		deaths = 0;
		xp = 0;
		damage = 0;
		revives = 0;
		cash = 0;
		id = (int) (Math.random() * 999999999);
		this.rank = 0;
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
			int timeouts = 0;
			String toSend = "";
			int sendAfter = 0;
			boolean hydar = false;
			while (this.alive) {
				int l4 = 0;
				int actualSize = 0;
				int max = 1024;
				char[] buffer = new char[max];
				try {
					l4 = ir.read(buffer, 0, max);
					actualSize += l4;
				} catch (java.net.SocketTimeoutException ste) {
					/**
					 * kill after multiple timeouts
					 */
					// ste.printStackTrace();
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
					headers = new String(buffer).trim();
					timeouts = 0;
					toSend = "";
					if (S3Server.verbose)
						System.out.println("incoming: " + headers);
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
					// messages are usually null terminated
					String[] msgs = headers.split("\0");
					for (String m : msgs) {
						if (!m.endsWith("%"))
							m += "%";
						if (!m.startsWith("%"))
							m = "%" + m;
					}

					for (String m : msgs) {
						// "extension message" - main protocol for the game. A message triggers a
						// "command"(
						if (m.startsWith("%xt%S")) {
							xt = true;
							ArrayList<String> msg = new ArrayList<String>(Arrays.asList(m.split("%")));
							if(msg.size()<6)
								continue;
							int cmd = Integer.parseInt(msg.get(5));
							if (cmd == 10) {
								if (msg.size() < 9)
									continue;
								int health = Integer.parseInt(msg.get(8));
								if (health <= 0) {
									long time = new Date().getTime() - startTime;
									deaths++;
									dead = true;
									room.targets.remove((Integer) (myPlayerNum));
									if (room.someAlive()) {
										room.retargetAll(myPlayerNum);
										room.writeAll("\0%xt%11%-1%" + (time) + "%" + myPlayerNum + "%" + myPlayerNum
												+ "%" + ((rank >= 27) ? 20000 : 15000) + "%");
									} else
										room.end(false);
									// doubleWrite("\0%xt%11%-1%"+(time+((rank>=27)?21000:28000))+"%"+myPlayerNum+"%"+((rank>=27)?21000:28000)+"%");
									// room.writeAll("\0%xt%12%-1%"+(time)+"%"+myPlayerNum+"%"+myPlayerNum+"%"+((time+((rank>=27)?21000:28000)))+"%");
									// room.timer.schedule(new
									// ReviveTask("\0%xt%10%-1%"+(time)+"%"+myPlayerNum+"%"+100+"%",this),((rank>=27)?21000:28000));
									// lose if all dead
								} else {
									dead = false;
									room.targets.add(myPlayerNum);
									room.retargetAll(-1);
									if (revives < deaths)
										revives++;
								}
							} else if (cmd == 23) {
								if (msg.size() < 10)
									continue;
								if (S3Server.verbose)
									System.out.println(
											">>>>>>>>>>>>>>>>>>>>>>>dev attacking\n\n\n>>>>>>>>>>>>>>>>>>>>>>>>>>");
								// System.out.println(room.zombies.get(Integer.parseInt(msg.get(8))));
								if (msg.get(9).equals("2")) {
									room.raise(Integer.parseInt(msg.get(8)));
								} else {
									// aoe attack
								}
							} else if (cmd == 7) {
								if (msg.size() < 9)
									continue;
								int[] data = room.parseDamage(msg.get(8), myPlayerNum);
								kills += data[0];
								damage += data[1];
								xp += data[2];
							} else if (cmd == 3) {
								if (msg.size() < 14)
									continue;
								if (room == null) {
									mode = Integer.parseInt(msg.get(12));
									nm = Integer.parseInt(msg.get(13));
									this.name = msg.get(7);
									this.rank = Integer.parseInt(msg.get(8));
									for (Room r : S3Server.games) {
										if (r.mode == mode && r.nm == nm && r.players.size() < 4 && !r.setup
												&& !r.end) {
											r.players.add(this);
											this.room = r;
											break;
										}
									}
									if (this.room == null) {
										this.room = new Room(this, mode, nm);
										S3Server.games.add(this.room);
									}
									this.myPlayerNum = room.nextPlayerNum();
									room.targets.add(myPlayerNum);
								}
								ArrayList<String> playerNames = new ArrayList<String>();
								ArrayList<Integer> playerRanks = new ArrayList<Integer>();
								ArrayList<Boolean> readyList = new ArrayList<Boolean>();
								room.players.forEach(x -> {
									playerNames.add("\"" + x.name + "\"");
									playerRanks.add(x.rank);
									readyList.add(x.ready);
								});
								if (!hydar) {
									for (S3ServerThread x : room.players) {
										toSend = "\0" + "%xt%15%" + 0 + "%-1%1%" + room.players.size() + "%"
												+ x.myPlayerNum + "%" + playerNames + "%" + playerRanks + "%"
												+ readyList + "%1%" + mode + "%" + (mode) + "%" + room.map;
										if (x != this) {
											try {
												x.doubleWrite(toSend);
											} catch (Exception e) {
												e.printStackTrace();
											}
										}
									}

									timeouts++; // doubleWrite(toSend);+room.players.size()+"%0%"+playerNames+"%"+playerRanks+"%"+readyList+"%1%\""+mode+"\"%"+nm+"%%%%%"
								} else
									hydar = true;
							} else if (cmd == 26) {

								// time = 0;
								ArrayList<String> playerNames = new ArrayList<String>();
								ArrayList<Integer> playerRanks = new ArrayList<Integer>();
								ArrayList<Boolean> readyList = new ArrayList<Boolean>();
								room.players.forEach(x -> {
									playerNames.add("\"" + x.name + "\"");
									playerRanks.add(x.rank);
									readyList.add(x.ready);
								});
								// toSend+="\0"+"%xt%25%"+time+"%%1%"+room.players.size()+"%"+this.myPlayerNum+"%"+playerNames+"%"+playerRanks+"%"+readyList+"%1%"+mode+"%"+(mode)+"%"+room.mapURL+"%"+room.nm+"%"+room.nm;
								for (S3ServerThread x : room.players) {
									try {
										toSend = "\0" + "%xt%25%" + 0 + "%%1%" + room.players.size() + "%"
												+ x.myPlayerNum + "%" + playerNames + "%" + playerRanks + "%"
												+ readyList + "%1%" + mode + "%" + (mode) + "%" + room.mapURL + "%"
												+ room.nm + "%" + room.nm;
										x.doubleWrite(toSend);
										toSend = "";
									} catch (Exception e) {
										e.printStackTrace();
									}
								}

								// room.writeAll(toSend);

							}
							msg.set(2, "" + cmd);
							long time;
							if (startTime != 0)
								time = new Date().getTime() - startTime;
							else
								time = 0;
							msg.set(3, "" + time);
							msg.set(4, "" + -1);

							if (cmd == 4) {
								this.ready = true;
								time = new Date().getTime() / 1000;
								time = 0;
								startTime = new Date().getTime();
								ArrayList<String> playerNames = new ArrayList<String>();
								ArrayList<Integer> playerRanks = new ArrayList<Integer>();
								ArrayList<Boolean> readyList = new ArrayList<Boolean>();
								room.players.forEach(x -> {
									playerNames.add("\"" + x.name + "\"");
									playerRanks.add(x.rank);
									readyList.add(x.ready);
								});
								// toSend+="\0"+"%xt%15%"+time+"%1%"+room.players.size()+"%"+room.indexOf(id)+"%"+playerNames+"%"+playerRanks+"%"+readyList+"%1%"+mode+"%"+(mode)+"%"+room.map;
								// room.writeFrom(id,toSend);
								// toSend="";
								// toSend+="\0"+"%xt%26%-1%"+time+"%1.0"+"%"+room.nm+"%";
								// room.writeAll(toSend);
								// toSend="";
								// toSend="\0"+"%xt%17%-1%"+time+"%1"+"%"+10+"%";
								// room.writeAll(toSend);
								// sendAfter=1;
								// msg.remove(5);
								if (room.allReady() && !room.setup)
									room.setup();
								msg.set(3, "0");
								msg.set(msg.size() - 2, "" + room.SBEmult);
								msg.set(msg.size() - 1, "" + room.barriHP);
								String tmp = msg.get(3);
								msg.set(3, msg.get(4));
								msg.set(4, tmp);
								msg.remove(5);

								// msg.add(""+room.nm);
								// room.writeAll(pl);
								// pl
							}
							ArrayList<Integer> pc = new ArrayList<Integer>(Arrays.asList(
									new Integer[] { 1, 2, 13, 9, 7, 10, 23, 22, 20, 19, 12, 5, 6, 24, 14, 11, 26 }));
							if (pc.contains(cmd) && cmd != 26) {
								if(msg.size()<8)
									continue;
								msg.remove(2);
								msg.remove(2);
								// msg.remove(msg.get(2));
								String tmp = msg.get(2);
								msg.set(2, msg.get(3));
								msg.set(3, tmp);
								msg.set(5, "" + myPlayerNum);
								if (cmd == 7) {
									msg.set(4, "0");
								//	// msg.set(5,"0");
								}
								// msg.remove(3);
								// msg.add("");
							}
							String pl = "\0" + String.join("%", msg) + "%";
							// if(cmd==4){
							// pl+=""+room.nm+"%";
							// }
							// room.writeAll(pl);
							if (pc.contains(cmd) && cmd != 23)
								room.writeFrom(this.id, pl);
							else if (!(cmd == 4 && !room.allReady()))
								room.writeAll(pl);

							// toSend="\0"+"%xt%3%"+time+"%-1%"+name+"%\""+rank+"\"%0%\""+mode+"\"%\""+room.map+"\"%\""+(mode+1)+"\"%"+nm;
							// toSend="\0"+"%xt%3%"+time+"%-1%"+"glenn m%50%0%%0%2%0%";
							// doubleWrite(toSend);
							// doubleWrite(toSend);
							// 7 name
							// 8 lvl
							// 12 mode
							// 13 NM
							// room.players.forEach(x->x.doubleWrite()
						}
						// smartfox setup(hard coded) - dont think most of it matters
						else if (m.equals("<msg t='sys'><body action='verChk' r='0'><ver v='165' /></body></msg>"))
							toSend = "\0<msg t='sys'><body action='apiOK' r='0'><ver v='165'/></body></msg>\n";
						else if (m.equals(
								"<msg t='sys'><body action='login' r='0'><login z='SAS3'><nick><![CDATA[]]></nick><pword><![CDATA[]]></pword></login></body></msg>"))
							toSend = "\0<msg t='sys'><body action='logOK' r='0'><login id='" + id
									+ "' mod='0' n='SAS3'/></body></msg>\n";
						else if (m.equals("<msg t='sys'><body action='getRmList' r='-1'></body></msg>"))
							toSend = "\0<msg t='sys'><body action='rmList' r='-1'><rmList><rm></rm></rmList></body></msg>";

						else if (S3Server.verbose)
							System.out.println("UNKNOWN****************************");
					}
					try {
						Thread.sleep(1);
					} catch (InterruptedException ee) {
						Thread.currentThread().interrupt();
					}
				} else {
					timeouts++;
					if (timeouts > 120) {
						this.alive = false;
						output.close();
						input.close();
						ir.close();
						this.client.close();
					}
					headers = "";
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
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		this.alive = false;
		if (room != null) {
			room.dropPlayer(id);
		}
		return;
	}
}

class WaveStartTask extends TimerTask {
	public Room room;

	public WaveStartTask(Room room) {
		this.room = room;
		room.p1 = 0;
		room.p2 = 0f;
		room.p3 = 0f;
	}

	@Override
	public void run() {
		// send WaveStrtCommand
		if (!room.alive)
			return;
		room.p1 = 0;
		room.p2 = 0.0f;
		room.p3 = 0f;
		room.wave++;
		room.writeAll("\0" + "%xt%17%-1%" + 0 + "%" + room.wave
				+ "%" + room.waveTotal + "%");
		if (room.mode == 1) {
			room.timer.schedule(new SpawnNestTask(room), 5000);
			room.timer.schedule(new PowerupTask(room), 7000);
		} else {
			room.timer.schedule(new SpawnTask(room), 5000);
			room.timer.schedule(new PowerupTask(room), 30000);
		}
		room.init = true;
		room.r = false;
	}
}

class SpawnNestTask extends TimerTask {
	public Room room;

	public SpawnNestTask(Room r) {
		this.room = r;
	}

	@Override
	public void run() {
		room.spawnNests();
		room.timer.schedule(new SpawnTask(room), 1);
	}
}

class SpawnTask extends TimerTask {
	public Room room;
	public static AbstractZombie[] zombies = new AbstractZombie[] { new AbstractZombie(0, 1), new AbstractZombie(1, 4),
			new AbstractZombie(2, 10), new AbstractZombie(3, 12), new AbstractZombie(4, 14), new AbstractZombie(5, 16),
			new AbstractZombie(6, 999), new AbstractZombie(7, 999), new AbstractZombie(8, 999),
			new AbstractZombie(9, 18), new AbstractZombie(10, 999), new AbstractZombie(11, 999) };

	public SpawnTask(Room room) {
		this.room = room;
	}

	@Override
	public void run() {
		float loc2 = room.commaHash();
		ArrayList<AbstractZombie> loc3 = new ArrayList<AbstractZombie>();
		for (AbstractZombie z : zombies) {
			if (/** (z.index==9)&& */
			!(room.map == 1 && z.index == 9) && z.weight <= loc2) {
				loc3.add(z);
			}
		}
		// create loc5
		float chanceSum = 0.0f;
		for (AbstractZombie z : loc3)
			chanceSum += z.chance;
		float loc7 = 1;// locals from -;
		int loc8_ = loc3.size() - 1;// locals from -;
		float[] loc5 = new float[loc3.size()];
		while (loc8_ >= 0) {
			loc5[loc8_] = loc7;
			loc7 -= loc3.get(loc8_).chance / chanceSum;
			loc8_--;
		}
		float loc6 = room.dashL(loc2);
		loc7 = loc6 * 2500f / 1000f;
		room.p3 += loc7;
		float loc8 = room.p3 - room.p2;

		float loc10 = 0f;
		AbstractZombie loc12 = null;
		float loc16 = 0f;
		int loc17 = 0;
		float loc13 = 0f;
		StringBuffer cmds = new StringBuffer(Math.max((int) (loc8) * 15, 0));
		while (loc10 < loc8) {
			loc12 = loc3.get(loc3.size() - 1);
			loc16 = (float) Math.random();
			loc17 = 0;
			while (loc17 < loc5.length) {
				if (loc16 < loc5[loc17]) {
					loc12 = loc3.get(loc17);
					break;
				}
				loc17++;
			}
			loc13 = (((room.mode == 1 ? 1 : loc12.cap) - 1f) * 0.5f + 1f) * room.SBEmult;
			loc10 += loc13;
			room.p2 += loc13;
			cmds.append(room.spawnCmd(loc12));

		}
		String spawnCmd = cmds.toString();
		if (spawnCmd.length() > 0) {
			room.writeAll(spawnCmd);
		}
		room.p1++;

		if (!room.alive)
			return;
		if (room.p1 < room.p4) {
			/**
			 * if(this.§]3§(§3H§.§-O§,3)) { this.§60§.delay = §6=§ + (Math.random() * §="§ -
			 * §="§ / 2); } else { this.§60§.delay = 1; } this.§60§.reset();
			 * this.§60§.start();
			 */
			if (room.mode == 2 && room.bracket3(3))
				room.timer.schedule(new SpawnTask(room), 2500);
			else if (room.mode == 2)
				room.timer.schedule(new SpawnTask(room), 1000);
			else if (room.mode == 1)
				room.timer.schedule(new SpawnTask(room),
						(Math.max(2000 - room.p1 * 10, 1000)) * room.nests.size() / room.wave);
			else
				room.timer.schedule(new SpawnTask(room), (Math.max(2500 - room.p1 * 10, 1500)));

		} else {
			// if(room.init && room.p1 >=24 && !(room.bracket3(3)))

			// {

			// room.timer.schedule(new WaveEndTask(room),1);

			// }
		}
	}
}

class WaveEndTask extends TimerTask {
	public Room room;

	public WaveEndTask(Room room) {
		this.room = room;
	}

	@Override
	public void run() {
		int index=0;
		if (!room.alive)
			return;
		for (S3ServerThread t : room.players)
			if (!t.dead)
				index=t.myPlayerNum;
		if (room.zombies.size() > 0) {
			String hitCmd = "\0%xt%7%-1%"+0+"%"+index+"%";
			for (Zombie z : room.zombies.values()) {
				hitCmd += z.number + ":" + "999999:d,";
			}
			room.zombies.clear();
			room.totalCapacity = 0;
			hitCmd = hitCmd.substring(0, hitCmd.length() - 1) + "%";
			room.writeAll(hitCmd);
		}
		room.writeAll("\0" + "%xt%18%-1%" + 0 + "%" + room.wave + "%" + room.waveTotal + "%");
		room.timer.cancel();
		room.timer.purge();
		room.timer = new Timer();
		if (room.wave < room.waveTotal) {
			room.timer.schedule(new WaveStartTask(room), 1000);
		} else {
			room.end(true);
		}
	}
}

class AbstractZombie {
	public int index;
	public int weight;// 6c
	public float chance;// function 8o
	public float cap;// %O
	public volatile float hp;
	public int xp;

	public AbstractZombie(int index, int weight) {
		this.index = index;
		this.weight = weight;
		switch (this.index) {
		case 0:
			chance = 100f;
			cap = 1f;
			hp = 160f;
			xp = 10;
			break;
		case 1:
			chance = 30f;
			cap = 1.5f;
			hp = 100f;
			xp = 15;
			break;
		case 2:
			chance = 15f;
			cap = 6f;
			hp = 500f;
			xp = 60;
			break;
		case 3:
			chance = 5f;
			cap = 15f;
			hp = 3000f;
			xp = 150;
			break;
		case 4:
			chance = 2f;
			cap = 12f;
			hp = 2500f;
			xp = 120;
			break;
		case 5:
			chance = 1f;
			cap = 108f;
			hp = 4000f;
			xp = 360;
			break;
		case 6:
			chance = 0f;
			cap = 36f;
			hp = 3000f;
			xp = 120;
			break;
		case 7:
			chance = 0f;
			cap = 12f;
			hp = 2000f;
			xp = 40;
			break;
		case 8:
			chance = 0f;
			cap = 4f;
			hp = 1000f;
			xp = 40;
			break;
		case 9:
			chance = 0.1f;
			cap = 600f;
			hp = 30000f;
			xp = 6000;
			break;
		case 10:
			chance = 0f;
			cap = 3f;
			hp = 300f;
			xp = 0;
			break;
		case 11:
			chance = 0f;
			cap = 0.5f;
			hp = 100f;
			xp = 5;
			break;
		case 12:
			chance = 0f;
			cap = 1f;
			hp = 80000f;
			xp = 6000;
			break;
		default:
			chance = 0.0f;
			cap = 0f;
			hp = 0f;
			xp = 0;
			break;
		}

	}

	@Override
	public String toString() {
		return "" + index;
	}
}

class Zombie extends AbstractZombie {
	public int number = 0;
	public int parent = -1;
	public int target=-1;
	public Zombie(int index, int weight, int number, int target, float SBEmult) {
		super(index, weight);
		this.target=target;
		this.number = number;
		hp = hp * ((SBEmult - 1f) * 0.5f + 1f);
	}

	public Zombie(int index, int weight, int number, int target, int parent, float SBEmult) {
		super(index, weight);
		this.parent = parent;
		this.target=target;
		this.number = number;
		hp = hp * ((SBEmult - 1f) * 0.5f + 1f);
	}

	@Override
	public String toString() {
		return "" + number + "(" + index + "):" + hp;
	}

}

class PowerupTask extends TimerTask {
	public Room room;
	public static final int[] GUNS = new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 18, 19, 20,
			21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43 };
	public static final int[] GRENADES = new int[] { 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 3, 3 };
	public static final int[] SENTRIES = new int[] { 0, 1 };

	public PowerupTask(Room room) {
		this.room = room;
	}

	@Override
	public void run() {
		int loc6 = (int) (Math.random() * this.room.powerups.length);
		int loc7 = this.room.powerups[loc6];//spawn point
		for (S3ServerThread player : room.players) {
			player.xp += 50;
			float loc2 = 2f * Math.min(0.083f, player.rank * 0.0032f + 0.0138f);
			float loc3 = 0f * 2f * loc2;
			float loc4 = 0.5f * (1f - loc2 - loc3);
			float loc5 = 0.3f * (1f - loc2 - loc3);
			int loc10 = 0;
			float loc11 = (float) Math.random();

			int type = 0;
			int subtypes = 0;
			float loc9 = 0f;// subtypes
			// String name = "";
			int item = 0;
			/**
			 * 0,2,"grenades" 1,1,"ammo" 2,1,"shield" 3,37,"gun" 4,2,"sentry" 5,1,"cash"
			 */
			if (loc11 < loc2) {
				type = 4;
				subtypes = 2;
				loc9 = Math.max(1, Math.min(2f, (player.rank - 5) / 15 + 1));
				// name = "sentry";
			} else if (loc11 < loc3 + loc2) {
				type = 2;
				subtypes = 1;
				loc9 = 1;
				// name = "shield";
			} else if (loc11 < loc5 + loc3 + loc2) {
				type = 0;
				subtypes = 11;
				loc9 = 11;
				// name = "grenades";
			} else if (loc11 < loc4 + loc5 + loc3 + loc2) {
				type = 3;
				loc9 = 10;
				subtypes = 37;
				loc10 = Math.round(Math.min(player.rank + 1, 37f - loc9));
				if (loc11 > 0.8f) {
					loc11 = (loc11 - 0.8f) * 6f + 0.8f;
				}
				// name = "gun";
			} else {
				type = 5;
				loc9 = 1;
				subtypes = 1;
				// name = "cash";
			}
			int loc12 = (int) (Math.random() * loc9) + loc10;
			if (loc12 >= subtypes) {
				loc12 = subtypes - 1;
			}
			switch (type) {
			case 3:
				item = GUNS[loc12];
				break;
			case 0:
				item = GRENADES[loc12];
				break;
			case 4:
				item = SENTRIES[loc12];
				break;
			case 1:
			case 2:
			case 5:
				item = 0;
				break;
			}
			try {
				player.doubleWrite("\0" + "%xt%16%-1%" + 0 + "%" + loc7 + "%" + type + "%" + item + "%");
			} catch (IOException e) {

			}
		}
		if (room.mode == 1) {
			room.timer.schedule(new PowerupTask(room), 15000);
			return;
		}
	}
}

class Room {
	public volatile CopyOnWriteArrayList<S3ServerThread> players;
	public volatile CopyOnWriteArrayList<Integer> targets;
	public volatile boolean[] slots = new boolean[]{false,false,false,false};
	public int mode;// 1 purge 2 onslaught 3 apoc
	public int nm;
	public int id;
	public int map;
	public volatile ConcurrentHashMap<Integer, Zombie> zombies;
	public String mapURL;
	public volatile float rankSum;
	public volatile float SBEmult;
	public volatile float barriHP;
	public volatile Timer timer;
	public volatile boolean setup = false;
	public volatile boolean init = false;
	public volatile boolean r = true;
	// SPAWNER ARGS
	public volatile int p1 = 0;// §[D§
	public volatile float p2 = 0f;// §]?§
	public volatile float p3 = 0f;// §5-§
	public volatile int p4 = 24;
	public volatile int wave = 0;
	public volatile int waveTotal;
	public volatile boolean alive = true;
	public volatile int nextSpawnNum = 99;
	public volatile float totalCapacity = 0f;
	public static final AbstractZombie SKELETOR = new AbstractZombie(10, 999);
	public static final AbstractZombie NEST = new AbstractZombie(12, 999);
	public static final AbstractZombie MAMUSHKA2 = new AbstractZombie(6, 999);
	public static final AbstractZombie MAMUSHKA3 = new AbstractZombie(7, 999);
	public static final AbstractZombie MAMUSHKA4 = new AbstractZombie(8, 999);
	public static final AbstractZombie WORM = new AbstractZombie(11, 999);

	public volatile int[] spawns;
	public volatile int[] powerups;
	public volatile CopyOnWriteArrayList<Integer> nests;

	// GAME END STUFF
	private StringBuffer names;
	private StringBuffer kills;
	private StringBuffer damage;
	private StringBuffer deaths;
	private StringBuffer revives;
	private StringBuffer xp;
	private StringBuffer cash;
	private StringBuffer ranks;

	public volatile boolean end = false;
	public static final String[] MAP_URLS = new String[] { "http://sas3maps.ninjakiwi.com/sas3maps/FarmhouseMap.swf",
			"http://sas3maps.ninjakiwi.com/sas3maps/AirbaseMap.swf",
			"http://sas3maps.ninjakiwi.com/sas3maps/KarnivaleMap.swf",
			"http://sas3maps.ninjakiwi.com/sas3maps/VerdammtenstadtMap.swf",
			"http://sas3maps.ninjakiwi.com/sas3maps/BlackIsleMap.swf" };

	public Room(S3ServerThread p1, int mode, int nm) {
		players = new CopyOnWriteArrayList<S3ServerThread>();
		targets = new CopyOnWriteArrayList<Integer>();
		nests = new CopyOnWriteArrayList<Integer>();
		zombies = new ConcurrentHashMap<Integer, Zombie>(4096);
		names = new StringBuffer(100);
		kills = new StringBuffer(100);
		damage = new StringBuffer(100);
		deaths = new StringBuffer(100);
		revives = new StringBuffer(100);
		xp = new StringBuffer(100);
		cash = new StringBuffer(100);
		ranks = new StringBuffer(100);
		players.add(p1);
		this.mode = mode;
		this.nm = nm;
		this.SBEmult = 0f;
		this.map = 1 + (int) (Math.random() * 5);
		this.mapURL = MAP_URLS[map - 1];
		this.id = (int) (Math.random() * 999999999);
		timer = new Timer();
		this.powerups = powerups();
		this.spawns = spawns();
	}

	// starts the game, waves will begin after 5 seconds(constructor only
	// initializes lobby data)
	public void setup() {
		System.out.println("Starting game...");
		this.setup = true;
		this.rankSum = 0;
		for (S3ServerThread t : players) {
			this.rankSum = Math.max(t.rank, rankSum);
		}
		//this.rankSum /= (Math.pow((float)players.size(), 0.9f));
		if (this.nm != 0)
			this.SBEmult = (1f + this.rankSum / 10f) / 2f * 10f;
		else
			this.SBEmult = (1f + this.rankSum / 10f) / 2f;
		this.barriHP = (int) (600f * this.SBEmult * this.SBEmult);

		// this.waveTotal = 1;
		// writeAll("\0"+"%xt%18%-1%"+0+"%"+wave+"%"+waveTotal+"%");
		switch (mode) {
		case 1:
			waveTotal = 3;
			p4 = 7200;
			break;
		case 3:
			waveTotal = 1;
			p4 = 7200;
			break;
		default:
			if (this.rankSum >= 38)
				this.waveTotal = 11;
			else if (this.rankSum >= 30)
				this.waveTotal = 10;
			else if (this.rankSum >= 22)
				this.waveTotal = 9;
			else if (this.rankSum >= 15)
				this.waveTotal = 8;
			else if (this.rankSum >= 9)
				this.waveTotal = 7;
			else if (this.rankSum >= 5)
				this.waveTotal = 6;
			else
				this.waveTotal = 5;
			break;
		}
		timer.schedule(new WaveStartTask(this), 5000);
		// timer.schedule(new WaveEndTask(this),5000);
	}
	public int nextPlayerNum(){
		for(int i=0;i<slots.length;i++)
			if(!slots[i]){
				slots[i]=true;
				return i;
			}
		return -1;
	}
	// spawns nests at the start of a purge wave
	public void spawnNests() {
		int playerIndex = targets.get((int) (Math.random() * targets.size()));
		StringBuffer cmds = new StringBuffer(32 * wave);
		for (int i = 0; i < wave; i++) {
			int point = powerups[(int) (Math.random() * powerups.length)];
			while (nests.contains(point))
				point = powerups[(int) (Math.random() * powerups.length)];
			nests.add(point);
			String spawnCmd = "\0%xt%9%-1%" + (new Date().getTime() - players.get(0).startTime) + "%" + playerIndex + "%"
					+ NEST.index + "%" + point + "%" + SBEmult + "%" + nextSpawnNum + "%" + -1 + "%";
			zombies.put(nextSpawnNum, new Zombie(NEST.index, NEST.weight, nextSpawnNum, playerIndex, point, SBEmult));
			totalCapacity += NEST.cap;
			nextSpawnNum++;
			cmds.append(spawnCmd);
		}
		writeAll(cmds.toString());
	}

	// spawns skeletons, triggered by incoming AttackCmd with "type" 2
	public void raise(int zombieNum) {
		int playerIndex = targets.get((int) (Math.random() * targets.size()));
		int skeleCount = (int) (rankSum / 4 + 3);
		StringBuffer skeleBuffer = new StringBuffer(skeleCount * 45);
		for (int i = 0; i < skeleCount; i++) {
			float loc7 = 2f * (float) Math.PI / skeleCount * i;
			skeleBuffer.append("\0%xt%24%-1%" + (new Date().getTime() - players.get(0).startTime) + "%" + playerIndex
					+ "%" + 10 + "%-1%" + SBEmult + "%" + nextSpawnNum + "%" + zombieNum + "%"
					+ Math.round(Math.cos(loc7) * 100) + "%" + Math.round(Math.sin(loc7) * 100) + "%");
			zombies.put(nextSpawnNum, new Zombie(SKELETOR.index, SKELETOR.weight, nextSpawnNum, playerIndex, SBEmult));
			totalCapacity += SKELETOR.cap;
			nextSpawnNum++;
		}
		writeAll(skeleBuffer.toString());
	}

	// retarget all mobs to living players - used when someone dies/disconnects
	public void retargetAll(int deadNum) {
		if (players.size() == 0)
			return;
		ArrayList<StringBuffer> cmds = new ArrayList<StringBuffer>();
		for (S3ServerThread t : players) {
			if (!t.dead) {
				StringBuffer s = new StringBuffer(40 + zombies.size() * 4 / players.size());
				s.append("\0%xt%22%-1%"+(new Date().getTime() - players.get(0).startTime)+"%");
				s.append(t.myPlayerNum);
				s.append("%[");
				cmds.add(s);
			}
		}
		for (Zombie z : zombies.values()) {
			if(z.target==deadNum||deadNum==-1){
				cmds.get(z.number % cmds.size()).append("" + z.number + ",");
				z.target=players.get(z.number % cmds.size()).myPlayerNum;
			}
		}
		for (StringBuffer x : cmds) {
			x.deleteCharAt(x.length() - 1);
			x.append("]%");
			writeAll(x.toString());
		}
	}

	// end the game
	public void end(boolean win) {
		if (this.end)
			return;
		System.out.println("Ending game...");
		this.end = true;
		int xpBonus = 0;
		for (S3ServerThread player : players) {
			xpBonus += player.xp;
		}
		for (S3ServerThread player : players) {
			player.xp += (int) (xpBonus * (((float) Math.random() + 4f) / 30f));
			switch (this.map) {
			case 1:
				player.xp = (int) (1.4f * player.xp);
				break;
			case 2:
				player.xp = (int) (1.2f * player.xp);
				break;
			default:
				break;
			}
			if (this.nm != 0) {
				player.xp = (int) (1.2f * player.xp);
			}
			// $1K$
			player.xp = (int) (1f + (.1f * players.size()) * player.xp);
			if (wave > 1)
				player.xp += (int) (Math.random() * 200) + wave * 20;
			if (!win && mode != 3)
				player.xp = (int) (0.3333333f * player.xp);
			player.cash = (int) (0.2f * player.xp);
			if (mode != 1 && player.rank >= 40 && nm == 0) {
				player.xp = 0;
			}
			names.append("" + player.name + ",");
			kills.append("" + player.kills + ",");
			damage.append("" + player.damage + ",");
			deaths.append("" + player.deaths + ",");
			revives.append("" + player.revives + ",");
			xp.append("" + player.xp + ",");
			cash.append("" + player.cash + ",");
			ranks.append("" + player.rank + ",");
		}
		if (names.length() > 0 && players.size() > 0) {
			String a1 = names.deleteCharAt(names.length() - 1).toString();
			String a2 = kills.deleteCharAt(kills.length() - 1).toString();
			String a3 = damage.deleteCharAt(damage.length() - 1).toString();
			String a4 = deaths.deleteCharAt(deaths.length() - 1).toString();
			String a5 = revives.deleteCharAt(revives.length() - 1).toString();
			String a6 = xp.deleteCharAt(xp.length() - 1).toString();
			String a7 = cash.deleteCharAt(cash.length() - 1).toString();
			String a8 = ranks.deleteCharAt(ranks.length() - 1).toString();
			writeAll("\0" + "%xt%8%-1%" + (new Date().getTime() - players.get(0).startTime) + "%" + a1 + "%" + a2 + "%"
					+ a3 + "%" + a4 + "%" + a5 + "%" + a6 + "%" + a7 + "%" + a8 + "%" + (win ? 1 : 0) + "%");
			for (S3ServerThread t : players) {
				t.alive = false;
			}
		}

		this.alive = false;
		timer.cancel();
		S3Server.games.remove(this);
	}

	// checks if anyone is alive
	public boolean someAlive() {
		for (S3ServerThread t : players) {
			if (!t.dead)
				return true;
		}
		return false;
	}

	// checks if all players finished building(loading graphics)
	public boolean allReady() {
		for (S3ServerThread t : players) {
			if (!t.ready)
				return false;
		}
		return true;
	}

	// spawns multiple of the same mob from a parent(bloater/mamushka)
	public void multiSpawn(AbstractZombie z, Zombie parent, int count) {
		StringBuffer cmds = new StringBuffer(32 * count);
		int playerIndex = targets.get((int) (Math.random() * targets.size()));
		for (int i = 0; i < count; i++) {
			String spawnCmd = "\0%xt%9%-1%" + (new Date().getTime() - players.get(0).startTime) + "%" + playerIndex
					+ "%" + z.index + "%" + -1 + "%" + SBEmult + "%" + nextSpawnNum + "%" + parent.number + "%";
			zombies.put(nextSpawnNum, new Zombie(z.index, z.weight, nextSpawnNum, playerIndex, SBEmult));
			totalCapacity += z.cap;
			nextSpawnNum++;
			cmds.append(spawnCmd);
		}
		writeAll(cmds.toString());
	}

	// returns string for spawn command
	public String spawnCmd(AbstractZombie z) {
		try {
			int spawn;
			// spawns=normal spawn points, nests = nest locations
			// the locations are integers(found in the swf)
			if (mode == 1)
				spawn = nests.get((int) (Math.random() * nests.size()));
			else
				spawn = spawns[(int) (Math.random() * spawns.length)];
			int playerIndex = targets.get((int) (Math.random() * targets.size()));
			String spawnCmd = "\0%xt%9%-1%" + (new Date().getTime() - players.get(0).startTime) + "%" + playerIndex
					+ "%" + z.index + "%" + spawn + "%" + SBEmult + "%" + nextSpawnNum + "%" + -1 + "%";
			zombies.put(nextSpawnNum, new Zombie(z.index, z.weight, nextSpawnNum, playerIndex, SBEmult));
			totalCapacity += z.cap;
			nextSpawnNum++;
			return spawnCmd;
		} catch (Exception e) {
			return "";
		}

	}

	// consume a damage packet - lower hp/kill mobs etc
	public int[] parseDamage(String dmgStr, int playerNum) {
		int kills = 0;
		int damage = 0;
		int xp = 0;
		String[] damages = dmgStr.split(",");
		StringBuffer hitCmd = null;
		StringBuffer targetCmd = null;
		boolean sendHit = false;
		boolean sendTarget = false;
		boolean forgetAboutTarget=damages.length>10;
		for (String s : damages) {
			try {
				String[] params = s.split(":");
				damage += Integer.parseInt(params[1]);
				if (params.length > 2 && params[2].equals("d")) {
					xp += kill(Integer.parseInt(params[0]));
					kills++;
				} else {

					int num = Integer.parseInt(params[0]);
					// boolean
					// killed=room.damage(Integer.parseInt(params[0]),Integer.parseInt(params[1]));
					Zombie z = zombies.get(num);
					if (z != null) {
						z.hp -= Integer.parseInt(params[1]);
						if (z.hp <= 0) {
							if (hitCmd == null) {
								sendHit = true;
								hitCmd = new StringBuffer(dmgStr.length() * 3 / 2);
								hitCmd.append("\0%xt%7%-1%"+(new Date().getTime() - players.get(0).startTime)+"%"+playerNum+"%");
							}
							zombies.remove(num);
							hitCmd.append(z.number + ":999999:d,");
							totalCapacity -= z.cap;
							finishKill(z);
							xp += z.xp;
							kills++;
						} else if(!forgetAboutTarget){
							if (targetCmd == null) {
								targetCmd = new StringBuffer(dmgStr.length() / 2);
								targetCmd.append("\0%xt%22%-1%"+(new Date().getTime() - players.get(0).startTime)+"%");
								targetCmd.append(playerNum);
								targetCmd.append("%[");
								
							}
							z.target=playerNum;
							sendTarget = true;
							targetCmd.append("" + z.number + ",");
						}
						zombies.replace(num, z);
					}
				}
			} catch (Exception e) {
				continue;
			}
		}
		// forcibly kill mobs that should have been killed by aoe
		if (sendHit) {
			hitCmd.deleteCharAt(hitCmd.length() - 1);
			hitCmd.append("%");
			writeAll(hitCmd.toString());
		}
		// mobs attack the player that shot them
		if (sendTarget) {
			for (S3ServerThread t : players)
				if (t.myPlayerNum == playerNum && !t.dead) {
					targetCmd.deleteCharAt(targetCmd.length() - 1);
					targetCmd.append("]%");
					writeAll(targetCmd.toString());
					break;
				}
		}
		return new int[] { kills, damage, xp };
	}

	// used by parsedamage
	public int kill(int number) {
		Zombie z1 = null;
		z1 = zombies.get(number);
		if (z1 != null) {
			totalCapacity -= z1.cap;
			z1.hp = 0;
			zombies.remove(z1.number);
			finishKill(z1);
			return z1.xp;
		}
		return 0;
	}

	// used by parsedamage
	public void finishKill(Zombie z1) {
		if (z1.index == 5) {
			multiSpawn(MAMUSHKA2, z1, 2);
		} else if (z1.index == 6) {
			multiSpawn(MAMUSHKA3, z1, 2);
		} else if (z1.index == 7) {
			multiSpawn(MAMUSHKA4, z1, 2);
		} else if (z1.index == 3) {
			multiSpawn(WORM, z1, 5);
		} else if (z1.index == 12) {
			nests.remove((Integer) z1.parent);
			if (nests.size() == 0) {
				r = true;
				timer.schedule(new WaveEndTask(this), 1);
			}
		} else {
			// System.out.println("%" + p1);
			if (init && p1 >= p4 && !(bracket3(3)) && !r)

			{
				if (S3Server.verbose)
					System.out.println("||||||Wave end");
				r = true;
				timer.schedule(new WaveEndTask(this), 1);

			}
		}

	}

	// from SWF - checks if can end wave
	public boolean bracket3(int max) {
		return totalCapacity > max;
	}

	// see $[Q$/$0$
	public int[] spawns() {
		switch (this.map) {
		case 1:
			return new int[] { 9, 6, 5, 8, 11, 10, 4 };
		case 2:
			return new int[] { 18, 17, 16, 13, 12, 11, 15, 14, 19 };
		case 3:
			return new int[] { 31, 37, 38, 35, 34, 33, 32, 36 };
		case 4:
			return new int[] { 13, 12, 14, 31 };
		case 5:
			return new int[] { 43, 42, 41, 40, 47, 7, 46, 44, 9, 17, 4, 45 };
		default:
			return new int[] {};
		}
	}

	// see $[Q$/$0$
	public int[] powerups() {
		switch (this.map) {
		case 1:
			return new int[] { 0, 1, 2, 3 };
		case 2:
			return new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
		case 3:
			return new int[] { 1, 3, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27,
					28, 29, 30 };
		case 4:
			return new int[] { 0, 2, 3, 6, 7, 12, 13, 14, 15, 17, 21, 26, 33, 35, 37, 38 };
		case 5:
			return new int[] { 0, 1, 2, 3, 5, 8, 11, 12, 15, 21, 25, 32, 33, 34, 36, 37 };
		default:
			return new int[] {};
		}
	}

	// remove a player
	public void dropPlayer(int id) {
		int index=-1;
		for (S3ServerThread t : players) {
			if (t.id == id) {
				
				index=t.myPlayerNum;
				writeFrom(id, "\0" + "%xt%21%-1%" + 0 + "%" + t.myPlayerNum + "%");
				if (!end && setup) {
					names.append("" + t.name + "(disconnected),");
					kills.append("" + t.kills + ",");
					damage.append("" + t.damage + ",");
					deaths.append("" + (t.deaths + 1) + ",");
					revives.append("" + t.revives + ",");
					xp.append("0,");
					cash.append("0,");
					ranks.append("" + t.rank + ",");
				}
				players.remove(t);
				targets.remove((Integer) (t.myPlayerNum));
				break;
			}
		}
		if(index==-1)
			return;
		slots[index]=false;
		if (players.size() == 0) {
			this.alive = false;
			timer.cancel();
			S3Server.games.remove(this);
		}
		if (!this.someAlive())
			this.end(false);
		else {
			retargetAll(index);
			ArrayList<String> playerNames = new ArrayList<String>();
			ArrayList<Integer> playerRanks = new ArrayList<Integer>();
			ArrayList<Boolean> readyList = new ArrayList<Boolean>();
			players.forEach(x -> {
				playerNames.add("\"" + x.name + "\"");
				playerRanks.add(x.rank);
				readyList.add(x.ready);
			});
			for (S3ServerThread t : players) {
				try {
					t.doubleWrite(
							"\0" + "%xt%15%" + 0 + "%-1%1%" + players.size() + "%" + t.myPlayerNum + "%" + playerNames
									+ "%" + playerRanks + "%" + readyList + "%1%" + mode + "%" + (mode) + "%" + map);
				} catch (IOException e) {

				}
			}
			// if(setup)
			// writeAll("\0%xt%11%-1%" + (new Date().getTime() - players.get(0).startTime) +
			// "%0%" + index + "%"
			// + 999999999 + "%");
		}
	}

	// something wave related idk
	public float dashL(float param1) {
		float loc2 = 0.0f;
		float loc3 = (this.nm == 0) ? 0.9f : 2.5f;
		float loc4 = 0.0f;
		float loc5 = 0.0f;
		switch (this.map) {
		case 1:
			loc2 = 0.65f;
			break;
		case 2:
		case 3:
			loc2 = 0.75f;
			break;
		case 5:
			loc2 = 1.1f;
			break;
		default:
			loc2 = 1.0f;
			break;
		}
		if (param1 > 45f) {
			loc4 = 45f;
			loc5 = param1 - 45f;
		} else {
			loc4 = param1;
			loc5 = 0f;
		}
		float loc6 = 10f - (loc4 - 10f) / 7f;
		float loc7 = loc4 / 18f;
		float loc8 = (float) Math.pow(loc6, loc7) * 1.2f;
		float loc9 = (float) Math.pow(loc5, 1.5f) * 5.2f;
		return (loc8 + loc9) / 4f * (players.size()) * loc2 * loc3;
	}

	// something wave related idk
	public float commaHash() {
		return this.rankSum + (this.p1 + (float) p4 * (float) this.wave)
				/ ((float) p4 * (float) this.waveTotal) * 10f * ((this.waveTotal - 4) / 12f + 1f);
	}

	public void writeFrom(int id, String toWrite) {
		players.forEach(x -> {
			if (x.id != id) {
				try {
					x.doubleWrite(toWrite);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

	}

	public int indexOf(int id2) {
		for (int i = 0; i < players.size(); i++) {
			if (players.get(i).id == id2)
				return i;
		}
		return -1;
	}

	public void writeAll(String toWrite) {
		players.forEach(x -> {
			try {
				x.doubleWrite(toWrite);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

	}

	// h
	public void h() {

	}
}

//class for main method
public class S3Server {
	//
	public static boolean verbose = false;
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
		try {

			server = new ServerSocket(port);
		} catch (IOException e) {
			System.out.println(
					"error. Please ensure the port is available");
			return;
		}

		// server loop(only ends on ctrl-c)
		ArrayList<S3ServerThread> threads = new ArrayList<S3ServerThread>();
		try {
			server.setSoTimeout(1000);
		} catch (Exception eeeeeee) {
			System.out.println("???");
		}

		System.out.println("SAS3 Server Started! Listening for connections...");
		while (true) {

			Socket client = null;
			try {
				client = server.accept();
				System.out.println("accept???");
				// client.setTcpNoDelay(true);
				ipThreads = new ConcurrentHashMap<InetAddress, Integer>();
				// for (S3ServerThread l:threads){System.out.println(l.isWebSocket);}
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
					// output.write("ServerMessage,Service unavailable. Please report".getBytes());
					output.flush();
					output.close();
					client.close();
					continue;
				}
				S3ServerThread connection = new S3ServerThread(client);
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
					threads = new ArrayList<S3ServerThread>();
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
