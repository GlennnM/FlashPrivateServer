package xyz.hydar.flash.mp;

import java.io.IOException;

public class FlashLauncher {

	public static void main(String[] args) throws IOException, InterruptedException {
		try {
		new FilePolicyServer(843).start(true);
		}catch(IOException ioe) {
			ioe.printStackTrace();
		}
		new FlashServer(4480).start(true);
		new CoopServer(5577).start(true);
		//Thread.ofPlatform().start(new CoopServer(5577));
		new S4Server(8124).start(true);
		while(true)
			Thread.sleep(1000000000);//TODO: something else
	}
	
}
