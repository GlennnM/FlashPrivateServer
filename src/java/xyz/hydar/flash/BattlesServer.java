 package xyz.hydar.flash;
import static xyz.hydar.flash.FlashLauncher.CONFIG;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import xyz.hydar.flash.BattlesGameClient.TimedAction;
import xyz.hydar.flash.util.FlashUtils;
import xyz.hydar.flash.util.FlashUtils.BasicCache;
import xyz.hydar.flash.util.Scheduler;
import xyz.hydar.net.ClientContext;
import xyz.hydar.net.LineClientContext;
import xyz.hydar.net.ServerContext;
import xyz.hydar.net.TextClientContext;

/**Client context for players in lobby or queues. Messages are delimited with \n.<br>
 * The connection's purpose is fulfilled when the client is redirected to a game server through connect().
 * */
class BattlesClient extends LineClientContext {
	private BattlesPlayer player = null;
	private BattlesGameServer gs=null;
	private boolean quickMatch = false;
	private boolean queued = false;
	private boolean assault = false;
	private boolean matched=false;
	private String code = null;
	private String joinCode = null;
	private int timeouts=0;
	/**pass true to allow \0 delimiter(in case of policy file request)*/
	public BattlesClient() throws IOException {
		super(StandardCharsets.ISO_8859_1,true,CONFIG.BATTLES);
	}
	/**Creates a new private game. It will not start listening for connections yet, <br>
	 * but its first player slot will be initialized.*/
	public void createPrivate() throws IOException{
		quickMatch = false;
		queued = true;
		do{
			code=FlashUtils.upperNoise("B2GARB",14);
		}while (BattlesServer.privateMatches.containsKey(code));
		BattlesGameServer gs = new BattlesGameServer(player,code);
		BattlesServer.privateMatches.put(code, gs);
		sendln("FindingYouAMatch," + code);
	}
	/**Join a private game, if it exists.*/
	private void joinPrivate(String joinCode)  throws IOException{
		sendln("FindingYouAMatch," + joinCode);
		if ((gs=BattlesServer.privateMatches.remove(joinCode))!=null) {
			this.connect(gs,gs.p1);
		} else {
			sendln("CouldntFindYourCustomBattle");
		}
	}
	/**Add the player associated with this context to a queue, or start a game with the player already there.*/
	public void queuePublic() throws IOException{
		var queue = assault?BattlesServer.queue1:BattlesServer.queue2;
		BattlesClient opp=queue.poll();
		if (opp==null) {
			queue.add(this);
		} else {
			BattlesGameServer gs = new BattlesGameServer(opp.player);
			this.connect(gs, opp.player);
		}
		queued = true;
	}
	/**Redirect this client to a game server.*/
	public void connect(BattlesGameServer gs, BattlesPlayer opp) throws IOException{
		gs.start(IntStream.generate(BattlesServer::nextPort).limit(100),true);
		String foundGame="FoundYouAGame," + BattlesServer.ip + "," + gs.getPort() + "," + 0 + ",4480";
		opp.sendln(foundGame);
		//If players have same ID, we allow the first player to join.
		if(Objects.equals(player,opp)) {
			sendln("ServerMessage,please wait a few seconds to prevent desync...");
			opp.sendln("ServerMessage,please wait a few seconds to prevent desync...");
			Scheduler.schedule(()->{
				if(alive) {
					sendln(foundGame);
					flush();
				}
			},5000);
		}else sendln(foundGame);
		opp.thread.flush();
		matched=true;
		queued=false;
	}
	@Override
	public void onMessage(String msg) throws IOException {
		timeouts=0;
		if (BattlesServer.verbose)
			System.out.println("incoming: " + msg);
		String[] cmd = msg.split(",",-1);
		switch (cmd[0]) {
		case "<policy-file-request/>":
			send(PolicyFileServer.POLICY);
			break;
		case "Hello":
			//argument 1 is "version"(float)->can respond with "YourGameIsTooOld"
			//(but we don't)
			break;
		case "FindMeAQuickBattle":
			sendln("GimmeUrPlayerInfo");
			quickMatch = true;
			assault = cmd.length > 3 && (Integer.parseInt(cmd[3]) == 0);
			break;
		case "HeresMyPlayerInfo":
			// set player info
			this.player = new BattlesPlayer(this, cmd);
			if(matched)
				break;
			else if (quickMatch) {
				//public match
				sendln("FindingYouAMatch");
				if(!queued) {
					queuePublic();
					break;
				}
			}else if (!queued) {
				//private match
				if (joinCode != null) 
					joinPrivate(joinCode);
				else
					createPrivate();
			}
			break;
		case "ILeftGame":
			reset();//reset state
			return;
		case "CreateMeACustomBattle":
			sendln("GimmeUrPlayerInfo");
			break;
		case "FindMyCustomBattle":
			if (BattlesServer.privateMatches.containsKey(cmd[2])) {
				joinCode = cmd[2];
				sendln("GimmeUrPlayerInfo");
			} else {
				sendln("CouldntFindYourCustomBattle");
			}
			break;
		case "FindMyGame":
			break;
		case "ImStilHereHostingMyPrivateMatch"://typo intended
			break;
		default:
			alive=false;
			break;
		}
	}
	/**Reset the matchmaking state, such as if leaving a queue.*/
	private void reset() {
		BattlesServer.queue1.remove(this);
		BattlesServer.queue2.remove(this);
		if(code!=null)
			BattlesServer.privateMatches.remove(code);
		matched=queued=quickMatch=false;
		code=joinCode=null;
		//check if we created a server but didn't connect to it
		if(gs!=null&&gs.p1==player) {
			gs.alive=false;
		}
		player=null;
	}
	//Override so we don't time out the connection if we are queued.
	@Override
	public void onTimeout(){
		timeouts++;
		if (BattlesServer.queue1.contains(this) || BattlesServer.queue2.contains(this)) {
			timeouts--;
		}//TODO: timeout in pmatch lobby?
		if (timeouts > 10) {
			close();
			return;
		}
	}
	@Override
	public void onOpen() {
		CONFIG.acquire(this);
	}
	@Override
	public void onClose() {
		//reset state
		reset();
		CONFIG.release(this);
	}
}
/**Game server instance. When both players are connected and their data is verified, the game will start.*/
class BattlesGameServer extends ServerContext{
	public volatile BattlesPlayer p1;
	public volatile BattlesPlayer p2;
	public volatile int round=0;
	public volatile boolean full=false;
	public volatile String reason = "Player disconnected";
	public volatile int map =ThreadLocalRandom.current().nextInt(22);
	public final int seed = ThreadLocalRandom.current().nextInt(20000);
	public final String code;
	public static final byte[] BALANCE = "HeresDaBalance,baseVals;STARTING_ENERGY=50;ENERGY_COST_PER_GAME=5;ENERGY_COST_SPY=3;ENERGY_COST_EXTRA_TOWER=6;ENERGY_REGEN_TIME=720;MEDS_FOR_WIN=5;MEDS_FOR_LOSS=2;BATTLESCORE_FOR_WIN=10;BATTLESCORE_FOR_LOSS=2;SURRENDER_ROUND_REWARD=7;COST_MULTIPLIER_REGEN=1.8;COST_MULTIPLIER_CAMO=2.5;UNLOCK_ROUND_REGEN=8;UNLOCK_ROUND_CAMO=12;STARTING_CASH=650;STARTING_HEALTH=150;CASH_PER_TICK_MINIMUM=0;CASH_PER_TICK_STARTING=250;CASH_PER_TICK_STARTING_DEFEND=60;CASH_PER_TICK_MAXIMUM_DEFEND=3000;GIVE_CASH_TIME=6;FIRST_ROUND_START_TIME=6;ROUND_EXPIRY_TIME_BASE=8;ROUND_EXPIRY_TIME_MUL=1.5;PREMATCH_SCREEN_TIME=30;MAX_BLOON_QUE_SIZE=10;baseValsEnd;bloonSetsid=GroupRed1;type=0;name=Grouped Reds;hotkey=1;quantity=8;interval=0.1;cost=25;incomeChange=1;unlockRound=2;,id=SpacedBlue1;type=1;name=Spaced Blues;hotkey=Shift+1;quantity=6;interval=0.33;cost=25;incomeChange=1;unlockRound=2;,id=GroupBlue1;type=1;name=Blue Bloon;hotkey=2;quantity=6;interval=0.1;cost=42;incomeChange=1.7;unlockRound=4;,id=SpacedPink1;type=4;name=Spaced Pink;hotkey=Shift+2;quantity=3;interval=0.5;cost=42;incomeChange=1.7;unlockRound=4;,id=GroupGreen1;type=2;name=Grouped Greens;hotkey=3;quantity=5;interval=0.08;cost=60;incomeChange=2.4;unlockRound=6,id=SpacedBlack;type=5;name=Spaced Black;hotkey=Shift+3;quantity=3;interval=0.6;cost=60;incomeChange=2.4;unlockRound=6,id=GroupYellow1;type=3;name=Grouped Yellows;hotkey=4;quantity=5;interval=0.06;cost=75;incomeChange=3;unlockRound=8,id=SpaceWhite1;type=6;name=Spaced White;hotkey=Shift+4;quantity=4;interval=0.5;cost=75;incomeChange=3;unlockRound=8,id=GroupPink1;type=4;name=Grouped Pink;hotkey=5;quantity=3;interval=0.05;cost=90;incomeChange=3.6;unlockRound=10,id=SpaceLead1;type=7;name=Spaced Leads;hotkey=Shift+5;quantity=2;interval=1.5;cost=90;incomeChange=3.6;unlockRound=10,id=GroupWhite1;type=6;name=Group White;hotkey=6;quantity=3;interval=0.15;cost=125;incomeChange=5;unlockRound=11,id=SpaceZebra1;type=8;name=Space Zebra;hotkey=Shift+6;quantity=3;interval=0.6;cost=125;incomeChange=5;unlockRound=11,id=GroupBlack1;type=5;name=Black Bloon;hotkey=7;quantity=3;interval=0.15;cost=150;incomeChange=6;unlockRound=12,id=SpaceRainbow1;type=9;name=Space Rainbow;hotkey=Shift+7;quantity=1;interval=1;cost=150;incomeChange=6;unlockRound=12,id=GroupZebra1;type=8;name=Zebra Bloon;hotkey=8;quantity=3;interval=0.18;cost=200;incomeChange=6;unlockRound=13,id=GroupRainbow1;type=9;name=Rainbow Bloon;hotkey=Shift+8;quantity=3;interval=0.18;cost=450;incomeChange=3;unlockRound=13,id=GroupLead1;type=7;name=Grouped Leads;hotkey=9;quantity=4;interval=0.2;cost=200;incomeChange=6;unlockRound=15,id=SpaceCerem1;type=10;name=Space Ceremic;hotkey=Shift+9;quantity=1;interval=1;cost=300;incomeChange=0;unlockRound=15,id=CeremicGroup1;type=10;name=Fast Ceremic;hotkey=0;quantity=1;interval=0.25;cost=450;incomeChange=-5;unlockRound=18,id=SpaceMOABGroup1;type=11;name=Space MOAB;hotkey=Shift+0;quantity=1;interval=5;cost=1500;incomeChange=-60;unlockRound=18,id=Moab2;type=11;name=Fast MOAB;hotkey=O;quantity=1;interval=0.5;cost=1500;incomeChange=-140;unlockRound=20,id=BFB1;type=12;name=BFB;hotkey=Shift+O;quantity=1;interval=4;cost=2500;incomeChange=-350;unlockRound=20,id=FastBFBGroup1;type=12;name=Fast BFB;hotkey=P;quantity=1;interval=0.6;cost=2500;incomeChange=-350;unlockRound=22,id=ZOMG1;type=13;name=ZOMG;hotkey=Shift+P;quantity=1;interval=6;cost=9000;incomeChange=-1500;unlockRound=22;bloonSetsEnd\n".getBytes();
	public static final byte[] BUGS = ("RelayMsg,SentChatMsg," + FlashUtils.encode(
			"Known issues:\n-wins and losses aren't shown\n-wins, losses, battlescore don't update")+"\n")
			.getBytes();
	public static final byte[] WELCOME = ("RelayMsg,SentChatMsg," + FlashUtils.encode(
			"Welcome to Flash Private Server! Please report any bugs to Glenn M#9606 on discord, and use !help for a list of commands.")+"\n")
			.getBytes();
	public static final byte[] SHIFT9 = ("RelayMsg,SentChatMsg,"
			+ FlashUtils.encode("they WILL die,\n(only you can see this)")+"\n").getBytes();
	public static final byte[] HELP = ("RelayMsg,SentChatMsg," + FlashUtils.encode(
			"\nCommands:\n!help - displays this\n!history - displays your most recent 10 matches\n!source - view the source code on GitHub\n!profile <url> - set a profile picture for your opponents to see(140x140)\n!random <count> - generates random tower(s)\n!map <count> - generates random map(s)\n!lagtest - tests game timer vs real time\n!bugs - displays known bugs")+"\n")
			.getBytes();
	public BattlesGameServer(BattlesPlayer p1){
		this(p1,null);
	}
	public BattlesGameServer(BattlesPlayer p1, String code) {
		this.p1 = p1;
		this.code=code;
		Scheduler.schedule(this::checkAlive,30000);
	}
	/**Initialize a player slot. If both are filled, start the game.*/
	public void registerPlayer(BattlesPlayer p){
		if(!p1.init&&Objects.equals(p,p1)) {
			p.init=true;
			p1=p;
		}else if(p2==null) {
			p.init=true;
			p2=p;
		}else {
			throw new IllegalStateException("can't join");
		}
		if(p1!=null&&p2!=null) {
			command("OpponentChangedBattleOptions,"+BattlesServer.MAPIDS.get(map)+",0");//ensure maps aren't desynced
			if(p1.thread instanceof BattlesGameClient gst1 &&
				p2.thread instanceof BattlesGameClient gst2) {
				gst1.side=0;
				gst2.side=1;
				gst1.setOpponent(p2);
				gst2.setOpponent(p1);
				close();
			}
			full=true;
		}
	}
	/**Runs 30 seconds after starting. If the game hasn't started, end it.*/
	public void checkAlive() {
		if((p1==null || !(p1.thread instanceof BattlesGameClient))&&
				(p2==null || !(p2.thread instanceof BattlesGameClient))) {
			this.alive=false;
			cleanup();
		}
	}
	/**Send a packet to both players*/
	public void command(String command){
		p1.sendln(command);
		p2.sendln(command);
	}
	public void command(byte[] command){
		p1.thread.send(command);
		p2.thread.send(command);
	}
	/**Send a chat message to both players*/
	public void announce(String s){
		String b64 = FlashUtils.encode(s);
		command("RelayMsg,SentChatMsg,"+b64);
	}
	/**Runs when any player is ready to start a round, starts the round if both are ready*/
	public synchronized void tryStartRound(){
		if (p1.ready&&p2.ready) {
			if (round == 0) {
				command(WELCOME);
			}
			if (round == 38) {
				announce("SYNCING...");
			}
			command("ServerStartingARound," + ++round);
			p1.ready = false;
			p2.ready = false;
		}
	}
	//not in onClose, since it continues after closed
	public void cleanup() {
		System.out.println("game finished");
		String filename = "FlashLog.txt";
		try(FileWriter fw = new FileWriter(filename, true)){
			String type=code==null?"Quick":"Private";
			String report=toString();
			if(report==null||report.length()==0)
				return;
			report+="\nType: "+type+"\n";
			if(round>0){
				int id1 = p1.id;
				int id2 = p2.id;
				List<String> entries1=BattlesServer.history.computeIfAbsent(id1,x->new CopyOnWriteArrayList<>());
				List<String> entries2=BattlesServer.history.computeIfAbsent(id2,x->new CopyOnWriteArrayList<>());
				if(entries1.size()>4)entries1.remove(0);
				if(entries2.size()>4)entries2.remove(0);
				entries1.add(report);
				if(entries1!=entries2)
					entries2.add(report);
				fw.write(report);
			}
		}catch(Exception ioe) {
			ioe.printStackTrace();
		}
	}
	/**Used by !setround. Requires both players to agree and the round number ot be valid*/
	public void forceSetRound(int newRound) throws IOException{
		if (!(p1.thread instanceof BattlesGameClient gst1)
				|| !(p2.thread instanceof BattlesGameClient gst2)) {
			announce("Invalid game state.");
			return;
		}
		
		if(gst1.actionType!=TimedAction.SETROUND||gst2.actionType!=TimedAction.SETROUND) {
			announce("!setround failed.");
		}else if(newRound<round) {
			announce("The round was too low.");
		}else if(newRound>round+100) {
			announce("The round was too high. Try 100 or less rounds at a time");
		}else {
			while(round<newRound) {
				p1.ready=true;
				p2.ready=true;
				tryStartRound();
			}
		}
		gst1.actionType=TimedAction.NONE;
		gst2.actionType=TimedAction.NONE;
	}
	@Override
	public ClientContext newClient() throws IOException{
		return new BattlesGameClient(this);
	}
	@Override
	public void onOpen() {}
	@Override
	public void onClose() {
		if(p1==null&&p2==null)return;
		else if (p1 !=null && (p2==null||!(p2.thread instanceof BattlesGameClient))) {
			p1.thread.close();
		}
		else if (p2 !=null && (p1==null||!(p1.thread instanceof BattlesGameClient))) {
			p2.thread.close();
		}
	}
	@Override
	public String toString() {
		if (p1 == null || p2 == null || !(p1.thread instanceof BattlesGameClient gst1)
				|| !(p2.thread instanceof BattlesGameClient gst2))
			return "";
		if (p1.win == 1) {
			return p2.name + "," + p2.id + "," + "WIN\n" + p1.name + "," +p1.id + ","
					+ "LOSE\nRound: " + round + ", Approx time: " + gst1.time/60 + "m" + gst1.time%60
					+ "s, Cause: " + reason;
		} else if (p2.win == 1) {
			return p1.name + "," + p1.id + "," + "WIN\n" + p2.name + "," + p2.id + ","
					+ "LOSE\nRound: " + round + ", Approx time: " + gst2.time/60 +  "m" + gst2.time%60
					+ "s, Cause: " + reason;
		}
		return null;
	}

}
/**Client which is connected to a game server. The packets are still delimited by \n.*/
class BattlesGameClient extends LineClientContext {
	public final BattlesGameServer parent;
	public volatile BattlesPlayer player;
	public volatile BattlesPlayer opponent;
	public volatile int side;
	public int win;//0 unknown 1 lose 2 win
	public volatile boolean init = false;
	public int timeouts = 0;

