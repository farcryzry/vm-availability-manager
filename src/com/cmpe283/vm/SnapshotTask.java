package com.cmpe283.vm;

public class SnapshotTask implements Runnable {
	
	private int interval;
	
	public SnapshotTask(int interval) {
		this.interval = interval;
	}

	@Override
	public void run() {
		SnapshotManager.backupCache(interval);
	}

}
