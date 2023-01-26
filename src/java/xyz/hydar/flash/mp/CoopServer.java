package xyz.hydar.flash.mp;
import static java.lang.Integer.parseInt;
import static xyz.hydar.flash.mp.FlashLauncher.CONFIG;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import xyz.hydar.flash.util.FlashUtils;
import xyz.hydar.flash.util.Scheduler;
import xyz.hydar.net.ClientContext;
import xyz.hydar.net.LineClientContext;
import xyz.hydar.net.ServerContext;
/**Client context for players in lobby or queues. Messages are delimited with \n.<br>
 * The connection's purpose is fulfilled when the client is redirected to a game server through connect().
 * */
class CoopClient extends LineClientContext {
	private CoopPlayer player = null;
	private int timeouts = 0;
	private CoopGameServer gs = null;
	private boolean queued=false;
	private boolean matched=false;
	private String code;
	private final CoopServer parent;
	/**
	1:<-game info
	2:->(join)code
	3:->(create)priv match info
	4:<-received priv
	7:->cancel(private)
	8:<-invalid code
	9:<-someone joined priv
	11:->cancel(public)
	12:->modes selected for queue
	13:<-found quick match
	14:->left queue
	15:->remove player info(cancel)
	17:->user info
	18:<-received user info
	
	*/
	// constructor initializes socket
	public CoopClient(CoopServer parent) throws IOException {
		super(StandardCharsets.ISO_8859_1,CONFIG.BTD5);
		this.parent=parent;
	}
	/**Attempt to queue, or start a game with the player there.*/
	public void queuePublic() throws IOException{
		if(matched)return;
		CoopPlayer opp = CoopServer.checkQueue(player);
		if (opp!=null) {
			String ms=player.getMap(opp);
			//System.out.println(ms);
			String map=ms.substring(0,ms.length()-1);
			int mode = parseInt(ms.substring(ms.length()-1));
			int reverse=ThreadLocalRandom.current().nextInt(2);
			CoopGameServer gs = new CoopGameServer(opp,null,map,mode,reverse);
			connect(gs,opp);
		}
		queued = true;
	}
	/**Connect to a quick match lobby as player 2, with player 1 provided as p.*/
	public void connect(CoopGameServer gs, CoopPlayer p) throws IOException{
		gs.start(IntStream.generate(CoopServer::nextPort).limit(100),parent.isNio());
		p.sendln("13,"+CONFIG.HOST+","+gs.getPort()+",843,13042641,"+player.id+","+player.name+","+gs.map+","+gs.mode+","+gs.reverse+",0");
		if(p.thread!=null)p.thread.flush();
		//If players cannot be distinguished, we have to 
		if(Objects.equals(player,p)) {
			Scheduler.schedule(()->connectQuick(p),5000);
		}else connectQuick(p);
		queued=false;
		matched=true;
	}
	@Override
	public void onMessage(String line) throws IOException{
		timeouts = 0;
		if(CoopServer.verbose)
			System.out.println("incoming: "+line);
		String[] msg = line.split(",");
		switch (msg[0]) {
		case "<policy-file-request/>":
			send(PolicyFileServer.POLICY);
			break;
		case "12":
			if(queued)break;
			//store data and add to q
			if(player!=null)
				player.setConstraints(msg[1]);
			else {
				alive=false;
				return;
			}
			queuePublic();
			break;
		case "17":
			//store data
			reset();
			player = CoopPlayer.ofLobby(msg,this);
			sendln("18");
			break;
		case "3":
			//store data and check code
			if(queued||matched)break;
			code = msg[1];
			String map = msg[2];
			int mode = parseInt(msg[3]);
			int reverse = parseInt(msg[4]);
			if(!CoopServer.privateMatches.containsKey(code)){
				if(player==null) {
					alive=false;
					return;
				}
				var gs=new CoopGameServer(player,code,map,mode,reverse);
				CoopServer.privateMatches.put(code,gs);
				gs.start(IntStream.generate(S4Server::nextPort).limit(100),parent.isNio());
				sendln("4,"+CONFIG.HOST+","+gs.getPort()+",0");
			}else{
				sendln("5");
			}
			break;
		case "7":
		case "15":
		case "11":
		case "14":
			reset();
			break;
		case "2":
			//join private
			if(queued||matched)break;
			if((gs = CoopServer.privateMatches.remove(msg[1]))==null){
				sendln("8");
			}else{
				gs.p1.sendln("9,"+""+0+","+",0");
				if(Objects.equals(player,gs.p1)) {
					Scheduler.schedule(this::connectPrivate,5000);
				}else connectPrivate();
				queued=true;
			}
			break;
		}
	}
	/**connect packets in methods so they can be scheduled if delay is needed*/
	private void connectQuick(CoopPlayer p) {
		if(!alive)return;
		sendln("13,"+CONFIG.HOST+","+gs.getPort()+",843,13042641,"+p.id+","+p.name+","+gs.map+","+gs.mode+","+gs.reverse+",1");
		flush();
	}
	private void connectPrivate() {
		if(!alive)return;
		sendln("1,"+CONFIG.HOST+","+gs.getPort()+",0,13042641,"+gs.p1.id+","+gs.p1.name+","+gs.map+","+gs.mode+","+gs.reverse);
		flush();
	}
	@Override
	public void onTimeout(){
		
		timeouts++;
		int max=45;
		if ((CoopServer.queue.stream().anyMatch(x->x.thread==this))) {
			max=24*3600;
		}else if(code!=null&&CoopServer.privateMatches.containsKey(code)) {
			max=600;
			if(timeouts%15==0)sendln("hydar");
		}
		if (timeouts > max) {
			alive=false;
		}
	}
	/**Resets the state, i.e. if a back button is pressed*/
	private void reset() {
		queued=false;
		if(player!=null) {//strict equals since this queue can have multiple players
			CoopServer.queue.removeIf(x->(x.thread==this));
		}
		if(code!=null)
			CoopServer.privateMatches.remove(code);
		if(gs!=null&&gs.p1.thread==this) 
			gs.alive=false;
		gs=null;
		code=null;
		matched=false;
	}
	@Override
	public void onClose() {
		reset();
		CONFIG.release(this);
	}
	@Override
	public void onOpen() {
		CONFIG.acquire(this);
	}
}
/**Game server instance. When both players are connected and their data is verified, the game will start.*/
class CoopGameServer extends ServerContext {
	public volatile CoopPlayer p1;
	public volatile CoopPlayer p2;
	public final String map;
	public final int mode;
	public final int reverse;
	public volatile boolean full=false;
	public volatile boolean welcome=false;
	public final String code;
	public static final byte[] WELCOME=("106,214,"+FlashUtils.encode("\nWelcome to Flash Private Server!\nType !help for a list of commands, and report any bugs to Glenn M#9606")+"\n").getBytes();
	public CoopGameServer(CoopPlayer p1, String code, String map, int mode, int reverse) throws IOException {
		this.p1 = p1;
		this.map=map;
		this.mode=mode;
		this.reverse=reverse;
		this.code=code;
		Scheduler.schedule(this::checkAlive,30000);
	}
	
