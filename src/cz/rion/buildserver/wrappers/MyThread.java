package cz.rion.buildserver.wrappers;

import java.util.ArrayList;
import java.util.List;

public abstract class MyThread {

	private static final List<MyThreadObserver> observers = new ArrayList<>();

	public static interface MyThreadObserver {
		void ThreadStarted(MyThread thread);

		void ThreadFinished(MyThread thread);

	}

	private static final List<MyThread> threads = new ArrayList<>();

	private final Thread _thread = new Thread() {

		@Override
		public void run() {
			synchronized (threads) {
				threads.add(MyThread.this);
			}
			for (MyThreadObserver obs : observers) {
				obs.ThreadStarted(MyThread.this);
			}
			Thread.currentThread().setName(name);
			try {
				runAsync();
			} catch (Throwable t) {
				t.printStackTrace();
			}

			synchronized (threads) {
				threads.remove(MyThread.this);
			}
			for (MyThreadObserver obs : observers) {
				obs.ThreadFinished(MyThread.this);
			}

		}
	};

	private final String name;

	public String getName() {
		return name;
	}

	public int getID() {
		return id;
	}

	private final int id;
	private static int ids = 0;

	public MyThread(String name) {
		_thread.setName(name);
		this.name = name;
		id = ids;
		ids++;
	}

	public String getStackTrace() {
		try {

			StackTraceElement[] trace = _thread.getStackTrace();
			if (trace != null) {
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < trace.length; i++) {
					if (i > 0) { // Not first
						sb.append("\r\n");
					}
					StackTraceElement t = trace[i];
					sb.append("\t" + t.getClassName() + ":" + t.getMethodName() + ":" + t.getFileName() + ":" + t.getLineNumber());
				}
				return sb.toString();
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return "Failed getting stack trace";
	}

	public static final void getThreads(List<MyThread> lst) {
		lst.clear();
		synchronized (threads) {
			lst.addAll(threads);
		}
	}

	protected abstract void runAsync();

	public void start() {
		_thread.start();
	}

	public void join() throws InterruptedException {
		_thread.join();
	}

	public boolean isCurrentThread() {
		return Thread.currentThread() == _thread;
	}

	public static void addThreadObserver(MyThreadObserver obs) {
		synchronized (threads) {
			observers.add(obs);
			for (MyThread thread : threads) {
				obs.ThreadStarted(thread);
			}
		}
	}
}
