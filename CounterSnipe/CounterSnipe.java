import java.util.ArrayList;
import java.util.Arrays;
import java.net.InetAddress;
import java.net.Socket;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.net.ServerSocket;

/**
 * Countersnipe Server
 * 
 * The simplest server. It uses smartfox just like sas3, and the server still
 * has to verify hits, but the protocol is just much simpler in general so it
 * doesn't matter
 *
 * Also features bots(they join after queue for 30s)
 * 
 * (although a few things didnt work properly so i just modded the swf to make
 * them work lol
 *
 * changes: in "class_12"(the one outside packages) we duplicate the if
 * statement with if(something.running) to also include the winning timer event/
 * the winning equivalent of the method called there and in screens_Login we
 * change the frames from 44/45 to 59 and 60 and remove the stop statement from
 * one of them then ip/port change as usual
 * smartfox smartConnect -> false (bluebox not implemented)
 * 
 * 
 */
class FilePolicyServer extends Thread {
	/**
	 * Flash requests a "cross domain policy" xml whenever contacting a server. It
	 * is probably already started by the sas4 server(they both use port 843)
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
						this.alive = false;
						break;
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
			if (CounterSnipe.verbose)
				e.printStackTrace();
		}
	}
}

class ServerThread extends Thread {
	/**
	 * Server threads accept connections, then link to an opponent's thread
	 */
	public Socket client = null;
	OutputStream output;
	public volatile boolean alive;// thread alive/dead
	public volatile int id;
	public volatile ServerThread opponent = null;

	public volatile String name;
	public volatile int rank;
	public volatile int gun;
	public volatile int pfp;
	public volatile int execPoint;
	public volatile int enemyPoint;
	public volatile int map;
	public volatile int suppressor;
	public volatile int damage;
	public volatile int round = 1;
	public volatile int score = 0;

	public volatile int enemyX;
	public volatile int enemyY;
	public volatile int execX;
	public volatile int execY;
	public boolean side;
//	public volatile double execHp; assume he just gets 1shot?
	public volatile int hp;// 100
	public volatile boolean roundActive = true;

	public volatile Timer timer;

	public static int getDamage(int gun) {
		switch (gun - 1) {
		case 0:
			return 50;
		case 1:
			return 40;
		case 2:
			return 50;
		case 3:
			return 25;
		case 4:
			return 50;
		case 5:
			return 75;
		case 6:
			return 75;
		case 7:
			return 100;
		case 8:
			return 20;
		case 9:
			return 200;
		default:
			return 0;
		}
	}

