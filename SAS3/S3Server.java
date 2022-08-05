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
import java.util.Timer;
import java.util.TimerTask;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.net.ServerSocket;

/**
 * @TODO 
 * powerups-done
 * death/revive
 * targeting
 * dev attacks
 * game end
 * purge+apoc mode
 * winning/xp
 * performance
 * make mgl work-done
 * leaving-done
 * test mp
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
	public volatile int id;
	public volatile int myPlayerNum;
	public volatile int kills;
	public volatile long startTime;
	// constructor initializes socket
	public void doubleWrite(String s) throws IOException {
		this.output.write(s.getBytes(StandardCharsets.UTF_8));
		//this.output.flush();
		if(startTime!=0)
			this.output.write(("\0"+"%xt%13%-1%"+(new Date().getTime()-startTime)+"%0%").getBytes(StandardCharsets.UTF_8));
		else
			this.output.write(("\0"+"%xt%13%-1%"+0+"%0%").getBytes(StandardCharsets.UTF_8));
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
		myPlayerNum=-1;
		mode=0;nm=0;kills=0;
		id=(int)(Math.random()*999999999);
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
			while (this.alive) {
				int l4=0;
				int actualSize=0;
				int max=1024;
				char[] buffer = new char[max];
				try {
					l4 = ir.read(buffer, 0, max);
					actualSize+=l4;
				} catch (java.net.SocketTimeoutException ste) {
					/**
					 * kill after multiple timeouts
					 */
					//ste.printStackTrace();
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
					String[] msgs = headers.split("\0");
					for(String m:msgs){
					if(!m.endsWith("%"))
						m+="%";
					if(!m.startsWith("%"))
						m="%"+m;
					}
					for (String m : msgs) {
						//String[] msg = m.split(",");
						if(m.equals("<msg t='sys'><body action='verChk' r='0'><ver v='165' /></body></msg>"))
							toSend +="\0<msg t='sys'><body action='apiOK' r='0'><ver v='165'/></body></msg>\n";
						else if(m.equals("<msg t='sys'><body action='login' r='0'><login z='SAS3'><nick><![CDATA[]]></nick><pword><![CDATA[]]></pword></login></body></msg>"))
							toSend +="\0<msg t='sys'><body action='logOK' r='0'><login id='"+id+"' mod='0' n='SAS3'/></body></msg>\n";
						else if(m.equals("<msg t='sys'><body action='getRmList' r='-1'></body></msg>"))
							toSend +="\0<msg t='sys'><body action='rmList' r='-1'><rmList><rm></rm></rmList></body></msg>\n";
						else if(m.startsWith("%xt%SAS3%%")){
							ArrayList<String> msg = new ArrayList<String>(Arrays.asList(m.split("%")));
							int cmd = Integer.parseInt(msg.get(5));
							if(cmd==6){
								
							}
							if(cmd==7){
								kills+=room.parseDamage(msg.get(8));
								
							}
							if(cmd==3){
								if(room==null){
									mode=Integer.parseInt(msg.get(12));
									nm=Integer.parseInt(msg.get(13));
									this.name=msg.get(7);
									this.rank=Integer.parseInt(msg.get(8));
										for(Room r:S3Server.games){
											if(r.mode==mode&&r.nm==nm&&r.players.size()<4){
												r.players.add(this);
												this.room=r;
												break;
											}
										}
									if(this.room==null){
										this.room=new Room(this,mode,nm);
										S3Server.games.add(this.room);
									}
									this.myPlayerNum=room.players.size()-1;
								}
								long time = new Date().getTime() / 1000;
								ArrayList<String> playerNames=new ArrayList<String>();
								ArrayList<Integer> playerRanks=new ArrayList<Integer>();
								ArrayList<Boolean> readyList=new ArrayList<Boolean>();
								room.players.forEach(x->{playerNames.add("\""+x.name+"\"");playerRanks.add(x.rank);readyList.add(x.ready);});
								if(!hydar){
										for(ServerThread x:room.players){
									toSend="\0"+"%xt%15%"+0+"%-1%1%"+room.players.size()+"%"+x.myPlayerNum+"%"+playerNames+"%"+playerRanks+"%"+readyList+"%1%"+mode+"%"+(mode)+"%"+room.map;
											if(x!=this){
												try{
													x.doubleWrite(toSend);
												}catch(Exception e){
													e.printStackTrace();
												}
											}
										}
									
									timeouts++;	//doubleWrite(toSend);+room.players.size()+"%0%"+playerNames+"%"+playerRanks+"%"+readyList+"%1%\""+mode+"\"%"+nm+"%%%%%"
								}else 
								hydar=true;
							}else if(cmd==26){
								
								long time = new Date().getTime() / 1000;
								time=0;
								ArrayList<String> playerNames=new ArrayList<String>();
								ArrayList<Integer> playerRanks=new ArrayList<Integer>();
								ArrayList<Boolean> readyList=new ArrayList<Boolean>();
								room.players.forEach(x->{playerNames.add("\""+x.name+"\"");playerRanks.add(x.rank);readyList.add(x.ready);});
								//toSend+="\0"+"%xt%25%"+time+"%%1%"+room.players.size()+"%"+this.myPlayerNum+"%"+playerNames+"%"+playerRanks+"%"+readyList+"%1%"+mode+"%"+(mode)+"%"+room.mapURL+"%"+room.nm+"%"+room.nm;
										for(ServerThread x:room.players){
											try{
												toSend="\0"+"%xt%25%"+0+"%%1%"+room.players.size()+"%"+x.myPlayerNum+"%"+playerNames+"%"+playerRanks+"%"+readyList+"%1%"+mode+"%"+(mode)+"%"+room.mapURL+"%"+room.nm+"%"+room.nm;
												x.doubleWrite(toSend);
											}catch(Exception e){
												e.printStackTrace();
											}	
										}
								
										//room.writeAll(toSend);
								
							}
							msg.set(2,""+cmd);
							long time;
							if(startTime!=0)
								time= new Date().getTime()-startTime;
							else time=0;
							msg.set(3,""+time);
							msg.set(4,""+-1);
							
							if(cmd==4){
							this.ready=true;
							time = new Date().getTime() / 1000;
							time=0;
							startTime=new Date().getTime();
								ArrayList<String> playerNames=new ArrayList<String>();
								ArrayList<Integer> playerRanks=new ArrayList<Integer>();
								ArrayList<Boolean> readyList=new ArrayList<Boolean>();
								room.players.forEach(x->{playerNames.add("\""+x.name+"\"");playerRanks.add(x.rank);readyList.add(x.ready);});
									//toSend+="\0"+"%xt%15%"+time+"%1%"+room.players.size()+"%"+room.indexOf(id)+"%"+playerNames+"%"+playerRanks+"%"+readyList+"%1%"+mode+"%"+(mode)+"%"+room.map;
									//	room.writeFrom(id,toSend);
									//toSend="";
									//toSend+="\0"+"%xt%26%-1%"+time+"%1.0"+"%"+room.nm+"%";
									//	room.writeAll(toSend);
									//toSend="";
								//	toSend="\0"+"%xt%17%-1%"+time+"%1"+"%"+10+"%";
								//		room.writeAll(toSend);
									//sendAfter=1;
									//msg.remove(5);
									if(!room.setup)
										room.setup();
									msg.set(3,"0");
									msg.set(msg.size()-2,""+room.SBEmult);
									msg.set(msg.size()-1,""+room.barriHP);
								String tmp = msg.get(3);
								msg.set(3,msg.get(4));
								msg.set(4,tmp);
								msg.remove(5);
									//msg.add(""+room.nm);
									//room.writeAll(pl);
									//pl
							}
							ArrayList<Integer> pc = new ArrayList<Integer>(Arrays.asList(new Integer[]{1,2,13,9,7,10,23,22,20,19,12,5,6,24,14,11,26}));
							if(pc.contains(cmd)&&cmd!=26){
								msg.remove(2);
								msg.remove(2);
								//msg.remove(msg.get(2));
								String tmp = msg.get(2);
								msg.set(2,msg.get(3));
								msg.set(3,tmp);
								msg.set(5,""+myPlayerNum);
								//msg.remove(3);
								//msg.add("");
							}
							String pl = "\0"+String.join("%",msg)+"%";
							//if(cmd==4){
							//	pl+=""+room.nm+"%";
							//}
							//room.writeAll(pl);
							if(pc.contains(cmd))
								room.writeFrom(this.id,pl);
							else 
							room.writeAll(pl);
							
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
						else System.out.println("UNKNOWN****************************");
					}
					try {
						Thread.sleep(1);
					} catch (InterruptedException ee) {
						Thread.currentThread().interrupt();
					}
				} else {
					timeouts++;
					if (timeouts > 30) {
						this.alive = false;
						output.close();
						input.close();
						ir.close();
						this.client.close();
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
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		this.alive = false;
		if(room!=null){
			room.dropPlayer(id);
		}
		return;
	}
}
class WaveStartTask extends TimerTask{
	public Room room;
	
