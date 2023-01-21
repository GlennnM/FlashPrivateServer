package xyz.hydar.flash.mp;
import static xyz.hydar.flash.mp.FlashLauncher.CONFIG;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import xyz.hydar.flash.util.FlashUtils;
import xyz.hydar.flash.util.Scheduler;
import xyz.hydar.net.ClientContext;
import xyz.hydar.net.ServerContext;



record S4Player(short level, byte[] data){}

/**
 * Individual game server; can have up to 4 clients/bots connected. Each one
 * will have a S4GameClient Most of the multiplayer protocol is in here and
 * S4GameClient. See o2144, o14519, o7788
 */
class S4GameServer extends ServerContext{
	public final int code;
	public final short mode;
	public short seed;
	public final boolean autostart;
	public final Set<S4GameClient> players = new CopyOnWriteArraySet<>();
	public final int mapSet;
	public final AtomicInteger nextId=new AtomicInteger();
	public final long startedAt=FlashUtils.now();
	public volatile short map;
	public volatile byte host=0;
	public volatile short minLvl;
	public volatile short maxLvl;
	public volatile int startAt=-1;
	public volatile boolean started=false;
	public volatile boolean ingame=false;
	public volatile int lastTick=0;
	public volatile int ingameSince=Integer.MAX_VALUE;
	/**
	 * Initializes the game server. This does not bind any port until start() is called.
	 * */
	public S4GameServer(short mode, int code, short auto){
		this.started = false;
		this.autostart = (auto >= 0);
		if (this.autostart) {
			this.minLvl = (short) (auto - 10);
			this.maxLvl = (short) (auto + 10);
		} else {
			this.minLvl = (short) -1000;
			this.maxLvl = (short) 1000;
		}
		this.mode = mode;
		this.code = code;
		// Pick a map based on mode; nm and event maps both have separate id's
		// playing normal on an NM map and vice versa is playable; playing normal on an
		// event map does nothing
		var rng=ThreadLocalRandom.current();
		this.seed = (short)rng.nextInt(0,32768);
		if (this.mode <= 2) {
			//normal/NM
			this.map=S4Server.getMap(mode,rng.nextInt(9));
			this.mapSet=0;
		} else if (this.mode < 100) {
			// event
			this.map=code==0?S4Server.getEventMap():0;
			if(this.map<1000){
				mapSet=map;
				var mapArr=S4Server.EVENT_MAP_SETS[mapSet];
				map=mapArr[rng.nextInt(mapArr.length)];
			}else this.mapSet=-1;
		}else this.mapSet=0;
		// Otherwise it is a contract, but contract lobbies seem to work fine without
		// having added the map.
		// I think the clients try to change the map after joining
		Scheduler.schedule(this::checkAlive,30000);
		
	}
	/**Runs at most once a second when players send a ping or time packet(-9 or -3)<br>
	 * Performs tasks that need to be performed continuously before the game starts, such as updating the auto-start timer and level ranges.<br>Public lobbies(code=0) only.*/
	public void autoTick() throws IOException {
		for(var player:players) {
			if(player.bot)
				player.send((ByteBuffer)null);
		}
		if(started||!autostart)
			return;
		int t = flashTime();
		if(lastTick==0)
			lastTick=t;
		int ticks = (t-lastTick)/1000;
		if(ticks==0)return;
		lastTick=t;
		minLvl-=ticks;
		maxLvl+=ticks;
		if(autostart && startAt != -1 && lastTick > startAt && allLoaded()) {
			getNonBot().startGame(false);
			autoClear();
		}
	}
	/**Updates and resends the auto-start timer as well as finishing the building process if possible.<br>
	 * The building process normally completes when a player finishes building, 
	 * but can also complete from the last building player leaving.*/
	public void autoCheck(){
		if(!ingame&&started&&players.stream().allMatch(x->x.built)) {
			var src=getNonBot();
			if(src!=null)
				src.finishBuild();
			return;
		}else if(started)return;
		if(players.size()==1) {
			autoClear();
		}else if(autostart&&this.allLoaded()){
			if (players.size() == 2) {
				this.startAt = Math.max(startAt,lastTick+20000);
			}else if (players.size() == 4) {
				this.startAt = lastTick;
			}
			if (!this.started) {
				int st = Math.max(0,startAt-lastTick);
				players.forEach(x->x.send(ByteBuffer.allocate(5).put((byte)-13).putInt(st).flip()));
			}
		}
		try {
			autoTick();
		} catch (IOException e) {}
	}
	/**Resets the auto-start timer, i.e. if only 1 player is left or the map changes.*/
	public void autoClear() {
		this.startAt=-1;
	}
	/**Returns true if a level {@code lvl} player can join this lobby.<br>
	 * Returns false if the lobby is started, full, or the level is out of the lobby range.*/
	public boolean allows(short lvl) {
		return (!this.started) && (this.players != null) && (this.players.size() < 4) && (lvl <= maxLvl)
				&& (lvl >= minLvl);
	}
	/**Returns true if all players are level 96 or higher, allowing for a level 101 boost according to the default rules.*/
	public boolean can101(){
		for(S4GameClient g:players){
			if(g!=null&&!g.bot&&g.player!=null&&g.player.level()<96)
				return false;
		}
		return true;
	}
	/**Returns true if all players have finished loading.*/
	public boolean allLoaded(){
		return players.stream().allMatch(x->x.loaded);
	}
	/**Runs 30 seconds after server is created. If no one has connected yet, close the server*/
	public void checkAlive() {
		if(players.isEmpty()) {
			alive=false;
			close();
			dequeue();
		}
	}
	/**Returns whether the game mode of this lobby is an Active Event.*/
	public boolean isEvent() {
		return mode>2&&mode<100;
	}

