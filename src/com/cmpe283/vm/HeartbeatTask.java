package com.cmpe283.vm;

import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.VirtualMachine;

public class HeartbeatTask implements Runnable {

	private VirtualMachine vm;
	private String backupVhostName;
	private int interval;

	public HeartbeatTask(VirtualMachine vm, String backupVhostName, int interval) {
		this.vm = vm;
		this.backupVhostName = backupVhostName;
		this.interval = interval;

		if (this.interval <= 0)
			this.interval = 5000;
	}

	@Override
	public void run() {

		while (vm != null) {
			try {
				if (!VcenterManager.checkVMHeartbeat(vm)) {
					if(vm.getGuest().guestState.equals("notRunning") && !VcenterManager.isPoweredOn(vm)) {
						VcenterManager.powerOn(vm, false);
					}

					if (!vm.getGuest().guestState.equals("poweredOff")
							&& !vm.getGuest().guestState.equals("notRunning")) {
						System.out.println(String.format("VM %s is %s", vm.getName(), vm.getGuest().guestState));

						HostSystem host = VcenterManager.getVhostByVM(vm);

						if (host == null) {
							System.out.println(String.format("VM %s cannot find it's vHost", vm.getName()));
							return;
						}

						synchronized (HeartbeatTask.class) {
							if (VcenterManager.checkVhostStatus(host)) {

								////if(SnapshotManager.revert(vm)) {
								//	VcenterManager.powerOn(vm.getName());
								//} //else {
								//	System.out.println(String.format("VM %s failed to revert. Need to investigate!", vm.getName(), host.getName()));

									//VcenterManager.powerOff(vm);
									//VcenterManager.migrate(vm, backupVhostName, false);
									//VcenterManager.powerOn(vm);
								//}

							} else {
								System.out.println(String.format("VM %s's vHost %s cannot be detected. Try to revert it's snapshot...", vm.getName(), host.getName()));

								if (SnapshotManager.revertForVhost(host)) {
									VcenterManager.powerOn(vm, false);
								} else {
									System.out.println(String.format("VM %s's vHost %s snapshot Need to investigate!", vm.getName(), host.getName()));

									//VcenterManager.powerOff(vm);
									//VcenterManager.migrate(vm, backupVhostName, false);
									//VcenterManager.powerOn(vm);
								}
							}
						}

					}
				}

				Thread.sleep(interval);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
