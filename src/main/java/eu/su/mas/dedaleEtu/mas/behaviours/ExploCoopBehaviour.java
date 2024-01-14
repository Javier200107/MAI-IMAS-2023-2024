package eu.su.mas.dedaleEtu.mas.behaviours;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import dataStructures.serializableGraph.SerializableSimpleGraph;
import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.env.gs.gsLocation;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;

import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import eu.su.mas.dedaleEtu.mas.behaviours.ShareMapBehaviour;

import jade.core.AID;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

/**
 * <pre>
 * This behaviour allows an agent to explore the environment and learn the associated topological map.
 * The algorithm is a pseudo - DFS computationally consuming because its not optimised at all.
 * 
 * When all the nodes around him are visited, the agent randomly select an open node and go there to restart its dfs. 
 * This (non optimal) behaviour is done until all nodes are explored. 
 * 
 * Warning, this behaviour does not save the content of visited nodes, only the topology.
 * Warning, the sub-behaviour ShareMap periodically share the whole map
 * </pre>
 * 
 * @author hc
 *
 */
public class ExploCoopBehaviour extends SimpleBehaviour {

	private static final long serialVersionUID = 8567689731496787661L;

	private boolean finished = false;

	private boolean explored = false;

	/**
	 * Current knowledge of the agent regarding the environment
	 */
	private MapRepresentation myMap;

	private List<String> list_agentNames;

	private static final int TICK_TIME = 300;
	private int blocked_counter = 0;
	private int randomMovementStepsLeft = 0;

	private static final int BUFFER_SIZE = 8;
	private List<String> nodeBuffer = new ArrayList<>(BUFFER_SIZE);

	/**
	 *
	 * @param myagent
	 * @param myMap      known map of the world the agent is living in
	 * @param agentNames name of the agents to share the map with
	 */
	public ExploCoopBehaviour(final AbstractDedaleAgent myagent, MapRepresentation myMap, List<String> agentNames) {
		super(myagent);
		this.myMap = myMap;
		this.list_agentNames = agentNames;
	}

	/**
	 * Performs a random movement based on the given list of observations. It moves to a random next node and updates
	 * the node buffer accordingly.
	 *
	 * @param observationList List of observations along with their locations.
	 */
	private void performRandomMovement(List<Couple<Location, List<Couple<Observation, Integer>>>> observationList) {
		// Move to a random next node based on the observations.
		Location nextLocation = moveToNextRandomNode(observationList);

		// Check if the next location is valid and not already in the node buffer.
		if (nextLocation != null && !this.nodeBuffer.contains(nextLocation.toString())) {

			// Remove the oldest entry if the buffer is full.
			if (this.nodeBuffer.size() == BUFFER_SIZE) {
				this.nodeBuffer.remove(0);
			}

			// Add the new location to the node buffer.
			this.nodeBuffer.add(nextLocation.getLocationId());
		}

		// Decrement the counter for the number of random steps.
		this.randomMovementStepsLeft -= 1;

		// Check if the random walk is completed.
		if (this.randomMovementStepsLeft == 0) {
			System.out.println(this.myAgent.getLocalName() + " - Completed random walk.");
		}
	}

	/**
	 * Selects and moves to a random next node from the provided list of locations and observations. Ensures that the
	 * chosen node is not already in the node buffer.
	 *
	 * @param locationObservationList List of locations paired with their observations.
	 * @return The goal node if moved successfully; otherwise, null.
	 */
	private Location moveToNextRandomNode(
			List<Couple<Location, List<Couple<Observation, Integer>>>> locationObservationList) {
		Random random = new Random();
		int randomIndex = random.nextInt(locationObservationList.size());
		Location selectedNode = locationObservationList.get(randomIndex).getLeft();

		// Find a node that is not in the node buffer.
		Location goalNode = findUnvisitedNode(selectedNode, locationObservationList);

		// Attempt to move to the goal node.
		boolean isMoveSuccessful = attemptMoveToNode(goalNode, locationObservationList);

		return isMoveSuccessful ? goalNode : null;
	}

	/**
	 * Finds a node that has not been visited (not in the node buffer). Starts from the provided node and checks other
	 * nodes if necessary.
	 *
	 * @param startNode               The node to start the search from.
	 * @param locationObservationList List of locations paired with their observations.
	 * @return An unvisited node if found; otherwise, returns the start node.
	 */
	private Location findUnvisitedNode(Location startNode,
			List<Couple<Location, List<Couple<Observation, Integer>>>> locationObservationList) {
		if (!this.nodeBuffer.contains(startNode.toString())) {
			return startNode;
		}

		for (Couple<Location, List<Couple<Observation, Integer>>> locationObservation : locationObservationList) {
			Location currentNode = locationObservation.getLeft();
			if (!this.nodeBuffer.contains(currentNode.toString())) {
				return currentNode;
			}
		}

		return startNode;
	}