	/**Returns the first non-bot player connected to the server*/
	public S4GameClient getNonBot() {
		for (S4GameClient player:players)
			if (!player.bot) return player;
		return null;
	}
	@Override
	public void onOpen() {
	}
	@Override
	public void onClose() {
		
	}
	public void dequeue() {
		S4Server.games.computeIfPresent(((long)code<<32)|(mode&-1L),(k,v)->{
			v.remove(this);
			if(v.isEmpty())
				return null;
			else return v;
		});
	}
	public int flashTime() {
		return (int)(FlashUtils.now()-startedAt);
	}
	/**Initializes a new connection context.*/
	@Override
	public ClientContext newClient() throws IOException {
		return new S4GameClient(this);
	}
	
}
class S4GameClient extends ClientContext {
	public volatile S4Player player;
	public final S4GameServer parent;
	public final byte id;
	//game state(started means building started, parent.ingame means actually started)
	//TODO: atomic
	public volatile boolean welcomed=false;
	public volatile boolean loaded=false;
	public volatile boolean built=false;

	public final boolean bot;
	public final int vs;
	
	private float load=0f;
	private short ping=0;
	private short frameTime=1000;
	private boolean valid=false;
	protected ByteBuffer local;//write to self
	protected ByteBuffer remote;//write to peers
	//dummy constructor for bots
	private S4GameClient(S4GameServer parent,short level,int vs) {
		this.parent=parent;
		this.id = (byte)parent.nextId.getAndIncrement();
		this.bot=true;
		this.player = new S4Player(level, switch (vs) {
			case 1 -> S4Server.vsbot;
			case 2 -> S4Server.deadtab;
			default -> S4Server.bot;
		});
		this.vs = vs;
		this.local=null;
		this.remote=null;
		this.loaded=welcomed=built=true;
	}
	/**Initializes the client context.*/
	public S4GameClient(S4GameServer parent) throws IOException {
		super(CONFIG.SAS4_GAME);
		this.parent = parent;
		this.player = null;
		this.vs=0;
		this.id = (byte)parent.nextId.getAndIncrement();
		this.bot=false;
		this.local=alloc(1024);
		this.remote=alloc(1024);
	}
	/**Returns a new byte buffer of size {@code length}, which might be direct depending on settings.*/
	private ByteBuffer alloc(int length) {
		if(opt.out().direct()) {
			return ByteBuffer.allocateDirect(length);
		}else return ByteBuffer.allocate(length);
	}
	/**Returns the local buffer after ensuring it can hold {@code length} bytes, and expanding it or flushing it if not.*/
	protected ByteBuffer local(int length) {
		//System.out.println(""+local.position()+"%"+local.remaining()+"%"+length);
		if(length>=1024) {
			 System.out.println("buffer overflow(local, %d, %s)".formatted(length,local));
			 alive=false;
		}else if(length>=local.capacity()) {
			 local=alloc(Integer.highestOneBit((local.position()+length)<<1)).put(local.flip());
			// System.out.println("local buffer size increased to "+local.capacity());
		}else if(length>=local.remaining()) {
			// System.out.println("flushing local");
			 flushLocal();
		}
		return local;
	}
	/**Returns the remote buffer after ensuring it can hold {@code length} bytes, and expanding it or flushing it if not.*/
	protected ByteBuffer remote(int length) {
		 if(length>=32768) {
			 System.out.println("buffer overflow(remote, %d, %s)".formatted(length,remote));
			 alive=false;
		 }else if(length>=remote.capacity()||(length>=remote.remaining()&&remote.remaining()<CONFIG.scaleEarly)) {
			 remote=alloc(Integer.highestOneBit((remote.position()+length)<<1)).put(remote.flip());
			 //System.out.println("remote buffer size increased to "+remote.capacity());
		 }else if(length>=remote.remaining()) {//TODO:add minimum (config) or multiple buffers?
		//	 System.out.println("flushing remote");
			 flushRemote();
		 }
		 return remote;
	}
	/**Writes the contents of the local buffer to the client associated with this context, and clears it.*/
	protected void flushLocal() {
		if(local.position()==0)return;
		send(local.flip());
		local.clear();
	}
	/**Writes the contents of the local buffer to all the other clients connected to the game server associated with this context, and clears it.*/
	protected void flushRemote() {
		if(remote.position()==0)return;
		for(var client:parent.players) {
			if(client.id!=id) {
				client.send(remote.flip());
				remote.position(remote.limit());
			}
		}remote.clear();
	}
	/**Copies {@code length} bytes from before the current position in the local buffer and appends them to the remote buffer.*/
	protected void forward(int length) {
		if(parent.players.size()==1)return;
		remote(length).put(local.slice(local.position()-length,length));
	}
	/**Registers this client, sending its player data to all peers and sending it all other players' data*/
	public void register() {
		for (S4GameClient g : parent.players) {
			if(!bot){ 
				if(g.id==this.id)
					playerData(g,WriteMode.REMOTE);
				else
					playerData(g,WriteMode.LOCAL);
			}
			if(!g.welcomed&&parent.players.size()>=2){
				g.chat("Welcome to SAS4 Private Server!\n!help for a list of commands.",false);
				if(g.id==parent.host) {
					if(parent.mode==1&&g.player.level()==1)
						g.chat("Try using !map and !start if selection options don't appear.",false);
					else if(parent.isEvent()) {
						if(parent.code!=0) {
							g.chat("A special code changed the game type. "
								+ "If this was unintentional, change the code.\nMap: "
								+ (Arrays.binarySearch(S4Server.EVENT_MAPS,parent.map)+1)
								+ (parent.map<1114?"\nIt wasn't me.":"")
							,false);
						}
					}
				}
				g.welcomed=true;
			}
		}
		parent.autoCheck();
		if(parent.mode==5&&parent.players.size()==3&&!bot)unboost();
	}
	/**Alert all players of the bot specified by target and then register it, since bots don't have output buffers.*/
	public void registerBot(S4GameClient target) {
		playerData(target,WriteMode.ALL);
		target.register();
	}

