import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.URI;
import java.net.InetAddress;
import java.net.URL;
import java.net.Socket;
import java.net.URLClassLoader;
import java.net.SocketTimeoutException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.Writer;
import java.io.IOException;
import java.io.File;
import java.lang.Character;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.text.FieldPosition;
import java.text.ParseException;
import java.util.TimeZone;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.net.ServerSocket;
import java.security.SecureRandom;
/**
@TODO
string builder??
ports
map, loadout, custom time
*/
class ServerThread extends Thread {
	public Socket client = null;
	OutputStream output;
	public volatile boolean alive;
	Player player=null;
	public int code=0;
	public int mode=0;
	// constructor initializes socket
	private void doubleWrite(String s) throws IOException{
		this.output.write(s.getBytes(StandardCharsets.UTF_8));
		this.output.flush();
		System.out.println("OUT: "+s);
	}
	public ServerThread(Socket socket) throws IOException{
		this.client = socket;
		this.alive=true;
		this.output = this.client.getOutputStream();
	}
	@Override
	public void run() {
		try {
			this.alive=true;
			this.client.setSoTimeout(1000);
			SimpleDateFormat s = null;
			InputStream input = this.client.getInputStream();
			int size=1024;
			Date lastUpdate=null;
			int timeouts=0;
			byte[] buffer;
			int l4=0;
			while(this.alive){
				//System.out.println("alive");
				//headers = "";
				//line="";
				/*if(lastUpdate!=null&&(newTime-lastUpdate.getTime()>2000)){
					lastUpdate = new Date();
					doubleWrite("ServerMessage,");
				}*/
				buffer=new byte[1024];
				try {
					l4=input.read(buffer,0,1024);
				} catch (java.net.SocketTimeoutException ste) {
					/**
					kill after multiple timeouts
					*/
					//System.out.println("timeout: "+line);
					//return;
					//idea: always send 0, host is 1 if there was someone already
				}
				if (l4>0) {
					System.out.println("incoming:");
					code = (((int)buffer[2]&0xff)<<24)|(((int)buffer[3]&0xff)<<16)|(((int)buffer[4]&0xff)<<8)|((int)buffer[5]&0xff);
					int length = ((int)buffer[10]&0xff)<<24+((int)buffer[11]&0xff)<<16+((int)buffer[12]&0xff)<<8+((int)buffer[13]&0xff);
					int[] data = new int[length];
					for(int i=0;i<length;i++)
						data[i]=((int)buffer[14+2*i]&0xff)<<8+((int)buffer[15+2*i]);
					
					mode = (((int)buffer[8]&0xff)<<8)|((int)buffer[9]&0xff);
					System.out.println("Code"+code);
					//Player player = new Player(buffer[7],data);
					GameServer g = S4Server.games.get(code);
					while(g!=null&&g.playerThreads.size()>3){
						code++;
						g = S4Server.games.get(code);
					}
					if(g==null){
						g=new GameServer(8128+S4Server.games.keySet().size()*2,this.mode,this.code);
						S4Server.games.put(this.code,g);
						new Thread(g).start();
					}byte[] ip = "localhost".getBytes(StandardCharsets.UTF_8);
					byte[] pl =new byte[10+ip.length];
					pl[0]=(byte)(ip.length>>8);
					pl[1]=(byte)(ip.length&0xff);
					int i=0;
					int port = S4Server.games.get(code).port;
					for(i=0;i<ip.length;i++)
						pl[2+i]=ip[i];
					pl[i+2]=(byte)(port>>8);
					pl[i+3]=(byte)(port&0xff);
					pl[i+4]=(byte)(mode>>8);
					pl[i+5]=(byte)(mode&0xff);
					pl[i+6]=(byte)(code>>24);
					pl[i+7]=(byte)((code>>16)&0xff);
					pl[i+8]=(byte)((code>>8)&0xff);
					pl[i+9]=(byte)((code)&0xff);
					output.write(pl);
					System.out.println("OUT: "+pl.length);
					for(int a=0;a<pl.length;a++)
							System.out.print(">"+pl[a]+" ");
					output.flush();
				}else{
					//if(toSend.startsWith("FindingYouAMatch"))
					//	doubleWrite("ServerMessage");
					//if(timeouts==0&&)
					//	timeouts-=2;
					timeouts++;
					
					if(timeouts>10){
						this.alive=false;
						output.close();
						input.close();
						this.client.close();
						return;
					}
				}
			}
		}catch(Exception ioe_){
			try{
				this.alive=false;
				output.close();
				this.client.close();
				ioe_.printStackTrace();
				return;	
			}catch(Exception e){
				e.printStackTrace();
			}return;
		}
	}
}
class FilePolicyServer extends Thread{
	public ServerSocket server;
	public int port;
	public boolean alive=true;
	public FilePolicyServer(int port){
		try{
			this.port=port;
			this.server = new ServerSocket(port);
			this.server.setSoTimeout(1000);
		}catch(IOException e){
			return;
		}
		this.alive=true;
	}
	@Override
	public void run(){
		while (this.alive) {
			Socket client = null;
			try {
				client = server.accept();
				FilePolicyServerThread connection = new FilePolicyServerThread(client);
				new Thread(connection).start();
			}catch(Exception e){
				//e.printStackTrace();
				continue;
			}
		}
	}
}
class FilePolicyServerThread extends Thread{
	public Socket client;
	public OutputStream output;
	public boolean alive;
	public void doubleWrite(String s) throws IOException{
		this.output.write((s+"\n").getBytes(StandardCharsets.UTF_8));
		this.output.flush();
		System.out.println("#OUT: "+s);
	}
	public FilePolicyServerThread(Socket client){
		this.client = client;
		try{
		this.output = client.getOutputStream();}catch(Exception e){e.printStackTrace();}
	}
	@Override
	public void run(){
		try{
			this.alive=true;
			this.client.setSoTimeout(1000);
			InputStream input = this.client.getInputStream();
			InputStreamReader ir = new InputStreamReader(input, Charset.forName("UTF-8"));
			int size=1024;
			int timeouts=0;
			String headers = "";
			String line="";
			while(this.alive){
			//System.out.println("SIDE"+this.side);
			//	System.out.println("GST alive");
				//ask for player info
				//System.out.println(opponent);
				//if(!this.init)
				//	continue;
				try {
					char[] buffer=new char[1024];
					int l4=ir.read(buffer,0,1024);
					line = new String(buffer).trim();
					headers = new String(line);
				} catch (java.net.SocketTimeoutException ste) {
					/**
					kill after multiple timeouts
					*/
					ste.printStackTrace();
				}
				if (line.length()>0) {
					timeouts=0;
					System.out.println("#incoming: "+headers);
					if(headers.equals("<policy-file-request/>")){
						String xml = "<?xml version=\"1.0\"?>\n<!DOCTYPE cross-domain-policy SYSTEM \n\"http://www.adobe.com/xml/dtds/cross-domain-policy.dtd\">\n<cross-domain-policy>\n";
						xml += "<site-control permitted-cross-domain-policies=\"master-only\"/>\n";
						xml += "<allow-access-from domain=\"*\" to-ports=\"*\"/>\n";
						xml += "</cross-domain-policy>\n";
						doubleWrite(xml);
						this.alive=false;
						output.close();
						input.close();
						ir.close();
						this.client.close();
						return;
					}
				}else{
					System.out.println("%%timeout");
					timeouts++;
					if(timeouts>10){
						this.alive=false;
						output.close();
						input.close();
						ir.close();
						this.client.close();
						return;
					}
					headers = "";
					line="";
					continue;					
				}
			}
		}catch(Exception e){
			this.alive=false;
				e.printStackTrace();
		}
	}
}
class Player{
	public int level;
	public int[] header;
	public byte[] data;
	public Player(int level, int[] header){
		this.level=level;
		this.header=header;
	}
	public void setData(byte[] data){
		this.data=data;
		
	}
}
class GameServer extends Thread{
	public ServerSocket server;
	public int port;
	public boolean alive=true;
	public ArrayList<GameServerThread> playerThreads;
	public int mode;
	public int code;
	public boolean init;
	public short map;
	public byte nextId;
	public byte host;
	public GameServer(int port,int mode,int code){
		this.nextId=0;
		this.host=0;
		this.map=(short)1102;
		this.map+=(short)(Math.random()*8);
		if(Math.random()<0.2)
			this.map=(short)1112;
		try{
			this.port=port;
			this.server = new ServerSocket(port);
			this.server.setSoTimeout(1000);
		}catch(IOException e){
			return;
		}
		playerThreads = new ArrayList<GameServerThread>();
		this.mode=mode;
		this.code=code;
		this.init=false;
		this.alive=true;
	}
	@Override
	public void run(){
		while (this.alive) {
			for(int i=0;i<playerThreads.size();i++)
				if(!playerThreads.get(i).alive){
					playerThreads.remove(playerThreads.get(i));
					i--;
				}
			if(init&&playerThreads.size()==0){
				this.alive=false;
				try{
				this.server.close();
				}catch(Exception e){}
				S4Server.games.remove(code);
				return;
			}
			Socket client = null;
			try {
				client = server.accept();
				this.init=true;
				GameServerThread connection = new GameServerThread(client,this,this.nextId,this.host);
				this.nextId++;
				playerThreads.add(connection);
				new Thread(connection).start();
			}catch(Exception e){
				//e.printStackTrace();
				continue;
			}
		}
	}//202->
	public void setMap(short map) throws IOException{
		System.out.println(map);
		this.map=map;
		byte[] pl = new byte[9];
		pl[0]=((byte)(-14));
		pl[3]=((byte)((mode>>8)&0xff));
		pl[4]=(byte)(mode&0xff);
		long time = (new Date()).getTime();
		pl[1]=((byte)((map>>8)&0xff));
		pl[2]=(byte)(map&0xff);
		for(GameServerThread g:playerThreads)
			g.map=this.map;
		this.writeAll(pl,0,5);
	}
	public void writeAll(byte[] data,int off, int length) throws IOException{
		for(GameServerThread g:playerThreads){
			System.out.println(data.length);
			g.output.write(data,off,length);
			g.output.flush();
		}
	}public void newPlayer(byte id){
		for(GameServerThread g:playerThreads){
			try{
				if(g.id==id){
					for(GameServerThread h:playerThreads)
						if(h.id!=id){
							g.output.write(h.playerData());
							g.output.flush();
						}
				}else	
					for(GameServerThread h:playerThreads)
						if(h.id==id){
							g.output.write(h.playerData());
							g.output.flush();
						}
			}catch(IOException e){
				continue;
			}
		}
	}
}
class GameServerThread extends Thread{
	public Socket client;
	public volatile OutputStream output;
	public boolean alive;
	public short map;
	public boolean init;
	public Player player;
	public GameServer parent;
	public byte id;
	public byte host;
	public byte[] playerData(){
		int len = this.player.data.length;
		byte[] pl = new byte[len+5];
		try{
			pl[0]=(byte)-2;
			pl[1]=(byte)(len>>24);
			pl[2]=(byte)((len>>16)&0xff);
			pl[3]=(byte)((len>>8)&0xff);
			pl[4]=(byte)((len)&0xff);
			pl[5]=(byte)4;
			pl[6]=this.id;
			short nameLen = (short)((((short)this.player.data[0]&0xff)<<8)|((short)this.player.data[1]&0xff));
			
			//nameLen|=((short)(this.player.data[1])&0xff);
			//System.out.println("%%%NAMELEN "+nameLen+"//"+this.player.data[0]+"//"+this.player.data[1]);
			for(int i=7;i<10+nameLen;i++)
				pl[i]=this.player.data[i-7];
			pl[10+nameLen]=(byte)((this.player.level>>8)&0xff);
			System.out.println("pl%%%"+this.player.level);
			pl[11+nameLen]=(byte)(this.player.level&0xff);
		for(int i=12+nameLen;i<pl.length;i++)
			pl[i]=this.player.data[i-5];
		}catch(Exception e){
			e.printStackTrace();
		}
		return pl;
		
	}
	public byte[] getPowerups(){
		short nameLen = (short)((((short)this.player.data[0]&0xff)<<8)|((short)this.player.data[1]&0xff));
		int dataLen = (((int)this.player.data[nameLen+2]&0xff)<<24)|(((int)this.player.data[nameLen+3]&0xff)<<16)|(((int)this.player.data[nameLen+4]&0xff)<<8)|((int)this.player.data[nameLen+5]&0xff);
		byte gearCount = this.player.data[nameLen+7];
		int gearBytes=0;
		for(int i=0;i<gearCount;i++){
			gearBytes+=18+(this.player.data[nameLen+gearBytes+20]*2);
		}
		//System.out.println("gearCount: "+gearCount+"\ngearBytes: "+gearBytes);
		int dictionaryBytes=this.player.data[nameLen+gearBytes+8]*2+1;
		//System.out.println("dictionaryBytes: "+dictionaryBytes);
		byte powerupBytes = this.player.data[nameLen+gearBytes+24+dictionaryBytes];
		//System.out.println("powerupBytes: "+powerupBytes);
		byte[] powerups = new byte[powerupBytes];
		for(int i=0;i<powerupBytes;i++)
			powerups[i]=this.player.data[nameLen+gearBytes+25+dictionaryBytes+i];
		int len = (powerupBytes+1)*4+1;
		byte[] pl = new byte[len+5];
		pl[0]=(byte)-2;
		pl[1]=(byte)(len>>24);
		pl[2]=(byte)((len>>16)&0xff);
		pl[3]=(byte)((len>>8)&0xff);
		pl[4]=(byte)((len)&0xff);
		pl[5]=(byte)11;
		pl[9]=this.id;
		for(int i=0;i<powerupBytes;i++)
			pl[13+4*i]=powerups[i];
		return pl;
		
	}
	public GameServerThread(Socket client, GameServer parent, byte id, byte host){
		this.parent = parent;
		this.map=parent.map;
		this.alive=true;
		this.init=false;
		this.player=null;
		this.id=id;
		this.host=host;
		this.client = client;
		try{
		this.output = client.getOutputStream();}catch(Exception e){e.printStackTrace();}
	}
	@Override
	public void run() {
		try {
			this.alive=true;
			this.client.setSoTimeout(1000);
			InputStream input = this.client.getInputStream();
			int size=1024;
			int timeouts=0;
			byte[] buffer;
			int l4=0;
			if(!this.init)
				{
					this.init=true;
					byte[] pl = new byte[17];
					pl[0]=((byte)(-4));
					pl[1]=host;
					
					pl[2]=id;
					long time = (new Date()).getTime();
					
					pl[3]=((byte)(time>>24));
					pl[4]=((byte)((time>>16)&0xff));
					pl[5]=((byte)((time>>8)&0xff));
					pl[6]=((byte)((time)&0xff));//time
					
					pl[7]=((byte)((map>>8)&(short)0xff));
					pl[8]=(byte)(map&(short)0xff);//map
					//04 54 is last stand
				//	pl[13]=((byte)0x00);
				//	pl[14]=((byte)0x04);//seed
					
				//	pl[15]=((byte)0x00);
				//	pl[16]=((byte)0x04);
					
				//	pl[17]=((byte)0x00);
				//	pl[18]=((byte)0x00);
				//	pl[19]=((byte)0x00);
				//	pl[20]=((byte)0x00);
					output.write(pl,0,17);
					output.flush();
				}
			while(this.alive){
				buffer=new byte[1024];
				try {
					l4=input.read(buffer,0,1024);
				} catch (java.net.SocketTimeoutException ste) {
					//
				}
				if (l4>0) {
					System.out.println("incoming: "+(buffer[0]));
					if(buffer[0]==(byte)0&&buffer[1]==(byte)202){
						int length = (((int)buffer[10]&0xff)<<24)|(((int)buffer[11]&0xff)<<16)|(((int)buffer[12]&0xff)<<8)|((int)buffer[13]&0xff);
						int[] header = new int[length];
						int i;
						for(i=0;i<length;i++)
							header[i]=(((int)buffer[14+2*i]&0xff)<<8)|((int)buffer[15+2*i]&0xff);
						this.player=new Player(buffer[9],header);
						this.player.setData(Arrays.copyOfRange(buffer,14+2*i,l4));
						this.parent.newPlayer(this.id);
						
						
						
					}
					else if(buffer[0]==(byte)(-14)){
						this.map=(short)((((short)buffer[1]&0xff)<<8)|((short)buffer[2]&0xff));
						parent.setMap(this.map);
					}else if(buffer[0]==(byte)(-3)){
						byte[] pl = new byte[5];
						pl[0]=((byte)(-3));
						long time = (new Date()).getTime();
						pl[1]=((byte)(time>>24));
						pl[2]=((byte)((time>>16)&0xff));
						pl[3]=((byte)((time>>8)&0xff));
						pl[4]=((byte)((time)&0xff));
						output.write(pl,0,5);
						output.flush();
					}
					
				}else{
					//if(toSend.startsWith("FindingYouAMatch"))
					//	doubleWrite("ServerMessage");
					//if(timeouts==0&&)
					//	timeouts-=2;
					timeouts++;
					
					if(timeouts>10){
						this.alive=false;
						output.close();
						input.close();
						this.client.close();
						return;
					}
				}
			}
		}catch(Exception ioe_){
			try{
				this.alive=false;
				output.close();
				this.client.close();
				ioe_.printStackTrace();
				return;	
			}catch(Exception e){
				e.printStackTrace();
			}return;
		}
	}
}

