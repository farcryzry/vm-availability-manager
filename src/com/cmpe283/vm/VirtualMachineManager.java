package com.cmpe283.vm;

import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.VirtualMachine;

public class VirtualMachineManager {
	private static final Logger logger = Logger
			.getLogger(VirtualMachineManager.class.getName());

	private static ServiceInstance ServiceInstance;

	public VirtualMachineManager() {
		try {
			ServiceInstance = new ServiceInstance(new URL(VCenterSettings.URL),
					VCenterSettings.USER_NAME, VCenterSettings.PASSWORD, true);
			if (ServiceInstance == null)
				throw new Exception("ServiceInstance cannot be initialized");
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
	}
	
	public void logout() {
		ServiceInstance.getServerConnection().logout();
	}
	
	public VirtualMachine getVmByName(String vmName) throws InvalidProperty, RuntimeFault, RemoteException, Exception {
		Folder vmFolder = ServiceInstance.getRootFolder();
		VirtualMachine vm = (VirtualMachine) new InventoryNavigator(
				vmFolder).searchManagedEntity("VirtualMachine", vmName);
		
		if(vm == null) throw new Exception("vm is null");
		
		return vm;
	}

	public List<VirtualMachine> getVMs() throws InvalidProperty, RuntimeFault, RemoteException {
		List<VirtualMachine> virtualMachines = new ArrayList<VirtualMachine>();

		Folder vmFolder = ServiceInstance.getRootFolder();
		ManagedEntity[] entities = new InventoryNavigator(vmFolder)
				.searchManagedEntities("VirtualMachine");
		if (entities != null) {
			for (ManagedEntity entity : entities) {
				virtualMachines.add((VirtualMachine) entity);
			}
		} else {
			logger.warning("Found No Virtual Machines!");
		}

		return virtualMachines;
	}
}