	public void checkAlive() {
		if((p1==null || !(p1.thread instanceof CoopGameClient))&&
				(p2==null || !(p2.thread instanceof CoopGameClient))) {
			this.alive=false;
		}
	}
	public void registerPlayer(CoopPlayer p){
		if(!p1.init&&Objects.equals(p,p1)) {
			p.init=true;
			p1=p;
		}else if(p2==null) {
			p.init=true;
			p2=p;
		}else {
			throw new IllegalStateException("can't join");
		}
		if(p1!=null&&p2!=null&&p1.init&&p2.init) {
			if(p1.thread instanceof CoopGameClient gst1 &&
				p2.thread instanceof CoopGameClient gst2) {
				gst1.setOpponent(p2);
				gst2.setOpponent(p1);
			}
			full=true;
			close();
		}
	}
	public void command(String command) {
		p1.sendln(command);
		p2.sendln(command);
	}
	public void announce(String s){
		String b64 = FlashUtils.encode(s);
		command("106,214,"+b64);
	}
	@Override
	public void onClose() {
		if(p1==null&&p2==null)return;
		else if (p1 !=null && (p2==null||!(p2.thread instanceof CoopGameClient))) {
			p1.thread.close();
		}
		else if (p2 !=null && (p1==null||!(p1.thread instanceof CoopGameClient))) {
			p2.thread.close();
		}
	}
	@Override
	public ClientContext newClient() throws IOException {
		return new CoopGameClient(this);
	}
}
/**Client which is connected to a game server. The packets are still delimited by \n.*/
class CoopGameClient extends LineClientContext {
	public final CoopGameServer parent;
	private volatile CoopPlayer player = null;
	private volatile CoopPlayer opponent = null;
	private int time = 0;
	private int timeouts=0;
	private double lastTime = 0;
	private long lastTest = 0;

