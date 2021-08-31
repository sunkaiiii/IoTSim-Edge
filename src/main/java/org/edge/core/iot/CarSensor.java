package org.edge.core.iot;

import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.SimEvent;
import org.edge.core.feature.Battery;
import org.edge.core.feature.EdgeLet;
import org.edge.core.feature.IoTType;
import org.edge.core.feature.policy.NetworkDelayCalculationPolicy;
import org.edge.core.feature.policy.SimpleNetworkDelayCalculator;
import org.edge.exception.NullConnectionException;
import org.edge.network.NetworkModel;
import org.edge.network.NetworkType;

public class CarSensor extends IoTDevice {

	static EdgeLet edgeLet;
	static {
		edgeLet = new EdgeLet(EdgeLet.id++,1000, 1, 30, 10, new UtilizationModelFull(), new UtilizationModelFull(),
				new UtilizationModelFull());
	}
	public static final double DATA_GENERATION_TIME = 1;

	public static final double DATA_FREQUENCY = 1;
	public static final int COMPLEXITY_OF_DATAPACKAGE = 1;

	public static final int DATA_SIZE = 2;
	public static final int MAX_BATTERY_CAPACITY = 100;
	public static final double BATTERY_DRAINAGE_RATE = 0.5d;

	public static final int PROCESSING_ABILITY = 1;

	public CarSensor(NetworkModel networkModel, long capacityToStore, double transfer_frequency) {
		super(IoTType.CAR_SENSOR, "carSensor", DATA_FREQUENCY, DATA_GENERATION_TIME,
				COMPLEXITY_OF_DATAPACKAGE, DATA_SIZE, networkModel, MAX_BATTERY_CAPACITY, BATTERY_DRAINAGE_RATE,
				PROCESSING_ABILITY, capacityToStore, transfer_frequency, edgeLet);
	}

	public CarSensor(NetworkModel networkModel) {
		super(IoTType.CAR_SENSOR, "carSensor", DATA_FREQUENCY, DATA_GENERATION_TIME,
				COMPLEXITY_OF_DATAPACKAGE, DATA_SIZE, networkModel, MAX_BATTERY_CAPACITY, BATTERY_DRAINAGE_RATE,
				PROCESSING_ABILITY, edgeLet);
	}


	@Override
	public boolean updateBatteryBySensing(Battery battery) {
		battery.setCurrentCapacity(battery.getCurrentCapacity()-this.battery_drainage_rate);
		if(battery.getCurrentCapacity()<0)
			return  true;
		return false;



	}



	@Override
	public boolean updateBatteryByProcessing(Battery battery) {
		battery.setCurrentCapacity(battery.getCurrentCapacity()-this.getProcessingAbility());
		if(battery.getCurrentCapacity()<0)
			return  true;
		return false;
	}

	@Override
	public boolean updateBatteryByTransmission(Battery battery) {
		NetworkModel networkModel = this.getNetworkModel();

		float batteryConsumptionSpeed = networkModel.getCommunicationProtocol().getBatteryDrainageRate();
		battery.setCurrentCapacity(battery.getCurrentCapacity()-batteryConsumptionSpeed);
		if(battery.getCurrentCapacity()<0)
			return  true;
		return false;
	}

	@Override
	public double getTransmissionSpeed()   {


		//if the communication speed is slower than the NetworkType speed,
		//then use communication speed. otherwise, use NetworkType speed.
		//
		float transmissionSpeed = this.getNetworkModel().getCommunicationProtocol().getTransmissionSpeed();
		NetworkType netWorkType = this.getNetworkModel().getNetWorkType();
		double speedRate = netWorkType.getSpeedRate();

		int connectedEdgeDevice =this.getAttachedEdgeDeviceId();
		if(connectedEdgeDevice==-1)
			throw new NullConnectionException("there is no connection with loT  device "+ this.getId());
		if(transmissionSpeed<=speedRate)
			return transmissionSpeed;

		return speedRate;
	}







	@Override
	public void startEntity() {
		super.startEntity();
	}



	@Override
	public void processEvent(SimEvent ev) {
		super.processEvent(ev);
	}



	@Override
	public void shutdownEntity() {

	}




	@Override
	public double getNetworkDelay() {
		int attachedEdgeDeviceId2 = this.getAttachedEdgeDeviceId();
		if(attachedEdgeDeviceId2==-1)
			return 0;

		NetworkDelayCalculationPolicy networkDelayCalculationPolicy = this.getNetworkDelayCalculationPolicy();
		if(networkDelayCalculationPolicy==null) {
			networkDelayCalculationPolicy=new SimpleNetworkDelayCalculator();
			this.setNetworkDelayCalculationPolicy(networkDelayCalculationPolicy);
		}

		double networkDelay = networkDelayCalculationPolicy.getNetworkDelay(this.getNetworkModel(), edgeLet, this.getMobility(), null);

		return networkDelay;
	}



	@Override
	public EdgeLet processData(EdgeLet generated_data) {
		double time = generated_data.getSubmissionTime();
		double finishTime = generated_data.getFinishTime();
		double duration = finishTime- time;
		while(duration > 0 && !getTaskQueue().isEmpty()){
			double handleTime = getTaskQueue().pop()/getProcessingAbility();
			duration -= handleTime;
		}
		return generated_data;
	}

}
