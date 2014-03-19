package com.cmpe283.vm;

import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.vmware.vim25.FileFault;
import com.vmware.vim25.HostVMotionCompatibility;
import com.vmware.vim25.InsufficientResourcesFault;
import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.InvalidState;
import com.vmware.vim25.ManagedEntityStatus;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.TaskInProgress;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.VirtualMachineMovePriority;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.VmConfigFault;
import com.vmware.vim25.mo.ComputeResource;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;

public class VcenterManager {
	private static final Logger logger = Logger.getLogger(VcenterManager.class.getName());

	private static ServiceInstance ServiceInstance;

	private static boolean isFailoverOn = true;

	public VcenterManager() {
		try {
			ServiceInstance =
					new ServiceInstance(new URL(VCenterSettings.URL), VCenterSettings.USER_NAME, VCenterSettings.PASSWORD, true);
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
		VirtualMachine vm =
				(VirtualMachine) new InventoryNavigator(vmFolder).searchManagedEntity("VirtualMachine", vmName);

		if (vm == null)
			throw new Exception("vm is null");

		return vm;
	}

	public HostSystem getVhostByName(String hostName) throws Exception {
		Folder vmFolder = ServiceInstance.getRootFolder();
		HostSystem host =
				(HostSystem) new InventoryNavigator(vmFolder).searchManagedEntity("HostSystem", hostName);
		if (host == null)
			throw new Exception("host is null");

		return host;
	}

	public List<VirtualMachine> getVMs() throws InvalidProperty, RuntimeFault, RemoteException {
		List<VirtualMachine> virtualMachines = new ArrayList<VirtualMachine>();

		Folder vmFolder = ServiceInstance.getRootFolder();
		ManagedEntity[] entities =
				new InventoryNavigator(vmFolder).searchManagedEntities("VirtualMachine");
		if (entities != null) {
			for (ManagedEntity entity : entities) {
				virtualMachines.add((VirtualMachine) entity);
			}
		} else {
			logger.warning("Found No Virtual Machines!");
		}

		return virtualMachines;
	}

	public boolean powerOn(String vmName) {

		try {
			VirtualMachine vm = getVmByName(vmName);

			if (isPoweredOn(vm))
				return true;

			Task task = vm.powerOnVM_Task(null);
			System.out.println(String.format("VM %s now is powerring on! Please wait...", vm.getName()));

			if (task.waitForTask() == Task.SUCCESS) {
				System.out.println(String.format("VM %s  is powered on successfully!", vm.getName()));
				return true;
			}

			return false;

		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
		return false;
	}

	private boolean isPoweredOn(VirtualMachine vm) {
		if (vm == null)
			return false;

		VirtualMachinePowerState vmps = vm.getRuntime().getPowerState();
		return vmps == VirtualMachinePowerState.poweredOn;
	}

	/*
	 * 4. If the vHost is not alive, try to make it alive. If even after a fixed
	 * number of attempts, the vHost does not come up, remove the vHost from the
	 * list.
	 */
	public boolean migrate(VirtualMachine vm, String newHostName, boolean isColdMigration) {
		try {

			HostSystem newHost = getVhostByName(newHostName);

			boolean isVhostAvailable = false;

			for (int i = 0; i < 10; i++) {
				
				isVhostAvailable = checkVhostStatus(newHost);
				if (isVhostAvailable) break;

				Thread.sleep(2000);
			}

			if (!isVhostAvailable) {
				//todo: remove vhost
				return false;
			}

			ComputeResource cr = (ComputeResource) newHost.getParent();

			if (isColdMigration || checkVmotionCompatibility(vm, newHost)) {

				Task task =
						vm.migrateVM_Task(cr.getResourcePool(), newHost, VirtualMachineMovePriority.highPriority, isColdMigration
								? VirtualMachinePowerState.poweredOff
								: VirtualMachinePowerState.poweredOn);

				if (task.waitForTask() == Task.SUCCESS) {
					System.out.println(isColdMigration ? "Cold Migrated" : "VMotioned!");
				} else {
					System.out.println(isColdMigration ? "Cold Migrated" : "VMotioned!");
					TaskInfo info = task.getTaskInfo();
					System.out.println(info.getError().getFault());
				}
			}

			return false;

		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
		return false;
	}

	private boolean checkVhostStatus(HostSystem host) {
		if (host == null)
			return false;

		ManagedEntityStatus hostStatus = host.getOverallStatus();

		System.out.println(String.format("VHost %s: Overall Status: %s", host.getName(), hostStatus));

		return hostStatus == ManagedEntityStatus.green;
	}

	private boolean checkVmotionCompatibility(VirtualMachine vm, HostSystem host) throws RuntimeFault, RemoteException {
		String[] checks = new String[] { "cpu", "software" };
		HostVMotionCompatibility[] vmcs =
				ServiceInstance.queryVMotionCompatibility(vm, new HostSystem[] { host }, checks);

		String[] comps = vmcs[0].getCompatibility();
		if (checks.length != comps.length) {
			System.out.println("CPU/software NOT compatible. Exit.");
			return false;
		}

		return true;
	}

	/*
	 * 3. When a VM fails with ping heartbeat, then failover to another VM
	 * host/resource pool using VMDK image format (Cold migration). default 5
	 * seconds. vm.getGuestHeartbeatStatus();
	 */
	public void failover(int interval) {
		if (interval <= 0)
			interval = 5000;

		try {
			List<VirtualMachine> vms = getVMs();

			while (isFailoverOn) {
				for (VirtualMachine vm : vms) {
					ManagedEntityStatus heartbeatStatus = vm.getGuestHeartbeatStatus();
					switch (heartbeatStatus) {
					case red:
						migrate(vm, "1.1.1.1", true);
						break;
					case gray:
						System.out.println(String.format("VM %s: VMWare Tools is not installed or not running", vm.getName()));
						break;
					case yellow:
						System.out.println(String.format("VM %s: Intermittent heartbeat, possible due to heavy load at the gust OS", vm.getName()));
						break;
					default:
						break;
					}
				}

				Thread.sleep(interval);
			}

		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}

	}

	public void turnOffFailOver() {
		isFailoverOn = false;
	}
}