	public CoopGameClient(CoopGameServer parent){
		super(CONFIG.BTD5_GAME);
		this.parent=parent;
	}
	/**Initialize this player's opponent and send it's data. This makes them appear in the lobby.*/
	public void setOpponent(CoopPlayer opponent){
		this.opponent=opponent;
		sendln(opponent.forOpponent(player));
	}
	@Override
	public void onData(ByteBuffer src, int len) throws IOException {
		super.onData(src,len);
		if(opponent!=null&&opponent.thread!=null)
			opponent.thread.flush();
		flush();
	}
	/**Wrap a string in a chat packet(b64 deflate)*/
	public void chat(String msg){
		sendln("106,214,"+FlashUtils.encode(msg));
	}
	@Override
	public void onMessage(String line){
		timeouts=0;
		if (lastTest>0) {
			long delta=FlashUtils.now()-lastTest;
			if(delta>5000) {
				double percent= 100 * 1000 * (time - lastTime)/delta;
				String chat = "\"Lag test\" results: The game ran at %.2f%% speed over about 5 seconds"
						.formatted(percent);
				parent.announce(chat);
				lastTest = 0;
			}
		}
		String[] msg = line.split(",");
		if(CoopServer.verbose)
			System.out.println("#incoming: "+line);
		if(player==null||!player.init) {
			if(msg[0].equals("101")) {
				player = CoopPlayer.ofGame(msg,this);
				parent.registerPlayer(player);
			}else {
				onTimeout();
			}
			return;
		}
		if(!parent.full)return;
		switch (msg[0]) {
		case "<policy-file-request/>":
			send(PolicyFileServer.POLICY);
			break;
		case "101":
			break;
		case "103":
			opponent.sendln("104");
			break;
		case "108"://ping
			sendln("108");
			break;
		default:
			if(msg.length>1&&msg[0].equals("106")){
				if(!parent.welcome&&msg[1].equals("203")){
					parent.welcome=true;
					send(CoopGameServer.WELCOME);
					opponent.thread.send(CoopGameServer.WELCOME);
				}
				else if(msg[1].equals("214")&&msg.length>2){
					String chat=FlashUtils.decode(msg[2]);
					if(chat.startsWith("!help")){
						chat("Commands:\n!help !source !lagtest");
					}else if(chat.startsWith("!source")){
						chat("https://github.com/GlennnM/NKFlashServers");
					}else if(chat.startsWith("!lagtest")){
						parent.announce("Starting lag test...");
						lastTest = FlashUtils.now();
						lastTime=time;
					}
				}else if(msg[1].equals("207")&&msg.length>2){
					time = (int) (Double.parseDouble(msg[2]));
				}
			}
			opponent.sendln(line);
			break;
		}
	}
	@Override
	public void onClose() {
		try {
			if(opponent!=null) {
				opponent.sendln("107");
				opponent.thread.flush();
			}
		}finally {
			if(opponent==null||opponent.thread==null||!opponent.thread.alive)
				parent.alive=false;
			CONFIG.release(this);
		}
	}
	/**Override so we don't time out the connection if we are in a private game. <br>
	 * Unlike in battles, private match hosts actually connect to the game server before it starts.
	 */
	@Override
	public void onTimeout(){
		timeouts++;
		int max=45;
		if(parent.code!=null && CoopServer.privateMatches.containsKey(parent.code)) {
			max=600;
			if(timeouts%15==0)sendln("hydar");
		}
		if (timeouts > max) {
			alive=false;
		}
	}

	@Override
	public void onOpen() {
		CONFIG.acquire(this);
	}
}
/**Represents the map and difficulty preferences selected in queue options.*/
enum Constraint{
	EASY_BEGINNER(0,0),EASY_INTERMEDIATE(1,0),EASY_ADVANCED(2,0),EASY_EXPERT(3,0),
	MEDIUM_BEGINNER(0,1),MEDIUM_INTERMEDIATE(1,1),MEDIUM_ADVANCED(2,1),MEDIUM_EXPERT(3,1),
	HARD_BEGINNER(0,2),HARD_INTERMEDIATE(1,2),HARD_ADVANCED(2,2),HARD_EXPERT(3,2);
	private static final String[] BEGINNER = new String[] {"AlpineLake","ZFactor","PumpkinPatch","SantasWorkshop","ExpressShipping","SnowyBackyard","RabbitHoles"};
	private static final String[] INTERMEDIATE = new String[] {"Hearthside","SnakeRiver","HauntedSwamp","Jungle","CountryRoad","LavaFields","WaterHazard","SixFeet","TrickOrTreat"};
	private static final String[] ADVANCED = new String[]{"TheEye","Dollar","TheGreatDivide","ScorchedEarth","RinkRevenge","DuneSea","CryptKeeper","Candyland"};
	private static final String[] EXPERT = new String[]{"Castle","Spider","TreeTops","Runway","DownTheDrain","DarkForest"};
	private final int mapDiff,gameDiff;
	Constraint(int mapDiff, int gameDiff){
		this.mapDiff=mapDiff;
		this.gameDiff=gameDiff;
	}
	public boolean isIn(int[] src) {
		return src.length>=7 && src[mapDiff]==1 && src[gameDiff+4]==1;
	}
	public String getMap() {
		Random rng=ThreadLocalRandom.current();
		String map=switch(mapDiff){
			case 1->map=INTERMEDIATE[rng.nextInt(INTERMEDIATE.length)];
			case 2->map=ADVANCED[rng.nextInt(ADVANCED.length)];
			case 3->map=EXPERT[rng.nextInt(EXPERT.length)];
			default->map=BEGINNER[rng.nextInt(BEGINNER.length)];
		};
		return map+gameDiff;
	}
}
/**Wraps an EnumSet of constraints, providing utility for choosing a compatible map.*/
class Constraints{
	public static final Constraints ALL = new Constraints("1:1:1:1:1:1:1");
	private final EnumSet<Constraint> constraints;
	public Constraints(String src) {
		int[] cons=Arrays.stream(src.split(":")).mapToInt(Integer::parseInt).toArray();
		(constraints=EnumSet.allOf(Constraint.class)).removeIf(x->(!x.isIn(cons)));
	}
	public static String getMap(Constraints c1,Constraints c2) {
		Constraint c = FlashUtils.pickFromIntersect(c1.constraints, c2.constraints);
		return c==null?null:c.getMap();
	}
}
/**Holds player data. Some fields are mutable, as the state advances when the
 * associated thread changes from a lobby client to a game client.*/
