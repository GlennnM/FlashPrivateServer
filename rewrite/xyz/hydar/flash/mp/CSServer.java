package xyz.hydar.flash.mp;
import static xyz.hydar.flash.mp.FlashLauncher.CONFIG;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import xyz.hydar.net.ClientContext;
import xyz.hydar.net.ClientOptions;
import xyz.hydar.net.ServerContext;
import xyz.hydar.net.TextClientContext;

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
class CSServerThread extends TextClientContext {
	/**
	 * Server threads accept connections, then link to an opponent's thread
	 */
	public final int id = CSServer.nextName.addAndGet(ThreadLocalRandom.current().nextInt(1000,10000));
	public final String name;

	public volatile int rank;
	public volatile int gun;
	public volatile int pfp;
	public volatile int suppressor;
	public volatile int damage;
	
	public volatile CSGame game;
	public volatile CSServerThread opponent = null;
	public volatile boolean side;
	public volatile int score = 0;
	public volatile int hp;// 100
	static boolean hydar = true;// hydar

	public static final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
		@Override
		public Thread newThread(Runnable r) {
			return Thread.ofPlatform().name("CS room tasks").unstarted(r);
		}
	});
	// constructor initializes socket
	public CSServerThread() {
		super(StandardCharsets.ISO_8859_1,'\0',CONFIG.CS);
		this.name = "Hydar" + id;
	}
	//extra constructor for bots
	CSServerThread(Void amBot) {
		super(StandardCharsets.ISO_8859_1,'\0',ClientOptions.NONE);
		this.name = "BOT_hydar" + id;
	}
	public static int getDamage(int gun) {
		return switch (gun - 1) {
		case 0 -> 50;
		case 1 -> 40;
		case 2 -> 50;
		case 3 -> 25;
		case 4 -> 50;
		case 5 -> 75;
		case 6 -> 75;
		case 7 -> 100;
		case 8 -> 20;
		case 9 -> 200;
		default -> 0;
		};
	}
	/**Parse a hit packet - do damage, end round if needed.
	 * Must always run synchronized by game.hitLock.*/
	public void hit(int x, int y){
		int dmg = (int) getDamage(x, y, game.execX, game.execY);
		if (game==null||!game.roundActive)
			return;
		if (dmg > 0) {
			writeAll("%xt%9%-1%" + opponent.name + "%" + name + "%\0");
			writeAll("%xt%10%-1%" + opponent.name + "%" + name + "%%%" + opponent.gun + "%" + 0 + "%"
					+ opponent.suppressor + "%\0");
			return;
		}
		dmg = (int) getDamage(x, y, game.enemyX, game.enemyY);
		if (dmg > 0) {
			hp -= (dmg);
			// do stuff
			if (hp <= 0) {
				game.roundActive=false;
				// doubleWrite("\0%xt%8%-1%"+name+"%"+opponent.name+"%"+opponent.name+"%"+name+"%"+opponent.gun+"%"+dmg+"%"+0+"%"+round+"%"+opponent.suppressor+"%");
				// opponent.doubleWrite("\0%xt%8%-1%"+opponent.name+"%"+name+"%"+opponent.name+"%"+name+"%"+opponent.gun+"%"+dmg+"%"+0+"%"+round+"%"+opponent.suppressor+"%");
				game.setupRound();
				opponent.score++;
				int match = (opponent.score >= 3 || game.round == 5)?1:0;
				doubleWrite("%xt%8%-1%" + name + "%" + opponent.name + "%" + opponent.name + "%" + name + "%"
						+ opponent.gun + "%" + dmg + "%" + match + "%" + game.round + "%" + opponent.suppressor + "%\0");
				opponent.doubleWrite("%xt%8%-1%" + opponent.name + "%" + name + "%" + opponent.name + "%" + name + "%"
						+ opponent.gun + "%" + dmg + "%" + match + "%" + game.round + "%" + opponent.suppressor + "%\0");
				if (match == 0) {
					timer.schedule(game::startRound, 2000,TimeUnit.MILLISECONDS);
					game.round++;
				}else{
					game.alive=false;
					System.out.println("ending match");
				}
			} else {
				opponent.doubleWrite("%xt%8%-1%" + opponent.name + "%" + name + "%%%" + opponent.gun + "%" + dmg
						+ "%0%" + game.round + "%" + opponent.suppressor + "%\0");
				doubleWrite("%xt%8%-1%" + name + "%" + opponent.name + "%%%" + opponent.gun + "%" + dmg + "%0%"
						+ game.round + "%" + opponent.suppressor + "%\0");
				writeAll("%xt%10%-1%" + opponent.name + "%" + name + "%%%" + opponent.gun + "%" + 0 + "%"
						+ opponent.suppressor + "%\0");
			}

		} else {
			writeAll("%xt%10%-1%" + opponent.name + "%" + name + "%%%" + opponent.gun + "%" + 0 + "%"
					+ opponent.suppressor + "%\0");
			// miss
		}
	}
	//True when x,y hits the player at eX,eY and it doesn't hit legs/arms.
	//(adapted from SP behavior)
	private static boolean hitScan1(double x, double y, double eX, double eY) {
		double x1 = -3 + eX;
		double x2 = 3 + eX;
		double y1 = -8 + eY + 11;
		double y2 = 8 + eY + 11;
		return (x >= x1 && x <= x2 && y >= y1 && y <= y2);
	}
	//True when x,y hits the player at eX,eY.
	//(adapted from SP behavior)
	private static boolean hitScan2(double x, double y, double eX, double eY) {
		double x1 = -4 + eX;
		double x2 = 4 + eX;
		double y1 = -8 + eY + 11;
		double y2 = 10 + eY + 11;
		return (x >= x1 && x <= x2 && y >= y1 && y <= y2);
	}
	//Return the damage dealt to the player at eX,eY by a shot from this.opponent at x,y.
	public double getDamage(int x, int y, int eX, int eY) {
		int dx = x - eX;
		int dy = y - eY;
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
	public void doubleWrite(String s){
		send(s);
		if (CSServer.verbose)
			System.out.println("OUT: " + s);
	}
	public void writeAll(String s){
		this.opponent.doubleWrite(s);
		this.doubleWrite(s);
	}
	@Override
	public void onMessage(String m) {
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
		if(game==null||game.alive)timeouts=0;
		if(CSServer.verbose)
			System.out.println("IN: "+m);
		if (m.startsWith("%xt%sniperExt%")) {
			List<String> msg = Arrays.asList(m.split("%"));
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
				if ((opponent=CSServer.queue.poll()) == null)
					CSServer.queue.add(this);
				else
					game=new CSGame(this,opponent);
				break;
			case 2:
				// """endCache()"""
				game.enemyX = Integer.parseInt(msg.get(6));
				game.enemyY = Integer.parseInt(msg.get(7));
				game.execX = Integer.parseInt(msg.get(8));
				game.execY = Integer.parseInt(msg.get(10));
				break;
			case 3:
				game.hitLock.lock();
				try {
					opponent.hit(Integer.parseInt(msg.get(6)), Integer.parseInt(msg.get(7)));
				}finally {
					game.hitLock.unlock();
				}
				break;
			case 5:
				if(game!=null)game.alive=false;
				break;
			case 14:
				doubleWrite(
						"%xt%15%-1%DON'T USE THIS, just click \"Multiplayer\" to play a public game. A bot will join after 30s if no one joins%\0");
				break;
			}

		}
		// smartfox setup(hard coded) - none of it matters
		else if (m.equals("<msg t='sys'><body action='verChk' r='0'><ver v='161' /></body></msg>"))
			doubleWrite("<msg t='sys'><body action='apiOK' r='0'><ver v='161'/></body></msg>\0");
		else if (m.equals(
				"<msg t='sys'><body action='login' r='0'><login z='SniperZone'><nick><![CDATA[]]></nick><pword><![CDATA[]]></pword></login></body></msg>")) {
			doubleWrite("<msg t='sys'><body action='logOK' r='0'><login id='" + id
					+ "' mod='0' n='SniperZone'/></body></msg>\0");
			doubleWrite("%xt%16%0%1%" + name + "%\0");
		} else if (m.equals("<msg t='sys'><body action='getRmList' r='-1'></body></msg>"))
			doubleWrite(
					"<msg t='sys'><body action='rmList' r='-1'><rmList><rm id='0' maxu='2' maxs='2' temp='0' game='1' priv='0' lmb='0' ucnt='0' scnt='0'><n>Main Lobby</n></rm></rmList></body></msg>\0");
		else if (m.equals("<msg t='sys'><body action='autoJoin' r='-1'></body></msg>"))
			doubleWrite(
					"<msg t='sys'><body action='joinOK' r='0'><pid id='0'></pid><uLs><u></u></uLs></body></msg>\0");
		else
			close();
	}
	@Override
	public void onOpen(){
		CONFIG.acquire(this);
		
	}
	private int timeouts;
	@Override
	public void onTimeout() {
		if (++timeouts > 30 && this.opponent == null && CSServer.queue.remove(this)) {
			this.opponent = new BotCSServerThread();
			this.game=new CSGame(this, opponent);
		} else if (timeouts > 120) {
			doubleWrite("%xt%5%-1%\0");
			close();
		}
	}
	@Override
	public void onClose(){
		CSServer.queue.remove(this);
		if (opponent != null)
			opponent.doubleWrite("%xt%17%-1%0%\0");
		System.out.println("ending thread");
		if(game!=null)game.alive=false;
		CONFIG.release(this);
	}
}
/**Contains objects common to both players, including the lock for hits.<br>
 * If a player dies before their shot registers, roundActive will become false in the locked segment<br>
 * leading to the second shot doing nothing.
 * */
