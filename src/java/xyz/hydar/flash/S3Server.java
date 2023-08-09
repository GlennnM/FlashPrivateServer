package xyz.hydar.flash;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static xyz.hydar.flash.FlashLauncher.CONFIG;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import xyz.hydar.flash.util.FlashUtils;
import xyz.hydar.net.ClientContext;
import xyz.hydar.net.ServerContext;
import xyz.hydar.net.TextClientContext;

/**
*	SAS3 Multiplayer Server
*	For some reason this game is more "server sided" than any other NK game.
*	The server is not just used as a relay and for matchmaking but also has to:
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
class S3Client extends TextClientContext {
	private Room room;
	private boolean hydar;//false until sent stats to room
	
	public volatile String name;
	public volatile int rank=0;
	public volatile boolean ready = false;
	public volatile boolean dead = false;// INGAME alive/dead
	public volatile int myPlayerNum = -1;
	public volatile int kills=0, xp=0, damage=0, deaths=0, revives=0, cash=0;
	
	//commands that should be sent to all players except self
	private static final Set<Integer> WRITE_FROM = Set.of(1, 2, 13, 9, 7, 10, 23, 20, 22, 19, 12, 5, 6, 24, 14, 11, 26);
	// constructor initializes socket
	public S3Client() {
		super(StandardCharsets.ISO_8859_1,'\0',CONFIG.SAS3);
	}
	public void doubleWrite(String s){
		
		send(s);
		if (S3Server.verbose)
			System.out.println("OUT: " + s);
	}
	//Remove a zombie and add to kills/xp.
	public void killWithCredit(int znum, StringBuilder spawnCmd) {
		int xp=room.kill(znum,spawnCmd);
		if(xp>=0) {//weapons like SCIMTR can kill multiple times - only count 1
			this.xp += xp;
			this.kills++;
		}
	}
	@Override
	public void onMessage(String m){
		// "extension message" - main protocol for the game. A message triggers a
		// "command"(
		if (m.startsWith("%xt%S")) {
			List<String> msg = Arrays.asList(m.split("%"));
			if(msg.size()<6) return;
			msg=new ArrayList<>(msg);
			int cmd = Integer.parseInt(msg.get(5));
			switch (cmd) {
			case 10:
				if (msg.size() < 9||room==null)
					return;
				int health = Integer.parseInt(msg.get(8));
				if (health <= 0) {
					long time = room.flashTime();
					deaths++;
					dead = true;
					room.targets.remove((Integer) (myPlayerNum));
					if (room.someAlive()) {
						room.retargetAll(myPlayerNum);
						room.writeAll("%xt%11%-1%" + (time) + "%" + myPlayerNum + "%" + myPlayerNum + "%"
								+ ((rank >= 27) ? 20000 : 15000) + "%\0");
					} else
						// lose if all dead
						room.end(false);
				} else {
					dead = false;
					room.targets.add(myPlayerNum);
					room.retargetAll(-1);
					if (revives < deaths)
						revives++;
				}
				break;
			case 23:
				if (msg.size() < 10||room==null)
					return;
				if (msg.get(9).equals("2")) {
					room.raise(Integer.parseInt(msg.get(8)));
				} else {
					// aoe attack
				}
				break;
			case 7:
				if (msg.size() < 9||room==null)
					return;
				room.parseDamage(msg.get(8), this);
				break;
			case 3:
				if (msg.size() < 14)
					return;
				if (room == null) {
					int mode = Integer.parseInt(msg.get(12));
					int nm = Integer.parseInt(msg.get(13));
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
				if (!hydar) {
					String playerList=room.playerList();
					for (S3Client x : room.players) {
						//if(x==this)continue;
						x.doubleWrite("%xt%15%" + 0 + "%-1%1%" + room.players.size() + "%"
								+ x.myPlayerNum + "%" + playerList + "%1%" + room.mode + "%" + room.mode + "%" + room.map+"%\0");
					}
					room.flushAll();
					hydar = true;
				}
				break;
			case 20:
				int barrier=Integer.parseInt(msg.get(8));
				int startTime=Integer.parseInt(msg.get(9));
				if(barrier<0 || barrier>1000)return;//limit size of map
				int time=room.flashTime();
				if("1".equals(msg.get(10))) {
					
					int status = room.barriers
						.computeIfAbsent(barrier, x->new Room.Barrier(room.players.size()))
						.acquire(startTime, time);
					if(status<=0){
						if(status<0)
							room.barriers.remove(barrier);
						return;
					}
				}
				msg.set(9,""+time);
				break;
			case 26:
				String playerList=room.playerList();
				for (S3Client x : room.players) {
					x.doubleWrite("%xt%25%" + 0 + "%%1%" + room.players.size() + "%"
							+ x.myPlayerNum + "%" + playerList + "%1%" + room.mode + "%" + room.mode + "%" + room.mapURL + "%"
							+ room.nm + "%" + room.nm+"\0");
				}
				break;
			}
			msg.set(2, "" + cmd);
			msg.set(3, "" + room.flashTime());
			msg.set(4, "" + -1);
			if (cmd == 4) {
				this.ready = true;
				room.startTime = FlashUtils.now();
				if (room.allReady() && !room.setup)
					room.setup();
				msg.set(msg.size() - 2, "" + room.SBEmult);
				msg.set(msg.size() - 1, "" + room.barriHP);
				Collections.swap(msg,3,4);
				msg.remove(5);
			}else if (WRITE_FROM.contains(cmd) && cmd != 26) {
				if(msg.size()<8)
					return;
				msg.remove(2);
				msg.remove(2);
				Collections.swap(msg,2,3);
				msg.set(5, "" + myPlayerNum);
				if (cmd == 7) {
					msg.set(4, "0");
				}
			}
			msg.add("\0");
			String pl = String.join("%", msg);
			if (WRITE_FROM.contains(cmd) && cmd != 23 && cmd != 20)
				room.writeFrom(this, pl);
			else if (cmd != 4 || room.allReady())
				room.writeAll(pl);
			// 7 name
			// 8 lvl
			// 12 mode
			// 13 NM
			// room.players.forEach(x->x.doubleWrite()
		}
		// smartfox setup(hard coded) - none of it matters
		else if (m.equals("<msg t='sys'><body action='verChk' r='0'><ver v='165' /></body></msg>"))
			doubleWrite("<msg t='sys'><body action='apiOK' r='0'><ver v='165'/></body></msg>\0");
		else if (m.equals(
				"<msg t='sys'><body action='login' r='0'><login z='SAS3'><nick><![CDATA[]]></nick><pword><![CDATA[]]></pword></login></body></msg>"))
			doubleWrite("<msg t='sys'><body action='logOK' r='0'><login id='" + ThreadLocalRandom.current().nextInt(1000000000)
					+ "' mod='0' n='SAS3'/></body></msg>\0");
		else if (m.equals("<msg t='sys'><body action='getRmList' r='-1'></body></msg>"))
			doubleWrite("<msg t='sys'><body action='rmList' r='-1'><rmList><rm></rm></rmList></body></msg>\0");
		else{
			alive=false; 
			if (S3Server.verbose) 
				System.out.println("UNKNOWN");
		}
		if(room!=null)
			room.flushAll();
		else flush();
	}
	@Override
	public void onOpen(){
		CONFIG.acquire(this);
	}
	@Override
	public void onClose(){
		if (room != null) {
			room.dropPlayer(this);
		}
		CONFIG.release(this);
	}
}
/**Starts a wave, scheduling powerup and spawn tasks.*/
class WaveStartTask implements Runnable{
	private final Room room;
	public WaveStartTask(Room room) {
		this.room = room;
	}
	@Override
	public void run() {
		// send WaveStrtCommand
		if (!room.alive)
			return;
		room.writeAll("%xt%17%-1%" + 0 + "%" + room.wave
				+ "%" + room.waveTotal + "%\0");
		room.flushAll();
		if (room.mode == 1) {
			Room.timer.schedule(new SpawnNestTask(room), 5000, TimeUnit.MILLISECONDS);
			Room.timer.schedule(new PowerupTask(room), 7000, TimeUnit.MILLISECONDS);
		} else {
			Room.timer.schedule(new SpawnTask(room), 5000, TimeUnit.MILLISECONDS);
			Room.timer.schedule(new PowerupTask(room), 30000, TimeUnit.MILLISECONDS);
		}
		room.init = true;
		room.r = false;
	}
}
/**Spawns a set of mobs in a lobby when ran. If the wave changes while active, it will be cancelled.*/
class SpawnTask implements Runnable {
	private final Room room;
	private final int wave;
	public SpawnTask(Room room) {
		this.room = room;
		this.wave=room.wave;
	}
	@Override
	public void run() {
		if(!room.alive || room.wave!=wave||room.zombies.size()>CONFIG.SAS3_MOBCAP) {
			return;
		}
		float loc2 = room.commaHash();
		List<ZombieType> loc3 = new ArrayList<>();
		// create loc5
		float chanceSum = 0.0f;
		for (var z : ZombieType.values()) {
			if (//(z.index==9)&& 
			!(room.map == 1 && z.index == 9) && z.weight <= loc2) {
				loc3.add(z);
				chanceSum+=z.chance;
			}
		}
		//System.out.println(room.zombies.values().stream().filter(x->x.type==ZombieType.NEST).toList());
		float loc7 = 1;// locals from -;
		float[] loc5 = new float[loc3.size()];
		for(int i=loc3.size()-1;i>=0;i--) {
			loc5[i] = loc7;
			loc7 -= loc3.get(i).chance / chanceSum;
		}
		loc7 = room.dashL(loc2) * 2500f / 1000f;
		room.p3 += loc7;
		float loc8 = room.p3 - room.p2;
		float loc10 = 0f;
		var cmds = new StringBuilder(Math.max((int) (loc8) * 15, 0));
		int time=room.flashTime();
		var rng=ThreadLocalRandom.current();
		while (loc10 < loc8) {
			ZombieType loc12 = loc3.get(loc3.size() - 1);
			float loc16 = rng.nextFloat();
			for(int i=0;i<loc5.length;i++){
				if (loc16 < loc5[i]) {
					loc12 = loc3.get(i);
					break;
				}
			}
			float loc13 = (((room.mode == 1 ? 1 : loc12.cap) - 1f) * 0.5f + 1f) * room.SBEmult;
			loc10 += loc13;
			room.p2 += loc13;
			cmds.append(room.spawnCmd(loc12,-1,time));
		}
		String spawnCmd = cmds.toString();
		if (spawnCmd.length() > 0) {
			room.writeAll(spawnCmd);
			room.flushAll();
		}
		if (++room.p1 < room.p4) {
			//delay before spawning next group
			int delay = switch (room.mode) {
				case 2 -> room.bracket3(3)?2500:1000;
				case 1 -> (Math.max(2000 - wave * 200 - room.p1 * 25, 900));//speeds up over time
				default -> Math.max(2500 - room.p1 * 10, 1500);
			};
			Room.timer.schedule(new SpawnTask(room), delay,TimeUnit.MILLISECONDS);
		}
	}
}
/**Spawns nests in a lobby when ran. If the wave changes while active, it will be cancelled.*/
class SpawnNestTask implements Runnable{
	private final Room room;
	private final int wave;
	public SpawnNestTask(Room r) {
		this.room = r;
		this.wave=r.wave;
	}
	@Override
	public void run() {
		if(!room.alive||room.wave!=this.wave) {
			return;
		}
		room.spawnNests();
		Room.timer.schedule(new SpawnTask(room), 1000,TimeUnit.MILLISECONDS);
	}
}
/**Spawns a powerup in a lobby when ran. If the wave changes while active, it will be cancelled.*/
class PowerupTask implements Runnable {
	private final Room room;
	private final int wave;
	public static final int[] GUNS = new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 18, 19, 20,
			21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43 };
	public static final int[] GRENADES = new int[] { 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 3, 3 };
	public static final int[] SENTRIES = new int[] { 0, 1 };
	public PowerupTask(Room room) {
		this.room = room;
		this.wave=room.wave;
	}
	@Override
	public void run() {
		if(!room.alive||room.wave!=this.wave)return;
		int loc6 = ThreadLocalRandom.current().nextInt(room.powerups.length);
		int loc7 = this.room.powerups[loc6];//spawn point
		for (S3Client player : room.players) {
			player.xp += 50;
			float loc2 = 2f * Math.min(0.083f, player.rank * 0.0032f + 0.0138f);
			float loc3 = 0f * 2f * loc2;
			float loc4 = 0.5f * (1f - loc2 - loc3);
			float loc5 = 0.3f * (1f - loc2 - loc3);
			int loc10 = 0;
			float loc11 = ThreadLocalRandom.current().nextFloat();

			int type = 5;
			int subtypes = 1;
			float loc9 = 1f;// subtypes (default=cash)
			// String name = "";
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
			}
			int loc12 = (int) (ThreadLocalRandom.current().nextFloat(loc9)) + loc10;
			if (loc12 >= subtypes) {
				loc12 = subtypes - 1;
			}
			int item = switch (type) {
				case 0 -> GRENADES[loc12];
				case 3 -> GUNS[loc12];
				case 4 -> SENTRIES[loc12];
				case 1, 2, 5 -> 0;
				default -> 0;
			};
			player.doubleWrite("%xt%16%-1%" + 0 + "%" + loc7 + "%" + type + "%" + item + "%\0");
		}
		if (room.mode == 1) {
			Room.timer.schedule(new PowerupTask(room), 15000,TimeUnit.MILLISECONDS);
			return;
		}
	}
}
/**index is the type ID, weight and chance affect spawning, cap affects spawning as well as round ends.*/
enum ZombieType {
	SWARMER(0,1,100f,1f,160,10), RUNNER(1,4,30f,1.5f,100,15), CHOKER(2,10,15f,6f,500,60),
	BLOATER(3,12,5f,15f,3000,150), SHADOW(4,14,2f,12f,2500,120), MAMUSHKA(5,16,1f,108f,4000,360),
	MAMUSHKA2(6,999,0f,36f,3000,120), MAMUSHKA3(7,999,0f,12f,2000,40), MAMUSHKA4(8,999,0f,4f,1000,40),
	DEVASTATOR(9,18,0.1f,600f,30000,6000), SKELEMAN(10,999,0f,3f,300,0), WORM(11,999,0f,0.5f,100,5),
	NEST(12,999,0f,1f,80000,6000);
	public final int index;
	public final int weight;// 6c
	public final float chance;// function 8o
	public final float cap;// %O
	public final int hp;
	public final int xp;
	ZombieType(int index, int weight, float chance, float cap, int hp, int xp) {
		this.index = index;
		this.weight = weight;
		this.chance=chance;
		this.cap=cap;
		this.hp=hp;
		this.xp=xp;
	}
}
/**"SBEmult" is based on the difficulty and used to scale hp.*/
class Zombie{
	public final ZombieType type;
	public volatile int target;
	public volatile int hp;
	public Zombie(ZombieType type, int target, float SBEmult) {
		this.target=target;
		this.type=type;
		hp = (int)(type.hp * ((SBEmult - 1f) * 0.5f + 1f));
	}
	@Override
	public String toString() {
		return "(" + type + "):" + hp;
	}
}
/**Game state. Does not run on its own thread, instead the clients execute or schedule its methods.*/
class Room {
	public static final ScheduledExecutorService timer=newSingleThreadScheduledExecutor((r)->new Thread(r,"SAS3 room tasks"));
	private static final int[][] SPAWN_LOCATIONS={null,//map 0
		{ 9, 6, 5, 8, 11, 10, 4 },
		{ 18, 17, 16, 13, 12, 11, 15, 14, 19 },
		{ 31, 37, 38, 35, 34, 33, 32, 36 },
		{ 13, 12, 14, 31 },
		{ 43, 42, 41, 40, 47, 7, 46, 44, 9, 17, 4, 45 }
	};
	private static final int[][] POWERUP_LOCATIONS= {null,//map 0
		{ 0, 1, 2, 3 },
		{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 },
		{ 1, 3, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27,
				28, 29, 30 },
		{ 0, 2, 3, 6, 7, 12, 13, 14, 15, 17, 21, 26, 33, 35, 37, 38 },
		{ 0, 1, 2, 3, 5, 8, 11, 12, 15, 21, 25, 32, 33, 34, 36, 37 }
	};
	public final List<S3Client> players=new CopyOnWriteArrayList<>();
	public final List<Integer> targets=new CopyOnWriteArrayList<>();
	public final List<Nest> nests= new CopyOnWriteArrayList<>();
	public final Map<Integer,Barrier> barriers=new ConcurrentHashMap<>();
	/**
	 * For some reason clients will send more barrier packets when receiving one.
	 * To counter this we block a limited number of 'repeats'
	 * of a packet with the same time value, depending on player count,
	 * and then start allowing them again.
	 * */
	static record Barrier(AtomicInteger time, AtomicInteger repeats, int max) {
		Barrier(int max){
			this(new AtomicInteger(-1),new AtomicInteger(max), max);
		}
		/**
		 * Update time and repeats.
		 * If startTime(included in packet) is greater than
		 * the previous time(or prev is -1)
		 * we set it to newTime(which is absolute from clock).
		 * Returns: the time if successful, 0 if did nothing
		 * , -1 if it should be deallocated(repeat max reached).
		 * */
		int acquire(int startTime, int newTime) {
			//Update the value and check if it was successfully modified at the same time.
			if(time.getAndAccumulate(startTime, (x,y)->x<y?newTime:x)<startTime) {
				repeats.set(max);
				return newTime;
			}else if(startTime==0){
				return (repeats.decrementAndGet() > 0) ? 0 : -1;
			}
			return 0;
		}
	};
	static record Nest(Zombie z, int point) {}
	
