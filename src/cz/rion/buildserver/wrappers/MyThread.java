package cz.rion.buildserver.wrappers;

import java.util.ArrayList;
import java.util.List;

public abstract class MyThread {

	private static final List<MyThread> threads = new ArrayList<>();

	private final Thread _thread = new Thread() {

		@Override
		public void run() {
			synchronized (threads) {
				threads.add(MyThread.this);
			}
			try {
				runAsync();
			} catch (Throwable t) {
				t.printStackTrace();
			}
			synchronized (threads) {
				threads.remove(MyThread.this);
			}
		}
	};

	private String name = "Default thread";

	public void setName(String name) {
		_thread.setName(name);
		this.name = name;
	}

	public String getName() {
		return name;
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

}
