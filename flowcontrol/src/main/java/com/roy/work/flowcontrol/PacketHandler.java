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
import org.opendaylight.controller.sal.packet.ICMP;
import org.opendaylight.controller.sal.packet.Packet;
import org.opendaylight.controller.sal.packet.PacketResult;
import org.opendaylight.controller.sal.packet.RawPacket;
import org.opendaylight.controller.sal.packet.TCP;
import org.opendaylight.controller.sal.packet.UDP;
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
	
	public void addflowentry(byte type, Node switchnode, NodeConnector input, NodeConnector output, InetAddress srcAddr, InetAddress dstAddr, byte[] srcMAC, byte[] dstMAC){
		// Create flow table entry for further incoming packets
		// Match incoming packets
		Match match = new Match();
		match.setField(MatchType.DL_TYPE, (short) 0x0800); // IPv4	
		match.setField(MatchType.NW_PROTO, type);
		match.setField(MatchType.IN_PORT, input);
		match.setField(MatchType.DL_SRC, srcMAC);
		match.setField(MatchType.DL_DST, dstMAC);
		match.setField(MatchType.NW_SRC, srcAddr);
		match.setField(MatchType.NW_DST, dstAddr);
			
		List<Action> actions = new LinkedList<Action>(); // List of actions applied to the packet						
		actions.add(new SetNwDst(dstAddr)); // Re-write destination IP to server instance IP						
		actions.add(new Output(output)); // Output packet on port to server instance						
		Flow flow = new Flow(match, actions); // Create the flow
		
		Status status = flowProgrammerService.addFlow(switchnode, flow); // Use FlowProgrammerService to program flow.
		//log.info("Installed flow {} in node {}", flow, switchnode);

		// Create flow table entry for response packets
		match = new Match();
		match.setField(MatchType.DL_TYPE, (short) 0x0800); // IPv4
		match.setField(MatchType.NW_PROTO, type);
		match.setField(MatchType.IN_PORT, output);
		match.setField(MatchType.DL_SRC, dstMAC);
		match.setField(MatchType.DL_DST, srcMAC);
		match.setField(MatchType.NW_SRC, dstAddr);
		match.setField(MatchType.NW_DST, srcAddr);
		
		actions = new LinkedList<Action>();  // List of actions applied to the packet						
		actions.add(new SetNwDst(srcAddr)); // Re-write destination IP to server instance IP						
		actions.add(new Output(input)); // Output packet on port to server instance
		
		flow = new Flow(match, actions); // Create the flow						
		status = flowProgrammerService.addFlow(switchnode, flow); // Use FlowProgrammerService to program flow.
		//log.info("Installed flow {} in node {}", flow, switchnode);	
	}
	
	/*overload addflowentry function*/
	public void addflowentry(byte type, Node switchnode, NodeConnector input, NodeConnector output, short srcPort, short dstPort, InetAddress srcAddr, InetAddress dstAddr, byte[] srcMAC, byte[] dstMAC){
		// Create flow table entry for further incoming packets
		// Match incoming packets
		Match match = new Match();
		match.setField(MatchType.DL_TYPE, (short) 0x0800); // IPv4	
		match.setField(MatchType.NW_PROTO, type);          
		match.setField(MatchType.IN_PORT, input);
		match.setField(MatchType.DL_SRC, srcMAC);
		match.setField(MatchType.DL_DST, dstMAC);
		match.setField(MatchType.NW_SRC, srcAddr);
		match.setField(MatchType.NW_DST, dstAddr);
		match.setField(MatchType.TP_SRC, srcPort);
		match.setField(MatchType.TP_DST, dstPort);
			
		List<Action> actions = new LinkedList<Action>(); // List of actions applied to the packet						
		actions.add(new SetNwDst(dstAddr)); // Re-write destination IP to server instance IP						
		actions.add(new Output(output)); // Output packet on port to server instance						
		Flow flow = new Flow(match, actions); // Create the flow
		
		Status status = flowProgrammerService.addFlow(switchnode, flow); // Use FlowProgrammerService to program flow.
		//log.info("Installed flow {} in node {}", flow, switchnode);

		// Create flow table entry for response packets
		match = new Match();
		match.setField(MatchType.DL_TYPE, (short) 0x0800); // IPv4
		match.setField(MatchType.NW_PROTO, type);
		match.setField(MatchType.IN_PORT, output);
		match.setField(MatchType.DL_SRC, dstMAC);
		match.setField(MatchType.DL_DST, srcMAC);
		match.setField(MatchType.NW_SRC, dstAddr);
		match.setField(MatchType.NW_DST, srcAddr);
		match.setField(MatchType.TP_SRC, dstPort);
		match.setField(MatchType.TP_DST, srcPort);
		
		actions = new LinkedList<Action>();  // List of actions applied to the packet						
		actions.add(new SetNwDst(srcAddr)); // Re-write destination IP to server instance IP						
		actions.add(new Output(input)); // Output packet on port to server instance
		
		flow = new Flow(match, actions); // Create the flow						
		status = flowProgrammerService.addFlow(switchnode, flow); // Use FlowProgrammerService to program flow.
		//log.info("Installed flow {} in node {}", flow, switchnode);	
	}
	
	public void routingpath(byte type, Node startNode, Node endNode, NodeConnector startConnector, NodeConnector endConnector, InetAddress clientAddr, InetAddress dstAddr, byte[] srcMAC, byte[] dstMAC){
		//two hosts on the same node
		if (startNode.getNodeIDString().equals(endNode.getNodeIDString())){
			this.addflowentry(type, startNode, startConnector, endConnector, clientAddr, dstAddr, srcMAC, dstMAC);			
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
				this.addflowentry(type, startNode, startConnector, firstConnector, clientAddr, dstAddr, srcMAC, dstMAC);						
			}
			else{
				NodeConnector tailConnector = edges.get(i).getTailNodeConnector();
				Node passingNode = tailConnector.getNode();
				NodeConnector headConnector = edges.get(i-1).getHeadNodeConnector();
				this.addflowentry(type, passingNode, headConnector, tailConnector, clientAddr, dstAddr, srcMAC, dstMAC);
																    
				//last edge
				if(i == (edges.size()-1)){
			    	NodeConnector lastConnector = edges.get(i).getHeadNodeConnector();
			    	this.addflowentry(type, endNode, lastConnector, endConnector, clientAddr, dstAddr, srcMAC, dstMAC);
				}
			}
		}
		}
	}
	
	/*overload routingpath function*/
	public void routingpath(byte type, Node startNode, Node endNode, NodeConnector startConnector, NodeConnector endConnector, short srcPort, short dstPort, InetAddress clientAddr, InetAddress dstAddr, byte[] srcMAC, byte[] dstMAC){
		//two hosts on the same node
		if (startNode.getNodeIDString().equals(endNode.getNodeIDString())){
			this.addflowentry(type, startNode, startConnector, endConnector, srcPort, dstPort, clientAddr, dstAddr, srcMAC, dstMAC);			
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
				this.addflowentry(type, startNode, startConnector, firstConnector, srcPort, dstPort, clientAddr, dstAddr, srcMAC, dstMAC);						
			}
			else{
				NodeConnector tailConnector = edges.get(i).getTailNodeConnector();
				Node passingNode = tailConnector.getNode();
				NodeConnector headConnector = edges.get(i-1).getHeadNodeConnector();
				this.addflowentry(type, passingNode, headConnector, tailConnector, srcPort, dstPort, clientAddr, dstAddr, srcMAC, dstMAC);
																    
				//last edge
				if(i == (edges.size()-1)){
			    	NodeConnector lastConnector = edges.get(i).getHeadNodeConnector();
			    	this.addflowentry(type, endNode, lastConnector, endConnector, srcPort, dstPort, clientAddr, dstAddr, srcMAC, dstMAC);
				}
			}
		}
		}
	}
	
	
	public void packetforward(RawPacket inPkt, Ethernet ethFrame, IPv4 ipv4Pkt, NodeConnector endConnector, byte[] dstMAC, InetAddress dstAddr){
		// Forward initial packet to selected server
		log.trace("Forwarding packet to " + dstAddr.toString() + " through port " + endConnector.getNodeConnectorIDString());				
		ipv4Pkt.setDestinationAddress(dstAddr);
		ethFrame.setDestinationMACAddress(dstMAC);
		inPkt.setOutgoingNodeConnector(endConnector);
		dataPacketService.transmitDataPacket(inPkt);
	}

	@Override
	public PacketResult receiveDataPacket(RawPacket inPkt) {
		//Packet coming time
		TimeStamp timestamp = inPkt.getIncomingTime();
		//Packet size
		int size = inPkt.getPacketData().length;
		// The connector, the packet came from ("port")
		NodeConnector ingressConnector = inPkt.getIncomingNodeConnector();
		// The node that received the packet ("switch")
		Node node = ingressConnector.getNode();

		// Use DataPacketService to decode the packet.
		Packet pkt = dataPacketService.decodeDataPacket(inPkt);

		if (pkt instanceof Ethernet) {
			Ethernet ethFrame = (Ethernet) pkt;
			byte[] srcMAC = ethFrame.getSourceMACAddress();
			byte[] dstMAC = ethFrame.getDestinationMACAddress();
			Object l3Pkt = ethFrame.getPayload();

			if (l3Pkt instanceof IPv4) {
				IPv4 ipv4Pkt = (IPv4) l3Pkt;
				InetAddress clientAddr = intToInetAddress(ipv4Pkt.getSourceAddress());
				InetAddress dstAddr = intToInetAddress(ipv4Pkt.getDestinationAddress());
				Object l4Datagram = ipv4Pkt.getPayload();
				
				// The connector, source and destination ("port")
				HostNodeConnector startHostConnector = iptohost.hostFind(clientAddr);
				HostNodeConnector endHostConnector = iptohost.hostFind(dstAddr);
				NodeConnector startConnector = startHostConnector.getnodeConnector();
				NodeConnector endConnector = endHostConnector.getnodeConnector();
				// The node, source and destination ("switch")
				Node startNode = startConnector.getNode();
				Node endNode = endConnector.getNode();
								
				if(l4Datagram instanceof ICMP){
					ICMP icmpPkt = (ICMP) l4Datagram;
					byte type = 1;
					String PktType = "ICMP";
					log.trace(timestamp.getStringValue());
					log.trace("Packet size: {}", size);
					log.trace("ICMP Datagram");
					log.trace("Packet from " + startNode.getNodeIDString() + " " + startConnector.getNodeConnectorIDString());
					log.trace("Packet to " + endNode.getNodeIDString() + " " + endConnector.getNodeConnectorIDString());
					this.routingpath(type, startNode, endNode, startConnector, endConnector, clientAddr, dstAddr, srcMAC, dstMAC);	
					this.packetforward(inPkt, ethFrame, ipv4Pkt, endConnector, dstMAC, dstAddr);				
					return PacketResult.CONSUME;
				}
				
				else if(l4Datagram instanceof TCP){
					TCP tcpDatagram = (TCP) l4Datagram;
					short scrPort = tcpDatagram.getSourcePort();
					short dstPort = tcpDatagram.getDestinationPort();
					byte type = 6;
					String PktType = "TCP";
					log.trace(timestamp.getStringValue());
					log.trace("Packet size: {}", size);
					log.trace("TCP Datagram");	
					this.routingpath(type, startNode, endNode, startConnector, endConnector, scrPort, dstPort, clientAddr, dstAddr, srcMAC, dstMAC);
					this.packetforward(inPkt, ethFrame, ipv4Pkt, endConnector, dstMAC, dstAddr);				
					return PacketResult.CONSUME;
				}
				
				else if(l4Datagram instanceof UDP){
					UDP udpDatagram = (UDP) l4Datagram;
					short srcPort = udpDatagram.getSourcePort();
					short dstPort = udpDatagram.getDestinationPort();
					byte type = 17;
					String PktType = "UDP";
					log.trace(timestamp.getStringValue());
					log.trace("Packet size: {}", size);
					log.trace("UDP Datagram");
					this.routingpath(type, startNode, endNode, startConnector, endConnector, srcPort, dstPort, clientAddr, dstAddr, srcMAC, dstMAC);
					this.packetforward(inPkt, ethFrame, ipv4Pkt, endConnector, dstMAC, dstAddr);				
					return PacketResult.CONSUME;
				}
				else{
					return PacketResult.CONSUME;
				}			
			}
		}

		// We did not process the packet -> let someone else do the job.
		return PacketResult.IGNORED;
	}

}
