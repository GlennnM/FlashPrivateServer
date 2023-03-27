package xyz.hydar.flash;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import xyz.hydar.flash.util.FlashConfig;
import xyz.hydar.flash.util.FlashUtils;
import xyz.hydar.net.ServerContext;
/**Main class. Launches servers from a configuration and allows them to be stopped through "stop" from STDIN.*/
public class FlashLauncher {
	public static FlashConfig CONFIG;
	public static List<ServerContext> servers=new ArrayList<>();
	public static void main(String[] args) throws IOException, InterruptedException, NoSuchElementException {
		System.out.println("Please ensure port 843 is openable(this requires root usually)");
		try {
			startChecked(PolicyFileServer::new, true, 843, true);
		}catch(IOException ioe) {
			ioe.printStackTrace();
		}
		if(args.length<=1) {
			if(args.length==1&&(args[0].equals("-h")||args[0].equals("-help")||args[0].equals("help"))) {
				System.out.println("arguments: 0=flash.properties(fallback: builtin default)");
				System.out.println("arguments: 1=config file(fallback: builtin default)");
				System.out.println("arguments: 2=game+port i.e. Battles 4480 (fallback to default file, then defaults)");
				System.out.println("arguments: 3=config+game+port i.e. flash.properties Battles 4480 (fallback builtins)");
				System.out.println("======================================================================================");
				return;
			}
			CONFIG=new FlashConfig(args.length==0?"flash.properties":args[0]);
			startChecked(BattlesServer::new, CONFIG.BATTLES_ENABLED, CONFIG.BATTLES_PORT, CONFIG.BATTLES_NIO);
			startChecked(CoopServer::new, CONFIG.BTD5_ENABLED, CONFIG.BTD5_PORT, CONFIG.BTD5_NIO);
			startChecked(S4Server::new, CONFIG.SAS4_ENABLED, CONFIG.SAS4_PORT, CONFIG.SAS4_NIO);
			startChecked(S3Server::new, CONFIG.SAS3_ENABLED, CONFIG.SAS3_PORT, CONFIG.SAS3_NIO);
			startChecked(CSServer::new, CONFIG.CS_ENABLED, CONFIG.CS_PORT, CONFIG.CS_NIO);
		}else if(args.length==2&&Arrays.stream(args[1].split(",",-1)).allMatch(FlashUtils::isInt)) {
			CONFIG=new FlashConfig("flash.properties");
			startGames(args[0],args[1]);
			
		}else if(args.length==3&&Arrays.stream(args[2].split(",",-1)).allMatch(FlashUtils::isInt)) {
			CONFIG=new FlashConfig(args[0]);
			startGames(args[1],args[2]);
		}
		System.out.println("Type \"stop\" to close all servers.");
		try(var s=new BufferedReader(new InputStreamReader(System.in))){
			while(true) {
				String cmd=s.readLine();
				if(cmd.equals("stop")) {
					System.out.println("Type stop again to confirm");
					if(s.readLine().equals("stop")) {
						servers.forEach(ServerContext::close);
						break;
					}
				}
			}
			Thread.sleep(2000);
			long threads;
			while((threads=CONFIG.threadCount.sum())>0) {
				Thread.sleep(6000);
				System.out.println("Waiting for "+threads+" threads...");
			}
			System.out.println("Exiting. "+CONFIG.threadCount.sum());
		}catch(NoSuchElementException e){
			
		}finally {
			servers.stream().filter(x->x.alive).forEach(x->x.close());
		}
	}
	static void startChecked(Supplier<ServerContext> factory, boolean enabled, int port, boolean nio) throws IOException {
		if(enabled) {
			ServerContext server=factory.get();
			server.start(port,nio);
			servers.add(server);
		}
	}
	static void startGames(String gameStr, String portStr) throws IOException {
		String[] games=gameStr.split(",",-1);
		String[] ports=portStr.split(",",-1);
		if(games.length!=ports.length) {
			System.out.println("Different number of ports and games.");
			System.out.println("They should be equal, i.e. \"Battles,SAS4 4480,8124\"");
		}
		for(int i=0;i<games.length;i++) {
			startGame(games[i],Integer.parseInt(ports[i]));
		}
	}
	static void startGame(String game, int port) throws IOException {
		boolean nio;
		var server=(switch(game.toLowerCase().trim()) {
			case "battles","btdb"->{
				nio=CONFIG.BATTLES_NIO;
				yield new BattlesServer();
			}
			case "coop","btd5"->{
				nio=CONFIG.BTD5_NIO;
				yield new CoopServer();
			}
			case "sas4"->{
				nio=CONFIG.SAS4_NIO;
				yield new S4Server();
			}
			case "sas3"->{
				nio=CONFIG.SAS3_NIO;
				yield new S3Server();
			}
			case "cs","countersnipe"->{
				nio=CONFIG.CS_NIO;
				yield new CSServer();
			}
			default->throw new IllegalArgumentException("Unknown game. try: battles,btd5,sas4,sas3,CS");
		});
		server.start(port,nio);
		servers.add(server);
	}
	
}