	public WaveStartTask(Room room){
		this.room = room;
		room.p1=0;
		room.p2=0f;
		room.p3=0f;
	}
	@Override
	public void run(){
		//send WaveStrtCommand
		if(!room.alive)
			return;
		room.p1=0;
		room.p2=0.0f;
		room.p3=0f;
		room.writeAll("\0"+"%xt%17%-1%"+(new Date().getTime()-room.players.get(0).startTime)+"%"+room.wave+"%"+room.waveTotal+"%");
		room.wave++;
		room.timer.schedule(new SpawnTask(room),5000);
		room.timer.schedule(new PowerupTask(room),30000);
		room.init=true;
		room.r=false;
	}
}
class SpawnTask extends TimerTask{
	public Room room;
	public static AbstractZombie[] zombies = new AbstractZombie[]{
			new AbstractZombie(0,1),
			new AbstractZombie(1,4),
			new AbstractZombie(2,10),
			new AbstractZombie(3,12),
			new AbstractZombie(4,14),
			new AbstractZombie(5,16),
			new AbstractZombie(6,999),
			new AbstractZombie(7,999),
			new AbstractZombie(8,999),
			new AbstractZombie(9,18),
			new AbstractZombie(10,999),
			new AbstractZombie(11,999)
			};
	public SpawnTask(Room room){
		this.room = room;
	}
	@Override
	public void run(){
		float loc2 = room.commaHash();
		ArrayList<AbstractZombie> loc3 = new ArrayList<AbstractZombie>();
		for(AbstractZombie z:zombies){
			if(!(room.map==1&&z.index==9)&&z.weight<=loc2){
				loc3.add(z);
			}
		}
		System.out.println("s3/"+loc3.size());
		//create loc5
		float chanceSum=0.0f;
		for(AbstractZombie z:loc3)
			chanceSum+=z.chance;
		float loc7=1;//locals from -;
		int loc8_=loc3.size()-1;//locals from -;
		float[] loc5 = new float[loc3.size()];
		while(loc8_>=0){
			loc5[loc8_]=loc7;
			loc7 -= loc3.get(loc8_).chance / chanceSum;
			loc8_--;
		}
		
		float loc6=room.dashL(loc2);
		loc7=loc6*2500f/1000f;
		room.p3+=loc7;
		float loc8 = room.p3-room.p2;
		
		float loc10=0f;
		AbstractZombie loc12=null;
		float loc16=0f;
		int loc17=0;
		float loc13=0f;
		System.out.println("l8");
		System.out.println(loc8);
		while(loc10<loc8){
			loc12 = loc3.get(loc3.size() - 1);
            loc16 = (float)Math.random();
            loc17 = 0;
            while(loc17 < loc5.length)
            {
               if(loc16 < loc5[loc17])
               {
                  loc12 = loc3.get(loc17);
                  break;
               }
               loc17++;
            }
			loc13 = ((loc12.cap - 1f) * 0.5f + 1f) * room.SBEmult;
            loc10 += loc13;
            room.p2 += loc13;
			room.spawn(loc12);
			
		}
		room.p1++;
		
		if(!room.alive)
			return;
		if(room.p1 < 24)
         {
			/**if(this.§]3§(§3H§.§-O§,3))
			 {
				this.§60§.delay = §6=§ + (Math.random() * §="§ - §="§ / 2);
			 }
			 else
			 {
				this.§60§.delay = 1;
			 }
			 this.§60§.reset();
			 this.§60§.start();*/
			 if(room.bracket3(3))
				room.timer.schedule(new SpawnTask(room),2500);
			 else
				room.timer.schedule(new SpawnTask(room),1000);
			 
         }
         else
         {
			// if(room.init && room.p1 >=24 && !(room.bracket3(3)))
			
			// {
				
			//	room.timer.schedule(new WaveEndTask(room),1);
				
			// }
         }
	}
}
class WaveEndTask extends TimerTask{
	public Room room;
	public WaveEndTask(Room room){
		this.room = room;
	}
	@Override
	public void run(){
		if(!room.alive)
			return;
		if(room.zombies.size()>0){
			String hitCmd="\0%xt%7%-1%0%-1%";
			synchronized(room.zombies){
				for(Zombie z:room.zombies){
					hitCmd+=z.number+":"+"999999:d,";
				}
			}room.zombies=new CopyOnWriteArrayList<Zombie>();
			hitCmd=hitCmd.substring(0,hitCmd.length()-1)+"%";
			room.writeAll(hitCmd);
		}
		room.writeAll("\0"+"%xt%18%-1%"+0+"%"+room.wave+"%"+room.waveTotal+"%");
		room.timer.schedule(new WaveStartTask(room),1000);
	}
}
class AbstractZombie{
	public int index;
	public int weight;//6c
	public float chance;//function 8o
	public float cap;//%O
	public volatile float hp;
	public AbstractZombie(int index, int weight){
		this.index=index;
		this.weight=weight;
		switch(this.index){
			case 0:
				chance=100f;
				cap=1f;
				hp=160f;
				break;
			case 1:
				chance=30f;
				cap=1.5f;
				hp=100f;
				break;
			case 2:
				chance=15f;
				cap=6f;
				hp=500f;
				break;
			case 3:
				chance=5f;
				cap=15f;
				hp=3000f;
				break;
			case 4:
				chance=2f;
				cap=12f;
				hp=2500f;
				break;
			case 5:
				chance=1f;
				cap=108f;
				hp=4000f;
				break;
			case 6:
				chance=0f;
				cap=36f;
				hp=3000f;
				break;
			case 7:
				chance=0f;
				cap=12f;
				hp=2000f;
				break;
			case 8:
				chance=0f;
				cap=4f;
				hp=1000f;
				break;
			case 9:
				chance=0.1f;
				cap=600f;
				hp=30000f;
				break;
			case 10:
				chance=0f;
				cap=3f;
				hp=300f;
				break;
			case 11:
				chance=0f;
				cap=0.5f;
				hp=100f;
				break;
			case 12:
				chance=0f;
				cap=1f;
				hp=80000f;
				break;
			default:
				chance=0.0f;
				cap=0f;
				hp=0f;
				break;
		}
		
	}
	