	/**Updates the loading or building percentage of this client.*/
	public void loadingState(float percent) {
		//System.out.println("id: "+id+" "+percent+"%");
		remote(11)
			.put((byte)-2)
			.putInt(6)
			.put((byte) 7)
			.put(id)
			.putFloat(percent);
		if(parent.autostart){
			parent.autoCheck();
		}
	}
	/**Force loading or building to complete.*/
	public void fullLoad() {
		loadingState(this.load=1.0f);
	}
	/**Change the map, alerting all players. Packets start with -14.*/
	public void setMap(short map) throws IOException {
		if(parent.started)return;
		int index;
		if(map==-1) {
			announce("Invalid map");
		}else if(parent.mode<=2||(parent.mode>=100&&map>=1114&&map<=1119)) {
			changeMap(map);
		}else if(parent.mapSet==-1) {
			announce("Map changes are not enabled");
		}else if((index=Arrays.binarySearch(S4Server.EVENT_MAP_SETS[parent.mapSet],map))>=0) {
			changeMap(map);
			if(parent.code!=0)
				announce("Map changed to "+(index+1));
		}else if(parent.code!=0&&(index=(FlashUtils.shortSearch(S4Server.NORMAL_MAPS, map)))>=0) {
			changeMap(S4Server.EVENT_MAPS[index]);
			announce("Map changed to "+(index+1));
		}else{
			announce("Map not allowed here. Allowed:\n"+S4Server.EVENT_MAP_DESC[parent.mapSet]);
		}
	}
	//implementation for confirmed map change
	private void changeMap(short map) {
		parent.map = map;
		local(5).put((byte)-14).putShort(parent.map).putShort(parent.mode);
		forward(5);
		parent.autoCheck();
	}
	//used for playerData on registering
	enum WriteMode{LOCAL,REMOTE,ALL}
	@Override
	public void onOpen() {
		S4Server.connections++;
		CONFIG.acquire(this);
		local(17)
			.put((byte)-4).put(parent.host).put(id)
			.putInt(parent.flashTime())
			.putShort(parent.map)
			.putShort(parent.mode)
			.putShort(parent.seed)
			.putInt(parent.startAt==-1?0:(parent.startAt-parent.lastTick));
		flushLocal();
	}

	/**ensures that the client is cleaned up properly*/
	@Override
	public void onClose() {
		try {
			flushRemote();
		}finally {
			leave();
			CONFIG.release(this);
		}
	}

	/**Parses SAS4 packets out of a given buffer until more bytes are needed. All full packets will be parsed by {@code parsePacket} and then removed from it.*/
	@Override
	public void onData(ByteBuffer data, int length) throws IOException {
		if(length<0) {
			alive=false;
			return;
		}
		int left=data.position();
		data.flip();
		if(left<1) return;
		int len=0, offset=0;
		while(left>0&&(len=length(data.position(offset))+1)>0) {
			if(left<len)break;
			parsePacket(data.position(offset),offset,len);
			offset+=len;
			left-=len;
		}
		data.position(offset).compact().position(left);
		flushLocal();
		flushRemote();
		//System.out.println("]");
		//byte opcode=data.get();
		//int start=0;
	}

