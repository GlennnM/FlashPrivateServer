package xyz.hydar.flash.mp;

import java.io.IOException;
import java.util.Arrays;

import xyz.hydar.flash.util.FlashConfig;
import xyz.hydar.flash.util.FlashUtils;

public class FlashLauncher {
	public static FlashConfig CONFIG;
	public static void main(String[] args) throws IOException, InterruptedException {
		try {
			new PolicyFileServer().start(843,true);
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

			if(CONFIG.BATTLES_ENABLED)
				new BattlesServer().start(CONFIG.BATTLES_PORT,CONFIG.BATTLES_NIO);
			if(CONFIG.BTD5_ENABLED)
				new CoopServer().start(CONFIG.BTD5_PORT,CONFIG.BTD5_NIO);
			if(CONFIG.SAS4_ENABLED)
				new S4Server().start(CONFIG.SAS4_PORT,CONFIG.SAS4_NIO);
			if(CONFIG.SAS3_ENABLED)
				new S3Server().start(CONFIG.SAS3_PORT,CONFIG.SAS3_NIO);
			if(CONFIG.CS_ENABLED)
				new CSServer().start(CONFIG.CS_PORT,CONFIG.CS_NIO);
		}else if(args.length==2&&Arrays.stream(args[1].split(",",-1)).allMatch(FlashUtils::isInt)) {
			CONFIG=new FlashConfig("flash.properties");
			startGames(args[0],args[1]);
			
		}else if(args.length==3&&Arrays.stream(args[2].split(",",-1)).allMatch(FlashUtils::isInt)) {
			CONFIG=new FlashConfig(args[0]);
			startGames(args[1],args[2]);
		}
		
		//Thread.ofPlatform().start(new CoopServer(5577));
		while(true)
			Thread.sleep(1000000000);//TODO: something else
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
		(switch(game.toLowerCase().trim()) {
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
		}).start(port,true);
	}
	
}
