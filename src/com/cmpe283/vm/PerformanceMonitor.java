package com.cmpe283.vm;

import java.util.HashMap;
import java.util.logging.Logger;

import com.vmware.vim25.PerfCounterInfo;
import com.vmware.vim25.PerfEntityMetricBase;
import com.vmware.vim25.PerfEntityMetricCSV;
import com.vmware.vim25.PerfMetricId;
import com.vmware.vim25.PerfMetricSeriesCSV;
import com.vmware.vim25.PerfProviderSummary;
import com.vmware.vim25.PerfQuerySpec;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.PerformanceManager;
import com.vmware.vim25.mo.VirtualMachine;

public class PerformanceMonitor {

	private static final Logger logger = Logger.getLogger(SnapshotManager.class.getName());

	private PerformanceManager perfMgr;
	private HashMap<Integer, PerfCounterInfo> countersInfoMap;
	private HashMap<String, Integer> countersMap;
	private PerfMetricId[] pmis;
	private String[] counters;

	public PerformanceMonitor(PerformanceManager perfMgr) {
		try {
			if (perfMgr == null)
				throw new Exception("Performance Monitor cannot be initialized");
			this.perfMgr = perfMgr;
			setUp();
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
	}

	private void setUp() throws Exception {
		PerfCounterInfo[] pcis = perfMgr.getPerfCounter();

		// create map between counter ID and PerfCounterInfo, counter name and
		// ID
		countersInfoMap = new HashMap<Integer, PerfCounterInfo>();
		countersMap = new HashMap<String, Integer>();
		for (int i = 0; i < pcis.length; i++) {
			countersInfoMap.put(pcis[i].getKey(), pcis[i]);
			countersMap.put(pcis[i].getGroupInfo().getKey() + "." + pcis[i].getNameInfo().getKey()
					+ "." + pcis[i].getRollupType(), pcis[i].getKey());
		}

		counters =
				new String[] { "cpu.usage.average", "cpu.usagemhz.average", "cpu.used.summation",
						"cpu.wait.summation", "mem.usage.average", "mem.overhead.average",
						"mem.consumed.average", "net.usage.average", "net.received.average",
						"net.transmitted.average", "disk.commands.summation", "disk.usage.average",
						"datastore.datastoreReadBytes.latest", "virtualDisk.readOIO.latest",
						"virtualDisk.writeOIO.latest" };

		pmis = createPerfMetricId(counters);
	}

	public void printPerf(ManagedEntity me) throws Exception {
		PerfProviderSummary pps = perfMgr.queryPerfProviderSummary(me);
		int refreshRate = pps.getRefreshRate().intValue();

		// only return the latest one sample
		PerfQuerySpec qSpec = createPerfQuerySpec(me, 1, refreshRate);

		PerfEntityMetricBase[] pValues = perfMgr.queryPerf(new PerfQuerySpec[] { qSpec });

		System.out.println("---------------------------------------------------------------------");
		System.out.println(String.format("Statistics for VM: %s", me.getName()));

		try {
			VirtualMachineDescription wmDesc = new VirtualMachineDescription((VirtualMachine) me);
			System.out.println(wmDesc);
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (pValues != null) {
			displayValues(pValues);
		}
	}

	public void printStatisticsForVm(VirtualMachine vm) {
		try {
			printPerf(vm);
		} catch (Exception e) {
			e.printStackTrace();
			logger.warning(e.getMessage());
		}
	}

	private PerfMetricId[] createPerfMetricId(String[] counters) {
		PerfMetricId[] metricIds = new PerfMetricId[counters.length];
		for (int i = 0; i < counters.length; i++) {
			PerfMetricId metricId = new PerfMetricId();
			metricId.setCounterId(countersMap.get(counters[i]));
			metricId.setInstance("*");
			metricIds[i] = metricId;
		}
		return metricIds;
	}

	private PerfQuerySpec createPerfQuerySpec(ManagedEntity me, int maxSample, int interval) {

		PerfQuerySpec qSpec = new PerfQuerySpec();
		qSpec.setEntity(me.getMOR());
		// set the maximum of metrics to be return
		qSpec.setMaxSample(new Integer(maxSample));
		qSpec.setMetricId(pmis);
		qSpec.setFormat("csv");
		qSpec.setIntervalId(new Integer(interval));

		return qSpec;
	}

	private void displayValues(PerfEntityMetricBase[] values) {
		for (int i = 0; i < values.length; ++i) {
			printPerfMetricCSV((PerfEntityMetricCSV) values[i]);
		}
	}

	private void printPerfMetricCSV(PerfEntityMetricCSV pem) {

		PerfMetricSeriesCSV[] csvs = pem.getValue();

		HashMap<Integer, PerfMetricSeriesCSV> stats = new HashMap<Integer, PerfMetricSeriesCSV>();

		for (int i = 0; i < csvs.length; i++) {
			stats.put(csvs[i].getId().getCounterId(), csvs[i]);
		}

		System.out.println("Counter                                 Value(Unit)");
		System.out.println("---------------------------------------------------------------------");
		for (String counter : counters) {
			Integer counterId = countersMap.get(counter);
			PerfCounterInfo pci = countersInfoMap.get(counterId);
			String value = null;
			if (stats.containsKey(counterId))
				value = stats.get(counterId).getValue();

			String counterName =
					String.format("%s.%s.%s", pci.getGroupInfo().getKey(), pci.getNameInfo().getKey(), pci.getRollupType());
			String unit = pci.getUnitInfo().getKey();
			value = String.format("%s(%s)", value, unit);

			System.out.println(String.format("%-40s%s", counterName, value));
		}
		System.out.println("---------------------------------------------------------------------");
	}
}
