package eu.su.mas.dedaleEtu.mas.behaviours;

import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

public class TankerBehaviour extends TickerBehaviour {
    /**
     * When an agent choose to migrate all its components should be serializable
     */
    private static final long serialVersionUID = 9088209402507795289L;
    private final AbstractDedaleAgent agent;
    private final String agentName;

    private List<Location> prevNodes = new ArrayList<>(8);

    public TankerBehaviour(final AbstractDedaleAgent myagent) {
        super(myagent, 600);
        this.agent = (AbstractDedaleAgent) this.myAgent;
        this.agentName = this.agent.getLocalName();
    }

    /**
     * Chooses the next node to move to based on available observations. Prioritizes nodes that are not in the node
     * buffer.
     *
     * @param observationsList A list of observations associated with locations.
     * @return The chosen goal location or null if no move is possible.
     */
    private Location chooseNextGoalNode(List<Couple<Location, List<Couple<Observation, Integer>>>> observationsList) {

        // Ensure there are available locations
        if (observationsList.isEmpty()) {
            return null;
        }

        // Initialize the random number generator
        Random random = new Random();

        // Randomly choose a start index for selecting the next node
        int startIndex = random.nextInt(observationsList.size());

        // Initialize the goal node
        Location goalNode = null;

        // Iterate over the observations list to find a suitable goal node
        for (int i = 0; i < observationsList.size(); i++) {
            int currentIndex = (startIndex + i) % observationsList.size();
            Location currentNode = observationsList.get(currentIndex).getLeft();

            // Check if the current node is not in the node buffer
            if (!prevNodes.contains(currentNode)) {
                goalNode = currentNode;
                break;
            }
        }

        // Attempt to move to the chosen goal node
        boolean moved = false;
        if (goalNode != null) {
            moved = ((AbstractDedaleAgent) this.myAgent).moveTo(goalNode);
        }

        // Clear the node buffer if a move was successful
        if (moved) {
            prevNodes.clear();
            return goalNode;
        }

        // Return null if no suitable move was found
        return null;
    }

    @Override
    public void onTick() {
        // Example to retrieve the current position
        Location myPosition = ((AbstractDedaleAgent) this.myAgent).getCurrentPosition();

        if (myPosition != null) {
            // List of observable from the agent's current position
            List<Couple<Location, List<Couple<Observation, Integer>>>> lobs = agent.observe();// myPosition

            Location newNode = chooseNextGoalNode(lobs);

            if (newNode != null) {
                this.prevNodes.add(newNode);
            }

            System.out.println(agentName + " -- list of observables: " + lobs);
            System.out.println(agentName + " -- My current backpack capacity is:" + agent.getBackPackFreeSpace());

        }

    }
}