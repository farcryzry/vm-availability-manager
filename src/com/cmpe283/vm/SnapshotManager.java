package com.cmpe283.vm;

import java.net.URL;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.ManagedEntityStatus;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.VirtualMachineSnapshotInfo;
import com.vmware.vim25.VirtualMachineSnapshotTree;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mo.VirtualMachineSnapshot;

public class SnapshotManager {
	private static final Logger logger = Logger.getLogger(SnapshotManager.class.getName());

	private static boolean stopBackup = false;

	private static String showTaskErrorMessage(Task task) throws InvalidProperty, RuntimeFault, RemoteException {
		return task.getTaskInfo().getError().getLocalizedMessage();
	}

	/*
	 * 2. Refresh the backup cache update every 10 minute. default 600000
	 */
	public static void backupCache(int interval) {

		if (interval <= 0)
			interval = 600000;

		try {
			List<VirtualMachine> vms = VcenterManager.getVMs();
			List<HostSystem> hosts = VcenterManager.getVhosts();

			while (!stopBackup) {

				for (VirtualMachine vm : vms) {
					create(vm.getName(), "latest_snapshot", "");
				}

				for (HostSystem host : hosts) {
					createForVhost(host.getName());
				}

				Thread.sleep(interval);
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
	}

	public static void stopBackup() {
		stopBackup = true;
	}

	private static boolean create(String vmName, String snapshotName, String snapshotDescription) {

		try {
			VirtualMachine vm = VcenterManager.getVmByName(vmName);

			if (VcenterManager.checkVMHeartbeat(vm)) {

				Task task = vm.createSnapshot_Task(snapshotName, snapshotDescription, false, false);

				if (task.waitForTask() == Task.SUCCESS) {
					System.out.println(String.format("Snapshot was created. vmName: %s, snapshotName: %s, description: %s", vmName, snapshotName, snapshotDescription));
					return true;
				} else {
					System.out.println(String.format("Snapshot for VM %s failed to create!!! %s", vmName, showTaskErrorMessage(task)));
				}
			} else {
				System.out.println("Cannot ping " + vm.getName() + ". Snapshot skipped.");
			}

			return false;
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
		return false;
	}

	public static void createForVhost(String hostName) throws Exception {
		ServiceInstance superVCenter =
				new ServiceInstance(new URL(Credentials.ROOT_VCENTER_URL), Credentials.VCENTER_USER_NAME, Credentials.PASSWORD, true);
		VirtualMachine vm =
				(VirtualMachine) new InventoryNavigator(superVCenter.getRootFolder()).searchManagedEntity("VirtualMachine", Credentials.VHOST_NAME_MAP.get(hostName));

		if (VcenterManager.checkVHostHeartbeat(hostName)) {
			String snapshotname = "vHost-" + vm.getName() + "-SnapShot";

			Task task = vm.createSnapshot_Task(snapshotname, "", false, false);
			if (task.waitForTask() == Task.SUCCESS)
				System.out.println(snapshotname + " was created.");
			else
				System.out.println(snapshotname + " create failure.");
		} else {
			System.out.println("Cannot ping " + vm.getName() + ". Snapshot skipped.");
		}

		superVCenter.getServerConnection().logout();
	}

	public synchronized static boolean revertForVhost(HostSystem host) throws Exception {

		if (host == null)
			return false;

		boolean result = true;
		ServiceInstance superVCenter =
				new ServiceInstance(new URL(Credentials.ROOT_VCENTER_URL), Credentials.VCENTER_USER_NAME, Credentials.PASSWORD, true);
		VirtualMachine vm =
				(VirtualMachine) new InventoryNavigator(superVCenter.getRootFolder()).searchManagedEntity("VirtualMachine", Credentials.VHOST_NAME_MAP.get(host.getName()));

		if (host.getOverallStatus() != ManagedEntityStatus.green) {
			result = revert(vm);
			while(host.getOverallStatus() != ManagedEntityStatus.green) {
				VcenterManager.powerOn(vm, false);
				Thread.sleep(3000);
			}
		}

		superVCenter.getServerConnection().logout();

		return result;
	}

	private void list(String vmName) {
		try {
			VirtualMachine vm = VcenterManager.getVmByName(vmName);

			VirtualMachineSnapshotInfo snapInfo = vm.getSnapshot();
			VirtualMachineSnapshotTree[] snapTree = snapInfo.getRootSnapshotList();

			printSnapshots(snapTree);

		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
	}

	private void printSnapshots(VirtualMachineSnapshotTree[] snapTree) {
		if (snapTree == null)
			return;

		for (int i = 0; i < snapTree.length; i++) {
			VirtualMachineSnapshotTree node = snapTree[i];
			System.out.println("Snapshot Name : " + node.getName());
			VirtualMachineSnapshotTree[] childTree = node.getChildSnapshotList();
			if (childTree != null) {
				printSnapshots(childTree);
			}
		}
	}

	public static boolean revert(VirtualMachine vm) {
		try {
			
			//if (VcenterManager.isPoweredOn(vm)) {
			//	return true;
			//}
			
			Task task = vm.revertToCurrentSnapshot_Task(null);
			if (task.waitForTask() == Task.SUCCESS) {
				System.out.println(String.format("VM %s is reverted to current snapshot", vm.getName()));
				return true;
			} else {
				System.out.println(String.format("Snapshot for VM %s failed to revert!!! %s", vm.getName(), showTaskErrorMessage(task)));
			}

			return false;
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
		return false;
	}

	public static boolean revert(String vmName) {
		try {
			VirtualMachine vm = VcenterManager.getVmByName(vmName);

			Task task = vm.revertToCurrentSnapshot_Task(null);
			if (task.waitForTask() == Task.SUCCESS) {
				System.out.println(String.format("VM %s is reverted to current snapshot", vmName));
				return true;
			} else {
				System.out.println(String.format("Snapshot for VM %s failed to revert!!! %s", vmName, showTaskErrorMessage(task)));
			}

			return false;
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
		return false;
	}

	private VirtualMachineSnapshot getSnapshotInTree(VirtualMachine vm, String snapshotName) {
		if (vm == null || snapshotName == null) {
			return null;
		}

		VirtualMachineSnapshotTree[] snapTree = vm.getSnapshot().getRootSnapshotList();
		if (snapTree != null) {
			ManagedObjectReference mor = findSnapshotInTree(snapTree, snapshotName);
			if (mor != null) {
				return new VirtualMachineSnapshot(vm.getServerConnection(), mor);
			}
		}
		return null;
	}

	private ManagedObjectReference findSnapshotInTree(VirtualMachineSnapshotTree[] snapTree, String snapshotName) {
		for (int i = 0; i < snapTree.length; i++) {
			VirtualMachineSnapshotTree node = snapTree[i];
			if (snapshotName.equals(node.getName())) {
				return node.getSnapshot();
			} else {
				VirtualMachineSnapshotTree[] childTree = node.getChildSnapshotList();
				if (childTree != null) {
					ManagedObjectReference mor = findSnapshotInTree(childTree, snapshotName);
					if (mor != null) {
						return mor;
					}
				}
			}
		}
		return null;
	}

	private boolean remove(String vmName, String snapshotName, boolean removechild) {
		try {
			VirtualMachine vm = VcenterManager.getVmByName(vmName);

			VirtualMachineSnapshot vmsnap = getSnapshotInTree(vm, snapshotName);
			if (vmsnap != null) {
				Task task = vmsnap.removeSnapshot_Task(removechild);
				if (task.waitForTask() == Task.SUCCESS) {
					System.out.println(String.format("Removed snapshot: %s", snapshotName));
					return true;
				} else {
					System.out.println(String.format("Snapshot for VM %s failed to remove!!! %s", vmName, showTaskErrorMessage(task)));
				}
			}

			return false;
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
		return false;
	}

	private boolean removeAll(String vmName, String snapshotName, boolean removechild) {
		try {
			VirtualMachine vm = VcenterManager.getVmByName(vmName);

			Task task = vm.removeAllSnapshots_Task();
			if (task.waitForTask() == Task.SUCCESS) {
				System.out.println("Removed all snapshots");
				return true;
			} else {
				System.out.println(String.format("Snapshots for VM %s failed to remove!!! %s", vmName, showTaskErrorMessage(task)));
			}

			return false;
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
		return false;
	}
}