	/**
	 * Attempts to move the agent to the specified node. If the first attempt fails, tries other nodes in the list until
	 * a successful move or all options are exhausted.
	 *
	 * @param initialNode             The initial node to attempt to move to.
	 * @param locationObservationList List of locations paired with their observations.
	 * @return True if the move was successful; otherwise, false.
	 */
	private boolean attemptMoveToNode(Location initialNode,
			List<Couple<Location, List<Couple<Observation, Integer>>>> locationObservationList) {
		for (Couple<Location, List<Couple<Observation, Integer>>> locationObservation : locationObservationList) {
			Location currentNode = locationObservation.getLeft();
			boolean moved = ((AbstractDedaleAgent) this.myAgent).moveTo(currentNode);
			if (moved) {
				return true;
			}
			// Optional: Add logic to handle failed movement, e.g., update node buffer.
		}
		return false;
	}

	// hohohoho

	private void initializeMapAndBehaviours() {
		if (this.myMap == null) {
			this.myMap = new MapRepresentation();
			this.myAgent.addBehaviour(new ShareMapBehaviour(this.myAgent, TICK_TIME, this.myMap, list_agentNames));
			this.myAgent.addBehaviour(new SharePath(this.myAgent, this.myMap));
		}
	}

	private Location getCurrentAgentPosition() {
		return ((AbstractDedaleAgent) this.myAgent).getCurrentPosition();
	}

	private List<Couple<Location, List<Couple<Observation, Integer>>>> getObservations() {
		return ((AbstractDedaleAgent) this.myAgent).observe();
	}

