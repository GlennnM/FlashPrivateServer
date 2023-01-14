package xyz.hydar.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ThreadFactory;

/**Represents an unstarted server, which can be started with start() (non-blocking).<br>
*Can use thread-per-connection with normal IO or async NIO interchangeably.<br>
*Only newClient() needs to be implemented(should return implementations of ClientContext).<br>
*Since servers usually start in daemon threads, make sure to keep the VM running
*/
public abstract class ServerContext{//TODO: options: ssl/thread factory for io
	public volatile boolean alive=true;
	public volatile Server server;
	public final int port;
	public ServerContext(int port) {
		this.port=port;
	}
	public abstract static class Basic extends ServerContext{
		public Basic(int port) {
			super(port);
		}
		@Override public void onOpen(){}
		@Override public void onClose() {}
	}
	public void start(ServerContext contextWithSameStrategy) throws IOException {
		start(contextWithSameStrategy.server instanceof Server.OfNio);
	}
	public void start(boolean nio) throws IOException {
		if(nio) {
			(server=new Server.OfNio(this)).start();
		}else
			(server=new Server.OfIo(this)).start();
	}
	public ServerSocket serverSocket() {
		return server.serverSocket();
	}
	public AsynchronousServerSocketChannel serverChannel() {
		return server.serverChannel();
	}
	public boolean isNio(){
		return server instanceof Server.OfNio;
	}
	public final void close(){
		alive=false;
		server.close();
	}
	public abstract void onOpen();
	public abstract void onClose();
	public abstract ClientContext newClient() throws IOException;
	abstract static class Server{
		public final ServerContext ctx;
		public abstract void start() throws IOException;
		public abstract void close();
		public abstract ServerSocket serverSocket();
		public abstract AsynchronousServerSocketChannel serverChannel();
		public Server(ServerContext ctx) {
			this.ctx=ctx;
		}
		public static class OfNio extends Server{

			public final AsynchronousServerSocketChannel server;
			public OfNio(ServerContext ctx) throws IOException {
				super(ctx);
				server=AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(ctx.port));
			}
			@Override
			public void start() throws IOException{
				ctx.onOpen();
				server.accept(null, 
						new CompletionHandler<AsynchronousSocketChannel, Void>(){
						@Override
						public void completed(AsynchronousSocketChannel result, Void attachment) {
							try {
								ctx.newClient().start(result);
							} catch (IOException e) {
								
							}
							if(ctx.alive)
								server.accept(null,this);
							else ctx.onClose();
						}

						@Override
						public void failed(Throwable exc, Void attachment) {
							ctx.alive=false;
							ctx.onClose();
							return;
						}
				});
			}
			@Override
			public final void close() {
				ctx.alive=false;
				try {
					server.close();
				}catch(IOException ioe) {}
			}

			@Override
			public ServerSocket serverSocket() {
				return null;
			}
			@Override
			public AsynchronousServerSocketChannel serverChannel() {
				return server;
			}
			
		}
		public static class OfIo extends Server{
			private final ServerSocket server;
			public OfIo(ServerContext ctx) throws IOException{
				super(ctx);
				this.server=new ServerSocket(ctx.port);
				server.setSoTimeout(1000);
			}
			@Override
			public void start() throws IOException{
				Thread.ofVirtual().start(this::run);
			}
			public void run() {
				ctx.onOpen();
				try(server){
					while(ctx.alive) {
						try {
							Socket client=server.accept();
							ctx.newClient().start(client);
						}catch(SocketTimeoutException ste) {
							continue;
						}
					}
				}catch(IOException ioe) {
					
				}finally {
					ctx.onClose();
				}
			}
			@Override
			public final void close() {
				ctx.alive=false;
				try {
					server.close();
				}catch(IOException ioe) {}
			}
			@Override
			public ServerSocket serverSocket() {
				return server;
			}
			@Override
			public AsynchronousServerSocketChannel serverChannel() {
				return null;
			}
		}
	}

}