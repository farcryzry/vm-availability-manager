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

	static {
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

	public static void logout() {
		ServiceInstance.getServerConnection().logout();
	}

	public static VirtualMachine getVmByName(String vmName) throws InvalidProperty, RuntimeFault, RemoteException, Exception {
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
	public static void showStatistics() {
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

	public static HostSystem getVhostByName(String hostName) throws Exception {
		Folder vmFolder = ServiceInstance.getRootFolder();
		HostSystem host =
				(HostSystem) new InventoryNavigator(vmFolder).searchManagedEntity("HostSystem", hostName);

		return host;
	}

	public static List<HostSystem> getVhosts() throws InvalidProperty, RuntimeFault, RemoteException {
		List<HostSystem> hosts = new ArrayList<HostSystem>();

		Folder vmFolder = ServiceInstance.getRootFolder();
		ManagedEntity[] entities =
				new InventoryNavigator(vmFolder).searchManagedEntities("HostSystem");
		if (entities != null) {
			for (ManagedEntity entity : entities) {
				hosts.add((HostSystem) entity);
			}
		} else {
			logger.warning("Found No Vhost!");
		}

		return hosts;
	}

	public static HostSystem getVhostByVM(VirtualMachine vm) {

		try {
			List<HostSystem> hosts = getVhosts();

			for (HostSystem host : hosts) {
				if (host.getMOR().val.equals(vm.getSummary().runtime.host.val)) {
					return host;
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}

		return null;
	}

	public static List<VirtualMachine> getVMs() throws InvalidProperty, RuntimeFault, RemoteException {
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

	public static boolean powerOn(String vmName) {
		try {
			Folder vmFolder = ServiceInstance.getRootFolder();
			VirtualMachine vm =
					(VirtualMachine) new InventoryNavigator(vmFolder).searchManagedEntity("VirtualMachine", vmName);
			return powerOn(vm, false);
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
		return false;
	}

	public static boolean powerOn(VirtualMachine vm, boolean waitToFinish) {

		try {

			if (isPoweredOn(vm)) {
				return true;
			}

			Task task = vm.powerOnVM_Task(null);
			System.out.println(String.format("VM %s now is powerring on! Please wait...", vm.getName()));

			if (task.waitForTask() == Task.SUCCESS) {
				while (waitToFinish) {
					if (VcenterManager.checkVMHeartbeat(vm))
						break;
					Thread.sleep(3000);
				}
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

	public static boolean powerOff(VirtualMachine vm) {

		try {

			if (!isPoweredOn(vm))
				return true;

			Task task = vm.powerOffVM_Task();
			System.out.println(String.format("VM %s now is powerring off! Please wait...", vm.getName()));

			if (task.waitForTask() == Task.SUCCESS) {
				System.out.println(String.format("VM %s  is powered off successfully!", vm.getName()));
				return true;
			} else {
				System.out.println(String.format("VM %s failed to power off!!! %s", vm.getName(), showTaskErrorMessage(task)));
			}

			return false;

		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
		return false;
	}

	public static boolean isPoweredOn(VirtualMachine vm) {
		if (vm == null)
			return false;

		VirtualMachinePowerState vmps = vm.getRuntime().getPowerState();
		return vmps == VirtualMachinePowerState.poweredOn;
	}

	public static boolean isVhostPoweredOn(HostSystem host) {

		try {
			ServiceInstance superVCenter =
					new ServiceInstance(new URL(Credentials.ROOT_VCENTER_URL), Credentials.VCENTER_USER_NAME, Credentials.PASSWORD, true);
			VirtualMachine vm =
					(VirtualMachine) new InventoryNavigator(superVCenter.getRootFolder()).searchManagedEntity("VirtualMachine", Credentials.VHOST_NAME_MAP.get(host.getName()));

			return isPoweredOn(vm);

		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}

		return false;
	}

	/*
	 * 4. If the vHost is not alive, try to make it alive. If even after a fixed
	 * number of attempts, the vHost does not come up, remove the vHost from the
	 * list.
	 */
	public static boolean migrate(VirtualMachine vm, String newHostName, boolean isColdMigration) {
		try {

			HostSystem newHost = getVhostByName(newHostName);

			if (newHost == null) {
				addHost("Team03_DC", newHostName);
				newHost = getVhostByName(newHostName);
			}

			boolean isVhostAvailable = false;

			for (int i = 0; i < 10; i++) {

				isVhostAvailable = checkVhostStatus(newHost);
				if (isVhostAvailable)
					break;

				Thread.sleep(2000);
			}

			if (!isVhostAvailable) {
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
					return true;
				} else {
					System.out.println(String.format("VM %s failed to do %s!!! %s", vm.getName(), isColdMigration
							? "Cold Migration" : "VMotion", showTaskErrorMessage(task)));
					return false;
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
		return false;
	}

	public static boolean checkVhostStatus(HostSystem host) {
		if (host == null)
			return false;

		ManagedEntityStatus hostStatus = host.getOverallStatus();

		System.out.println(String.format("VHost %s: Overall Status: %s", host.getName(), hostStatus));

		return hostStatus != ManagedEntityStatus.red;
	}

	private static boolean checkVmotionCompatibility(VirtualMachine vm, HostSystem host) throws RuntimeFault, RemoteException {
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
	public static void failover(int interval, String backupVhostName) {

		try {
			List<VirtualMachine> vms = getVMs();

			for (VirtualMachine vm : vms) {
				Thread t = new Thread(new HeartbeatTask(vm, backupVhostName, interval));
				t.start();
			}

		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
	}

	/*
	 * 6. Setup alarm on VM power off. If a VM is powered off by a user, then it
	 * should be able to prevent a failover from occurring. (A VM is not failed
	 * in this case by powered off by a user)
	 */

	public static void setPowerOffAlarm() {
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

	private static AlarmSpec buildAlarmSpec(String alarmName) {
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

	private static AlarmTriggeringAction createAlarmTriggerAction(Action action) {
		AlarmTriggeringAction alarmAction = new AlarmTriggeringAction();
		alarmAction.setYellow2red(true);
		alarmAction.setAction(action);
		return alarmAction;
	}

	private static StateAlarmExpression createStateAlarmExpression() {
		StateAlarmExpression expression = new StateAlarmExpression();
		expression.setType("VirtualMachine");
		expression.setStatePath("runtime.powerState");
		expression.setOperator(StateAlarmOperator.isEqual);
		expression.setRed("poweredOff");
		return expression;
	}

	private static MethodAction createPowerOffAction() {
		MethodAction action = new MethodAction();
		action.setName("PowerOffVM_Task");
		MethodActionArgument argument = new MethodActionArgument();
		argument.setValue(null);
		action.setArgument(new MethodActionArgument[] { argument });

		return action;
	}

	public static boolean addHost(String dcName, String hostName) {

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

	private static String showTaskErrorMessage(Task task) throws InvalidProperty, RuntimeFault, RemoteException {
		return task.getTaskInfo().getError().getLocalizedMessage();
	}

	public static boolean removeHost(String hostName) {

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

	private static Datacenter getDatacenterByName(String dcName) throws Exception {
		Folder rootFolder = ServiceInstance.getRootFolder();
		Datacenter dc =
				(Datacenter) new InventoryNavigator(rootFolder).searchManagedEntity("Datacenter", dcName);

		if (dc == null)
			throw new Exception("datacenter is null");

		return dc;
	}

	private static List<Datacenter> getDatacenters() throws InvalidProperty, RuntimeFault, RemoteException {
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

	public static boolean checkVMHeartbeat(VirtualMachine vm) throws Exception {
		boolean result = true;

		/*
		 * ManagedEntityStatus heartbeatStatus = vm.getGuestHeartbeatStatus();
		 * switch (heartbeatStatus) { case red:
		 * System.out.println(String.format("VM %s: down!!!", vm.getName()));
		 * result = false; break; case gray: System.out.println(String.format(
		 * "VM %s: VMWare Tools is not installed or not running.",
		 * vm.getName())); break; case yellow: System.out.println(String.format(
		 * "VM %s: Intermittent heartbeat, possible due to heavy load at the gust OS."
		 * , vm.getName())); break; default:
		 * System.out.println(String.format("VM %s: heartbeat status OK.",
		 * vm.getName())); break; }
		 */

		String ip = vm.getGuest().getIpAddress();
		int totalCount = 3;
		int successCount = 0;
		for (int i = 0; i < totalCount; i++) {
			result = ping(ip);
			if (result)
				successCount++;
			Thread.sleep(500);
		}

		System.out.println(String.format("Ping %s %s!", vm.getName(), result ? "ok"
				: String.format("failed(%s/%s)", successCount, totalCount)));

		return result;
	}

	public static boolean checkVHostHeartbeat(String hostName) throws Exception {

		HostSystem host = getVhostByName(hostName);

		return checkVHostHeartbeat(host);
	}

	public static boolean checkVHostHeartbeat(HostSystem host) throws Exception {
		boolean result = true;

		String ip = host.getConfig().getNetwork().getVnic()[0].getSpec().getIp().getIpAddress();
		int totalCount = 3;
		int successCount = 0;
		for (int i = 0; i < totalCount; i++) {
			result = ping(ip);
			if (result)
				successCount++;
			Thread.sleep(500);
		}

		System.out.println(String.format("Ping %s %s!", host.getName(), result ? "ok"
				: String.format("failed(%s/%s)", successCount, totalCount)));

		return result;
	}

	public static boolean ping(String ip) {
		try {
			String cmd = "";
			if (System.getProperty("os.name").startsWith("Windows")) {
				cmd = "ping -n 1 " + ip;
			} else {
				cmd = "ping -c 1 " + ip;
			}

			Process process = Runtime.getRuntime().exec(cmd);
			process.waitFor();

			System.out.println(String.format("--Ping %s %s!", ip, process.exitValue() == 0 ? "ok"
					: "failed"));

			return process.exitValue() == 0;

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
}