	/**Parses a SAS4 packet out of a given buffer. <br>Incoming first bytes: 0,202 start -> player data -3->time -14->change
	 * map -9->ping -10->loading state -15->building state -2-> game data*/
	public void parsePacket(ByteBuffer buffer,int offset, int actualSize) throws IOException {
		byte opcode=buffer.get();
		//if(offset==0)System.out.print("[");
		//if(offset+actualSize==buffer.limit())
		//System.out.print(""+opcode+":"+offset+"-"+(offset+actualSize)+"/"+buffer.limit()+"");
		//else
		//System.out.print(""+opcode+":"+offset+"-"+(offset+actualSize)+"/"+buffer.limit()+",");
		if(opcode!=0&&player==null) {
			alive=false;
			return;
		}
		switch(opcode){
			case 0:
				if(!parent.started){
					if((buffer.get()!=(byte)0xca) || !parent.players.add(this))break;
					int length = buffer.getInt(offset+10);
					byte[] data = new byte[actualSize-(length*2+14)];
					buffer.get(offset+length*2+14,data);
					this.player = new S4Player(buffer.getShort(offset+8), data);
					register();
					if(parent.players.size()<2){
						if(parent.mode!=7){
							boost((byte)100, 0);
							if (parent.code == 400||Math.abs(parent.code-2089076591)<20) {
								boost((byte)100, 0);
								boost((byte)100, 0);
							}
						}else{
							//System.out.println("else");
							boost((byte)100, 2);
							//parent.boost((byte)100, 1);
							//parent.boost((byte)100, 1);
						}
					}
					valid=validate(data);
					if(parent.code==0&&!valid) {
						chat("You can't join this lobby.\nTry a private/sandbox lobby(code apoc, lms, samp) instead",false);
						flushLocal();flushRemote();
						FlashUtils.sleep(2000);
						alive=false;
						break;
					}
				}else alive=false;
			break;
			case -14:
				setMap(buffer.getShort());
			break;
			case -3:
				local(5)
					.put((byte)-3)
					.putInt(parent.flashTime());
				parent.autoTick();
				break;
			case -9:
				ping=buffer.getShort();
				frameTime=buffer.getShort();
				if(frameTime==0)frameTime++;
				parent.autoTick();
			break;
			case -2:
				if (actualSize<=5)
					return;
				byte subop=buffer.get(offset+6);
				/**if(subop==5||subop==6) {
					var slice=buffer.slice(offset+11,actualSize-11);
					var copy=ByteBuffer.allocate(slice.limit());
					copy.put(slice);
					//System.out.println(""+subop+":"+HexFormat.of().formatHex(copy.array()));
				}*/
				if ((subop == (byte) 0x05) && (actualSize > 25&&buffer.getInt(offset+11)==0x3e7)) {
					byte chat_length = buffer.get(offset+23);
					if(actualSize>=24+chat_length && chat_length>=0) {
						byte[] chars=new byte[chat_length];
						buffer.get(offset+24,chars);
						String[] msg=new String(chars, StandardCharsets.UTF_8).split(" ",2);
						processChat(msg);
					}
					
				}else if(subop==0x07&&actualSize==20) {
					float load=buffer.getFloat(offset+8);
					if(!parent.started) {
						this.load=load;
						if(Math.abs(1.0f-load)<0.0001f&&!loaded) {
							loaded=true;
							fullLoad();
						}else 
							loadingState(load);
					}else if(!parent.ingame &&!built) {
						loadingState(load);
						if(Math.abs(1.0f-load)<0.0001f)
							finishBuild();
					}
					return;
				}//else if(subop==0x09)return;
				//if only bots, dont bother copying buffer
				else if(getPeer()==null) return;
				else if(actualSize>10 && (subop==5||subop==7||subop==2||subop==9)){
					buffer.putInt(offset+7,parent.flashTime());
				}
				int len = actualSize - 6;
				remote(len+5)
					.put((byte)-2)
					.putInt(len)//len bytes after this point
					.put(subop)
					.put(buffer.slice(offset+7,actualSize-7));
			break;
			case -17:
			if(id==parent.host && !parent.started)
				startGame(true);
			break;
			case -15:
			/**""building progress"", but it is always 0 so just ignore it(rely on -2 instead)*/
			break;
			case -10:
			/**same as above but for loading, except it doesn't lie as much, but why bother*/
			break;
		}
	}

	/**
	 * -2 packet with operation -1 as interpreted in o14519; reflects most of the
	 * data from the (0x00ca) packet containing player data. Appended to this is a
	 * byte which goes from 0-100(loaded percentage). Each starting opcode
	 * corresponds to a different function; however -2 is special in that it will
	 * have a "sub-operation" such as -1. Format: |1| operation |4| length IF
	 * type=bytearray |1| suboperation IF type -1 |data| - usually includes id and
	 * level early on See o14519, o2144.
	 */
	public void playerData(S4GameClient source, WriteMode target) {
		int len = source.player.data().length+1;
		int nameLen=len>2?ByteBuffer.wrap(source.player.data()).getShort():0;
		byte loadPercent=source.bot?100:(byte) (source.load*100);
		
		ByteBuffer targetBuffer=(target!=WriteMode.REMOTE)?local(len+5):remote(len+5);
		targetBuffer.put((byte)-2).putInt(len).put((byte)-1).put(source.id)
			.put(source.player.data(),0,nameLen+3)
			.putShort(source.player.level())
			.put(source.player.data(),nameLen+7,len-1-(nameLen+7))
			.put(loadPercent);
		if(target==WriteMode.ALL)
			forward(len+5);
	}
	//Verifies that data contains valid player data
	private static boolean validate(byte[] data) throws IOException {
		var dis=new DataInputStream(new ByteArrayInputStream(data));
		if(dis.available()<7)return false;
		dis.readUTF();
		dis.skip(5);
		int count=dis.read();
		for(int i=0;i<count;i++) {
			if(dis.available()<17)return false;
			int type=dis.read();
			int id=dis.readShort();
			int grade=dis.read();
			float em=dis.readFloat();
			dis.skip(4);
			int tmpLen=dis.read();
			if(dis.available()<tmpLen*2+5)return false;
			dis.skip(tmpLen*2+5);
			int mult=Math.round(em/0.05f);
			boolean recent = type==0&&(id>=181 && id<=190)||(id>=194 && id<=208)||(id==211)||(id==212);
			if(grade>12||(recent&&mult>11) ||(!recent&&mult>21))
				return false;
		}
		if(dis.available()<1||dis.available()<2*(count=dis.read()))return false;
		int total=0;
		for(int i=0;i<count;i++) {
			dis.read();
			int pts=dis.read();
			if(pts>25||(total+=pts)>108)return false;
		}
		return true;
	}