	static enum TimedAction{NONE,LAGTEST, SETROUND};
	public volatile TimedAction actionType=TimedAction.NONE;
	private long lastAction = 0;
	private int actionParam = 0;
	public int time = 0;//in game time
	public volatile boolean syncFailed=false;
	public BattlesGameClient(BattlesGameServer parent){
		super(StandardCharsets.ISO_8859_1,CONFIG.BATTLES_GAME);
		this.parent=parent;
		this.win = 0;
		if(BattlesServer.verbose)
			System.out.println("GST create");
	}
	/**Initialize this player's opponent and send it's data. This makes them appear in the lobby.*/
	public void setOpponent(BattlesPlayer opponent){
		this.opponent=opponent;
		sendln(opponent.forOpponent(player));//FoundYourGame packet
	}
	@Override
	public void onData(ByteBuffer input, int length) throws IOException{
		super.onData(input,length);
		flush();
		if(opponent!=null&&opponent.thread!=null)
			opponent.thread.flush();
	}
	@Override
	public void onOpen() {
		CONFIG.acquire(this);
	}
	@Override
	public void onClose(){
		try {
			if(BattlesServer.verbose)
				System.out.println(opponent);
			if(opponent==null)return;
			opponent.sendln("OpponentDisconnected");
			if (opponent.win == 0 && player.win == 0) {
				opponent.win = 2;
				player.win =1;
			}
		}finally {
			if(opponent==null||opponent.thread==null||!opponent.thread.alive) {
				parent.alive=false;
				parent.cleanup();
			}CONFIG.release(this);
		} 
	}
	@Override
	public void onMessage(String line) throws IOException {
		if (lastAction > 0) {
			double delta=FlashUtils.now()-lastAction;
			if(delta>5000) {
				switch(actionType) {
					case LAGTEST:
						double percent= 100 * 1000 * (time - actionParam)/delta;//time and lastTime are in seconds
						parent.announce("Lag test results: The game ran at %.2f%% speed over about 5 seconds"
								.formatted(percent));
						break;
						
					case SETROUND:
						parent.forceSetRound(actionParam);
						break;
					case NONE:
						break;
				}
				lastAction = 0;
				actionType=TimedAction.NONE;
			}
		}
		timeouts = 0;
		if (BattlesServer.verbose)
			System.out.println("#incoming: " + line);
		String[] msg = line.split(",",-1);
		//handle messages that can be received before fully initialized
		if (this.player == null || !this.player.init) {
			if (msg[0].equals("HeresMyPlayerInfo")) {
				this.player = new BattlesPlayer(this, msg);
				parent.registerPlayer(player);
			}else if(msg[0].equals("GiveMeDaBalance")) 
				send(BattlesGameServer.BALANCE);
			else 
				sendln("GimmeUrPlayerInfo");
			return;
		}
		if(!parent.full) {
			if(msg[0].equals("IChangedBattleOptions")&&msg.length>1) {
				parent.map = BattlesServer.MAPIDS.indexOf(msg[1]);
			}
			return;
		}
		//rest of message after ,
		String tail=msg.length>1?line.substring(line.indexOf(',')):"";
		opponent.sendln(switch (msg[0]) {
			case "IChangedBattleOptions" -> "OpponentChangedBattleOptions" + tail;
			case "IChangedMyTowerLoadout" -> "OpponentChangedTowerLoadout" + tail;
			case "IRequestYourTowerLoadout" -> "OpponentRequestsMyTowerLoadout" + tail;
			case "MyReadyToPlayStatus" -> "OpponentReadyStatus" + tail;
			case "MyGameIsLoaded" -> "OpponentHasLoaded" + tail;
			case "GimmeOpponentSyncData" -> "OpponentRequestsSync" + tail;
			case "IBuiltATower" -> "OpponentBuiltATower" + tail;
			case "ISoldATower" -> "OpponentSoldATower" + tail;
			case "IUpgradedATower" -> "OpponentUpgradedATower" + tail;
			case "IRemovedABloonWave" -> "OpponentRemovedABloonWave" + tail;
			case "IChangedATowerTarget" -> "OpponentTowerTargetChanged" + tail;
			case "IChangedAcePath" -> "OpponentChangedAcePath" + tail;
			case "IChangedTargetReticle" -> "OpponentChangedTargetReticle" + tail;
			case "IUsedAnAbility" -> "OpponentUsedAnAbility" + tail;
			case "ISentABloonWave" -> {
				if (tail.contains("Cerem")) {
					send(BattlesGameServer.SHIFT9);
				}
				yield ("OpponentSentABloonWave" + tail);
			}
			case "ILived" -> {
				this.win = 2;
				yield ("OpponentLived" + tail);
			}
			case "IDied" -> {
				System.out.println("game ended probably");
				this.win = 1;
				parent.reason = "Player died";
				yield ("OpponentDied" + tail);
			}
			case "ISurrender" -> {
				System.out.println("game ended probably");
				this.win = 1;
				parent.reason = "Player surrendered";
				yield ("OpponentSurrendered" + tail);
			}
			case "YouDidntRespondToMySyncs" ->{
				syncFailed=false;
				if(opponent.thread instanceof BattlesGameClient opp) {
					//if received twice without opponent sync, end connection
					if(opp.syncFailed && opp.timeouts>2) {
						opponent.thread.alive=false;
						sendln("OpponentResponseless" + tail);
					}else sendln("OpponentStillSyncing" + tail);
					opp.syncFailed=true;
				}
				yield "OpponentRequestsSync" + tail;//used to be "OpponentDidntGetMySyncResponses";
			}
			case "HeresMySyncData" -> {
				if (msg.length > 1) {
					time = (int) (Double.parseDouble(msg[1]));
				}
				syncFailed=false;
				yield "OpponentSyncRetrieved" + tail;
			}
			case "ImReadyToStartARound" -> {
				player.ready = true;
				parent.tryStartRound();
				yield null;
			}
			case "ILeftGame" -> {
				this.win = 1;
				yield "OpponentDisconnected";
			}
			case "GiveMeDaBalance" -> {
				send(BattlesGameServer.BALANCE);
				yield null;
			}
			case "RelayMsg" -> {
				if(parent.round<1) {
					//Sometimes the loaded packet wasn't sent, so send it again
					if(opponent.relays==0&&player.relays++>14) {
						player.relays = 0;
						opponent.sendln("OpponentHasLoaded");
					}
				}
				yield handleRelay(msg,line);
			}
			default -> null;
		});
	}
	/**used by randomizing commands*/
	private static String uniqueRandom(String[] list, int count,int max) {
		List<String> x=new ArrayList<>();
		count=Math.min(count,max);
		for (int n = 0; n < count; n++) {
			int q = ThreadLocalRandom.current().nextInt(list.length);
			while (x.contains(list[q]))
				q = ThreadLocalRandom.current().nextInt(list.length);
			x.add(list[q]);
		}
		return x.stream().collect(Collectors.joining(", "));
	}
	/**Wrap a string in a chat packet. It is encoded as deflated b64*/
	private void chat(String s) throws IOException{
		sendln("RelayMsg,SentChatMsg," + FlashUtils.encode(s));
	}
	/**Handle relay packets, usually chat packets. A relay packet is just a packet that starts with RelayMsg*/
	private String handleRelay(String[] msg, String line) throws IOException {
		if (msg.length > 1 && msg[1].equals("SentChatMsg")) {
			try {
				String decoded=FlashUtils.decode(msg[2]);
				if(decoded==null)
					return null;
				String[] cmd = decoded.trim().split(" ",2);
				switch (cmd[0]) {
				case "!help":
					send(BattlesGameServer.HELP);
					break;
				case "!history":
					chat(BattlesServer.getHistory(player.id));
					break;
				case "!source":
					chat("https://github.com/GlennnM/NKFlashServers");
					break;
				case "!profile":
					if (cmd.length == 1)
						chat("Current profile picture: " + BattlesServer.getProfile(player.id));
					else {
						if(cmd[1].equals("reset")) {
							chat("Reset profile picture.");
							BattlesServer.removeProfile(player.id, cmd[1]);
						}else if(BattlesPlayer.PROFILES.matcher(cmd[1]).matches()) {
							cmd[1]=cmd[1].startsWith("https://")?cmd[1]:"https://"+cmd[1];
							chat("Set profile picture to: " + cmd[1]);
							BattlesServer.setProfile(player.id, cmd[1]);
						}else chat("URL not permitted. allowed: avatars.ninjakiwi.com");
					}
					break;
				case "!random":
					int numTowers = cmd.length<=1?4:Integer.parseInt(cmd[1]);
					chat(uniqueRandom(BattlesServer.TOWERS,numTowers,10));
					break;
				case "!map":
					int numMaps = cmd.length<=1?1:Integer.parseInt(cmd[1]);
					chat(uniqueRandom(BattlesServer.MAPS,numMaps,10));
					break;
				case "!bugs":
					send(BattlesGameServer.BUGS);
					break;
				case "!lagtest":
					if(actionType!=TimedAction.NONE)break;
					parent.announce("Starting lag test...");
					lastAction = FlashUtils.now();
					actionParam = this.time;
					actionType=TimedAction.LAGTEST;
					break;
				/**
				 * DISABLED
				case "!setround":
					if(actionType!=TimedAction.NONE)break;
					if(!(opponent.thread instanceof BattlesGameClient gst))
						break;
					if(gst.actionType==TimedAction.SETROUND) {
						actionParam=0;
						parent.announce("Set round confirmed, will execute within 5 seconds...");
					}else {
						lastAction = FlashUtils.now();
						actionParam=param==null?parent.round+1:Integer.parseInt(param);
						parent.announce("[!setround] Requesting to start round "+actionParam+". Type !setround within 5 seconds if you want to confirm this");
						
					}
					actionType=TimedAction.SETROUND;
					break;*/
				default:
					return line;
				}
			} catch (Exception e) {
				chat("An error occurred while processing a chat message or command.");
				if(BattlesServer.verbose)
					e.printStackTrace();
			}
		} else
			return line;
		return null;
	}

}
/**Holds player data. Some fields are mutable, as the state advances when the
 * associated thread changes from a lobby client to a game client.*/