	public final AtomicBoolean[] slots = new AtomicBoolean[4];
	public volatile long startTime=0;
	public final Map<Integer, Zombie> zombies= new ConcurrentHashMap<>(1024);
	
	public volatile float rankAvg;
	public volatile float SBEmult;
	public volatile int barriHP;
	public volatile boolean setup = false;
	public volatile boolean init = false;
	public volatile boolean alive = true;
	public volatile boolean r = true;//ready for spawns
	// SPAWNER ARGS
	public volatile int p1 = 0;// §[D§
	public volatile float p2 = 0f;// §]?§
	public volatile float p3 = 0f;// §5-§
	public volatile int p4 = 24;
	public volatile int wave = 1;
	public volatile int waveTotal;
	public final AtomicInteger nextSpawnNum=new AtomicInteger(ThreadLocalRandom.current().nextInt(4));
	public final DoubleAdder totalCapacity=new DoubleAdder();

	public final int mode;// 1 purge 2 onslaught 3 apoc
	public final int nm;
	public final int map = 1+ThreadLocalRandom.current().nextInt(5);
	public final String mapURL = MAP_URLS[map - 1];
	public final int[] spawns=spawns(map);
	public final int[] powerups=powerups(map);
	

	// GAME END STUFF
	private final StringBuffer names=new StringBuffer(100);
	private final StringBuffer kills=new StringBuffer(100);
	private final StringBuffer damage=new StringBuffer(100);
	private final StringBuffer deaths=new StringBuffer(100);
	private final StringBuffer revives=new StringBuffer(100);
	private final StringBuffer xp=new StringBuffer(100);
	private final StringBuffer cash=new StringBuffer(100);
	private final StringBuffer ranks=new StringBuffer(100);

