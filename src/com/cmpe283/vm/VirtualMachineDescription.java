package com.cmpe283.vm;

import com.vmware.vim25.mo.VirtualMachine;

public class VirtualMachineDescription {
	private VirtualMachine vm;
	
	public VirtualMachineDescription(VirtualMachine vm) throws Exception {
		if(vm == null) throw new Exception("Virtual Machine is null.");
		this.vm = vm;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("----------------------------");
		sb.append(String.format("VM Name: %s", vm.getName()));
		sb.append(String.format("Guest OS: %s", vm.getSummary().getConfig().guestFullName));
		sb.append(String.format("VM Version: %s", vm.getConfig().version));
		sb.append(String.format("CPU: vCPU(s)", vm.getConfig().getHardware().numCPU));
		sb.append(String.format("Memory: %s MB", vm.getConfig().getHardware().memoryMB));
		sb.append(String.format("Memory Overhead: %s MB", (long) vm.getConfig().memoryAllocation.reservation / 1000000f));
		sb.append(String.format("VMware Tools:" + vm.getGuest().toolsRunningStatus));
		sb.append(String.format("IP Addresses: ", vm.getSummary().getGuest().getIpAddress()));
		sb.append(String.format("State: %s" + vm.getGuest().guestState));
		sb.append("----------------------------");
		
		return sb.toString();
	}
}
