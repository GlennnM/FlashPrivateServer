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
import java.nio.channels.InterruptedByTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
/**
 * Client context. Handles data with onData(the only method that needs to be overriden).<br>
 * onOpen and onClose can be implemented as well, and data can be sent with send()<br>
 * flush() only needs to be called if the ClientOptions write buffer size is >0.<br>
*	How to process the resulting byte buffers:
*	0. flip
*	1. Too small for header? -> pos=end, continue
*	2. Length from header is too small? -> pos=end, continue
*	3. Keep reading packets in order until 2.
*	4. If we read >=1 packets, compact the buffer to the last packet start before pos=end+continuing
 * */
public abstract class ClientContext{
	
	public volatile boolean alive=true;
	public volatile Client client=Client.NULL_CLIENT;
	public final ClientOptions opt;
	public static final ThreadFactory DEFAULT_FACTORY=new ThreadFactory() {
		@Override
		public Thread newThread(Runnable r) {
			return Thread.ofVirtual().unstarted(r);
		}
	};
	public ClientContext() {
		this(ClientOptions.DEFAULT);
	}
	public ClientContext(ClientOptions opt) {
		this.opt=opt;
	}
	public final void start(AsynchronousSocketChannel socket) throws IOException {
		if(client!=Client.NULL_CLIENT)throw new UnsupportedOperationException("already init");
		this.client = new Client.OfNio(this,socket);
		onOpen();
		client.start();
	}
	public final void start(Socket socket) throws IOException {
		start(socket,DEFAULT_FACTORY);
	}
	public final void start(Socket socket,ThreadFactory factory) throws IOException {
		if(client!=Client.NULL_CLIENT)throw new UnsupportedOperationException("already init");
		this.client = new Client.OfIo(this,socket,factory);
		onOpen();
		client.start();
	}
	public abstract void onOpen();
	public abstract void onClose();
	public void onTimeout() {alive=false;}
	public abstract void onData(ByteBuffer data, int length) throws IOException;
	public void send(ByteBuffer msg){
		client.send(msg);
	}
	public void send(byte[] msg){ 
		client.send(msg);
	}
	public void send(byte[] msg,int off, int length){
		client.send(msg,off,length);
	}
	public void flush() throws IOException{
		client.flush();
	}
	public InetAddress getInetAddress() throws IOException {
		return client.getInetAddress();
	}
	public Socket getSocket() {
		return client.getSocket();
	}
	public AsynchronousSocketChannel getChannel() {
		return client.getChannel();
	}
	public boolean isNio(){
		return getChannel()!=null;
	}
	public ByteBuffer ensureInputLength(ByteBuffer input, int length) throws IOException {
		if(length>input.remaining())
			return resizeInput(input,length);
		else return input;
	}
	public ByteBuffer ensureOutputLength(ByteBuffer output, int length) throws IOException {
		if(length>output.capacity())
			return resizeOutput(output,length);
		else if(length>output.remaining()) {
			flush();
			return output;
		}else return output;
	}
	public ByteBuffer resizeInput(ByteBuffer input, int length) {
		if(input.capacity()+length>opt.in().max()) {
			return null;
		}
		return resizeImpl(input, opt.in().max());
	}
	public ByteBuffer resizeOutput(ByteBuffer output,int length) {
		if(output.capacity()+length>opt.out().max()) {
			return null;
		}
		return resizeImpl(output, opt.out().max());
	}
	private static ByteBuffer resizeImpl(ByteBuffer target, int max) {
		int newLen=Integer.highestOneBit(target.position()<<1);
		if(newLen<=max)
			return (target.isDirect()?ByteBuffer.allocateDirect(newLen):ByteBuffer.allocate(newLen)).put(target.flip());
		else {
			return null;
		}
	}
	final boolean prepareOut(ByteBuffer outBuffer, int length) throws IOException {
		if(outBuffer!=null) {
			if((outBuffer=ensureOutputLength(outBuffer,length))!=null) {
				return true;
			}else {
				alive=false;
				return false;
			}
		}
		return false;
	}
	static interface Client{
		public void start() throws IOException;
		public Socket getSocket();
		public AsynchronousSocketChannel getChannel();
		public InetAddress getInetAddress() throws IOException;
		public ByteBuffer buffer();
		public void send(ByteBuffer msg);
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
								if((inBuffer=ctx.ensureInputLength(inBuffer,1))==null) {
									ctx.alive=false;
									break;
								}
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
				}catch(Exception e) {
					//TODO: logging
				}finally {
					ctx.onClose();
					ctx.alive=false;
					
				}
			}
			@Override
			public void flush() {
				System.out.println("hydr");
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
			private void sendImpl(ByteBuffer b, boolean direct) {
				try {
					if(!direct&&ctx.prepareOut(outBuffer,b.remaining())) {
						outBuffer.put(b);
						return;
					}if(!ctx.alive)return;
					output.write(b.array(),b.position(),b.remaining());
					b.position(b.limit());
					output.flush();
				}catch(IOException ioe) {
					ctx.alive=false;
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
			public void send(byte[] b){
				outLock.lock();
				try {
					if(ctx.prepareOut(outBuffer,b.length)) {
						outBuffer.put(b);
						return;
					}if(!ctx.alive)return;
					output.write(b);
				}catch(IOException ioe) {
					ctx.alive=false;
				}finally {
					outLock.unlock();
				}
			}
			@Override
			public void send(byte[] b, int off, int len){
				outLock.lock();
				try {
					if(ctx.prepareOut(outBuffer,len)) {
						outBuffer.put(b,off,len);
						return;
					}if(!ctx.alive)return;
					if(!client.isClosed())
						output.write(b, off, len);
					else ctx.alive=false;
				}catch(IOException ioe) {
					ctx.alive=false;
				}finally {
					outLock.unlock();
				}
			}
			@Override
			public InetAddress getInetAddress() {
				// TODO Auto-generated method stub
				return null;
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
				readWithTimeout(input,new CompletionHandler<Integer, Void>() {
					@Override
					public void completed(Integer result, Void attachment) {
						if(ses!=null&&nextTimeout!=null) {
							nextTimeout.cancel(false);
							nextTimeout=ses.scheduleWithFixedDelay(ctx::onTimeout,ctx.opt.timeout(),ctx.opt.timeout(), TimeUnit.MILLISECONDS);
						}
						try {
							if(ctx.alive) {
								ctx.onData(input,result);
							}if(result<0)ctx.alive=false;
						} catch (Exception e) {
							ctx.alive=false;
						}
						if(ctx.alive&&client.isOpen()) {
							
							if(input.remaining()==0)
								try {
									if((input=ctx.ensureInputLength(input,1))==null) {
										ctx.alive=false;
									}
								}catch(IOException ioe) {
									ctx.alive=false;
								}
							if(ctx.alive) {//realloc can fail, resulting in alive=false
								if(ctx.opt.mspt()>0)
									try{
										Thread.sleep(ctx.opt.mspt());
									}catch(InterruptedException iee) {
										Thread.currentThread().interrupt();
									}
								readWithTimeout(input,this);
								return;
							}
						}else close();
					}
					//TODO: make sure onCLose not called more than once
					@Override
					public void failed(Throwable exc, Void attachment) {
						close();
					}
				});
			}
			private void close() {
				ctx.alive=false;
				if(nextTimeout!=null)nextTimeout.cancel(false);
				ctx.onClose();
				if(client.isOpen())
					try {
						client.close();
					}catch(IOException ioe) {}
			}
			@Override
			public void flush() {
				if(output==null)return;
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
				try {
					bufferLock.lock();
					if(ctx.prepareOut(output,msg.remaining())) {
						output.put(msg);
						return;
					}
				}catch(IOException ioe) {
					ctx.alive=false;
				}finally {
					bufferLock.unlock();
				}
				if(!ctx.alive)return;
				sendImpl(msg);
			}
			private void sendImpl(ByteBuffer msg) {
				sendLock.lock(); 
				try {
					while(msg.hasRemaining()) {
						if(!client.isOpen()||client.write(msg).get()==0) {
							ctx.alive=false;
						};
					}
				} catch (InterruptedException | ExecutionException e) {
					ctx.alive=false;
				} finally {
					sendLock.unlock();
				}
			}
			@Override
			public InetAddress getInetAddress() throws IOException {
				if(client.getRemoteAddress() instanceof InetSocketAddress isa)
					return isa.getAddress();
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