	public volatile boolean end = false;
	public static final String[] MAP_URLS = new String[] { "http://sas3maps.ninjakiwi.com/sas3maps/FarmhouseMap.swf",
			"http://sas3maps.ninjakiwi.com/sas3maps/AirbaseMap.swf",
			"http://sas3maps.ninjakiwi.com/sas3maps/KarnivaleMap.swf",
			"http://sas3maps.ninjakiwi.com/sas3maps/VerdammtenstadtMap.swf",
			"http://sas3maps.ninjakiwi.com/sas3maps/BlackIsleMap.swf" };
	public Room(S3Client p1, int mode, int nm) {
		players.add(p1);
		this.mode = mode;
		this.nm = nm;
		Arrays.setAll(slots,x->new AtomicBoolean());
	}
	/**Starts the game, waves will begin after 5 seconds(constructor only initializes lobby data)*/
	public void setup() {
		System.out.println("Starting game...");
		this.setup = true;
		//(not a sum at all)
		this.rankAvg = (int)(players.stream().mapToInt(x->x.rank).average().orElse(0));
		//this.rankSum /= (Math.pow((float)players.size(), 0.9f));
		this.SBEmult = (1f + this.rankAvg / 10f) / 2f * (nm==0?1f:10f);
		this.barriHP = (int) (600f * this.SBEmult * this.SBEmult);
		switch (mode) {
		case 1:
			waveTotal = 3;
			p4 = 7200;
			break;
		case 3:
			waveTotal = 1;
			p4 = 7200;
			break;
		default: //copied from singleplayer
			if (this.rankAvg >= 38) this.waveTotal = 11;
			else if (this.rankAvg >= 30) this.waveTotal = 10;
			else if (this.rankAvg >= 22) this.waveTotal = 9;
			else if (this.rankAvg >= 15) this.waveTotal = 8;
			else if (this.rankAvg >= 9) this.waveTotal = 7;
			else if (this.rankAvg >= 5) this.waveTotal = 6;
			else this.waveTotal = 5;
			this.waveTotal--;//this is consistent with all the YT videos i could find
			break;
		}
		timer.schedule(new WaveStartTask(this), 5000,TimeUnit.MILLISECONDS);
	}
	/**Return a new player number.*/
	public int nextPlayerNum(){
		for(int i=0;i<slots.length;i++)
			if(slots[i].compareAndSet(false, true)){
				return i;
			}
		return -1;
	}
	/**Format the player list to be included in a welcome packet.*/
	public String playerList() {
		var playerNames = players.stream().map(x->"\""+x.name+"\"").toList();
		var playerRanks = players.stream().map(x->x.rank).toList();
		var readyList = players.stream().map(x->x.ready).toList();
		return playerNames + "%" + playerRanks + "%" + readyList;
	}
	/**ms since game started(used for syncing)*/
	public int flashTime() {
		return startTime==0?0:(int)(FlashUtils.now()-startTime);
	}
	/**Retarget all mobs to living players. Used when someone dies/disconnects*/
	public void retargetAll(int deadNum) {
		if (players.size() == 0)
			return;
		List<StringBuilder> cmds = new ArrayList<>();
		for (S3Client t : players) {
			if (!t.dead) {
				var s = new StringBuilder(40 + zombies.size() * 4 / players.size());
				s.append("%xt%22%-1%").append((flashTime())).append("%");
				s.append(t.myPlayerNum).append("%[");
				cmds.add(s);
			}
		}
		for (var z : zombies.entrySet()) {
			if(z.getValue().target==deadNum||deadNum==-1){
				cmds.get(z.getKey() % cmds.size()).append(z.getKey() + ",");
				z.getValue().target=players.get(z.getKey() % cmds.size()).myPlayerNum;
			}
		}
		for (StringBuilder x : cmds) {
			x.deleteCharAt(x.length() - 1).append("]%\0");
			writeAll(x.toString());
		}
	}
	/**End the game*/
	public void end(boolean win) {
		if (this.end)
			return;
		System.out.println("Ending game...");
		this.end = true;
		int xpBonus = 0;
		//300 * average rank is added in a won purge game
		//affects xp but not cash
		//(source: yt videos)
		int purgeBonus = (int) (rankAvg * 300); 
		for (S3Client player : players) {
			xpBonus += player.xp;
		}
		for (S3Client player : players) {
			player.xp += (int) (xpBonus * ((ThreadLocalRandom.current().nextFloat() + 4f) / 30f));
			player.xp=switch (this.map) {
				case 1->(int) (1.4f * player.xp);
				case 2->(int) (1.2f * player.xp);
				default->player.xp;
			};
			if (this.nm != 0) {
				player.xp = (int) (1.2f * player.xp);
			}
			// $1K$
			player.xp += (int) (.04f * (players.size()-1) * player.xp);
			if(mode==1){//purge
				player.xp/=2;//there is an xp modifier, idk the exact number
			}
			if (wave > 1)
				player.xp += (ThreadLocalRandom.current().nextInt(200)) + wave * 20;
			if (!win && mode != 3)
				player.xp = (int) (0.3333333f * player.xp);
			player.cash = (int) (0.2f * player.xp);
			if (player.rank >= 40 && nm == 0) {
				player.xp = 0;
			}
			if(mode == 1 && win) {
				//purge bonus doesn't affect cash, but can still be earned over lvl40
				player.xp+=purgeBonus;
			}
			record(player);
		}
		if (names.length() > 0 && players.size() > 0) {
			String results=Stream.of(names,kills,damage,deaths,revives,xp,cash,ranks)
				.map(x->x.deleteCharAt(x.length()-1))
				.collect(Collectors.joining("%"));
			writeAll("%xt%8%-1%" + (flashTime()) + "%"+results+"%" + (win ? 1 : 0) + "%\0");
			flushAll();
			for (S3Client t : players) {
				t.close();
			}
		}
		this.alive = false;
		S3Server.games.remove(this);
	}

