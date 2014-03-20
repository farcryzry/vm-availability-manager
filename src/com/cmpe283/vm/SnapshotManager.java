package com.cmpe283.vm;

import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.VirtualMachineSnapshotInfo;
import com.vmware.vim25.VirtualMachineSnapshotTree;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mo.VirtualMachineSnapshot;

public class SnapshotManager {
	private static final Logger logger = Logger.getLogger(SnapshotManager.class.getName());

	private static VcenterManager VcenterManager;

	private static HashMap<String, String> SnapshotMap;

	private static boolean stopBackup = false;

	public SnapshotManager() {
		try {
			SnapshotMap = new HashMap<String, String>();
			VcenterManager = new VcenterManager();
			if (VcenterManager == null)
				throw new Exception("Vcenter Manager cannot be initialized");
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
	}

	/*
	 * 2. Refresh the backup cache update every 10 minute. default 600000
	 */
	public void backupCache(int interval) {

		if (interval <= 0)
			interval = 600000;

		SnapshotMap.clear();
		try {
			List<VirtualMachine> vms = VcenterManager.getVMs();
			for (VirtualMachine vm : vms) {
				SnapshotMap.put(vm.getName(), null);
			}

			while (!stopBackup) {
				for (String vmName : SnapshotMap.keySet()) {
					create(vmName, "latest_snapshot", "");
				}

				Thread.sleep(interval);
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
	}

	public void stopBackup() {
		stopBackup = true;
	}

	private boolean create(String vmName, String snapshotName, String snapshotDescription) {

		try {
			VirtualMachine vm = VcenterManager.getVmByName(vmName);

			Task task = vm.createSnapshot_Task(snapshotName, snapshotDescription, false, false);

			if (task.waitForTask() == Task.SUCCESS) {
				System.out.println(String.format("Snapshot was created. vmName: %s, snapshotName: %s, description: %s", vmName, snapshotName, snapshotDescription));
				SnapshotMap.put(vmName, snapshotName);
				return true;
			}

			return false;
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
		return false;
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

	private boolean revert(String vmName, String snapshotName) {
		try {
			VirtualMachine vm = VcenterManager.getVmByName(vmName);

			VirtualMachineSnapshot vmsnap = getSnapshotInTree(vm, snapshotName);

			if (vmsnap != null) {
				Task task = vmsnap.revertToSnapshot_Task(null);
				if (task.waitForTask() == Task.SUCCESS) {
					System.out.println(String.format("VM %s is reverted to snapshot: %s", vmName, snapshotName));
					return true;
				}
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
			}

			return false;
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
		return false;
	}
}
