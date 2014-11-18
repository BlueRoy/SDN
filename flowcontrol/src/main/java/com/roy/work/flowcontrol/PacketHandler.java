package com.roy.work.flowcontrol;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

import org.opendaylight.controller.sal.action.Action;
import org.opendaylight.controller.sal.action.Output;
import org.opendaylight.controller.sal.action.SetDlDst;
import org.opendaylight.controller.sal.action.SetDlSrc;
import org.opendaylight.controller.sal.action.SetNwDst;
import org.opendaylight.controller.sal.action.SetNwSrc;
import org.opendaylight.controller.sal.core.ConstructionException;
import org.opendaylight.controller.sal.core.Edge;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.core.Path;
import org.opendaylight.controller.sal.core.TimeStamp;
import org.opendaylight.controller.sal.flowprogrammer.Flow;
import org.opendaylight.controller.sal.flowprogrammer.IFlowProgrammerService;
import org.opendaylight.controller.sal.match.Match;
import org.opendaylight.controller.sal.match.MatchType;
import org.opendaylight.controller.sal.packet.Ethernet;
import org.opendaylight.controller.sal.packet.IDataPacketService;
import org.opendaylight.controller.sal.packet.IListenDataPacket;
import org.opendaylight.controller.sal.packet.IPv4;
import org.opendaylight.controller.sal.packet.Packet;
import org.opendaylight.controller.sal.packet.PacketResult;
import org.opendaylight.controller.sal.packet.RawPacket;
import org.opendaylight.controller.sal.packet.TCP;
import org.opendaylight.controller.sal.routing.IRouting;
import org.opendaylight.controller.sal.utils.EtherTypes;
import org.opendaylight.controller.sal.utils.Status;
import org.opendaylight.controller.hosttracker.IfIptoHost;
import org.opendaylight.controller.hosttracker.hostAware.HostNodeConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacketHandler implements IListenDataPacket {

	private static final Logger log = LoggerFactory
			.getLogger(PacketHandler.class);

	private IDataPacketService dataPacketService;
	private IFlowProgrammerService flowProgrammerService;
	//private ISwitchManager switchManager;
	private IfIptoHost iptohost;
	private IRouting routing;

	static private InetAddress intToInetAddress(int i) {
		byte b[] = new byte[] { (byte) ((i >> 24) & 0xff),
				(byte) ((i >> 16) & 0xff), (byte) ((i >> 8) & 0xff),
				(byte) (i & 0xff) };
		InetAddress addr;
		try {
			addr = InetAddress.getByAddress(b);
		} catch (UnknownHostException e) {
			return null;
		}

		return addr;
	}

	/**
	 * Sets a reference to the requested DataPacketService
	 */
	void setDataPacketService(IDataPacketService s) {
		log.trace("Set DataPacketService.");

		dataPacketService = s;
	}

	/**
	 * Unsets DataPacketService
	 */
	void unsetDataPacketService(IDataPacketService s) {
		log.trace("Removed DataPacketService.");

		if (dataPacketService == s) {
			dataPacketService = null;
		}
	}

	/**
	 * Sets a reference to the requested FlowProgrammerService
	 */
	void setFlowProgrammerService(IFlowProgrammerService s) {
		log.trace("Set FlowProgrammerService.");

		flowProgrammerService = s;
	}

	/**
	 * Unsets FlowProgrammerService
	 */
	void unsetFlowProgrammerService(IFlowProgrammerService s) {
		log.trace("Removed FlowProgrammerService.");

		if (flowProgrammerService == s) {
			flowProgrammerService = null;
		}
	}


	/**
	 * Sets a reference to the requested HostTrackerService
	 */
	void setHostTrackerService(IfIptoHost s) {
		log.trace("Set HostTrackerService.");

		iptohost = s;
	}

	/**
	 * Unsets HosttrackerService
	 */
	void unsetHostTrackerService(IfIptoHost s) {
		log.trace("Removed HostTrackerService.");

		if (iptohost == s) {
			iptohost = null;
		}
	}
	
	/**
	 * Sets a reference to the requested RoutingService
	 */
	void setRoutingService(IRouting s) {
		log.trace("Set RoutingService.");

		routing = s;
	}

	/**
	 * Unsets RoutingService
	 */
	void unsetRoutingService(IRouting s) {
		log.trace("Removed RoutingService.");

		if (routing == s) {
			routing = null;
		}
	}
	

	@Override
	public PacketResult receiveDataPacket(RawPacket inPkt){
		TimeStamp timestamp = inPkt.getIncomingTime();
		log.trace(timestamp.getStringValue());
		// The connector, the packet came from ("port")
		NodeConnector ingressConnector = inPkt.getIncomingNodeConnector();
		// The node that received the packet ("switch")
		Node node = ingressConnector.getNode();

		//log.trace("Packet from " + node.getNodeIDString() + " " + ingressConnector.getNodeConnectorIDString());

		// Use DataPacketService to decode the packet.
		Packet pkt = dataPacketService.decodeDataPacket(inPkt);

		if (pkt instanceof Ethernet) {
			Ethernet ethFrame = (Ethernet) pkt;
			Object l3Pkt = ethFrame.getPayload();

			if (l3Pkt instanceof IPv4) {
				IPv4 ipv4Pkt = (IPv4) l3Pkt;
				InetAddress clientAddr = intToInetAddress(ipv4Pkt.getSourceAddress());
				InetAddress dstAddr = intToInetAddress(ipv4Pkt.getDestinationAddress());
				
				// The connector, source and destination ("port")
				HostNodeConnector startHostConnector = iptohost.hostFind(clientAddr);
				HostNodeConnector endHostConnector = iptohost.hostFind(dstAddr);
				NodeConnector startConnector = startHostConnector.getnodeConnector();
				NodeConnector endConnector = endHostConnector.getnodeConnector();
				// The node, source and destination ("switch")
				Node startNode = startConnector.getNode();
				Node endNode = endConnector.getNode();
				
				log.trace("Packet from " + startNode.getNodeIDString() + " " + startConnector.getNodeConnectorIDString());
				log.trace("Packet to " + endNode.getNodeIDString() + " " + endConnector.getNodeConnectorIDString());
				
				//two hosts on the same node
				if (startNode.getNodeIDString().equals(endNode.getNodeIDString())){
					Match match = new Match();
					match.setField(MatchType.DL_TYPE, (short) 0x0800); // IPv4																	
					match.setField(MatchType.IN_PORT, startConnector);
					match.setField(MatchType.NW_SRC, clientAddr);
					match.setField(MatchType.NW_DST, dstAddr);
					
					List<Action> actions = new LinkedList<Action>();
					actions.add(new SetNwDst(dstAddr));
					actions.add(new Output(endConnector));
					
					Flow flow = new Flow(match, actions);						
					Status status = flowProgrammerService.addFlow(startNode, flow);
					if (!status.isSuccess()) {
						log.error("Could not program flow: " + status.getDescription());
						return PacketResult.CONSUME;
					}
					log.info("Installed flow {} in node {}", flow, startConnector);

					match = new Match();
					match.setField(MatchType.DL_TYPE, (short) 0x0800); // IPv4
					match.setField(MatchType.IN_PORT, endConnector);
					match.setField(MatchType.NW_SRC, dstAddr);
					match.setField(MatchType.NW_DST, clientAddr);
					
					actions = new LinkedList<Action>();
					actions.add(new SetNwDst(clientAddr));						
					actions.add(new Output(startConnector));
					
					flow = new Flow(match, actions);
					status = flowProgrammerService.addFlow(endNode, flow);
					if (!status.isSuccess()) {
						log.error("Could not program flow: " + status.getDescription());
						return PacketResult.CONSUME;
					}
					log.info("Installed flow {} in node {}", flow, endConnector);					
				}
				
				//two hosts on the different nodes
				else{   
				//Get path from source node to destination node
				Path path = routing.getRoute(startNode, endNode);
				//Get edges on the path
				List<Edge> edges = path.getEdges();				
				
				//Get nodes, input port, output port
				for(int i = 0;i<edges.size();i++){
					if(i == 0){
						NodeConnector firstConnector = edges.get(i).getTailNodeConnector();
						// Create flow table entry for further incoming packets
						// Match incoming packets
						Match match = new Match();
						match.setField(MatchType.DL_TYPE, (short) 0x0800); // IPv4																	
						match.setField(MatchType.IN_PORT, startConnector);
						match.setField(MatchType.NW_SRC, clientAddr);
						match.setField(MatchType.NW_DST, dstAddr);
						
						List<Action> actions = new LinkedList<Action>(); // List of actions applied to the packet						
						actions.add(new SetNwDst(dstAddr)); // Re-write destination IP to server instance IP						
						actions.add(new Output(firstConnector)); // Output packet on port to server instance						
						Flow flow = new Flow(match, actions); // Create the flow
						
						Status status = flowProgrammerService.addFlow(startNode, flow); // Use FlowProgrammerService to program flow.
						if (!status.isSuccess()) {
							log.error("Could not program flow: " + status.getDescription());
							return PacketResult.CONSUME;
						}
						log.info("Installed flow {} in node {}", flow, startConnector);

						// Create flow table entry for response packets
						match = new Match();
						match.setField(MatchType.DL_TYPE, (short) 0x0800); // IPv4
						match.setField(MatchType.IN_PORT, firstConnector);
						match.setField(MatchType.NW_SRC, dstAddr);
						match.setField(MatchType.NW_DST, clientAddr);
						
						actions = new LinkedList<Action>();  // List of actions applied to the packet						
						actions.add(new SetNwDst(clientAddr)); // Re-write destination IP to server instance IP						
						actions.add(new Output(startConnector)); // Output packet on port to server instance
						
						flow = new Flow(match, actions); // Create the flow						
						status = flowProgrammerService.addFlow(startNode, flow); // Use FlowProgrammerService to program flow.
						if (!status.isSuccess()) {
							log.error("Could not program flow: " + status.getDescription());
							return PacketResult.CONSUME;
						}
						log.info("Installed flow {} in node {}", flow, firstConnector);
					}
					else{
						NodeConnector headConnector = edges.get(i).getTailNodeConnector();
						Node passingNode = headConnector.getNode();
						NodeConnector tailConnector = edges.get(i-1).getHeadNodeConnector();

						Match match = new Match();
						match.setField(MatchType.DL_TYPE, (short) 0x0800); // IPv4																	
						match.setField(MatchType.IN_PORT, tailConnector);
						match.setField(MatchType.NW_SRC, clientAddr);
						match.setField(MatchType.NW_DST, dstAddr);
						
						List<Action> actions = new LinkedList<Action>();
						actions.add(new SetNwDst(dstAddr));
						actions.add(new Output(headConnector));
						
						Flow flow = new Flow(match, actions);
						Status status = flowProgrammerService.addFlow(passingNode, flow);
						if (!status.isSuccess()) {
							log.error("Could not program flow: " + status.getDescription());
							return PacketResult.CONSUME;
						}
						log.info("Installed flow {} in node {}", flow, tailConnector);

						// Create flow table entry for response packets
						match = new Match();
						match.setField(MatchType.DL_TYPE, (short) 0x0800); // IPv4
						match.setField(MatchType.IN_PORT, headConnector);
						match.setField(MatchType.NW_SRC, dstAddr);
						match.setField(MatchType.NW_DST, clientAddr);
						// List of actions applied to the packet
						actions = new LinkedList<Action>();
						// Re-write destination IP to server instance IP
						actions.add(new SetNwDst(clientAddr));
						// Output packet on port to server instance
						actions.add(new Output(tailConnector));
						// Create the flow
						flow = new Flow(match, actions);
						// Use FlowProgrammerService to program flow.
						status = flowProgrammerService.addFlow(passingNode, flow);
						if (!status.isSuccess()) {
							log.error("Could not program flow: " + status.getDescription());
							return PacketResult.CONSUME;
						}
						log.info("Installed flow {} in node {}", flow, headConnector);									
					    
						//last edge
						if(i == (edges.size()-1)){
					    	NodeConnector lastConnector = edges.get(i).getHeadNodeConnector();
					    	
						match = new Match();
						match.setField(MatchType.DL_TYPE, (short) 0x0800); // IPv4																	
						match.setField(MatchType.IN_PORT, lastConnector);
						match.setField(MatchType.NW_SRC, clientAddr);
						match.setField(MatchType.NW_DST, dstAddr);
						
						actions = new LinkedList<Action>();
						actions.add(new SetNwDst(dstAddr));
						actions.add(new Output(endConnector));
						
						flow = new Flow(match, actions);						
						status = flowProgrammerService.addFlow(endNode, flow);
						if (!status.isSuccess()) {
							log.error("Could not program flow: " + status.getDescription());
							return PacketResult.CONSUME;
						}
						log.info("Installed flow {} in node {}", flow, lastConnector);

						match = new Match();
						match.setField(MatchType.DL_TYPE, (short) 0x0800); // IPv4
						match.setField(MatchType.IN_PORT, endConnector);
						match.setField(MatchType.NW_SRC, dstAddr);
						match.setField(MatchType.NW_DST, clientAddr);
						
						actions = new LinkedList<Action>();
						actions.add(new SetNwDst(clientAddr));						
						actions.add(new Output(lastConnector));
						
						flow = new Flow(match, actions);
						status = flowProgrammerService.addFlow(endNode, flow);
						if (!status.isSuccess()) {
							log.error("Could not program flow: " + status.getDescription());
							return PacketResult.CONSUME;
						}
						log.info("Installed flow {} in node {}", flow, endConnector);
						}
					}
				}
				}
				
				// Forward initial packet to selected server
				log.trace("Forwarding packet to " + dstAddr.toString() + " through port " + endConnector.getNodeConnectorIDString());	
				//ipv4Pkt.setDestinationAddress(dstAddr);			
				inPkt.setOutgoingNodeConnector(endConnector);
				dataPacketService.transmitDataPacket(inPkt);
				return PacketResult.CONSUME;
			}
		}

		// We did not process the packet -> let someone else do the job.
		return PacketResult.IGNORED;
	}

}
