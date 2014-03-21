package com.cmpe283.vm;

import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.vmware.vim25.Action;
import com.vmware.vim25.AlarmAction;
import com.vmware.vim25.AlarmSetting;
import com.vmware.vim25.AlarmSpec;
import com.vmware.vim25.AlarmTriggeringAction;
import com.vmware.vim25.ComputeResourceConfigSpec;
import com.vmware.vim25.HostConnectSpec;
import com.vmware.vim25.HostVMotionCompatibility;
import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.ManagedEntityStatus;
import com.vmware.vim25.MethodAction;
import com.vmware.vim25.MethodActionArgument;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.StateAlarmExpression;
import com.vmware.vim25.StateAlarmOperator;
import com.vmware.vim25.VirtualMachineMovePriority;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.mo.Alarm;
import com.vmware.vim25.mo.AlarmManager;
import com.vmware.vim25.mo.ComputeResource;
import com.vmware.vim25.mo.Datacenter;
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
					new ServiceInstance(new URL(Credentials.VCENTER_URL), Credentials.VCENTER_USER_NAME, Credentials.PASSWORD, true);
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

	/*
	 * 1. Gather statistics (such as CPU, I/O, network etc) for a VM and display
	 * in a text format
	 */
	public void showStatistics() {
		PerformanceMonitor performanceMonitor =
				new PerformanceMonitor(ServiceInstance.getPerformanceManager());
		try {
			List<VirtualMachine> vms = getVMs();
			for (VirtualMachine vm : vms) {
				performanceMonitor.printStatisticsForVm(vm);
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
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
			} else {
				System.out.println(String.format("VM %s failed to power on!!! %s", vm.getName(), showTaskErrorMessage(task)));
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
				if (isVhostAvailable)
					break;

				Thread.sleep(2000);
			}

			if (!isVhostAvailable) {
				// todo: remove vhost
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
					System.out.println(String.format("VM %s failed to do %s!!! %s", vm.getName(), isColdMigration
							? "Cold Migration" : "VMotion", showTaskErrorMessage(task)));
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
						System.out.println(String.format("VM %s: down!!!", vm.getName()));
						// migrate(vm, "1.1.1.1", true);

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

	/*
	 * 6. Setup alarm on VM power off. If a VM is powered off by a user, then it
	 * should be able to prevent a failover from occurring. (A VM is not failed
	 * in this case by powered off by a user)
	 */

	public void setPowerOffAlarm() {
		AlarmManager alarmMgr = ServiceInstance.getAlarmManager();

		try {
			for (VirtualMachine vm : getVMs()) {

				AlarmSpec spec = buildAlarmSpec("VmPowerOffAlarm." + vm.getName());

				Alarm[] alarms = alarmMgr.getAlarm(vm);

				for (Alarm alarm : alarms) {
					if (alarm.getAlarmInfo().getName().equals(spec.getName())) {
						alarm.removeAlarm();
					}
				}

				alarmMgr.createAlarm(vm, spec);

			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}

	}

	private AlarmSpec buildAlarmSpec(String alarmName) {
		AlarmSpec spec = new AlarmSpec();

		StateAlarmExpression expression = createStateAlarmExpression();

		AlarmAction methodAction = createAlarmTriggerAction(createPowerOffAction());

		// spec.setAction(methodAction);
		spec.setExpression(expression);
		spec.setName(alarmName);
		spec.setDescription("Monitor VM state and trigger some alarm actions");
		spec.setEnabled(true);

		AlarmSetting as = new AlarmSetting();
		as.setReportingFrequency(0); // as often as possible
		as.setToleranceRange(0);

		spec.setSetting(as);

		return spec;
	}

	private AlarmTriggeringAction createAlarmTriggerAction(Action action) {
		AlarmTriggeringAction alarmAction = new AlarmTriggeringAction();
		alarmAction.setYellow2red(true);
		alarmAction.setAction(action);
		return alarmAction;
	}

	private StateAlarmExpression createStateAlarmExpression() {
		StateAlarmExpression expression = new StateAlarmExpression();
		expression.setType("VirtualMachine");
		expression.setStatePath("runtime.powerState");
		expression.setOperator(StateAlarmOperator.isEqual);
		expression.setRed("poweredOff");
		return expression;
	}

	private MethodAction createPowerOffAction() {
		MethodAction action = new MethodAction();
		action.setName("PowerOffVM_Task");
		MethodActionArgument argument = new MethodActionArgument();
		argument.setValue(null);
		action.setArgument(new MethodActionArgument[] { argument });
		return action;
	}

	public boolean addHost(String dcName, String hostName) {

		HostConnectSpec newHost = new HostConnectSpec();
		newHost.setHostName(hostName);
		newHost.setUserName(Credentials.VHOST_USER_NAME);
		newHost.setPassword(Credentials.PASSWORD);
		String sslThumbprint = Credentials.VHOST_SSL_MAP.get(hostName);
		if (sslThumbprint != null) {
			newHost.setSslThumbprint(sslThumbprint);
		} else {
			return false;
		}

		try {
			Datacenter dc = getDatacenterByName(dcName);
			Task addHostTask =
					dc.getHostFolder().addStandaloneHost_Task(newHost, new ComputeResourceConfigSpec(), true);

			if (addHostTask.waitForTask() == Task.SUCCESS) {

				System.out.println(String.format("Host %s is added to Datacenter %s successfully", hostName, dcName));
				return true;
			} else {
				System.out.println(String.format("vHost %s failed to add!!! %s", hostName, showTaskErrorMessage(addHostTask)));
			}

		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}

		return false;
	}

	private String showTaskErrorMessage(Task task) throws InvalidProperty, RuntimeFault, RemoteException {
		return task.getTaskInfo().getError().getLocalizedMessage();
	}

	public boolean removeHost(String hostName) {

		try {
			HostSystem host = getVhostByName(hostName);

			Task disconTask = host.disconnectHost();
			System.out.println(String.format("disconnecting vHost %s ........", hostName));

			if (disconTask.waitForTask() == Task.SUCCESS) {

				System.out.println("vHost disconnedted.");

				ComputeResource cr = (ComputeResource) host.getParent();
				Task removeTask = cr.destroy_Task();

				System.out.println(String.format("removing vHost %s ........", hostName));
				if (removeTask.waitForTask() == Task.SUCCESS) {
					System.out.println("vHost removed.");
					return true;
				} else {
					System.out.println(String.format("vHost %s failed to remove!!! %s", hostName, showTaskErrorMessage(removeTask)));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}

		return false;
	}

	private Datacenter getDatacenterByName(String dcName) throws Exception {
		Folder rootFolder = ServiceInstance.getRootFolder();
		Datacenter dc =
				(Datacenter) new InventoryNavigator(rootFolder).searchManagedEntity("Datacenter", dcName);

		if (dc == null)
			throw new Exception("datacenter is null");

		return dc;
	}

	private List<Datacenter> getDatacenters() throws InvalidProperty, RuntimeFault, RemoteException {
		List<Datacenter> dcs = new ArrayList<Datacenter>();

		Folder rootFolder = ServiceInstance.getRootFolder();
		ManagedEntity[] entities =
				new InventoryNavigator(rootFolder).searchManagedEntities("Datacenter");

		if (entities != null) {
			for (ManagedEntity entity : entities) {
				dcs.add((Datacenter) entity);
			}
		} else {
			logger.warning("Found No Datacenters!");
		}

		return dcs;
	}
}