	/**Add a bot, if possible. {@code vs} is 0 for normal bots, 1 for vs bots, and 2 for deadtabs*/
	public void boost(short level,int vs) throws IOException {
		if ((level>101&&(parent.code==0||valid))||(level==101&&!parent.can101())||
				parent.players.size() > 3||parent.started||
				(parent.mode>2&&(parent.code==0||valid)&&level<1)||
				(vs>1&&parent.mode!=7&&parent.mode!=3)) {
			chat("Unable to boost.",false);
			return;
		}
		S4GameClient bot = S4GameClient.newBot(parent, level, vs);
		parent.players.add(bot);
		registerBot(bot);
		announce((vs==0)?("Added 1 bot!"):((vs==1)?"Added 1 VS bot!":"Added 1 deadtab. Make sure to remove deadtabs with !unboost or !disconnect before the game ends!"));
	}

	/**Remove a bot, if possible.*/
	public void unboost() {
		for (S4GameClient player:parent.players)
			if (player.bot) {
				announce("Removed 1 bot!");
				flushLocal();
				flushRemote();
				player.leave();
				return;
			}
		//chat("No bots to remove");
	}
	/**Disconnect and remove this client, and end the game server if it becomes empty.*/
	public void leave() {
		if(!parent.players.remove(this))
			return;
		var source=parent.getNonBot();
		if (source==null) {
			if(parent.alive)
				parent.close();
			parent.dequeue();
			return;
		}
		byte[] pl = { (byte) -6, id };
		parent.players.forEach(x->x.send(pl));
		if (parent.host == id) {
			// change host
			byte[] pl2 = { (byte) -7, id, source.id };
			parent.host = source.id;
			parent.players.forEach(x->x.send(pl2));
		}
		parent.autoCheck();
		if(parent.mode==5 && !this.bot && parent.players.size()==1)
			try {
				boost((short)100,0);
			}catch(IOException ioe) {
				alive=false;
			}
		flushRemote();
	}
	/**start building(start game button), or automatically in event/quickmatch*/
	public void startGame(boolean announce) {
		if(announce)announce("Starting building...");
		System.out.println("-------STARTING GAME " + parent.code + "@"+new Date()+"-------");
		S4Server.gamesStarted++;
		parent.players.forEach(x->x.send(new byte[] {(byte)-16}));
		parent.started = true;
		parent.close();
	}
	/**Called when one player finishes building, attempting to finish the process and start the game.*/
	public void finishBuild() {
		this.built=true;
		if(!parent.ingame && parent.started && parent.players.stream().allMatch(x->x.built)) {
			parent.ingame=true;
			int hydar=parent.flashTime();
			parent.ingameSince=hydar;
			var startlocal=ByteBuffer.allocate(5).put((byte)-11).putInt(hydar+3000);
			parent.players.forEach(x->x.send(startlocal.flip()));
		}
	}
	/**Checks for the length required for the SAS4 packet that starts at {@code input.position()}, or returns -1 if that length is not available.*/
	public int length(ByteBuffer input) {
		int end=input.remaining();
		byte opcode=input.get();
		return switch(opcode) {
			case -9 -> 4;
			case -3,-17 -> 0;
			case -14 -> 4;
			case -10, -15 -> 1;
			case -2 -> (end<=4)? -1 : input.getInt()+4;
			case 0->{
				//normal 0x00ca, then name(UTF), then 1 byte, then int length ->bytes
				int off=input.position()-1;
				int req=20;
				if(end<req)yield -1;
				//add "headers" short[] length
				if(end<(req+=input.getInt(10+off)*2))yield -1;
				//add name length
				if(end<(req+=input.position(off+req-6).getShort()))yield -1;
				//add bytes at end(some kind of serialized dictionaries
				if(end<(req+=input.position(off+req-3).getInt()))yield -1;
				yield req;
			}
			default->{
				if(parent.ingame&&player!=null)
				System.out.println("help :((( "+opcode);
				else alive=false;
				//announce("Invalid packet[%d]. Buffer will be cleared".formatted(opcode));
				//System.out.println(HexFormat.of().formatHex(input.array()));
				//alive=false;
				//input.clear();
				//FlashUtils.sleep(2000);
				yield -2;
			}
		};
	}
	/**Returns a non-bot other than this player*/
	public S4GameClient getPeer() {
		for(var thread:parent.players)
			if(thread.id!=id&&!thread.bot) 
				return thread;
		return null;
	}
	/**Send a chat message.
	 * @param all - whether the message should be seen by all players, or only this player
	 * @param s - the message*/
	public void chat(String s, boolean all) {
		if(this.bot||s==null)return;
		String s2 = "\n[BEGINFONT face='Calibri' size='17' color='#21d91f' CLOSEFONT]"+s+"[ENDFONT]";
		byte[] msg = s2.getBytes(StandardCharsets.UTF_8);
		S4GameClient peer=null;
		for(var thread:parent.players) {
			if(thread.id!=id) {
				peer=thread;break;
			}
		}
		if(peer==null||msg.length>256)return;
		int time = parent.flashTime();
		int len = msg.length + 26;
		local(msg.length+31).put((byte)-2)
			.putInt(len).put((byte) 0x05)
			.putInt(time).putInt(0x3e7)
			.putInt(-1).putShort((short) 0x100)
			.put(peer.id)
			.putShort((short) (msg.length))
			.put(msg).position(local.position()+8);
		if(all) {
			forward(msg.length+31);
			remote.put(remote.position()-(msg.length+11),id);
		}
	}
	/**Send a string in chat to all players*/
	public void announce(String s) {
		chat(s,true);
	}
	/**Process a chat message (array representing a space-delimited chat message) for commands.*/
	public void processChat(String[] msg) throws IOException{
		announce(switch (msg[0].toLowerCase()) {
			case "!source"->"https://github.com/GlennnM/NKFlashServers";
			case "!help"->"Flash Private Server by Glenn M#9606.\nCommands:\n!boost <lvl>, !vsboost, !deadtab, !unboost\n!start, !unlock, !ping, !source, !seed, !stats, !code, !range, !disconnect";
			case "!seed"->"Current seed: "+parent.seed+"\nMap ID: "+parent.map+"\nMode: "+parent.mode;
			case "!code"->"Current code: "+parent.code+"\nMap ID: "+parent.map+"\nMode: "+parent.mode+"\nSpecial codes: 400, apoc, lms, avs, samp";
			case "!range"->"Accepting levels "+parent.minLvl+"-"+parent.maxLvl;
			case "!host"->"You are "+(id==parent.host?"":"not ")+"the host.";
			case "!ping"->"Ping: "+ping+"ms, "+(1000/(0xffff&frameTime))+" reported FPS";
			case "!setseed"->{
				if(!valid&&parent.code!=0&&getPeer()==null) {
					if(msg.length>1&&FlashUtils.isShort(msg[1])) {
						parent.seed=Short.parseShort(msg[1]);
						onOpen();//TODO: try using this to make lobby merging?
						register();
					}
				}yield null;
			}
			case "!boost","!b"->{
				if(msg.length==1)
					boost((short)100, 0);
				else if(FlashUtils.isShort(msg[1]))
					boost(Short.parseShort(msg[1]), 0);
				yield null;
			}case "!vsboost"->{
				if(msg.length==1)
					boost((byte)100, 1);
				else if(FlashUtils.isShort(msg[1]))
					boost(Short.parseShort(msg[1]), 1);
				yield null;
			}case "!deadtab"->{//TODO: dead dead tab
				if(msg.length==1)
					boost((byte)100, 2);
				else if(FlashUtils.isShort(msg[1]))
					boost(Short.parseShort(msg[1]), 2);
				yield null;
			}case "!unboost","!ub","!kickbot"->{
				unboost();
				yield null;
			}case "!stats"->{
				stats();
				yield null;
			}case "!go", "!start"->{
				if(!parent.started && parent.allLoaded()) {
					if(id==parent.host) {
						if(msg.length==1||!parent.autostart)
							startGame(true);
						/**else if(FlashUtils.isInt(msg[1])){
							parent.startAt=parent.lastTick+Integer.parseInt(msg[1])*1000;
							parent.autoCheck();
						}*/
						yield null;
					}else yield "You are not the host";
				}
				else {
					yield "Cannot start game";
				}
			}case "!unlock"->{
				if(parent.started) {
					yield "The game already started";
				}else if(!parent.autostart || (parent.minLvl<0&&parent.maxLvl>100)) {
					yield "This lobby already allows all levels";
				}else if(id==parent.host) {
					parent.minLvl=-1;
					parent.maxLvl=101;
					yield "Now accepting all levels";
				}else yield "You are not the host";
			}
			case "!map"->{
				if(parent.started)
					yield "Game already started";
				else if(msg.length>1&&FlashUtils.isInt(msg[1])){
					int q=Integer.parseInt(msg[1]);
					if(q>0&&((parent.mode>2&&parent.mode<7)||id==parent.host)&&q<=S4Server.EVENT_MAPS.length){
						if(parent.mode>=100) {
							yield "Invalid map";
						}else{
							short m=S4Server.getMap(parent.mode,q-1);
							if(m==-1) {
								yield "Invalid map";//TODO: add av to contract maps
							}else setMap(m);
						}
						yield null;
					}else
						yield "Invalid map/not Apoc or LMS, or you aren't the host.";
				}else
					yield "Specify a map: 1-9 = normal maps, 10+ = contract maps";
			}case "!disconnect","!leave"->{
				this.alive=false;
				yield "Disconnecting player...";
			}default->null;
		});
	}