class BattlesPlayer {
	public final String name;
	public final int id;
	public final int bs;
	public final int w;
	public final int l;
	public final int decal;
	public final TextClientContext thread;
	public final String profile;
	public volatile int relays=0;
	public volatile int win=0;
	public volatile boolean ready=false;
	public volatile boolean init=false;
	//prevent ip grabbing
	public static final Pattern PROFILES = Pattern.compile("(^https:\\/\\/)?avatars.(nkstatic|ninjakiwi).com\\/mega\\/[a-zA-Z0-9-_]*\\.png$");
	public BattlesPlayer(TextClientContext thread, String[] playerInfo) {
		this(Integer.parseInt(playerInfo[1]), playerInfo[2], Integer.parseInt(playerInfo[3]), playerInfo[5], thread,Integer.parseInt(playerInfo[4]));
	}
	private BattlesPlayer(int id, String name, int bs, String profile, TextClientContext serverThread, int decal) {
		this.name = name;
		this.id = id;
		this.bs = bs;
		this.w = ThreadLocalRandom.current().nextInt(1000);
		this.l = ThreadLocalRandom.current().nextInt(1000);
		this.thread = serverThread;
		this.profile = BattlesServer.getProfile(id) != null ? BattlesServer.getProfile(id) : 
				PROFILES.matcher(profile).matches() ? profile : "Error002";
		this.decal = decal;
	}
	public void sendln(String s){
		if(s!=null)
		thread.sendln(s);
		if(BattlesServer.verbose)
			System.out.println("player.OUT: " + s);
	}
	/**Generate a packet representing this player's data and the game data from opponent's perspective.<br>
	 * This is used by BattlesGameClient::setOpponent
	 * */
	public String forOpponent(BattlesPlayer opponent) {
		if(opponent.thread instanceof BattlesGameClient gst) {
			return Stream.of("FoundYourGame",id,name,bs,decal,profile,gst.parent.seed,gst.side,gst.parent.map,w,l)
					.map(Object::toString)
					.collect(Collectors.joining(","));
		}else return null;
	}
	@Override
	public String toString() {
		return "" + id + name + bs + "" + w + "" + l + "" + profile;
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof BattlesPlayer p)
			return (id==p.id)&&(bs==p.bs);
	return false;
	}
	@Override
	public int hashCode() {
		return id*31+bs;
	}
}

