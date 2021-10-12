package org.edge.core.edge;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.edge.core.feature.EdgeLet;
import org.edge.core.feature.EdgeState;
import org.edge.core.iot.IoTDevice;
import org.edge.entity.ConnectionHeader;
import org.edge.entity.DevicesInfo;
import org.edge.protocol.CommunicationProtocol;
import org.edge.utils.LogUtil;


/**
 * this is edge data center extended from Datacenter
 * in this Datacenter, it has got its own edgeDatacenterCharacteristics
 * @author cody
 *
 */
public class EdgeDataCenter extends Datacenter {

	private EdgeDatacenterCharacteristics characteristics;

	public EdgeDatacenterCharacteristics getEdgeCharacteristics() {
		return this.characteristics;
	}

	public void setEdgeCharacteristics(EdgeDatacenterCharacteristics characteristics) {
		this.characteristics = characteristics;
	}

	public EdgeDataCenter(String name, EdgeDatacenterCharacteristics characteristics,
			VmAllocationPolicy vmAllocationPolicy, List<Storage> storageList, double schedulingInterval)
					throws Exception {
		super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval);
		this.characteristics = characteristics;
	}

	@Override
	public void processEvent(SimEvent ev) {
		// TODO Auto-generated method stub

		super.processEvent(ev);
	


	}
	@Override
	public void processOtherEvent(SimEvent ev) {
		// TODO Auto-generated method stub
		int tag = ev.getTag();
		switch (tag) {
		case EdgeState.REQUEST_CONNECTION:
			this.processConnectionRequest(ev);

			break;
		case EdgeState.LOST_CONNECTION:
			this.processConnectionLost(ev);
			break;

		default:
			break;
		}

	}

	private void processConnectionLost(SimEvent ev) {
		ConnectionHeader connectionInfo = (ConnectionHeader) ev.getData();
		List<Host> hostList = this.characteristics.getHostList();
		for (Host host : hostList) {
			List<Vm> vmList2 = host.getVmList();
			for (Vm vm : vmList2) {
				if (vm.getId() == connectionInfo.vmId) {
					EdgeDevice device=(EdgeDevice) host;
					device.removeConnection(connectionInfo);
					break;
				}
			}
		}

	}

	private double calculateDelayByV2RCommunications(SimEvent event, EdgeDevice device){
		if(device.getId()==100){
			return Integer.MAX_VALUE;
		}
		double queueExecutionDuration = device.getPendingResponse().stream().map(EdgeLet::getFinishTime).reduce(0.0, Double::sum);
		ConnectionHeader info = (ConnectionHeader) event.getData();
		IoTDevice ioTDevice = (IoTDevice) CloudSim.getEntity(info.ioTId);
		double complexity = ioTDevice.getComplexityOfDataPackage();
		double transmissionDelay = ioTDevice.getNetworkDelay();
		return queueExecutionDuration+transmissionDelay + complexity/this.getEdgeCharacteristics().getCpuTime(complexity,0);
	}

	private double calculateDelayByCloudComputingOverNetwork(SimEvent event,EdgeDevice device){
		if(device.getId()!=100){
			return Integer.MAX_VALUE;
		}
		double queueExecutionDuration = device.getPendingResponse().stream().map(EdgeLet::getFinishTime).reduce(0.0, Double::sum);
		ConnectionHeader info = (ConnectionHeader) event.getData();
		IoTDevice ioTDevice = (IoTDevice) CloudSim.getEntity(info.ioTId);
		double complexity = ioTDevice.getComplexityOfDataPackage();
		double transmissionDelay = ioTDevice.getNetworkDelay();
		return queueExecutionDuration+transmissionDelay + complexity/this.getEdgeCharacteristics().getCpuTime(complexity,0);
	}

	private double calculateDelayByLocalComputing(SimEvent event, EdgeDevice device){
		if(device.getId()==100){
			return Integer.MAX_VALUE;
		}
		ConnectionHeader info = (ConnectionHeader) event.getData();
		IoTDevice ioTDevice = (IoTDevice) CloudSim.getEntity(info.ioTId);
		double processingAbility = ioTDevice.getProcessingAbility();
		double queueExecutionDuration = ioTDevice.getTaskQueue().stream().reduce(0,Integer::sum);
		double overAllLatency = queueExecutionDuration + ioTDevice.getComplexityOfDataPackage()/processingAbility;
		System.out.println("cloud computing" + overAllLatency);
		return overAllLatency;
	}

	private boolean delaySatisfyConstraint(double constraint, SimEvent event, EdgeDevice device){
		return Stream.of(calculateDelayByV2RCommunications(event,device),calculateDelayByCloudComputingOverNetwork(event,device), calculateDelayByLocalComputing(event,device))
				.anyMatch(delay->delay<constraint);
	}

	private EdgeDevice getEdgeDevicesInConstraints(SimEvent event,List<EdgeDevice> devices){
		return devices.stream().filter(device -> delaySatisfyConstraint(100000,event,device)).findAny().orElse(null);
	}

	/**
	 * set up connection between edge devices in this data center and iot devices
	 * @param ev its data contains connection information between edge device and iot device
	 */
	private void processConnectionRequest(SimEvent ev) {
		//		LogUtil.info(CloudSim.clock());
		ConnectionHeader info = (ConnectionHeader) ev.getData();
		Class<? extends CommunicationProtocol>[] supported_comm_protocols_for_IoT = this.getEdgeCharacteristics()
				.getCommunicationProtocolSupported();
		Class<? extends IoTDevice>[] ioTDeviceSupported_for_IoT = this.getEdgeCharacteristics().getIoTDeviceSupported();
		boolean supportDevice = false;
		for (Class ioT : ioTDeviceSupported_for_IoT) {
			// if this edge device supports this ioTdevice
			if (ioT.equals(info.ioTDeviceType)) {
				supportDevice = true;
				// find the suitable protocol
				for (Class<? extends CommunicationProtocol> clzz : supported_comm_protocols_for_IoT) {
					//
					if (clzz.equals(info.communicationProtocolForIoT)
							/* || clzz.isAssignableFrom(device.getClass()) */) {
						List<EdgeDevice> hostList = this.characteristics.getHostList();

						final EdgeDevice availableDevice = getEdgeDevicesInConstraints(ev,hostList);
						if(availableDevice == null){
							continue;
						}
						List<Vm> vmList = availableDevice.getVmList();
						for (Vm vm : vmList) {
							if (vm.getId() == info.vmId) {
								boolean connect_IoT_device = availableDevice.connect_IoT_device(info);
								if (connect_IoT_device) {
									info.state = EdgeState.SUCCESS;
									// connection infor is from vm part
									info.sourceId = info.vmId;
									this.send(info.brokeId, this.getNeworkDelay(new DevicesInfo(info.ioTId, info.vmId)),
											EdgeState.CONNECTING_ACK, info);
								}
								return;
							}
						}
						return;
					}
				}

			}
		}
		if (supportDevice) {
			this.schedule(info.ioTId, this.getNeworkDelay(new DevicesInfo(info.ioTId, info.vmId)), EdgeState.DISCONNECTED,EdgeState.UNSUPPORTED_COMMUNICATION_PROTOCOL);
			LogUtil.info("EdgeDataCenter: the edgeDevice cannot support protocol "
					+ info.communicationProtocolForIoT.getSimpleName() + "for ioTdevice " + info.ioTId);
		} else {
			this.schedule(info.ioTId, this.getNeworkDelay(new DevicesInfo(info.ioTId, info.vmId)), EdgeState.DISCONNECTED,EdgeState.UNSUPPORTED_IOT_DEVICE);
			LogUtil.info("EdgeDataCenter: the edgeDevice cannot support IoT device "
					+ info.ioTDeviceType.getSimpleName());
		}

		info.state = EdgeState.FAILURE;
		this.send(info.brokeId, this.getNeworkDelay(new DevicesInfo(info.ioTId, info.vmId)), EdgeState.CONNECTING_ACK, info);
	}

	/**
	 * get network delay between edge device and iot device.
	 * for now, it simply return 0
	 * @param devicesInfo 
	 */
	private double getNeworkDelay(DevicesInfo devicesInfo) {
		return 0;
	}
}