class CSGame{
	public volatile int execPoint;
	public volatile int enemyPoint;
	public volatile int enemyX;
	public volatile int enemyY;
	public volatile int execX;
	public volatile int execY;
	public volatile int map;
	public volatile boolean roundActive=true;
	public volatile int round = 1;
	public final ReentrantLock hitLock=new ReentrantLock();
	public volatile boolean alive=true;
	public final CSServerThread p1, p2;
	public CSGame(CSServerThread p1, CSServerThread p2){
		this.p1=p1;
		this.p2=p2;
		p2.game=this;
		p2.opponent = p1;
		p1.side = false;
		p2.side = true;
		setupRound();
		p1.writeAll("%xt%6%-1%" + enemyPoint + "%" + execPoint + "%" + map + "%" + p2.name + "%" + p1.name + "%"
				+ p2.pfp + "%" +p1.pfp + "%" + p2.gun + "%" + p1.gun + "%" + p2.rank + "%" + p1.rank + "%\0");
		p1.writeAll("%xt%7%1%\0");
	}
	public void setupRound() {
		var rng=ThreadLocalRandom.current();
		this.execPoint = rng.nextInt(94);
		do this.enemyPoint = rng.nextInt(94);
		while (enemyPoint == execPoint);
		this.map = rng.nextInt(1,4);
		p1.hp = 100;
		p2.hp = 100;
	}
	public void startRound() {
		if(!alive)return;
		roundActive=true;
		for(CSServerThread p:List.of(p1,p2))
			p.doubleWrite("%xt%4%-1%" +enemyPoint + "%" + execPoint + "%" + map + "%" + p2.name + "%"
				+ p1.name + "%" + p2.pfp + "%" + p1.pfp + "%" + p2.gun + "%" + p1.gun + "%"
				+ p2.rank + "%" + p1.rank + "%\0");
	}
}
//Bot client. Its tasks are scheduled on the common scheduler, and it responds to commands through doubleWrite().
class BotCSServerThread extends CSServerThread {
	private final double difficulty;// affects 1st shot time
	protected final double delayMod;// affects time for shots after 1st
	private volatile ScheduledFuture<?> nextShot;
	private volatile boolean fastShot=false;// don't reset fast shot for another miss
	public BotCSServerThread() {
		super(null);
		var rng = ThreadLocalRandom.current();
		this.gun =(1+rng.nextInt(10));
		this.suppressor = 0;
		this.pfp = 14;
		if (Math.random() > 0.5) {
			this.pfp = 22 + rng.nextInt(7);
		}
		this.damage = getDamage(gun);
		this.rank = gun;
		difficulty = 0.5 + (10.0 - gun) / 20.0;
		delayMod = switch (gun) {
			case 9 -> 0.1;
			case 2, 4, 6 -> 0.5;
			case 8, 5 -> 1.5;
			default -> 1.0;
		};

	}