	/**Send some statistics in chat(!stats)*/
	public void stats() {
		int mb = 1024 * 1024;
		// get Runtime instance
		Runtime instance = Runtime.getRuntime();
		StringBuilder usage = new StringBuilder(100);
		// available memory
		usage.append("Games: ").append(S4Server.getGameCount()).append(", Players: ").append(S4Server.getS4PlayerCount());
		usage.append(", Threads: ").append(Thread.activeCount()).append("\n");
		// used memory
		usage.append("Used Memory: ").append((instance.totalMemory() - instance.freeMemory()) / mb).append(" MB, ");
		// Maximum available memory
		usage.append("Allocated: ").append(instance.totalMemory() / mb).append(" MB");
		usage.append("\nMax Memory: ").append(instance.maxMemory() / mb).append(" MB");
		announce(usage.toString());

		announce("Uptime: "+(Duration.ofMillis(FlashUtils.now()-S4Server.START).toString().toLowerCase().substring(2))+"\nGames started: "+S4Server.gamesStarted);
	}

	public void dequeue() {
		
	}
	/**
	 * Create a new bot and returns it. Uses a private constructor to avoid allocating byte buffers.
	 */
	public static S4GameClient newBot(S4GameServer parent, short level, int vs) {
		return new S4GameClient(parent,level, vs) {
			@Override
			public void send(byte[] src) {}
			@Override
			public void send(byte[] src,int off,int len) {}
			@Override
			public void send(ByteBuffer src) {
				long delta;
				//leave after building+4 minutes(deadtab, not alpha virus), building+6 seconds(vs bot),
				//or right away(normal)
				/**if(parent.ingame&&(parent.flashTime()-parent.ingameSince)>5000&&vs==2&&welcomed) {
					welcomed=false;
					//var die=ByteBuffer.allocate(39).put((byte)-2).putInt(34)
					//		.put((byte)5).putInt(parent.ingameSince).putInt(0x42c2497).
					//		putInt(-1).put(id).putInt(-1).putLong(0).putLong(0);
					//just dc when a player die or dc
					var die=ByteBuffer.allocate(27).put((byte)-2).putInt(22)
							.put((byte)5).putInt(parent.flashTime()).putInt(id).putInt(-1).
							put((byte)0x14).putLong(0);
					var die2=ByteBuffer.allocate(27).put((byte)-2).putInt(22)
							.put((byte)5).putInt(parent.flashTime()).putInt(id).putInt(-1).
							put((byte)0x04).putLong(0);
					parent.players.forEach(x->x.send(die.flip()));
					parent.players.forEach(x->x.send(die2.flip()));
				}*/
				if (parent.started&&(this.vs==0)||
					((delta=parent.flashTime()-parent.ingameSince)>6000&&this.vs==1)||
					(delta>240000&&parent.mode!=3&&this.vs==2)){
					leave();
					this.alive = false;
				}
			}
			@Override
			protected ByteBuffer local(int length){
				throw new UnsupportedOperationException("bots don't have local buffer");
			}
			@Override
			protected ByteBuffer remote(int length){
				throw new UnsupportedOperationException("bots don't have remote buffer");
			}
			@Override
			public void onData(ByteBuffer data, int length){}
			@Override
			public void onTimeout(){}
			@Override
			public void flushRemote(){}
			@Override
			public void flushLocal(){}
		};
	}

}