	public String toString(){
		return ""+index;
	}
}

class Zombie extends AbstractZombie{
	public int number=0;
	public int parent=-1;
	public Zombie(int index, int weight, int number, float SBEmult){
		super(index,weight);
		this.number=number;
		hp = hp * ((SBEmult - 1f) * 0.5f + 1f);
	}
	public Zombie(int index, int weight, int number, int parent, float SBEmult){
		super(index,weight);
		this.parent=parent;
		this.number=number;
		hp = hp * ((SBEmult - 1f) * 0.5f + 1f);
	}
	public String toString(){
		return ""+number+"("+index+"):"+hp;
	}
	
}
class PowerupTask extends TimerTask{
	public Room room;
	public final int[] GUNS = new int[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43};
	public final int[] GRENADES = new int[]{0,1,2,3,3};
	public final int[] SENTRIES = new int[]{0,1};
	public PowerupTask(Room room){
		this.room = room;
	}
	@Override
	public void run(){
		int loc6 = (int)(Math.random() * this.room.powerups.length);
		int loc7 = this.room.powerups[loc6];//loc8::"point"
		for(ServerThread player:room.players){
			float loc2 = 2f * Math.min(0.083f,(float)player.rank * 0.0032f + 0.0138f);
			float loc3 = 0f * 2f * loc2;
			float loc4 = 0.5f * (1f - loc2 - loc3);
			float loc5 = 0.3f * (1f - loc2 - loc3);
			int loc10 = 0;
			float loc11 = (float)Math.random();
			
			int type=0;
			int subtypes=0;
			float loc9 = 0f;//subtypes
			String name="";
			int item=0;
			/**
			*0,2,"grenades"
			*1,1,"ammo"
			*2,1,"shield"
			*3,37,"gun"
			*4,2,"sentry"
			*5,1,"cash"
			*/
			if(loc11 < loc2)
			 {
				type=4;
				subtypes=2;
				loc9=Math.max(1,Math.min(2f,(player.rank - 5) / 15 + 1));
				name="sentry";
			 }
			 else if(loc11 < loc3 + loc2)
			 {
				type=2;
				subtypes=1;
				loc9=1;
				name="shield";
			 }
			 else if(loc11 < loc5 + loc3 + loc2)
			 {
				type=0;
				subtypes=2;
				loc9=2;
				name="grenades";
			 }
			 else if(loc11 < loc4 + loc5 + loc3 + loc2)
			 {
				type=3;
				loc9 = 10;
				subtypes=37;
				loc10 = Math.round(Math.min(player.rank + 1,37f - loc9));
				if(loc11 > 0.8f)
				{
				   loc11 = (loc11 - 0.8f) * 6f + 0.8f;
				}
				name="gun";
			 }
			 else
			 {
				type=5;
				loc9=1;
				subtypes=1;
				name="cash";
			 }
			 int loc12=(int)(Math.random() * loc9) + loc10;
			 if(loc12 >= subtypes)
			 {
				loc12 = subtypes - 1;
			 }
			 switch(type)
			 {
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
				   item=0;
				   break;
			 }
			 try{
					player.doubleWrite("\0"+"%xt%16%-1%"+0+"%"+loc7+"%"+type+"%"+item+"%");
			 }catch(IOException e){
				 
			 }
		}
	}
}
class Room {
	public volatile CopyOnWriteArrayList<ServerThread> players;
	public int mode;
	public int nm;
	public int id;
	public int map;
	public volatile CopyOnWriteArrayList<Zombie> zombies;
	public String mapURL;
	public volatile int rankSum;
	public volatile float SBEmult;
	public volatile float barriHP;
	public volatile Timer timer;
	public volatile boolean setup=false;
	public volatile boolean init=false;
	public volatile boolean r=true;
	//SPAWNER ARGS
	public volatile int p1=0;//§[D§
	public volatile float p2=0f;//§]?§
	public volatile float p3=0f;//§5-§
	public volatile int wave=1;
	public volatile int waveTotal;
	public volatile boolean alive=true;
	public volatile int nextSpawnNum=99;
	