	@Override
	public void doubleWrite(String s) {
		int cmd = Integer.parseInt(s.split("%")[2]);
		var rng=ThreadLocalRandom.current();
		int delay;
		switch (cmd) {
		case 4:
		case 6:
			if(nextShot!=null)nextShot.cancel(false);
			fastShot=false;
			delay=(int) (10000 + rng.nextInt(40000) * difficulty);
			nextShot=timer.schedule(this::shoot,delay,TimeUnit.MILLISECONDS);
			return;
		case 5:
		case 17:
			this.alive = false;
			return;
		case 8:
		case 10:
			if (rng.nextFloat() < (0.7 - (opponent.suppressor/10.0))) {// if you shoot/miss, bot will shoot sooner
				if(game.alive&&!fastShot) {
					if(nextShot!=null)nextShot.cancel(false);
					delay = (int) (rng.nextDouble(3000,4000) * delayMod);
					fastShot=true;
					nextShot=timer.schedule(this::shoot, delay,TimeUnit.MILLISECONDS);
				}
			}
			return;
		}
		
	}
	private void shoot() {
		var rng=ThreadLocalRandom.current();
		float action = rng.nextFloat();
		game.hitLock.lock();
		try {
			if (action > 0.85f)
				this.opponent.hit(game.execX, game.execY);
			else if (action > 0.15f)
				this.opponent.hit(game.enemyX, game.enemyY);
			else
				this.opponent.hit(game.enemyX + 50, game.enemyY);
			int delay = (int) (rng.nextDouble(3000,4000) * this.delayMod);
			if(game.alive)
				nextShot=timer.schedule(this::shoot, delay, TimeUnit.MILLISECONDS);
		}finally {
			game.hitLock.unlock();
		}
	}
}

//class for main method
public class CSServer extends ServerContext{
	//
	public static boolean verbose = false;
	public static final Queue<CSServerThread> queue=new ConcurrentLinkedQueue<>();
	public static final AtomicInteger nextName=new AtomicInteger();
	@Override
	public ClientContext newClient(){
		// TODO Auto-generated method stub
		return new CSServerThread();
	}
	@Override
	public void onOpen() {
		System.out.println("Countersnipe server started! port - "+getPort());
	}

}