	private void safelyWait(long duration) {
		try {
			this.myAgent.doWait(duration);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void updateMapWithCurrentLocation(Location currentLocation) {
		this.myMap.addNode(currentLocation.getLocationId(), MapAttribute.closed);
	}

	private void handleTreasureObservation(List<Couple<Location, List<Couple<Observation, Integer>>>> observations) {

		if (!observations.get(0).getRight().isEmpty()
				&& !observations.get(0).getRight().get(0).getLeft().equals(Observation.LOCKSTATUS)) {
			Observation treasure_observed = observations.get(0).getRight().get(0).getLeft();
			System.out.println(this.myAgent.getLocalName() + " - I try to open the safe" + observations.get(0).getLeft()
					+ " : " + treasure_observed + ", "
					+ ((AbstractDedaleAgent) this.myAgent).openLock(treasure_observed));
		}
	}

	private String processObservationsAndUpdateMap(
			List<Couple<Location, List<Couple<Observation, Integer>>>> observations, Location currentLocation) {
		String nextNode = null;
		for (Couple<Location, List<Couple<Observation, Integer>>> observation : observations) {
			Location nodeId = observation.getLeft();
			boolean isNewNode = this.myMap.addNewNode(nodeId.getLocationId());
			if (!currentLocation.equals(nodeId)) {
				this.myMap.addEdge(currentLocation.getLocationId(), nodeId.getLocationId());
				if (nextNode == null && isNewNode) {
					nextNode = nodeId.getLocationId();
				}
			}
		}
		return nextNode;
	}

	private void manageAgentMovement(String nextNode,
			List<Couple<Location, List<Couple<Observation, Integer>>>> observations, Location currentLocation) {

		if (!this.myMap.hasOpenNode() || explored) {
			explored = true;
			nextNode = moveToNextRandomNode(observations).getLocationId();
			if (nextNode != null && !this.nodeBuffer.contains(nextNode)) {

				if (this.nodeBuffer.size() == this.BUFFER_SIZE) {
					this.nodeBuffer.remove(0);
				}

				this.nodeBuffer.add(nextNode);
			}
		} else {
			if (nextNode == null) {
				nextNode = this.myMap.getShortestPathToClosestOpenNode(currentLocation.getLocationId()).get(0);

			}

			MessageTemplate msgTemplate = MessageTemplate.and(MessageTemplate.MatchProtocol("SHARE-TOPO"),
					MessageTemplate.MatchPerformative(ACLMessage.INFORM));
			ACLMessage msgReceived = this.myAgent.receive(msgTemplate);
			if (msgReceived != null) {
				String mclass;
				try {
					mclass = msgReceived.getContentObject().getClass().getName();

				} catch (UnreadableException e) {
					throw new RuntimeException(e);
				}

				if (mclass == "dataStructures.serializableGraph.SerializableSimpleGraph") {
					SerializableSimpleGraph<String, MapAttribute> sgreceived = null;
					try {

						sgreceived = (SerializableSimpleGraph<String, MapAttribute>) msgReceived.getContentObject();
					} catch (UnreadableException e) {
						e.printStackTrace();
					}

					this.myMap.mergeMap(sgreceived);
				}
			}

			if (!explored && !((AbstractDedaleAgent) this.myAgent).moveTo(new gsLocation(nextNode))) {
				this.blocked_counter += 1;
				if (this.blocked_counter > 5) {
					System.out.println(
							this.myAgent.getLocalName() + " - I was blocked for too long. Doing a random walk.");
					this.blocked_counter = 0;
					this.randomMovementStepsLeft = 10;
				}
			} else {
				this.blocked_counter = 0;
			}
		}
	}

	// @Override
	// public void action() {

	// initializeMapAndBehaviours();

	// Location currentLocation = getCurrentAgentPosition();
	// if (currentLocation == null) {
	// return;
	// }
	// List<Couple<Location, List<Couple<Observation, Integer>>>> observations = getObservations();
	// if (randomMovementStepsLeft > 0) {
	// performRandomMovement(observations);
	// return;
	// }

	// safelyWait(TICK_TIME);

	// updateMapWithCurrentLocation(currentLocation);
	// handleTreasureObservation(observations);

	// String nextNode = processObservationsAndUpdateMap(observations, currentLocation);
	// manageAgentMovement(nextNode, observations, currentLocation);

	// }

	@Override
	public void action() {

		if (this.myMap == null) {
			this.myMap = new MapRepresentation();
			this.myAgent.addBehaviour(new ShareMapBehaviour(this.myAgent, TICK_TIME, this.myMap, list_agentNames));
			this.myAgent.addBehaviour(new SharePath(this.myAgent, this.myMap));
		}
		Location myPosition = ((AbstractDedaleAgent) this.myAgent).getCurrentPosition();

		if (myPosition != null) {
			List<Couple<Location, List<Couple<Observation, Integer>>>> lobs = ((AbstractDedaleAgent) this.myAgent)
					.observe();

			if (this.randomMovementStepsLeft > 0) {
				performRandomMovement(lobs);
				return;
			}

			try {
				this.myAgent.doWait(TICK_TIME);
			} catch (Exception e) {
				e.printStackTrace();
			}

			this.myMap.addNode(myPosition.getLocationId(), MapAttribute.closed);

			if (!lobs.get(0).getRight().isEmpty()
					&& !lobs.get(0).getRight().get(0).getLeft().equals(Observation.LOCKSTATUS)) {
				Observation treasure_observed = lobs.get(0).getRight().get(0).getLeft();
				System.out.println(this.myAgent.getLocalName() + " - I try to open the safe" + lobs.get(0).getLeft()
						+ " : " + treasure_observed + ", "
						+ ((AbstractDedaleAgent) this.myAgent).openLock(treasure_observed));
			}

			String nextNode = null;
			Iterator<Couple<Location, List<Couple<Observation, Integer>>>> iter = lobs.iterator();
			while (iter.hasNext()) {
				Couple<Location, List<Couple<Observation, Integer>>> CurrentNode = iter.next();
				Location nodeId = CurrentNode.getLeft();

				boolean isNewNode = this.myMap.addNewNode(nodeId.getLocationId());
				if (myPosition != nodeId) {
					this.myMap.addEdge(myPosition.getLocationId(), nodeId.getLocationId());
					if (nextNode == null && isNewNode)
						nextNode = nodeId.getLocationId();
				}
			}

			if (!this.myMap.hasOpenNode() || explored) {
				explored = true;
				nextNode = moveToNextRandomNode(lobs).getLocationId();
				if (nextNode != null && !this.nodeBuffer.contains(nextNode)) {

					if (this.nodeBuffer.size() == this.BUFFER_SIZE) {
						this.nodeBuffer.remove(0);
					}

					this.nodeBuffer.add(nextNode);
				}
			} else {
				if (nextNode == null) {
					nextNode = this.myMap.getShortestPathToClosestOpenNode(myPosition.getLocationId()).get(0);

				}

				MessageTemplate msgTemplate = MessageTemplate.and(MessageTemplate.MatchProtocol("SHARE-TOPO"),
						MessageTemplate.MatchPerformative(ACLMessage.INFORM));
				ACLMessage msgReceived = this.myAgent.receive(msgTemplate);
				if (msgReceived != null) {
					String mclass;
					try {
						mclass = msgReceived.getContentObject().getClass().getName();

					} catch (UnreadableException e) {
						throw new RuntimeException(e);
					}

					if (mclass == "dataStructures.serializableGraph.SerializableSimpleGraph") {
						SerializableSimpleGraph<String, MapAttribute> sgreceived = null;
						try {

							sgreceived = (SerializableSimpleGraph<String, MapAttribute>) msgReceived.getContentObject();
						} catch (UnreadableException e) {
							e.printStackTrace();
						}

						this.myMap.mergeMap(sgreceived);
					}
				}

				if (!explored && !((AbstractDedaleAgent) this.myAgent).moveTo(new gsLocation(nextNode))) {
					this.blocked_counter += 1;
					if (this.blocked_counter > 5) {
						System.out.println(
								this.myAgent.getLocalName() + " - I was blocked for too long. Doing a random walk.");
						this.blocked_counter = 0;
						this.randomMovementStepsLeft = 10;
					}
				} else {
					this.blocked_counter = 0;
				}
			}

		}
	}

	@Override
	public boolean done() {
		return finished;
	}

}