class CoopPlayer {
	public final String name;
	public final int id;
	public final int rank;//lobby only
	public final int clan;//lobby only
	public final int specId;//game only
	public final int specLvl;//game only
	public final LineClientContext thread;
	public volatile boolean init=false;
	public volatile Constraints constraints;
	public final String premiums;
	public static CoopPlayer ofGame(String[] msg, LineClientContext o) {
		var x= new CoopPlayer(parseInt(msg[2]),msg[3],0,0,msg[4],parseInt(msg[5]),parseInt(msg[6]),o);
		return x;
	}
	public static CoopPlayer ofLobby(String[] msg, LineClientContext o) {
		var x= new CoopPlayer(parseInt(msg[2]),msg[3],parseInt(msg[4]),parseInt(msg[5]),"",0,0,o);
		x.setConstraints(msg[1]);
		return x;
	}
	private CoopPlayer(int id, String name, int rank, int clan, String premiums, int specId, int specLvl, LineClientContext o) {
		this.name = name;
		this.id = id;
		this.thread = o;
		this.premiums=premiums;
		this.specId=specId;
		this.specLvl=specLvl;
		this.rank=rank;
		this.clan=clan;
		this.constraints = Constraints.ALL;
	}
	public String forOpponent(CoopPlayer player) {
		return "102,"+id+","+name+","+premiums+","+specId+","+specLvl;
	}
	public void sendln(String line){
		thread.sendln(line);
	}
	public void setConstraints(String cs){
		this.constraints = cs==null?Constraints.ALL:new Constraints(cs);
	}
	public String getMap(CoopPlayer p){
		return Constraints.getMap(constraints,p.constraints);
	}
	@Override
	public String toString() {
		return name;
	}
	@Override
	public boolean equals(Object o) {
		if(o instanceof CoopPlayer alt)
			return (this.id==alt.id);
		return false;
	}
	@Override
	public int hashCode() {
		return id;
	}
}
//class for main method
public class CoopServer extends ServerContext{
	public static boolean verbose=false;
	public static final List<CoopPlayer> queue=new CopyOnWriteArrayList<>();
	public static final Map<String, CoopGameServer> privateMatches=new ConcurrentHashMap<>();
	private static final int MIN_PORT=CONFIG.btd5Ports.min();
	private static final int MAX_PORT=CONFIG.btd5Ports.max();
	private static final int PORT_STEP=CONFIG.btd5Ports.step();
	public static volatile int nextPort = MIN_PORT;

	public static int nextPort() {//8127
		return nextPort=(nextPort>MAX_PORT?MIN_PORT:nextPort+PORT_STEP);
	}
	public static CoopPlayer checkQueue(CoopPlayer x){
		for(CoopPlayer n:queue) 
			if(n.getMap(x)!=null&&queue.remove(n)) 
				return n;
		queue.add(x);
		return null;
	}
	@Override
	public void onOpen() {
		System.out.println("Coop server started! IP - "+CONFIG.HOST+":"+getPort());
	}
	@Override
	public void onClose() {
		System.out.println("Coop server stopping. Quick matches won't be alerted");
		privateMatches.forEach((x,y)->y.close()); 
		queue.forEach(x->x.thread.close());
	}
	@Override
	public ClientContext newClient() throws IOException {
		return new CoopClient(this);
	}
}