	public void hit(double x, double y) throws IOException {
		int dmg = (int) hit(x, y, execX, execY);
		if (!roundActive)
			return;
		if (dmg > 0) {
			doubleWrite("\0%xt%9%-1%" + opponent.name + "%" + name + "%");
			opponent.doubleWrite("\0%xt%9%-1%" + opponent.name + "%" + name + "%");
			opponent.doubleWrite("\0%xt%10%-1%" + opponent.name + "%" + name + "%%%" + opponent.gun + "%" + 0 + "%"
					+ opponent.suppressor + "%");
			doubleWrite("\0%xt%10%-1%" + opponent.name + "%" + name + "%%%" + opponent.gun + "%" + 0 + "%"
					+ opponent.suppressor + "%");
			return;
		}
		dmg = (int) hit(x, y, enemyX, enemyY);
		if (dmg > 0) {
			hp -= (dmg);
			// do stuff
			if (hp <= 0) {
				// doubleWrite("\0%xt%8%-1%"+name+"%"+opponent.name+"%"+opponent.name+"%"+name+"%"+opponent.gun+"%"+dmg+"%"+0+"%"+round+"%"+opponent.suppressor+"%");
				// opponent.doubleWrite("\0%xt%8%-1%"+opponent.name+"%"+name+"%"+opponent.name+"%"+name+"%"+opponent.gun+"%"+dmg+"%"+0+"%"+round+"%"+opponent.suppressor+"%");
				this.roundActive = false;
				opponent.roundActive = false;
				startRound();
				opponent.score++;
				int match = 0;
				if (opponent.score >= 3 || round == 5)
					match = 1;
				doubleWrite("\0%xt%8%-1%" + name + "%" + opponent.name + "%" + opponent.name + "%" + name + "%"
						+ opponent.gun + "%" + dmg + "%" + match + "%" + round + "%" + opponent.suppressor + "%");
				opponent.doubleWrite("\0%xt%8%-1%" + opponent.name + "%" + name + "%" + opponent.name + "%" + name + "%"
						+ opponent.gun + "%" + dmg + "%" + match + "%" + round + "%" + opponent.suppressor + "%");
				if (match == 0) {
					String start = null;
					if (opponent.side) {
						start = "\0%xt%4%-1%" + enemyPoint + "%" + execPoint + "%" + map + "%" + opponent.name + "%"
								+ name + "%" + opponent.pfp + "%" + pfp + "%" + opponent.gun + "%" + gun + "%"
								+ opponent.rank + "%" + rank + "%";
					} else {
						start = "\0%xt%4%-1%" + enemyPoint + "%" + execPoint + "%" + map + "%" + name + "%"
								+ opponent.name + "%" + pfp + "%" + opponent.pfp + "%" + gun + "%" + opponent.gun + "%"
								+ rank + "%" + opponent.rank + "%";
					}
					timer.schedule(new DelayedWriteTask(this, start), 2000);
					opponent.timer.schedule(new DelayedWriteTask(opponent, start), 2000);
				}
				if (match == 1) {
					this.alive = false;
					opponent.alive = false;
				}
				round++;
				opponent.round++;
			} else {
				opponent.doubleWrite("\0%xt%8%-1%" + opponent.name + "%" + name + "%%%" + opponent.gun + "%" + dmg
						+ "%0%" + round + "%" + opponent.suppressor + "%");
				doubleWrite("\0%xt%8%-1%" + name + "%" + opponent.name + "%%%" + opponent.gun + "%" + dmg + "%0%"
						+ round + "%" + opponent.suppressor + "%");
				writeAll("\0%xt%10%-1%" + opponent.name + "%" + name + "%%%" + opponent.gun + "%" + 0 + "%"
						+ opponent.suppressor + "%");
			}

		} else {
			writeAll("\0%xt%10%-1%" + opponent.name + "%" + name + "%%%" + opponent.gun + "%" + 0 + "%"
					+ opponent.suppressor + "%");
			// miss
		}
	}

	private boolean hitScan1(double x, double y, double eX, double eY) {
		double x1 = -3 + eX;
		double x2 = 3 + eX;
		double y1 = -8 + eY + 11;
		double y2 = 8 + eY + 11;
		if (x >= x1 && x <= x2 && y >= y1 && y <= y2) {
			return true;
		}
		return false;
	}

	private boolean hitScan2(double x, double y, double eX, double eY) {
		double x1 = -4 + eX;
		double x2 = 4 + eX;
		double y1 = -8 + eY + 11;
		double y2 = 10 + eY + 11;
		if (x >= x1 && x <= x2 && y >= y1 && y <= y2) {
			return true;
		}
		return false;
	}

	public double hit(double x, double y, double eX, double eY) {
		double dx = x - eX;
		double dy = y - eY;
		double damage = opponent.damage;
		if (opponent.suppressor == 2)
			damage *= 0.8;
		if ((Math.sqrt(dx * dx + dy * dy)) <= 3)
			return damage * 2;
		if (hitScan1(x, y, eX, eY))
			return damage;
		if (hitScan2(x, y, eX, eY))
			return damage * 0.5;

		return 0;
	}

	public void doubleWrite(String s) throws IOException {
		this.output.write(s.getBytes(StandardCharsets.UTF_8));
		// add a dummy message after, otherwise it gets ignored(idk why)
		this.output.write("\0%xt%3%-1%".getBytes(StandardCharsets.UTF_8));
		this.output.flush();
		if (CounterSnipe.verbose)
			System.out.println("OUT: " + s);
	}

	public void writeAll(String s) throws IOException {
		this.opponent.doubleWrite(s);
		this.doubleWrite(s);
	}

