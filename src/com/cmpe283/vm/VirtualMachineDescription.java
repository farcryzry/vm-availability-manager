package com.cmpe283.vm;

import com.vmware.vim25.mo.VirtualMachine;

public class VirtualMachineDescription {
	private VirtualMachine vm;

	public VirtualMachineDescription(VirtualMachine vm) throws Exception {
		if (vm == null)
			throw new Exception("Virtual Machine is null.");
		this.vm = vm;
	}

	@Override
	public String toString() {
		String separator = System.getProperty("line.separator");
		StringBuilder sb = new StringBuilder();
		sb.append("---------------------------------------------------------------------");
		sb.append(separator);
		sb.append(String.format("VM Name: %s", vm.getName()));
		sb.append(separator);
		sb.append(String.format("Guest OS: %s", vm.getSummary().getConfig().guestFullName));
		sb.append(separator);
		sb.append(String.format("VM Version: %s", vm.getConfig().version));
		sb.append(separator);
		sb.append(String.format("CPU: %d vCPU(s)", vm.getConfig().getHardware().numCPU));
		sb.append(separator);
		sb.append(String.format("Memory: %d MB", vm.getConfig().getHardware().memoryMB));
		sb.append(separator);
		sb.append(String.format("Memory Overhead: %2f MB", (long) vm.getConfig().memoryAllocation.reservation / 1000000f));
		sb.append(separator);
		sb.append(String.format("VMware Tools: %s", vm.getGuest().toolsRunningStatus));
		sb.append(separator);
		sb.append(String.format("IP Addresses: %s", vm.getSummary().getGuest().getIpAddress()));
		sb.append(separator);
		sb.append(String.format("State: %s", vm.getGuest().guestState));
		sb.append(separator);
		sb.append("---------------------------------------------------------------------");

		return sb.toString();
	}
}
