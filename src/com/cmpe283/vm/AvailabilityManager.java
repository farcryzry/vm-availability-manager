package com.cmpe283.vm;

import java.util.logging.Logger;

public class AvailabilityManager {
	private static final Logger logger = Logger.getLogger(AvailabilityManager.class.getName());

	private VcenterManager vcenterManager;

	public AvailabilityManager() {
		try {
			vcenterManager = new VcenterManager();
			if (vcenterManager == null)
				throw new Exception("VM Manager cannot be initialized");
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
	}
	
	public VcenterManager getVcenterManager() {
		return vcenterManager;
	}
	
	public static void main(String [] args) {
		AvailabilityManager availabilityManager = new AvailabilityManager();
		VcenterManager vcenterManager = availabilityManager.getVcenterManager();
		SnapshotManager snapshotManager = new SnapshotManager();
		
		
		vcenterManager.showStatistics();
		
		//snapshotManager.backupCache(0);
		
		//vcenterManager.setPowerOffAlarm();
	}
}