	// constructor initializes socket
	public ServerThread(Socket socket) {
		this.client = socket;
		this.alive = true;
		this.name = null;
		timer = new Timer();
		id = (int) (Math.random() * 999999999);
		while (CounterSnipe.names.contains(id))
			id = (int) (Math.random() * 999999999);
		CounterSnipe.names.add(id);
		this.rank = 0;
		this.name = "Hydar" + id;
		if (client != null) {
			try {
				this.output = this.client.getOutputStream();
			} catch (IOException e) {
				this.alive = false;
			}
		}

	}

	public void startGame() throws IOException {
		this.opponent.opponent = this;
		this.opponent.side = true;
		this.side = false;
		startRound();
		writeAll("\0%xt%6%-1%" + enemyPoint + "%" + execPoint + "%" + map + "%" + opponent.name + "%" + name + "%"
				+ opponent.pfp + "%" + pfp + "%" + opponent.gun + "%" + gun + "%" + opponent.rank + "%" + rank + "%");
		writeAll("\0%xt%7%1%");
	}

	public void startRound() {
		this.execPoint = (int) (Math.random() * 94);
		this.enemyPoint = (int) (Math.random() * 94);
		while (enemyPoint == execPoint)
			enemyPoint = (int) (Math.random() * 94);
		this.map = (int) (1 + Math.random() * 3);
		opponent.execPoint = execPoint;
		opponent.enemyPoint = enemyPoint;
		opponent.map = map;
		hp = 100;
		opponent.hp = 100;
	}