//class for main method
public class BattlesServer extends ServerContext{

	public static final String[] TOWERS = new String[] { "Banana Farm", "Bomb Tower", "Boomerang Thrower", "Dart Monkey",
			"Dartling Gun", "Glue Gunner", "Ice Tower", "Monkey Ace", "Monkey Apprentice", "Monkey Buccaneer",
			"Monkey Village", "Mortar Tower", "Ninja Monkey", "Sniper Monkey", "Spike Factory", "Super Monkey",
			"Tack Shooter" };
	public static final String[] MAPS = new String[] { "Park", "Temple", "Yin Yang", "Cards", "Rally", "Mine", "Hydro Dam",
			"Pyramid Steps", "Patch", "Battle Park", "Ice Flow", "Yellow Brick Road", "Swamp", "Battle River",
			"Mondrian", "Zen Garden", "Volcano", "Water Hazard", "Indoor Pools", "A-Game", "Ink Blot", "Snowy Castle" };
	public static final List<String> MAPIDS = List.of( "Park", "Temple", "YinYang", "Cards", "Rally", "Mine", "HydroDam",
			"PyramidSteps", "Patch", "BattlePark", "IceFlowBattle", "YellowBrick", "Swamp", "BattleRiver",
			"Mondrian", "ZenGarden", "Volcano", "WaterHazard", "IndoorPools", "Agame", "InkBlot", "SnowyCastle" );
	
