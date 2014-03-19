package com.cmpe283.vm;

import java.util.List;
import java.util.logging.Logger;

import com.vmware.vim25.mo.VirtualMachine;

public class AvailabilityManager {
	private static final Logger logger = Logger.getLogger(AvailabilityManager.class.getName());

	private static VcenterManager VMManager;

	public AvailabilityManager() {
		try {
			VMManager = new VcenterManager();
			if (VMManager == null)
				throw new Exception("VM Manager cannot be initialized");
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
	}

	/*
	 * 1. Gather statistics (such as CPU, I/O, network etc) for a VM and display
	 * in a text format
	 */
	public void showStatistics() {
		try {
			List<VirtualMachine> vms = VMManager.getVMs();
			for (VirtualMachine vm : vms) {
				VirtualMachineDescription vmDesc = new VirtualMachineDescription(vm);
				System.out.println(vmDesc);
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
	}

}
