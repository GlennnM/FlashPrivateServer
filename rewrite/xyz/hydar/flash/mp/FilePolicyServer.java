package xyz.hydar.flash.mp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import xyz.hydar.net.ClientContext;
import xyz.hydar.net.ClientOptions;
import xyz.hydar.net.ServerContext;
import xyz.hydar.net.TextClientContext;

//class 
/**
 * @TODO idk
 */
public class FilePolicyServer extends ServerContext.Basic {
	/**
	 * Flash requests a "cross domain policy" xml whenever contacting a server(similar to CORS in http)
	 * By default this is handled on port 843, then that connection is ended instantly
	 */
	public static final byte[] POLICY ="""
	<?xml version="1.0"?>
	<!DOCTYPE cross-domain-policy SYSTEM 
	"http://www.adobe.com/xml/dtds/cross-domain-policy.dtd">
	<cross-domain-policy>
		<site-control permitted-cross-domain-policies="master-only"/>
		<allow-access-from domain="*" to-ports="*"/>
	</cross-domain-policy>\0""".getBytes();
	private static final ClientOptions OPTIONS=ClientOptions.builder().timeout(5000).input(64).mspt(500).build();
	@Override
	public ClientContext newClient() throws IOException {
		return new TextClientContext(StandardCharsets.ISO_8859_1,'\0',OPTIONS){
			@Override
			public void onMessage(String line) throws IOException {
				if (line.trim().equals("<policy-file-request/>")) 
					send(POLICY);
				this.alive=false;
			}

			@Override
			public void onOpen() {}
			@Override
			public void onClose() {}
		};
	}
}