	public static final Queue<BattlesClient> queue1=new ConcurrentLinkedQueue<>();// assault
	public static final Queue<BattlesClient> queue2=new ConcurrentLinkedQueue<>();// defend
	public static final Map<String, BattlesGameServer> privateMatches = new ConcurrentHashMap<>();
	public static final Map<Integer, String> profiles = BasicCache.synced(1024);
	public static final Map<Integer, List<String>> history = BasicCache.synced(1024);
	public static final String ip=CONFIG.HOST;
	@Override
	public void onOpen(){
		System.out.println("Battles server started! IP - "+CONFIG.HOST+":"+getPort());
	}
	public static boolean verbose=false;

	private static final int MIN_PORT=CONFIG.battlesPorts.min();
	private static final int MAX_PORT=CONFIG.battlesPorts.max();
	private static final int PORT_STEP=CONFIG.battlesPorts.step();
	public static volatile int nextPort = MIN_PORT;

	public static int nextPort() {//8129
		return nextPort=(nextPort>MAX_PORT?MIN_PORT:nextPort+PORT_STEP);
	}
	public static String getHistory(int uid) {
		String h = "\n====================\n";
		List<String> entries = history.get(uid);
		if (entries == null)
			return h;
		StringBuilder compact=new StringBuilder(entries.size()*50);
		for (String e : entries) {
			String[] lines = e.split("\n");
			if(lines.length<4)return h;
			boolean win=(lines[0].contains("" + uid));
			String[] opponent=(win?lines[1]:lines[0]).split(",");
			if(opponent.length<2)return h;
			compact.append((lines[3] + "|"+(win?"win":"lose")+"|" + lines[2] + "|" + "Opponent: "
					+ opponent[0] + '\n'));
		}
		return compact.toString();
	}

	public static String getProfile(int uid) {
		return profiles.get(uid);
	}

	public static void setProfile(int uid, String profile) {
		profiles.put(uid, profile);
	}

	public static void removeProfile(int uid, String profile) {
		profiles.remove(uid);
	}
	@Override
	public void onClose() {
		System.out.println("BTD Battles stopping. Quick matches won't be alerted");
		privateMatches.forEach((x,y)->y.close()); 
		Stream.of(queue1,queue2).flatMap(Queue::stream).forEach(x->x.close());
	}

	@Override
	public ClientContext newClient() throws IOException {
		return new BattlesClient();
	}

}
