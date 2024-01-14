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
	 * Moves to a random node from a list of locations and observations.
	 *
	 * @param locationsWithObservations A list of locations paired with their respective observations.
	 * @return The goal location node if movement is successful, otherwise null.
	 */
	private Location moveToNextRandomNode(
			List<Couple<Location, List<Couple<Observation, Integer>>>> locationsWithObservations) {

		// Initialize a random number generator
		Random randomGenerator = new Random();

		// Generate a random index to select a node from the list
		int randomIndex = 1 + randomGenerator.nextInt(locationsWithObservations.size() - 1);

		// Select a node based on the random index
		Location selectedNode = locationsWithObservations.get(randomIndex).getLeft();

		// Initialize the goal node as the selected node
		Location goalNode = selectedNode;

		// Check if the selected node is already in the buffer (visited)
		if (!this.nodeBuffer.contains(selectedNode)) {
			goalNode = selectedNode;
		} else {
			// If the selected node is in the buffer, find the next unvisited node
			for (int index = 1; index < locationsWithObservations.size(); index++) {
				selectedNode = locationsWithObservations.get(index).getLeft();
				if (!this.nodeBuffer.contains(selectedNode)) {
					goalNode = selectedNode;
					break;
				}
			}
		}

		// Attempt to move to the goal node
		boolean hasMoved = ((AbstractDedaleAgent) this.myAgent).moveTo(goalNode);

		// If unable to move, try the next node in the list
		int attemptIndex = 1;
		while (!hasMoved && attemptIndex < locationsWithObservations.size()) {
			goalNode = locationsWithObservations.get(attemptIndex).getLeft();
			hasMoved = ((AbstractDedaleAgent) this.myAgent).moveTo(goalNode);
			attemptIndex++;
			this.nodeBuffer.clear();
		}

		// If still unable to move, return null
		if (!hasMoved) {
			return null;
		}

		// Return the goal node if movement is successful
		return goalNode;
	}

	@Override
	public void action() {

		initializeMapAndBehaviours();

		Location currentLocation = getCurrentAgentPosition();
		if (currentLocation == null) {
			return;
		}
		List<Couple<Location, List<Couple<Observation, Integer>>>> observations = getObservations();
		if (randomMovementStepsLeft > 0) {
			performRandomMovement(observations);
			return;
		}

		safelyWait(TICK_TIME);

		updateMapWithCurrentLocation(currentLocation);
		handleTreasureObservation(observations);

		String nextNode = processObservationsAndUpdateMap(observations, currentLocation);
		if (observations != null && !observations.isEmpty()) {
			System.out.println(this.myAgent.getLocalName() + " - " + observations);
			manageAgentMovement(nextNode, observations, currentLocation);
		}

	}

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
			System.out.println(this.myAgent.getLocalName() + " - open the safe" + observations.get(0).getLeft() + " : "
					+ treasure_observed + ", " + ((AbstractDedaleAgent) this.myAgent).openLock(treasure_observed));
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
			Location nextNodeAux = moveToNextRandomNode(observations);
			if (nextNodeAux == null) {
				System.out
						.println(this.myAgent.getLocalName() + " - Exploration successufully done, behaviour removed.");
				this.finished = true;
				return;
			}
			nextNode = nextNodeAux.getLocationId();
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
					System.out.println(this.myAgent.getLocalName() + " - blocked too long. random walk.");
					this.blocked_counter = 0;
					this.randomMovementStepsLeft = 10;
				}
			} else {
				this.blocked_counter = 0;
			}
		}
	}

	@Override
	public boolean done() {
		return finished;
	}

}