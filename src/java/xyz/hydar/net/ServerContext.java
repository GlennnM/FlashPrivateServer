package xyz.hydar.net;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.stream.IntStream;

/**Represents an unstarted server, which can be started with start() (non-blocking).<br>
*Can use thread-per-connection with normal IO or async NIO interchangeably.<br>
*Only newClient() needs to be implemented(should return implementations of ClientContext).<br>
*Since servers usually start in daemon threads, make sure to keep the VM running
 * <br>
 * close() and alive=false differ in that close() stops listening immediately,<br>
 * while alive=false waits for the server timeout.<br>
 * The server timeout is 10 seconds and accepts are always looped.
*/
public abstract class ServerContext{//TODO: options: ssl
	public volatile boolean alive=true;
	volatile Server server;
	static final ThreadFactory DEFAULT;
	//'conditional compile' for 19+
	static {
		ThreadFactory tmp;
		try {
			Method meth=Thread.class.getMethod("ofVirtual");
			Object threadBuilder=meth.invoke(null);
			Class<?> builderClass=meth.getReturnType();
			builderClass.getMethod("name",String.class,long.class)
				.invoke(threadBuilder,"client-vthread-",0);
			tmp=(ThreadFactory)builderClass
					.getMethod("factory")
					.invoke(threadBuilder);
			System.out.println("Using vthread factory");
		}catch(IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			System.out.println("Using normal thread factory");
			tmp=Thread::new;
		}
		DEFAULT=tmp;
	}
	/**Equivalent to {@code start(port,50,nio)}.*/
	public void start(int port, boolean nio) throws IOException {
		start(port,50,nio);
	}

	/**Equivalent to {@code start(ports,50,nio)}.*/
	public void start(IntStream ports, boolean nio) throws IOException {
		start(ports,50,nio);
	}
	/**Starts listening for connections using the specified connection strategy.*/
	public void start(int port, int backlog, boolean nio) throws IOException {
		if(nio) {
			(server=new Server.OfNio(this, port, backlog)).start();
		}else
			(server=new Server.OfIo(this, port, backlog)).start();
	}
	/**Starts listening for connections using the first port in {@code ports} that successfully binds.*/
	public void start(IntStream ports, int backlog, boolean nio) throws IOException {
		ports.filter((port)->{
			try {
				if(nio) {
					(server=new Server.OfNio(this, port, backlog)).start();
				}else
					(server=new Server.OfIo(this, port, backlog)).start();
				return true;
			}catch(IOException ioe) {
				return false;
			}
		}).findFirst();
	}
	/**Starts listening for connections using the specified already-bound server.*/
	public void start(ServerSocket server) throws IOException {
		(this.server=new Server.OfIo(this,server)).start();
	}
	/**Starts listening for connections using the specified already-bound server.*/
	public void start(AsynchronousServerSocketChannel asc) throws IOException {
		(this.server=new Server.OfNio(this,asc)).start();
	}
	/**Return the server socket associated with this context if one exists, otherwise null.*/
	public ServerSocket serverSocket() {
		return server.serverSocket();
	}
	/**Return the AsynchronousServerSocketChannel associated with this context if one exists, otherwise null.*/
	public AsynchronousServerSocketChannel serverChannel() {
		return server.serverChannel();
	}
	/**Returns the port this context is bound to, or -1 if not bound or an exception(NIO only) occurs.*/
	public int getPort(){
		if(server==null)
			return -1;
		return server.getPort();
	}
	/**Return whether this server context uses NIO.*/
	public boolean isNio(){
		return server instanceof Server.OfNio;
	}
	/**Close the server context, stopping listening for connections*/
	public final void close(){
		alive=false;
		server.close();
	}
	/**Called when the server is opened. Default implementation does nothing.*/
	public void onOpen() {}
	/**Called when the server is closed. It may or may not still be bound. Default implementation does nothing.*/
	public void onClose() {};
	/**Factory method for ClientContexts. They will often be constructed referencing this object.<br>
	 * Regardless, this method will be called and the resulting clients will be started automatically<br> when connections are accepted.*/
	public abstract ClientContext newClient() throws IOException;
	abstract static class Server{
		public final ServerContext ctx;
		public abstract void start() throws IOException;
		public abstract void close();
		public abstract ServerSocket serverSocket();
		public abstract int getPort();
		public abstract AsynchronousServerSocketChannel serverChannel();
		public Server(ServerContext ctx) {
			this.ctx=ctx;
		}
		public static class OfNio extends Server{

			public final AsynchronousServerSocketChannel server;
			public OfNio(ServerContext ctx, int port, int backlog) throws IOException {
				super(ctx);
				server=AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(port),backlog);
			}
			public OfNio(ServerContext ctx, AsynchronousServerSocketChannel asc) throws IOException {
				super(ctx);
				server=asc;
			}
			@Override
			public int getPort() {
				try {
				if(server.getLocalAddress() instanceof InetSocketAddress isa)
					return isa.getPort();
				}catch(IOException ioe) {
					
				}
				return -1;
			}
			@Override
			public void start() throws IOException{
				ctx.onOpen();
				server.accept(null, 
						new CompletionHandler<AsynchronousSocketChannel, Void>(){
						@Override
						public void completed(AsynchronousSocketChannel result, Void attachment) {
							if(ctx.alive) {
								try {
									ctx.newClient().start(result);
								} catch (IOException e) {
									
								}
								server.accept(null,this);
							}else close2();
						}

						@Override
						public void failed(Throwable exc, Void attachment) {
							close2();
							return;
						}
				});
			}
			void close2() {
				ctx.alive=false;
				
				try{
					ctx.onClose();
				}finally {
					if(server.isOpen())
						try {
							server.close();
						}catch(IOException ioe) {}
				}
				
			}
			@Override
			public final void close() {
				ctx.alive=false;
				if(server.isOpen())
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
			public OfIo(ServerContext ctx, int port, int backlog) throws IOException{
				super(ctx);
				this.server=new ServerSocket(port,backlog);
				server.setSoTimeout(10000);
			}
			public OfIo(ServerContext ctx, ServerSocket server) throws IOException {
				super(ctx);
				this.server=server;
			}
			@Override
			public void start() throws IOException{
				DEFAULT.newThread(this::run).start();
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
					ctx.alive=false;
					ctx.onClose();
				}
			}
			@Override
			public final void close() {
				ctx.alive=false;
				if(!server.isClosed())
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
			@Override
			public int getPort(){
				return server.getLocalPort();
			}
		}
	}

}