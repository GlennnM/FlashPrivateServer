package xyz.hydar.flash.mp;

import java.io.IOException;

public class FlashLauncher {
	//public static FlashConfig CONFIG;
	public static void main(String[] args) throws IOException, InterruptedException {
		//CONFIG=new FlashConfig(args.length==0?"flash.properties":args[0]);
		try {
		new FilePolicyServer().start(843,true);
		}catch(IOException ioe) {
			ioe.printStackTrace();
		}
		new FlashServer().start(4480,true);
		new CoopServer().start(5577,true);
		new S4Server().start(8124,true);
		//Thread.ofPlatform().start(new CoopServer(5577));
		while(true)
			Thread.sleep(1000000000);//TODO: something else
	}
	
}