/**The lobby server, which directs clients to game servers.*/
public class S4Server extends ServerContext{
	public static final long START = FlashUtils.now();
	public static final byte[] bot;
	public static final byte[] vsbot;
	public static final byte[] deadtab;
	public static final Map<Long, Set<S4GameServer>> games= new ConcurrentHashMap<>();
	public static volatile String log = "";
	public static volatile int connections=0;
	public static volatile int gamesStarted=0;
	public static boolean verbose=false;//printing
	public static final String ip=CONFIG.HOST;
	public static final short[] NORMAL_MAPS={1008, 1018, 1067, 1009, 1054, 1043, 1016, 1101, 1110};
	public static final short[] NM_MAPS={1102, 1103, 1104, 1105, 1106, 1107, 1108, 1109, 1112};
	public static final short[] EVENT_MAPS={1092,1093,1094,1095,1096,1099,1100,1111,1113,1114,1115,1116,1117,1118,1119};
	public static final short[][] EVENT_MAP_SETS = { EVENT_MAPS,
			{ 1092, 1093, 1094, 1095, 1099, 1100, 1113, 1114, 1116, 1117, 1118, 1119 },
			{ 1092, 1093, 1095, 1099, 1113, 1114, 1116, 1117, 1119 }, { 1092, 1093, 1099, 1116 },
			{ 1092, 1094, 1095, 1099, 1100, 1114, 1119 } };
	public static final String[] EVENT_MAP_DESC={"All","All except Ice(8),VIP(5),Highway(11)","1, 2, 4, 6, 9, 10, 12, 13, 15","Ons(1), Vac(2), PO(6), Crash Site(12)","??? VS maps or something"};
	private static final ZoneId MINUS8=ZoneId.of("GMT-8");
	private static final LocalDateTime FEB_16_2022;
	private static final int MIN_PORT=CONFIG.sas4Ports.min();
	private static final int MAX_PORT=CONFIG.sas4Ports.max();
	private static final int PORT_STEP=CONFIG.sas4Ports.step();
	public static volatile int nextPort = MIN_PORT;

	static {
		TimeZone MINUS8=TimeZone.getTimeZone("GMT-8");
		Calendar c=Calendar.getInstance(MINUS8);
		c.set(2022,1,16,0,0,0);
		FEB_16_2022=LocalDateTime.ofInstant(c.toInstant(),ZoneId.of("GMT-8"));
		byte[] tmp1,tmp2,tmp3;
		try {
			tmp1 = Files.readAllBytes(Paths.get("bot.bin"));
			tmp2 = Files.readAllBytes(Paths.get("vs.bin"));
			tmp3 = Files.readAllBytes(Paths.get("deadtab.bin"));
		} catch (Exception e) {
			System.out.println("warning: bot data not found(bot.bin, vs.bin, deadtab.bin)");
			tmp1=tmp2=tmp3=new byte[0];
		}
		bot=tmp1;
		vsbot=tmp2;
		deadtab=tmp3;
	}
	//	this.map=(new short[]{1092,1095,1094,1100,1093,1099,1096,1111,1113})[(int)(Math.random()*9)];
	// onsl pods sur ls vac po vip is md
	public static int nextPort() {//8128
		return nextPort=(nextPort>MAX_PORT?MIN_PORT:nextPort+PORT_STEP);
	}
	public static int getGameCount() {
		return games.values().stream().mapToInt(Set::size).sum();
	}
	public static int getS4PlayerCount(){
		return games.values().stream().flatMap(Set::stream).mapToInt(x->x.players.size()).sum();//TODO remove on start
	}
	public static short getMap(short mode, int index){
		var mapArr=switch(mode) {
			case 1->NORMAL_MAPS;
			case 2->NM_MAPS;
			default->EVENT_MAPS;
		};
		if(index>=mapArr.length||index<0) {
			return -1;
		}return mapArr[index];
	}
	public static int offset2(){
		LocalDateTime now=LocalDateTime.now(MINUS8);
		return (int)ChronoUnit.DAYS.between(FEB_16_2022,now);
	}
	
