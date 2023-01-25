package xyz.hydar.flash.util;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Scheduler{
	private Scheduler() {}
	public static final ScheduledExecutorService ses=newSingleThreadScheduledExecutor(r->new Thread(r,"Scheduler thread"));

	public static void schedule(Runnable task, int millis) {
		ses.schedule(task,millis,TimeUnit.MILLISECONDS);
	}
}