//class for main method
public class S4Server {
	
	//
	public static volatile ConcurrentHashMap<InetAddress,Integer> ipThreads;
	public static volatile ConcurrentHashMap<Integer,GameServer> games;
	public static volatile ConcurrentHashMap<Integer,ArrayList<String>> history;
	public static volatile String log="";
	public static void main(String[] args) {
		games = new ConcurrentHashMap<Integer,GameServer>();
		//checks if a port is specified
		if (args.length == 0) {
			System.out.println("No port specified");
			System.exit(0);
		}
		int port = Integer.parseInt(args[0]);
		//checks if port is valid
		if (port < 1 || port > 65535) {
			System.out.println("Invalid port");
			System.exit(0);
		}
		ServerSocket server;
		FilePolicyServer fps = new FilePolicyServer(843);
		try{
			server = new ServerSocket(port);
			new Thread(fps).start();
		}catch(IOException e){
			return;
		}
		
		//server loop(only ends on ctrl-c)
		ArrayList<ServerThread> threads = new ArrayList<ServerThread>();
		try{
			server.setSoTimeout(1000);
		}catch(Exception eeeeeee){
			System.out.println("???");
		}while (true) {
			
			Socket client = null;
			try {
				client = server.accept();
				System.out.println("accept???");
				//client.setTcpNoDelay(true);
				ipThreads = new ConcurrentHashMap<InetAddress,Integer>();
				//for (ServerThread l:threads){System.out.println(l.isWebSocket);}
				for (int i = 0; i < threads.size(); i++) {
					if (threads.get(i)!=null&&(threads.get(i).alive||threads.get(i).isAlive())&&threads.get(i).client!=null) {
				ipThreads.put(threads.get(i).client.getInetAddress(),(ipThreads.get(threads.get(i).client.getInetAddress())==null)?1:ipThreads.get(threads.get(i).client.getInetAddress())+1);}
				if(threads.get(i)!=null&&!threads.get(i).alive&&!threads.get(i).isAlive()){
						threads.set(i,null);
					}
				}//fix all this garbage
				if(client!=null&&client.getInetAddress()!=null&&ipThreads.get(client.getInetAddress())!=null&&ipThreads.get(client.getInetAddress())>40){
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
				//find dead threads and replace them
				for (int i = 0; i < threads.size(); i++) {
					if (threads.get(i)==null||(!threads.get(i).alive&&!threads.get(i).isAlive())) {
						if(index<0){
							index = i;
							threads.set(i, connection);
						}
					} else
						alives++;
				}
				//all threads are dead -> reset threadpool
				//System.out.println("ALIVE: "+alives+", EXIST: ");
				if (alives == 0&&index==-1) {
					threads = new ArrayList<ServerThread>();
					threads.add(connection);
					index = 0;
					run = true;
					new Thread(threads.get(index)).start();
					continue;
				}
				
				else if(index>-1){
					//at least 1 thread is dead, so just replace it
					run = true;
					threads.set(index,connection);
					new Thread(threads.get(index)).start();
					continue;
				}
				//expand threadpool, or give 505 if already 256+
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