	// checks if anyone is alive
	public boolean someAlive() {
		for (S3Client t : players) {
			if (!t.dead)
				return true;
		}
		return false;
	}
	// checks if all players finished building(loading graphics)
	public boolean allReady() {
		for (S3Client t : players) {
			if (!t.ready)
				return false;
		}
		return true;
	}
	// returns string for spawn command(single zombie)
	public String spawnCmd(ZombieType z, int parent, int flashTime) {
		// spawns=normal spawn points, nests = nest locations
		// the locations are integers(found in the swf)
		int znum=nextSpawnNum.incrementAndGet();
		int target=targets.isEmpty() ? -1 : targets.get((parent>=0?parent:znum)%targets.size());
		if(mode==1 && nests.isEmpty())return "";
		int spawn = parent>=0?-1:(mode==1?nests.get(znum%nests.size()).point():
			spawns[znum%spawns.length]);
		zombies.put(znum, new Zombie(z, target, SBEmult));
		totalCapacity.add(z.cap);
		return new StringBuilder(64).append("%xt%9%-1%").append(flashTime())
			.append('%').append(target).append('%').append(z.index)
			.append('%').append(spawn).append('%').append(SBEmult)
			.append('%').append(znum).append('%').append(parent).append("%\0").toString();
	}
	// spawns multiple of the same mob from a parent(bloater/mamushka)
	public void multiSpawn(ZombieType z, int parent, int count, StringBuilder cmds) {
		int time=flashTime();
		for (int i = 0; i < count; i++) {
			cmds.append(spawnCmd(z,parent,time));
		}
	}
	// spawns nests at the start of a purge wave
	public void spawnNests() {
		var rng=ThreadLocalRandom.current();
		int playerIndex = targets.isEmpty() ? -1 : targets.get(rng.nextInt(targets.size()));
		StringBuilder cmds = new StringBuilder(32 * wave);
		for (int i = 0; i < wave; i++) {
			int[] spawns=Arrays.stream(powerups)
					.filter(x->nests.stream().noneMatch(nest->nest.point()==x))
					.toArray();
			int point = spawns[rng.nextInt(spawns.length)];
			int znum=nextSpawnNum.incrementAndGet();
			Zombie z=new Zombie(ZombieType.NEST, playerIndex, SBEmult);
			String spawnCmd = "%xt%9%-1%" + flashTime() + "%" + playerIndex + "%"
					+ ZombieType.NEST.index + "%" + point + "%" + SBEmult + "%" + znum + "%-1%\0";
			zombies.put(znum, z);
			nests.add(new Nest(z,point));
			totalCapacity.add(ZombieType.NEST.cap);
			cmds.append(spawnCmd);
		}
		writeAll(cmds.toString());
		flushAll();
	}
	// spawns skeletons, triggered by incoming AttackCmd with "type" 2
	public void raise(int zombieNum) {
		var rng=ThreadLocalRandom.current();
		int playerIndex = targets.isEmpty() ? -1 : targets.get(rng.nextInt(targets.size()));
		int skeleCount = (int) (rankAvg / 4 + 3);
		StringBuilder skeleBuilder = new StringBuilder(skeleCount * 45);
		for (int i = 0; i < skeleCount; i++) {
			float loc7 = 2f * (float) Math.PI / skeleCount * i;
			int skeleNum=nextSpawnNum.incrementAndGet();
			skeleBuilder.append("%xt%24%-1%" + flashTime() + "%" + playerIndex
					+ "%" + 10 + "%-1%" + SBEmult + "%" + skeleNum + "%" + zombieNum + "%"
					+ Math.round(Math.cos(loc7) * 100) + "%" + Math.round(Math.sin(loc7) * 100) + "%\0");
			zombies.put(skeleNum, new Zombie(ZombieType.SKELEMAN,playerIndex , SBEmult));
			totalCapacity.add(ZombieType.SKELEMAN.cap);
		}
		writeAll(skeleBuilder.toString());
		flushAll();
	}
	// consume a damage packet - lower hp/kill mobs etc
	public void parseDamage(String dmgStr, S3Client player) {
		int playerNum=player.myPlayerNum;
		String[] damages = dmgStr.split(",");
		StringBuilder hitCmd = null;
		StringBuilder targetCmd = null;
		StringBuilder spawnCmd=new StringBuilder();
		boolean forgetAboutTarget=damages.length>10||zombies.size()>2048;
		boolean tryEndWave=false;
		for (String s : damages) {
			String[] params = s.split(":");
			int znum=Integer.parseInt(params[0]);
			int dmg=Integer.parseInt(params[1]);
			player.damage += dmg;
			if (params.length > 2 && params[2].equals("d")) {
				tryEndWave=true;
				player.killWithCredit(znum, spawnCmd);
			} else {
				Zombie z = zombies.get(znum);
				if (z != null) {
					if ((z.hp -= dmg) <= 0) {
						if (hitCmd == null)
							hitCmd=new StringBuilder(damages.length * 9 + 25)
								.append("%xt%7%-1%").append(flashTime()).append("%")
								.append(playerNum).append("%");
						hitCmd.append(znum).append(":1:d,");//':d' indicates that the zombie should die
						tryEndWave=true;
						player.killWithCredit(znum, spawnCmd);
					} else if(!forgetAboutTarget&&z.target!=playerNum){
						if (targetCmd == null) 
							targetCmd = new StringBuilder(damages.length * 5 + 25)
								.append("%xt%22%-1%").append(flashTime()).append("%")
								.append(playerNum).append("%[");
						z.target=playerNum;
						targetCmd.append(znum).append(',');
					}
				}
			}
		}
		// mobs attack the player that shot them
		if (targetCmd!=null && !player.dead) {
			targetCmd.deleteCharAt(targetCmd.length() - 1).append("]%\0");
			writeAll(targetCmd.toString());
		}
		// forcibly kill mobs that should have been killed by aoe
		if (hitCmd!=null) {
			hitCmd.deleteCharAt(hitCmd.length() - 1).append("%\0");
			writeAll(hitCmd.toString());
		}
		// splitting mobs
		if (spawnCmd.length()>0) {
			writeAll(spawnCmd.toString());
		}
		if (tryEndWave && mode!=1&&init && p1 >= p4 && !(bracket3(3)) && !r){
			if (S3Server.verbose)
				System.out.println("||||||Wave end");
			r = true;
			endWave();
		}
		flushAll();
	}
	//remove a zombie and add spawn commands for worms/mamushkas to spawnCmd
	public int kill(int number, StringBuilder spawnCmd) {
		Zombie z1 = zombies.remove(number);
		if (z1!=null) {
			totalCapacity.add(-z1.type.cap);
			finishKill(z1,number,spawnCmd);
			return z1.type.xp;
		}
		return -1;//indicate that it was already killed
	}
	//add spawn commands for worms/mamushkas to spawnCmd
	public void finishKill(Zombie zombie, int znum, StringBuilder spawnCmd) {
		switch(zombie.type) {
			case BLOATER:
				multiSpawn(ZombieType.WORM, znum, 5, spawnCmd);
				break;
			case MAMUSHKA:
				multiSpawn(ZombieType.MAMUSHKA2, znum, 2, spawnCmd);
				break;
			case MAMUSHKA2:
				multiSpawn(ZombieType.MAMUSHKA3, znum, 2, spawnCmd);
				break;
			case MAMUSHKA3:
				multiSpawn(ZombieType.MAMUSHKA4, znum, 2, spawnCmd);
				break;
			case NEST:
				nests.removeIf(x->x.z()==zombie);
				if (nests.isEmpty()) {
					r = true;
					endWave();
				}
				break;
			default:
				break;
		}

	}
	/**End the current wave.*/
	public void endWave() {
		int index=0;
		if (!alive)
			return;
		for (S3Client t : players)
			if (!t.dead)
				index=t.myPlayerNum;
		if (zombies.size() > 0) {
			StringBuilder hitCmd = new StringBuilder(zombies.size()*7+25).append("%xt%7%-1%"+flashTime()+"%"+index+"%");
			for (var z : zombies.keySet()) {
				hitCmd.append(z).append(":1:d,");
			}
			zombies.clear();
			totalCapacity.reset();
			nextSpawnNum.set(ThreadLocalRandom.current().nextInt(4));
			hitCmd.deleteCharAt(hitCmd.length() - 1).append("%\0");
			writeAll(hitCmd.toString());
			flushAll();
		}
		writeAll("%xt%18%-1%0%" + wave + "%" + waveTotal + "%\0");
		p1 = 0;
		p2 = 0.0f;
		p3 = 0f;
		flushAll();
		if (wave++ < waveTotal) {
			timer.schedule(new WaveStartTask(this), 1000,TimeUnit.MILLISECONDS);
		} else {
			end(true);
		}
	}
	//Locations where a mob can spawn on each map. see $[Q$/$0$
	public static int[] spawns(int map) {
		return SPAWN_LOCATIONS[map];
	}
	//Locations where a powerup or nest can spawn on each map. see $[Q$/$0$
	public static int[] powerups(int map) {
		return POWERUP_LOCATIONS[map];
	}
	//add player's stats to end-of-game report when they dc or when game ends
	public void record(S3Client player) {
		names.append(player.name).append(',');
		kills.append(player.kills).append(',');
		damage.append(player.damage).append(',');
		deaths.append(player.deaths).append(',');
		revives.append(player.revives).append(',');
		xp.append(player.xp).append(',');
		cash.append(player.cash).append(',');
		ranks.append(player.rank).append(',');
	}
	// remove a player
	public void dropPlayer(S3Client target) {
		if(!players.remove(target))return;
		int index=target.myPlayerNum;
		System.out.println("drop "+index);
		targets.remove((Integer)index);
		writeFrom(target, "%xt%21%-1%" + 0 + "%" + target.myPlayerNum + "%\0");
		if (!end && setup) {
			target.name+="(disconnected)";
			target.deaths++;
			target.xp=target.cash=0;
			record(target);
		}
		slots[index].set(false);
		if (players.size() == 0) {
			this.alive = false;
			S3Server.games.remove(this);
		}else if (!this.someAlive())
			this.end(false);
		else {
			retargetAll(index);
			for (S3Client t : players) 
				t.doubleWrite("%xt%15%" + 0 + "%-1%1%" + players.size() + "%" + t.myPlayerNum + "%" + playerList() + "%1%" + mode + "%" + (mode) + "%" + map+"%\0");
			flushAll();
		}
	}
	// name from SWF - checks if can end wave
	public boolean bracket3(int max) {
		return totalCapacity.sum()> max;
	}
	// name from SWF - something wave related idk
	public float dashL(float param1) {
		float loc3 = (this.nm == 0) ? 0.9f : 2.5f;
		float loc4 = Math.min(param1,45f);
		float loc5 = Math.max(0,loc4-45f);
		float loc2 = switch (this.map) {
			case 1 -> 0.65f;
			case 2, 3 -> 0.75f;
			case 5 -> 1.1f;
			default -> 1.0f;
		};
		float loc6 = 10f - (loc4 - 10f) / 7f;
		float loc8 = (float) Math.pow(loc6, loc4 / 18f) * 1.2f;
		float loc9 = (float) Math.pow(loc5, 1.5f) * 5.2f;
		return (loc8 + loc9) / 4f * (players.size()) * loc2 * loc3;
	}
	// name from SWF - something wave related idk
	public float commaHash() {
		return this.rankAvg + (this.p1 + (float) p4 * (float) this.wave)
				/ ((float) p4 * (float) this.waveTotal) * 10f * ((this.waveTotal - 4) / 12f + 1f);
	}
	//send to all other players
	public void writeFrom(S3Client origin, String toWrite) {
		for(S3Client player:players){
			if(player==origin)continue;
			player.doubleWrite(toWrite);
		}
		
	}
	//send to all players
	public void writeAll(String toWrite) {
		for(S3Client player:players){
			player.doubleWrite(toWrite);
		}
	}
	public void flushAll(){
		for(var player:players)
			player.flush();
	}
	public void closeExt() {
		if(!setup) {
			players.forEach(S3Client::close);
		}
		else System.out.println("Waiting for instance "+this+"...");
	}
	// h
	public void h() {

	}
}
//class for main method
public class S3Server extends ServerContext {
	public static final boolean verbose = false;
	public static final List<Room> games = new CopyOnWriteArrayList<>();
	@Override
	public void onOpen() {
		System.out.println("SAS3 server started! port - "+getPort());
	}
	@Override
	public void onClose() {
		System.out.println("SAS3 stopping... "+games.size());
		games.forEach(Room::closeExt);
	}
	@Override
	public ClientContext newClient() throws IOException {
		return new S3Client();
	}
}