	public volatile int[] spawns;
	public volatile int[] powerups;
	
	public static final String[] MAP_URLS=new String[]{"http://sas3maps.ninjakiwi.com/sas3maps/FarmhouseMap.swf","http://sas3maps.ninjakiwi.com/sas3maps/AirbaseMap.swf","http://sas3maps.ninjakiwi.com/sas3maps/KarnivaleMap.swf","http://sas3maps.ninjakiwi.com/sas3maps/VerdammtenstadtMap.swf","http://sas3maps.ninjakiwi.com/sas3maps/BlackIsleMap.swf"};
	
	public void spawn(AbstractZombie z){
			int spawn = spawns[(int)(Math.random() * spawns.length)];
			String spawnCmd="\0%xt%9%-1%0%0%"+z.index+"%"+spawn+"%"+SBEmult+"%"+nextSpawnNum+"%-1%";
			zombies.add(new Zombie(z.index,z.weight,nextSpawnNum,SBEmult));
			nextSpawnNum++;
			writeAll(spawnCmd);
		
	}
	public void spawn(AbstractZombie z, Zombie parent){
			int spawn = spawns[(int)(Math.random() * spawns.length)];
			String spawnCmd="\0%xt%9%-1%0%0%"+z.index+"%"+spawn+"%"+SBEmult+"%"+nextSpawnNum+"%"+parent.number+"%";
			zombies.add(new Zombie(z.index,z.weight,nextSpawnNum,SBEmult));
			nextSpawnNum++;
			writeAll(spawnCmd);
		
	}
	public int parseDamage(String dmgStr){
		System.out.println(zombies);
		int kills=0;
		String[] damages = dmgStr.split(",");
		String hitCmd="\0%xt%7%-1%0%-1%";
		boolean sendHit=false;
		for(String s:damages){
			String[] params=s.split(":");
			if(params.length>2&&params[2].equals("d")){
				System.out.println("killing zombie");
				kill(Integer.parseInt(params[0]));
				kills++;
			}else{
				int num=Integer.parseInt(params[0]);
				//boolean killed=room.damage(Integer.parseInt(params[0]),Integer.parseInt(params[1]));
				for(Zombie z:zombies)
					if(z.number==num){
						sendHit=true;
						z.hp-=Integer.parseInt(params[1]);
						if(z.hp<=0){
							hitCmd+=z.number+":"+"999999:d,";
							finishKill(z);
							kills++;
						}break;
					}
			}
		}
		if(sendHit){
			hitCmd=hitCmd.substring(0,hitCmd.length()-1)+"%";
			writeAll(hitCmd);
		}return kills;
	}
	public boolean damage(int number, float dmg){
		Zombie z1=null;
			
		for(Zombie z:zombies){
			if(z.number==number){
				z1=z;
				z.hp-=dmg;
				if(z.hp<=0){
					forceKill(z);
					return true;
				}
				break;
			}
		}
		return false;
	}
	public void forceKill(Zombie z){
		String hitCmd="\0%xt%7%-1%0%-1%"+z.number+":"+"999999:d%";
		
		hitCmd=hitCmd.substring(0,hitCmd.length()-1)+"%";
		writeAll(hitCmd);
		kill(z.number);
	}
	public void kill(int number){
		Zombie z1=null;
			
			for(Zombie z:zombies){
				if(z.number==number&&z.hp>0){
					z1=z;
					z.hp=0;
					//zombies.remove(z);
					break;
				}
			}
		
		if(z1!=null){
			finishKill(z1);
		}
		
	}
	public void finishKill(Zombie z1){
		if(z1.index==5){
			spawn(new AbstractZombie(6,999),z1);
			spawn(new AbstractZombie(6,999),z1);
		}
		else if(z1.index==6){
			spawn(new AbstractZombie(7,999),z1);
			spawn(new AbstractZombie(7,999),z1);
		}
		else if(z1.index==7){
			spawn(new AbstractZombie(8,999),z1);
			spawn(new AbstractZombie(8,999),z1);
		}
		else if(z1.index==3){
			spawn(new AbstractZombie(11,999),z1);
			spawn(new AbstractZombie(11,999),z1);
			spawn(new AbstractZombie(11,999),z1);
			spawn(new AbstractZombie(11,999),z1);
			spawn(new AbstractZombie(11,999),z1);
		}else{
			System.out.println("%"+p1);
			if(init && p1 >=24 && !(bracket3(3))&&!r)

			 {
				System.out.println("||||||Wave end");
				r=true;
				timer.schedule(new WaveEndTask(this),1);
				
			 }
		}
	}
	public boolean bracket3(int max){
		float sum=0;
			for(Zombie z:zombies){
				if(z.hp>0)
					sum+=z.cap;
				if(sum>max)
					return true;
			}
		return false;
	}
	public int[] spawns(){
		switch(this.map){
			case 1:
				return new int[]{9,6,5,8,11,10,4};
			case 2:
				return new int[]{18,17,16,13,12,11,15,14,19};
			case 3:
				return new int[]{31,37,38,35,34,33,32,36};
			case 4:
				return new int[]{13,12,14,31};
			case 5:
				return new int[]{43,42,41,40,47,7,46,44,9,17,4,45};
			default:
				return new int[]{};
		}
	}
	public int[] powerups(){
		switch(this.map){
			case 1:
				return new int[]{0,1,2,3};
			case 2:
				return new int[]{0,1,2,3,4,5,6,7,8,9,10};
			case 3:
				return new int[]{1,3,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30};
			case 4:
				return new int[]{0,2,3,6,7,12,13,14,15,17,21,26,33,35,37,38};
			case 5:
				return new int[]{0,1,2,3,5,8,11,12,15,21,25,32,33,34,36,37};
			default:
				return new int[]{};
		}
	}
	public void dropPlayer(int id){
		
		for(ServerThread t:players){
			if(t.id==id){
				writeAll("\0"+"%xt%21%-1%"+0+"%"+t.myPlayerNum+"%");
				players.remove(t);
				if(players.size()==0){
					this.alive=false;
					timer.cancel();
					S3Server.games.remove(this);
				}
			}
		}
	}
	public float dashL(float param1){
		float loc2=0.0f;
		float loc3=(this.nm==0)?0.9f:2.5f;
		float loc4=0.0f;
		float loc5=0.0f;
		switch(this.map){
			case 1:
				loc2=0.65f;
				break;
			case 2:
			case 3:
				loc2=0.75f;
				break;
			case 5:
				loc2=1.1f;
				break;
			default:
				loc2=1.0f;
				break;
		}
		if(param1 > 45f)
         {
            loc4 = 45f;
            loc5 = param1 - 45f;
         }
         else
         {
            loc4 = param1;
            loc5 = 0f;
         }
		 float loc6 = 10f - (loc4 - 10f) / 7f;
         float loc7 = loc4 / 18f;
         float loc8 = (float)Math.pow(loc6,loc7) * 1.2f;
         float loc9 = (float)Math.pow(loc5,1.5f) * 5.2f;
         return (loc8 + loc9) / 4f * (float)(players.size()) * loc2 * loc3;
	}
	
