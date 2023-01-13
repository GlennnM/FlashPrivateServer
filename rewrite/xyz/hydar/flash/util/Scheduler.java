package xyz.hydar.flash.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class Scheduler{
	private Scheduler() {}
	private static final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
		
		@Override
		public Thread newThread(Runnable r) {
			return Thread.ofVirtual().name("Scheduler thread").unstarted(r);
		}
	});

	public static void schedule(Runnable task, int millis) {
		scheduler.schedule(task,millis,TimeUnit.MILLISECONDS);
	}
}