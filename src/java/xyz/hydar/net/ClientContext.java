package xyz.hydar.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Client context. Handles data with onData(the only method that needs to be
 * overriden).<br>
 * onOpen and onClose can be implemented as well, and data can be sent with
 * send()<br>
 * flush() only needs to be called if the ClientOptions write buffer size is
 * >0.<br>
 * <br>
 * close() and alive=false differ in that close() stops listening immediately,<br>
 * while alive=false waits for the next read to complete or timeout, specified in ClientOptions.<br>
 * <pre>
 *How to process the resulting byte buffers:
 *0. flip
 *1. Too small for header? -> pos=end, continue
 *2. Length from header is too small? -> pos=end, continue
 *3. Keep reading packets in order until 2.
 *4. If we read >=1 packets, compact the buffer to the last packet start before pos=end+continuing
 *eventually a context more specific to "fixed header+variable length" could be added
 * </pre>
 */
public abstract class ClientContext{
	/**Whether this client is alive. Setting to false will always end the connection and call onClose, if not already closed.*/
	public volatile boolean alive=true;
	volatile Client client=Client.NULL_CLIENT;
	public final ClientOptions opt;
	private static final ThreadFactory DEFAULT_FACTORY=Thread.ofVirtual().factory();
	public ClientContext() {
		this(ClientOptions.DEFAULT);
	}
	public ClientContext(ClientOptions opt) {
		this.opt=opt;
	}
	/**Starts this context by connecting to the given remote address.
	 * @throws IOException */
	public void start(InetSocketAddress remote, boolean nio) throws IOException {
		if(nio) {
			startNio(remote);
		}else startIo(remote,DEFAULT_FACTORY);
	}
	/**Starts this context by connecting to the given remote address. Will use the provided factory if in IO mode.
	 * @throws IOException */
	public void start(InetSocketAddress remote, boolean nio, ThreadFactory factory) throws IOException {
		if(nio) {
			startNio(remote);
		}else startIo(remote,factory);
	}
	private void startIo(InetSocketAddress isa, ThreadFactory factory) throws IOException{
		factory.newThread(()->{
			var socket=new Socket();
			try{
				socket.connect(isa);
				start(socket);
				
			} catch (IOException e) {
				try {
					onClose();
				} catch (IOException e1) {}
				if(!socket.isClosed())
					try {
						socket.close();
					} catch (IOException ee) {}
				throw new RuntimeException(e);
			}
		}).start();
	}
	private void startNio(InetSocketAddress isa) throws IOException {
		var channel=AsynchronousSocketChannel.open();
		channel.connect(isa, null, new CompletionHandler<Void, Void>() {
			private void close() {
				try {
					onClose();
				} catch (IOException e1) {}
				if(channel.isOpen())
					try {
						channel.close();
					} catch (IOException e) {}
			}
			@Override
			public void completed(Void result, Void attachment) {
				try {
					start(channel);
				} catch (IOException e) {
					close();
				}
			}

			@Override
			public void failed(Throwable exc, Void attachment) {
				close();
			}
		});
	}
	/**Starts this context given a Socket. It will run on a new thread.*/
	public final void start(Socket socket) throws IOException {
		start(socket,DEFAULT_FACTORY);
	}
	/**Starts this context given an AsynchronousSocketChannel.*/
	public final void start(AsynchronousSocketChannel socket) throws IOException {
		if(client!=Client.NULL_CLIENT)throw new UnsupportedOperationException("already init");
		this.client = new Client.OfNio(this,socket);
		onOpen();
		client.start();
	}
	/**Starts this context given a Socket. It will run on a new thread from the provided factory.*/
	public final void start(Socket socket, ThreadFactory factory) throws IOException {
		if(client!=Client.NULL_CLIENT)throw new UnsupportedOperationException("already init");
		this.client = new Client.OfIo(this,socket,factory);
		onOpen();
		client.start();
	}
	/**Runs after the socket is opened, as a result of a successful start() call.*/
	public abstract void onOpen() throws IOException;
	/**Runs when the socket is closed. The socket may or may not be open at this point.*/
	public abstract void onClose() throws IOException;
	/**Runs when a timeout occurs. Default behavior closes the connection. Will not be run in NIO mode with no ScheduledExectorService provided.*/
	public void onTimeout() {alive=false;}
	/**Runs when data is received. The buffer's position will be at the end of the input it received, which will be of length bytes.*/
	public abstract void onData(ByteBuffer data, int length) throws IOException;
	/**Sends the contents of the provided ByteBuffer(from position to limit).*/
	public void send(ByteBuffer msg){
		client.send(msg);
		
	}
	/**Sends the contents of the provided byte[]. Equivalent to send(msg,0,msg.length)*/
	public void send(byte[] msg){ 
		client.send(msg);
	}
	/**Sends {@code length} bytes of the provided byte[], starting at {@code off}.*/
	public void send(byte[] msg,int off, int length){
		client.send(msg,off,length);
	}
	/**Flushes the output buffer, if enabled. Otherwise does nothing.*/
	public void flush(){
		client.flush();
	}
	/**Returns the remote IP address.*/
	public InetAddress getInetAddress(){
		return client.getInetAddress();
	}
	/**Returns the remote port.*/
	public int getPort(){
		return client.getPort();
	}
	/**Returns the socket associated with this client, if in IO mode, otherwise null.*/
	public Socket getSocket() {
		return client.getSocket();
	}
	/**Returns the AsynchronousSocketChannel associated with this client, if in NIO mode, otherwise null.*/
	public AsynchronousSocketChannel getChannel() {
		return client.getChannel();
	}
	/**Returns whether this socket is in NIO mode.*/
	public boolean isNio(){
		return getChannel()!=null;
	}
	/**Resize and return the buffer holding at least length bytes, up to opt.in.max().<br>*/
	public ByteBuffer resizeInput(ByteBuffer input, int length) {
		return resizeImpl(input, length, opt.in().max());
	}
	/**Resize and return the buffer holding at least length bytes, up to opt.out.max().<br>*/
	public ByteBuffer resizeOutput(ByteBuffer output,int length) {
		return resizeImpl(output, length, opt.out().max());
	}
	//TODO: scattering read/gathering write instead of resizing?
	private static ByteBuffer resizeImpl(ByteBuffer target, int length, int max) {
		if(length<=target.remaining()||target.capacity()==max)
			return target;
		int newLen=Math.min(max,Integer.highestOneBit((target.position()+length)<<1));
		return (target.isDirect()?ByteBuffer.allocateDirect(newLen):ByteBuffer.allocate(newLen)).put(target.flip());
	}
	/**Closes the context immediately, without waiting for a timeout as would setting alive=false.*/
	public void close() {
		client.close();
	}
	//package-private
	static interface Client{
		public void start() throws IOException;
		public Socket getSocket();
		public AsynchronousSocketChannel getChannel();
		public InetAddress getInetAddress();
		public int getPort();
		public ByteBuffer buffer();
		public void send(ByteBuffer msg);
		void close();
		public void flush();
		public default void send(byte[] msg){
			send(ByteBuffer.wrap(msg));
		}
		public default void send(byte[] msg, int off, int len) {
			send(ByteBuffer.wrap(msg,off,len));
		}
		public static final Client NULL_CLIENT=new Client(){
			@Override public void start(){}
			@Override public void send(ByteBuffer msg){}
			@Override public void flush(){}
			@Override
			public InetAddress getInetAddress() {
				return null;
			}
			@Override
			public Socket getSocket() {
				return null;
			}
			@Override
			public AsynchronousSocketChannel getChannel() {
				return null;
			}
			@Override
			public ByteBuffer buffer() {
				return null;
			}
			@Override
			public int getPort() {
				return 0;
			}
			@Override
			public void close() {
			}
		};
		static final Lock NULL_LOCK=new ReentrantLock(){
			private static final long serialVersionUID = 1697081494093320661L;
			@Override
			public void lock() {
				
			}
			@Override
			public void unlock() {
				
			}
		};
		static class OfIo implements Client{
			protected final Socket client;
			protected final InputStream input;
			protected final OutputStream output;
			private final ClientContext ctx;
			private final ThreadFactory factory;
			protected volatile ByteBuffer inBuffer;
			protected volatile ByteBuffer outBuffer;
			private final Lock outLock;
			