	public float commaHash(){
		return (float)this.rankSum + ((float)this.p1 + 24f * (float)this.waveTotal) / (24f * (float)this.waveTotal) * 10f * ((float)(this.waveTotal - 4) / 12f + 1f);
	}
	public Room(ServerThread p1, int mode, int nm) {
		players = new CopyOnWriteArrayList<ServerThread>();
		zombies = new CopyOnWriteArrayList<Zombie>();
		players.add(p1);
		this.mode=mode;
		this.nm=nm;
		this.SBEmult=0f;
		this.map=1+(int)(Math.random()*5);
		this.mapURL=MAP_URLS[map-1];
		this.id=(int)(Math.random()*999999999);
		timer = new Timer();
		this.powerups=powerups();
		this.spawns=spawns();
	}
	public void setup(){
		this.setup=true;
		this.rankSum=0;
		for(ServerThread t:players){
			this.rankSum+=t.rank;
		}
		if(this.nm!=0)
			this.SBEmult=(1f + (float)this.rankSum / 10f) / 2f * 10f;
		else this.SBEmult=(1f + (float)this.rankSum / 10f) / 2f;
		
		this.barriHP=(int)(600f*this.SBEmult*this.SBEmult);
		
		if(this.rankSum>=38)
			this.waveTotal=11;
		else if(this.rankSum>=30)
			this.waveTotal=10;
		else if(this.rankSum>=22)
			this.waveTotal=9;
		else if(this.rankSum>=15)
			this.waveTotal=8;
		else if(this.rankSum>=9)
			this.waveTotal=7;
		else if(this.rankSum>=5)
			this.waveTotal=6;
		else 
			this.waveTotal=5;
		//writeAll("\0"+"%xt%18%-1%"+0+"%"+wave+"%"+waveTotal+"%");
		timer.schedule(new WaveStartTask(this),5000);
		//timer.schedule(new WaveEndTask(this),5000);
	}
	public void writeFrom(int id, String toWrite){
		players.forEach(x->{
			if(x.id!=id){
				try{
					x.doubleWrite(toWrite);
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		}
		);
		
	}
	public int indexOf(int id2){
			for(int i=0;i<players.size();i++){
					if(players.get(i).id==id2)
						return i;
			}
		return -1;
	}
	public void writeAll(String toWrite){
			players.forEach(x->{
				try{
					x.doubleWrite(toWrite);
				}catch(Exception e){
					e.printStackTrace();
				}
			}
			);
		
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
