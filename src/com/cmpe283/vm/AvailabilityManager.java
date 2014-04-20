package com.cmpe283.vm;

import java.rmi.RemoteException;
import java.util.logging.Logger;

import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.mo.VirtualMachine;

public class AvailabilityManager {
	private static final Logger logger = Logger.getLogger(AvailabilityManager.class.getName());

	public static void main(String[] args) throws InvalidProperty, RuntimeFault, RemoteException, Exception {

		//VcenterManager.showStatistics();
		
		
		//Thread t = new Thread(new SnapshotTask(0));
		//t.start();

		//VcenterManager.setPowerOffAlarm();

		
		VcenterManager.failover(0, "130.65.132.151");
		
		//VirtualMachine vm = VcenterManager.getVmByName("T03-VM03-Lin-Ruiyun");
		
		//VcenterManager.powerOff(vm);
		

	}
}
