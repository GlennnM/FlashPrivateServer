package xyz.hydar.net;

import java.util.concurrent.ScheduledExecutorService;

/**Client options. Use the builder to construct. Note that locked input doesn't actually mean anything*/
public record ClientOptions(int timeout, int mspt, ScheduledExecutorService timeoutSvc, BufferOptions in, BufferOptions out) {

	public static final ClientOptions DEFAULT = new ClientOptions(15000, 0, null, BufferOptions.DEFAULT,BufferOptions.NONE);
	public ClientOptions() {
		this(DEFAULT.timeout());
	}
	public ClientOptions(int timeout) {
		this(timeout, DEFAULT.mspt,null,DEFAULT.in, DEFAULT.out);
	}
	public ClientOptions(int timeout, int tickDelay) {
		this(timeout, tickDelay, null,DEFAULT.in, DEFAULT.out);
	}
	
	public static Builder builder() {
		return new Builder();
	}
	public static class Builder{
		int timeout=DEFAULT.timeout();
		int mspt=DEFAULT.mspt();
		ScheduledExecutorService timeoutSvc=DEFAULT.timeoutSvc();
		
		int inputMin=DEFAULT.in.min();
		int inputMax=DEFAULT.in.max();
		boolean inputDirect=DEFAULT.in.direct();
		boolean inputLock=DEFAULT.in.locked();
		
		int outputMin=DEFAULT.out.min();
		int outputMax=DEFAULT.out.max();
		boolean outputDirect=DEFAULT.out.direct();
		boolean outputLock=DEFAULT.out.locked();
		
		Builder(){}
		public Builder timeout(int timeout) {
			this.timeout=timeout;
			return this;
		}
		public Builder timeout(int timeout, ScheduledExecutorService timeoutSvc) {
			this.timeout=timeout;
			this.timeoutSvc=timeoutSvc;
			return this;
		}
		public Builder mspt(int mspt) {
			this.mspt=mspt;
			return this;
		}
		public Builder input(int fixedSize) {
			inputMin=inputMax=fixedSize;
			return this;
		}
		public Builder input(int min, int max) {
			inputMin=min;
			inputMax=max;
			return this;
		}
		public Builder inputDirect() {
			return inputDirect(true);
		}
		public Builder inputDirect(boolean direct) {
			inputDirect=direct;
			return this;
		}
		public Builder inputLocked() {
			return inputLocked(true);
		}
		public Builder inputLocked(boolean locked) {
			inputLock=locked;
			return this;
		}
		public Builder input(BufferOptions options) {
			inputMin=options.min();
			inputMax=options.max();
			inputDirect=options.direct();
			inputLock=options.locked();
			return this;
		}
		public Builder output(int fixedSize) {
			outputMin=outputMax=fixedSize;
			return this;
		}
		public Builder output(int min, int max) {
			outputMin=min;
			outputMax=max;
			return this;
		}
		public Builder outputDirect() {
			return inputDirect(true);
		}
		public Builder outputDirect(boolean direct) {
			outputDirect=direct;
			return this;
		}
		public Builder outputLocked() {
			return outputLocked(true);
		}
		public Builder outputLocked(boolean locked) {
			outputLock=locked;
			return this;
		}
		public Builder output(BufferOptions options) {
			outputMin=options.min();
			outputMax=options.max();
			outputDirect=options.direct();
			outputLock=options.locked();
			return this;
		}
		public ClientOptions build() {
			return new ClientOptions(
				timeout,mspt,timeoutSvc,
				new BufferOptions(inputMin,inputMax,inputDirect,inputLock),
				new BufferOptions(outputMin,outputMax,outputDirect,outputLock)
			);
		}
	}
}