			public OfIo(ClientContext ctx, Socket client, ThreadFactory factory) throws IOException {
				this.client=client;
				input=client.getInputStream();
				output=client.getOutputStream();
				inBuffer=ByteBuffer.allocate(ctx.opt.in().min());
				outBuffer=ctx.opt.out().max()<=0?null:ByteBuffer.allocate(ctx.opt.out().min());
				outLock=ctx.opt.out().locked()?new ReentrantLock():NULL_LOCK;
				client.setSoTimeout(ctx.opt.timeout());
				this.ctx=ctx;
				this.factory=factory;
			}
			@Override
			public ByteBuffer buffer() {return inBuffer;}
			@Override
			public void start(){
				factory.newThread(this::run).start();
			}
			public void run() {
				try(client;input;output){
					while(ctx.alive) {
						try {
							if(inBuffer.remaining()==0) {
								var resized=ctx.resizeInput(inBuffer,1);
								if(resized==null) {
									ctx.alive=false;
									break;
								}else inBuffer=resized;
							}
							int count=input.read(inBuffer.array(),inBuffer.position(),inBuffer.remaining());
							if(count<0) {
								ctx.onData(inBuffer,count);
								ctx.alive=false;
								break;
							}
							ctx.onData(inBuffer.position(inBuffer.position()+count),count);//TODO: end if full and nothing happens?
						}catch(SocketTimeoutException ste) {
							ctx.onTimeout();
						}
						if(ctx.opt.mspt()>0)
							try{
								Thread.sleep(ctx.opt.mspt());
							}catch(InterruptedException iee) {
								Thread.currentThread().interrupt();
							}
					}
				}catch(IOException e) {
					//TODO: logging
				}finally {
					try {
						ctx.onClose();
					} catch (IOException e1) {}
					ctx.alive=false;
				}
			}
			@Override
			public void close() {
				ctx.alive=false;
				try {
					client.shutdownInput();//stop reading
				} catch (IOException e) {
					
				}
			}
			@Override
			public void flush() {
				if(outBuffer==null)return;
				outLock.lock();
				try {
					if(outBuffer.position()==0)return;
					sendImpl(outBuffer.flip(),true);
					outBuffer.clear();
				}finally {
					outLock.unlock();
				}
			}
			@Override
			public int getPort() {
				return client.getPort();
			}
			private void sendImpl(ByteBuffer b, boolean direct) {
				try {
					if(!ctx.alive)return;
					if(!direct&&outBuffer!=null) {
						outBuffer=ctx.resizeOutput(outBuffer,b.remaining());
						while(ctx.alive&&outBuffer.remaining()<b.remaining()) {
							var slice=b.slice(b.position(),outBuffer.remaining());
							outBuffer.put(slice);
							b.position(b.position()+slice.limit());
							sendImpl(outBuffer.flip(),true);
							outBuffer.clear();
						}
						outBuffer.put(b);
						return;
					}
					output.write(b.array(),b.position(),b.remaining());
					b.position(b.limit());
					output.flush();
				}catch(IOException ioe) {
					ctx.alive=false;
				}catch(Exception e){
					ctx.alive=false;
					throw e;
				}
			}
			@Override
			public void send(ByteBuffer b){
				outLock.lock();
				try {
					sendImpl(b,false);
				}finally {
					outLock.unlock();
				}
			}
			@Override
			public InetAddress getInetAddress() {
				return client.getInetAddress();
			}
			@Override
			public Socket getSocket() {
				return client;
			}
			@Override
			public AsynchronousSocketChannel getChannel() {
				return null;
			}
		}
		static class OfNio implements Client {
			protected final ClientContext ctx;
			protected final AsynchronousSocketChannel client;
			protected volatile ByteBuffer input;
			protected volatile ByteBuffer output;
			private final Lock sendLock;
			private final ScheduledExecutorService ses;
			private static final ScheduledExecutorService asyncReadTimer=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"Async read scheduler"));
			private final Lock bufferLock;
			private ScheduledFuture<?> nextTimeout;
			public OfNio(ClientContext ctx, AsynchronousSocketChannel asc) throws IOException{
				this.client=asc;
				this.ctx=ctx;
				this.ses=ctx.opt.timeoutSvc();
				input=alloc(ctx.opt.in().min());
				output=ctx.opt.out().max()>0?allocW(ctx.opt.out().min()):null;
				sendLock=ctx.opt.out().locked()?new ReentrantLock():NULL_LOCK;
				bufferLock=(ctx.opt.out().locked()&&ctx.opt.out().max()>0)?new ReentrantLock():NULL_LOCK;
			}
			@Override
			public ByteBuffer buffer() {return input;}
			private ByteBuffer alloc(int len) {
				return ctx.opt.in().direct()?
						ByteBuffer.allocateDirect(len):ByteBuffer.allocate(len);
			}
			private ByteBuffer allocW(int len) {
				return ctx.opt.out().direct()?
						ByteBuffer.allocateDirect(len):ByteBuffer.allocate(len);
			}
			private final void onTimeout() {
				if(ctx.alive) {
					if(nextTimeout!=null)
						ctx.onTimeout();
					if(ctx.alive)
						nextTimeout=ses.schedule(this::onTimeout,ctx.opt.timeout(), TimeUnit.MILLISECONDS);
					
				}
			}
			private void readWithTimeout(ByteBuffer input, CompletionHandler<Integer, Void> handler) {
				if(ses==null) {
					client.read(input,ctx.opt.timeout(),TimeUnit.MILLISECONDS,null,handler);
				}else {
					client.read(input,null,handler); 
				}
			}
			@Override
			public void start() throws IOException{
				if(ses!=null) {
					onTimeout();
				}
				if(!ctx.alive) {
					close2();
					return;
				}
				readWithTimeout(input, new CompletionHandler<Integer, Void>() {
					@Override
					public void completed(Integer result, Void attachment) {
						if(ses!=null&&nextTimeout!=null) {
							nextTimeout.cancel(false);
							nextTimeout=ses.scheduleWithFixedDelay(ctx::onTimeout,ctx.opt.timeout(),ctx.opt.timeout(), TimeUnit.MILLISECONDS);
						}
						try {
							if(ctx.alive) {
								ctx.onData(input,result);
							}if(result<0)
								ctx.alive=false;
						}catch (IOException e) {
							ctx.alive=false;
						}catch (Exception e) {
							ctx.alive=false;
							throw e;
						}finally {
							if(ctx.alive&&client.isOpen()) {
								if(input.remaining()==0&&(input=ctx.resizeInput(input,1)).remaining()==0) {
									close2();
								}else{//realloc can fail, resulting in alive=false
									if(ctx.opt.mspt()>0)
										asyncReadTimer.schedule(()->readWithTimeout(input,this),ctx.opt.mspt(),TimeUnit.MILLISECONDS);
									else readWithTimeout(input,this);
								}
							}else close2();
						}
					}
					//TODO: make sure onCLose not called more than once
					@Override
					public void failed(Throwable exc, Void attachment) {
						close2();
					}
				});
			}
			@Override
			public void close() {
				ctx.alive=false;
				try {
					client.shutdownInput();
					client.shutdownOutput();
				} catch (IOException e) {}
			}
			void close2() {
				ctx.alive=false;
				if(nextTimeout!=null)nextTimeout.cancel(false);
				try {
					ctx.onClose();
				} catch (IOException e1) {}
				if(client.isOpen())
					try {
						client.close();
					} catch (IOException e) {
						
					}
				
			}
			@Override
			public void flush() {
				if(output==null||output.position()==0)return;
				bufferLock.lock();
				try {
				sendImpl(output.flip());
				output.clear();
				}finally {
					bufferLock.unlock();
				}
			}
			@Override
			public void send(ByteBuffer msg){
				bufferLock.lock();
				try {
					if(output!=null) {
						output=ctx.resizeOutput(output,msg.remaining());
						while(ctx.alive&&output.remaining()<msg.remaining()) {
							var slice=msg.slice(msg.position(),output.remaining());
							output.put(slice);
							sendImpl(output.flip());
							msg.position(msg.position()+slice.limit());
							output.clear();
						}
						output.put(msg);
						return;
					}
				}catch(Exception e){
					ctx.alive=false;
					throw e;
				}finally {
					bufferLock.unlock();
				}
				if(ctx.alive)
					sendImpl(msg);
			}
			private void sendImpl(ByteBuffer msg) {
				sendLock.lock(); 
				try {
					while(msg.hasRemaining()&&ctx.alive) {
						//TODO: write without blocking?
						//-->how ensure buffer isn't modified/another write isn't added without blocking
						if(!client.isOpen()||client.write(msg).get()==0) {
							ctx.alive=false;
							return;
						};
					}
				} catch (InterruptedException | ExecutionException e) {
					ctx.alive=false;
				} finally {
					sendLock.unlock();
				}
			}
			@Override
			public int getPort(){
				try {
				if(client.getRemoteAddress() instanceof InetSocketAddress isa)
					return isa.getPort();
				} catch (IOException e) {
					
				}
				return -1;
			}
			@Override
			public InetAddress getInetAddress(){
				try {
					if(client.getRemoteAddress() instanceof InetSocketAddress isa)
						return isa.getAddress();
				} catch (IOException e) {
					
				}
				return null;
			}
			@Override
			public Socket getSocket() {
				return null;
			}
			@Override
			public AsynchronousSocketChannel getChannel() {
				return client;
			}
		}
	}
	
	
	
}