	public static short getEventMap(){
		int eventOffset=offset2();
		long seed=33888522196117857l+eventOffset*3;
		short[] vs=EVENT_MAP_SETS[4];
		return switch(eventOffset % 21){
			case 4,10,15,17,19->vs[FlashUtils.xorRange(seed,vs.length)];
			case 14 -> 3;
			case 16 -> 2;
			case 18 -> 1;
			case 20 -> 0;
			default -> 0;
		};
		// short[]{1092,1095,1094,1100,1093,1099,1096,1111,1113})[(int)(Math.random()*9)];
			// onsl pods sur ls vac po vip is md
			//1092,1093,1094,1095,1096,1099,1100,1111,1113,1114,1115,1116,1117,1118,1119
			//
		
	}
	@Override
	public void onOpen() {System.out.println("SAS4 server started!");}
	@Override
	public void onClose() {}
	/**Initializes a new lobby client. Lobby clients process a single packet type:<br>
	 * incoming format: |2| bytes: 0x00ca(the number 202). This is a version number.<br> |4| bytes: match
	 * code(int). If it was a string it is converted to an int. Yes this means
	 * multiple codes could point to the same lobby<br> |2| bytes: player level(short).
	 * |2| bytes: game mode. 1=Normal 2=NM, 3/4/5/7=events, 100+ = contracts <br>|4|
	 * bytes: amount of values in array. <br>|2| bytes per value: the array. no idea
	 * what it does lol<br><br>
	 * 
	 * outgoing format: |2| bytes: ip length <br>|^| (length) bytes: ip(game server) <br>|2|
	 * bytes: port <br>|2| bytes: mode <br>|4| bytes: code
	 * <br>
	 * See o4356.
	 * <br>
	 * after this exchange this server isn't used as far as i'm aware
	 */
	@Override
	public ClientContext newClient() throws IOException {
		return new ClientContext(CONFIG.SAS4) {
			@Override
			public void onData(ByteBuffer src, int l4) throws IOException{
				if(l4<0) {
					alive=false;
					return;
				}
				int end=src.position();
				if(end<13) {
					return;
				}
				src.position(0);
				
				if(src.getShort()!=0x00ca){
					//System.out.println("probably a file policy request >:(");
					this.alive=false;
					return;
				}
				int arrayLength=src.getInt(10);
				if(end<13+arrayLength*2) {
					return;
				}
				int requestedCode = src.getInt();
				short level = src.getShort();
				short requestedMode=src.getShort();
				var rng=ThreadLocalRandom.current();
				if(requestedCode==2){//"create priv" button
					do requestedCode=rng.nextInt(100_000_000,1_000_000_000);
					while(S4Server.games.get(((long)requestedCode<<32)|(requestedMode&-1L))!=null);
				}
				if(requestedCode==1855064479&&level<99){//ranked-99-100
					requestedCode-=1000000000;
				}
				int code=requestedCode;
				short mode = requestedMode<100?switch(code) {
					case 193486639, 253193259, 1870369476->3;//avs, alpha, alphavirus
					case 193498321, 264330093 -> 4;//lms, last man standing
					case 2090085224, 193502727, 744744362, 2106828268 -> 5;//apoc, poc, apocalypse, sandbox
					case 2090715702, 2107572390, 1168011283 -> 7;//samp, samples, virussamples
					default->requestedMode;
					//extra modes:0="any" 6="contracts", they play exactly like normal games
					//(so not included)
				}:requestedMode;
				long fullCode = ((long)code<<32)|(mode&-1L);
				short autostart = code == 0?level:-1;// quickmatch or event
				
				var list=S4Server.games.computeIfAbsent(fullCode,(k)->new CopyOnWriteArraySet<>());
				S4GameServer g=list.stream()
					.filter(x->x.allows(level))
					.sorted(Comparator.comparingInt(x->-x.players.size()))
					.findFirst()
					.orElseGet(()->new S4GameServer(mode, code, autostart));
				//list.forEach(x->System.out.println(""+x.code+" "+x.alive+" "+x.started+" "+x.players+" "+x.port));
				if(list.add(g)) {
					g.start(IntStream.generate(S4Server::nextPort).limit(100), isNio());
				}
				//System.out.println(games);
				//byte[] data = new byte[arrayLength*2];
				//src.get(14,data);//"the array"(discard)
				byte[] ip = S4Server.ip.getBytes(StandardCharsets.UTF_8);
				//System.out.println("Started GS on "+g.getPort());
				var pl = ByteBuffer.allocate(10+ip.length)
					.putShort((short)ip.length) 
					.put(ip)
					.putShort((short)g.getPort())
					.putShort(mode)
					.putInt(code);
				send(pl.flip());
				alive=false;
			}
			@Override
			public void onOpen() {
				connections++;
				CONFIG.acquire(this);
			}
			@Override
			public void onClose() {
				CONFIG.release(this);
			}
		};
	}
}