	@Override
	public void run() {
		try {
			this.client.setSoTimeout(1000);
			InputStream input = this.client.getInputStream();
			InputStreamReader ir = new InputStreamReader(input, Charset.forName("UTF-8"));
			String headers = "";
			int timeouts = 0;
			String toSend = "";
			int sendAfter = 0;
			boolean hydar = true;
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
					this.alive = false;
					break;
				}
				if (actualSize > 0) {
					headers = new String(buffer).trim();
					timeouts = 0;
					toSend = "";
					if (CounterSnipe.verbose)
						System.out.println("incoming: " + headers);
					// messages are usually null terminated
					String[] msgs = headers.split("\0");
					for (String m : msgs) {
						if (!m.endsWith("%"))
							m += "%";
						if (!m.startsWith("%"))
							m = "%" + m;
					}

					for (String m : msgs) {
						// "extension message"
						/**
						 * => client->server <= server->client
						 *
						 * 1=>queue 2=>loaded + enemy/executive data 3=>shoot 4<=start round 5<=>error
						 * 6<=start game 7<=countdown 8<=hit(server response to 3) 9<=executive killed
						 * 10<=miss(another response to 3) 11<=also an error??? 14=>request private
						 * match creation 15<=private match id 16<=guest name(from server) 17<=opponent
						 * disconnected
						 */
						if (m.startsWith("%xt%sniperExt%")) {
							ArrayList<String> msg = new ArrayList<String>(Arrays.asList(m.split("%")));
							// if(msg.size()<6)
							// continue;
							int cmd = Integer.parseInt(msg.get(3));
							switch (cmd) {
							case 1:
								// perk 3 most likely suppressor(ms.get5)
								//
								// enemyPoint execPoint tehMap p1_txt p2_txt p1_inner p2_inner gun1 gun2 rank
								// rank2
								gun = Integer.parseInt(msg.get(6));
								suppressor = Integer.parseInt(msg.get(7));
								pfp = Integer.parseInt(msg.get(8));
								damage = getDamage(gun);
								rank = Integer.parseInt(msg.get(10));
								synchronized (CounterSnipe.lock) {
									if (CounterSnipe.queue.size() == 0) {
										CounterSnipe.queue.add(this);
									} else {
										this.opponent = CounterSnipe.queue.get(0);
										CounterSnipe.queue.remove(this.opponent);
										this.startGame();
									}
								}
								// doubleWrite("\0%xt%6%-1%1%1%1%Hydar%Hydar2%1%1%1%1%1%1%");
								break;
							case 2:
								// """endCache()"""
								enemyX = Integer.parseInt(msg.get(6));
								enemyY = Integer.parseInt(msg.get(7));
								execX = Integer.parseInt(msg.get(8));
								execY = Integer.parseInt(msg.get(10));
								opponent.enemyX = enemyX;
								opponent.enemyY = enemyY;
								opponent.execX = execX;
								opponent.execY = execY;
								// doubleWrite("\0%xt%7%1%");
								break;
							case 3:
								// doubleWrite("\0%xt%6%-1%1%1%1%Hydar%Hydar2%1%1%1%1%1%1%");
								// doubleWrite("\0%xt%7%1%1%");
								System.out.println(opponent);
								synchronized (this) {
									synchronized (opponent) {
										opponent.hit(Integer.parseInt(msg.get(6)), Integer.parseInt(msg.get(7)));
									}
								}
								break;
							case 5:
								opponent.doubleWrite("\0%xt%17%-1%0%");
								this.alive = false;
								break;
							case 14:
								doubleWrite(
										"\0%xt%15%-1%DON'T USE THIS, just click \"Multiplayer\" to play a public game. A bot will join after 30s if no one joins%");
								break;
							}

						}
						// smartfox setup(hard coded) - dont think most of it matters
						else if (m.equals("<msg t='sys'><body action='verChk' r='0'><ver v='161' /></body></msg>"))
							doubleWrite("\0<msg t='sys'><body action='apiOK' r='0'><ver v='161'/></body></msg>\n");
						else if (m.equals(
								"<msg t='sys'><body action='login' r='0'><login z='SniperZone'><nick><![CDATA[]]></nick><pword><![CDATA[]]></pword></login></body></msg>")) {
							doubleWrite("\0<msg t='sys'><body action='logOK' r='0'><login id='" + id
									+ "' mod='0' n='SniperZone'/></body></msg>\n");
							doubleWrite("\0%xt%16%0%1%" + name + "%");
						} else if (m.equals("<msg t='sys'><body action='getRmList' r='-1'></body></msg>"))
							doubleWrite(
									"\0<msg t='sys'><body action='rmList' r='-1'><rmList><rm id='0' maxu='2' maxs='2' temp='0' game='1' priv='0' lmb='0' ucnt='0' scnt='0'><n>Main Lobby</n></rm></rmList></body></msg>");
						else if (m.equals("<msg t='sys'><body action='autoJoin' r='-1'></body></msg>"))
							doubleWrite(
									"\0<msg t='sys'><body action='joinOK' r='0'><pid id='0'></pid><uLs><u></u></uLs></body></msg>");

						else
							timeouts += 40;
					}
					try {
						Thread.sleep(1);
					} catch (InterruptedException ee) {
						Thread.currentThread().interrupt();
					}
				} else {
					timeouts++;
					if (timeouts > 30 && this.opponent == null && CounterSnipe.queue.contains(this)) {
						this.opponent = new BotServerThread();
						this.startGame();
					} else if (timeouts > 120) {
						doubleWrite("\0%xt%5%-1%");
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
		synchronized (CounterSnipe.lock) {
			if (CounterSnipe.queue.contains(this))
				CounterSnipe.queue.remove(this);
		}
		CounterSnipe.names.remove((Integer) id);
		try {
			if (opponent != null)
				opponent.doubleWrite("\0%xt%17%-1%0%");
		} catch (Exception e) {

		}
		timer.cancel();
		return;
	}
}

class BotServerThread extends ServerThread {
	private double difficulty;// affects 1st shot time
	protected double delayMod;// affects time for shots after 1st

	public BotServerThread() {
		super(null);
		this.name = "BOT_" + name;
		this.gun = (int) (1 + Math.random() * 10);
		this.suppressor = 0;
		this.pfp = 14;
		if (Math.random() > 0.5) {
			this.pfp = 22 + (int) (Math.random() * 7);
		}
		this.damage = getDamage(gun);
		this.rank = gun;
		difficulty = 0.5 + (10.0 - (double) gun) / 20.0;
		switch (this.gun) {
		case 9:
			delayMod = 0.1;
			break;
		case 2:
		case 4:
		case 6:
			delayMod = 0.5;
			break;
		case 8:
		case 5:
			delayMod = 1.5;
			break;
		default:
			delayMod = 1.0;
			break;
		}

	}

	@Override
	public void doubleWrite(String s) {
		int cmd = Integer.parseInt(s.split("%")[2]);
		switch (cmd) {
		case 4:
		case 6:
			timer.cancel();
			timer.purge();
			timer = new Timer();
			timer.schedule(new BotFireTask(this), (10000 + (int) (Math.random() * 40000 * difficulty)));
			return;
		case 5:
		case 17:
			this.alive = false;
			timer.cancel();
			return;
		case 8:
		case 10:
			if (Math.random() < (0.7 - ((double)opponent.suppressor/10.0))) {// if you shoot/miss, bot will shoot sooner
				timer.cancel();
				timer.purge();
				timer = new Timer();
				int delay = (int) ((3000.0 + Math.random() * 1000.0) * delayMod);
				timer.schedule(new BotFireTask(this), delay);
			}
			return;
		}
	}

	@Override
	public void run() {
		this.alive = false;
	}
}

class BotFireTask extends TimerTask {
	public BotServerThread thread;

	public BotFireTask(BotServerThread t) {
		this.thread = t;
	}

	@Override
	public void run() {
		try {
			double action = Math.random();
			synchronized (thread) {
				synchronized (thread.opponent) {
					if (action > 0.85) {
						thread.opponent.hit(thread.opponent.execX, thread.opponent.execY);
					} else if (action > 0.15) {
						thread.opponent.hit(thread.opponent.enemyX, thread.opponent.enemyY);
					} else {
						thread.opponent.hit(thread.opponent.enemyX + 50, thread.opponent.enemyY);
					}
					int delay = (int) ((3000.0 + Math.random() * 1000.0) * thread.delayMod);
					thread.timer.schedule(new BotFireTask(thread), delay);
				}
			}
		} catch (IOException e) {

		}
	}
}

class DelayedWriteTask extends TimerTask {
	public ServerThread thread;
	public String string;

	public DelayedWriteTask(ServerThread s, String t) {
		this.thread = s;
		this.string = t;
	}

	@Override
	public void run() {
		try {
			thread.doubleWrite(string);
			thread.roundActive = true;
		} catch (IOException e) {

		}
	}
}

//class for main method
public class CounterSnipe {
	//
	public static volatile String ip = "";
	public static boolean verbose = true;
	public static volatile ConcurrentHashMap<InetAddress, Integer> ipThreads;
	public static volatile ArrayList<ServerThread> games;
	public static volatile ArrayList<ServerThread> queue;
	public static volatile ArrayList<Integer> names;
	public static Object lock = "";

	public static void main(String[] args) {

		CounterSnipe.games = new ArrayList<ServerThread>();
		CounterSnipe.queue = new ArrayList<ServerThread>();
		names = new ArrayList<Integer>();
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
			FilePolicyServer fps = new FilePolicyServer(843);
			new Thread(fps).start();
		} catch (Exception e) {
			System.out.println("policy server not started");
			// might already be running
		}
		try {
			ip = Files.readString(Paths.get("./config.txt")).trim();

			server = new ServerSocket(port);
		} catch (IOException e) {
			System.out.println(
					"error. Please ensure the port is available and config.txt exists(put IP or \"localhost\" there)");
			return;
		}

		// server loop(only ends on ctrl-c)
		ArrayList<ServerThread> threads = new ArrayList<ServerThread>();
		try {
			server.setSoTimeout(1000);
		} catch (Exception eeeeeee) {
			System.out.println("???");
		}

		System.out.println("Countersnipe Server Started! Listening for connections...");
		while (true) {

			Socket client = null;
			try {
				client = server.accept();
				// client.setTcpNoDelay(true);
				System.out.println("accept???");
				client.setTcpNoDelay(true);
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
					// output.write("ServerMessage,Service unavailable. Please report".getBytes());
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
