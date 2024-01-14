package eu.su.mas.dedaleEtu.mas.behaviours;

import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedaleEtu.princ.Utils;
import jade.core.AID;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

public class CollectorBehaviour extends TickerBehaviour {
    private static final long serialVersionUID = 9088209402507795289L;
    private static final int BUFFER_SIZE = 8;
    private static final int TICKER_TIME = Utils.TICK_TIME;
    private List<String> node_Buffer = new ArrayList<>(BUFFER_SIZE);
    private List<String> potential_treasures = new ArrayList<>();
    private HashMap<String, Integer> treasure_quantity = new HashMap<String, Integer>();
    private HashMap<String, String> treasure_types = new HashMap<String, String>();

    private boolean is_working = false;
    private boolean backing_up = false;
    private int backoff_wait_t = 0;
    private int mission_step = 0;
    private String conflict_node = null;
    private int conflict_counter = 0;
    private List<String> conflict_path = new ArrayList<>();

    private boolean stop_for_help = false;
    private int stop_patience = 0;

    private List<String> planned_route = new ArrayList<>();

    public CollectorBehaviour(final AbstractDedaleAgent myagent) {
        super(myagent, TICKER_TIME);
    }

    private List<String> getRemainingRoute() {
        List<String> remaining = new ArrayList<String>();
        for (int i = this.mission_step; i < this.planned_route.size(); i++) {
            remaining.add(this.planned_route.get(i));
        }
        return remaining;
    }

    /*
     * If collector passes by a treasure of its own type, saves the node to send it to an Explorer and ask for the route
     */
    private void updateAllPotentialTreasures() {
        List<String> treasures = new ArrayList<>();
        for (HashMap.Entry<String, String> node : this.treasure_types.entrySet()) {
            if (((AbstractDedaleAgent) this.myAgent).getMyTreasureType().toString() == node.getValue()
                    && this.treasure_quantity.get(node.getKey()) > 0) {
                treasures.add(node.getKey());
            }
        }
        this.potential_treasures = treasures;
    }

    /**
     * Moves to the next node in the planned route.
     *
     * @param observedLocations A list of observed locations and their associated observations.
     * @return The string representation of the next node if the move is successful, otherwise null.
     */
    private String moveToNextNode(List<Couple<Location, List<Couple<Observation, Integer>>>> observedLocations) {
        if (planned_route == null || planned_route.isEmpty() || mission_step >= planned_route.size()) {
            System.out.println(this.myAgent.getLocalName() + " - No valid mission route available.");
            resetMission();
            return null;
        }

        String targetNodeString = planned_route.get(mission_step);
        Location targetNode = findLocationFromString(observedLocations, targetNodeString);

        if (targetNode == null) {
            System.out.println(
                    this.myAgent.getLocalName() + " - Following node from mission is not valid. Aborting mission.");
            resetMission();
            return null;
        }

        if (!moveAgentTo(targetNode)) {
            solveDeadLock();
            return null;
        }

        mission_step++;
        if (mission_step == planned_route.size()) {
            System.out.println(this.myAgent.getLocalName() + " -- Finished mission: Final node was a treasure.");
            resetMission();
        }

        return targetNodeString;
    }

    /**
     * Resets the mission-related variables.
     */
    private void resetMission() {
        is_working = false;
        mission_step = 0;
        planned_route = null;
    }

    /**
     * Attempts to move the agent to the specified location.
     *
     * @param location The location to move to.
     * @return true if the move was successful, false otherwise.
     */
    private boolean moveAgentTo(Location location) {
        return ((AbstractDedaleAgent) this.myAgent).moveTo(location);
    }

    /**
     * Finds a location from a list based on its string representation.
     *
     * @param locations      The list of locations with observations.
     * @param locationString The string representation of the location to find.
     * @return The found location or null if not found.
     */
    private Location findLocationFromString(List<Couple<Location, List<Couple<Observation, Integer>>>> locations,
            String locationString) {
        for (Couple<Location, List<Couple<Observation, Integer>>> locationCouple : locations) {
            if (locationCouple.getLeft().toString().equals(locationString)) {
                return locationCouple.getLeft();
            }
        }
        return null;
    }

    private String selectRandomNode(List<Couple<Location, List<Couple<Observation, Integer>>>> locationObservations) {
        Random random = new Random();
        // Choose a random index, ensuring it's within the list's size range
        int randomIndex = random.nextInt(locationObservations.size());

        // Retrieve the selected location and its string representation
        Location selectedLocation = locationObservations.get(randomIndex).getLeft();
        String selectedLocationString = selectedLocation.toString();

        // If the selected location is not in the node buffer, it becomes the target
        Location targetLocation = selectedLocation;
        String targetLocationString = selectedLocationString;

        // If the selected location is already in the node buffer, find an alternative
        if (this.node_Buffer.contains(selectedLocationString)) {
            for (int i = 0; i < locationObservations.size(); i++) {
                Location alternativeLocation = locationObservations.get(i).getLeft();
                String alternativeLocationString = alternativeLocation.toString();

                if (!this.node_Buffer.contains(alternativeLocationString)) {
                    targetLocation = alternativeLocation;
                    targetLocationString = alternativeLocationString;
                    break;
                }
            }
        }

        // Try moving to the target location
        boolean hasMoved = ((AbstractDedaleAgent) this.myAgent).moveTo(targetLocation);
        int attempt = 1;
        while (!hasMoved && attempt < locationObservations.size()) {
            targetLocation = locationObservations.get(attempt).getLeft();
            hasMoved = ((AbstractDedaleAgent) this.myAgent).moveTo(targetLocation);
            attempt++;
            this.node_Buffer.clear();
        }

        // Return null if unable to move; otherwise, return the string representation of the target location
        return hasMoved ? targetLocationString : null;
    }

    private void receiveMission() {
        MessageTemplate msgTemplate = MessageTemplate.and(MessageTemplate.MatchProtocol("SHARE-PATH"),
                MessageTemplate.MatchPerformative(ACLMessage.INFORM));
        ACLMessage msgReceived = this.myAgent.receive(msgTemplate);

        if (msgReceived != null) {
            List<String> mission;
            try {
                mission = (List<String>) msgReceived.getContentObject();
                System.out.println(this.myAgent.getLocalName() + " - Got a mission! Starting it..");

                this.planned_route = mission;
                this.stop_for_help = false;
                this.stop_patience = 0;
                this.is_working = true;

            } catch (UnreadableException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean GetStopMssg() {
        MessageTemplate msgTemplate = MessageTemplate.and(MessageTemplate.MatchProtocol("STOP"),
                MessageTemplate.MatchPerformative(ACLMessage.INFORM));
        ACLMessage msgReceived = this.myAgent.receive(msgTemplate);

        if (msgReceived != null) {
            this.stop_for_help = true;
            this.stop_patience = 4;
            return true;
        }
        return false;
    }

    private int getBlockingInfo() {
        MessageTemplate msgTemplate = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
        ACLMessage msgReceived = this.myAgent.receive(msgTemplate);
        int backup = 0;
        // 0 = no action, 1 backup, -1 winning

        if (msgReceived != null) {
            String msg_id = msgReceived.getConversationId();
            if (Objects.equals(msg_id, "Blocked")) {
                List<String> agentPath;
                try {
                    agentPath = (List<String>) msgReceived.getContentObject();
                    this.conflict_path = agentPath;
                    int remaining_len = getRemainingRoute().size();
                    // Agent with more nodes to follow is backing off
                    if (remaining_len > agentPath.size()) {
                        backup = 1;
                        // If tied, compare their name strings
                    } else if (remaining_len == agentPath.size()
                            && this.myAgent.getLocalName().compareTo(msgReceived.getSender().getLocalName()) < 0) {
                        backup = 1;
                    } else {
                        backup = -1;
                    }
                } catch (UnreadableException e) {
                    e.printStackTrace();
                }
            }
        }
        return backup;
    }

    private void sendTreasuresRequest(String current_node) {
        List<String> receivers = new ArrayList<>(Arrays.asList("Explo1", "Explo2", "Explo3"));
        List<String> request_nodes = new ArrayList<>(this.potential_treasures);
        request_nodes.add(0, current_node);

        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.setSender(this.myAgent.getAID());
        for (String agentName : receivers) {
            msg.addReceiver(new AID(agentName, AID.ISLOCALNAME));
        }

        try {
            msg.setContentObject((Serializable) request_nodes);
        } catch (IOException e) {
            e.printStackTrace();
        }
        msg.setProtocol("SHARE-POINTS");
        ((AbstractDedaleAgent) this.myAgent).sendMessage(msg);
    }

    private void askExplorerForHelp() {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setSender(this.myAgent.getAID());
        List<String> receivers = new ArrayList<>(Arrays.asList("Explo1", "Explo2", "Explo3"));
        for (String agentName : receivers) {
            msg.addReceiver(new AID(agentName, AID.ISLOCALNAME));
        }
        msg.setContent("NeedHelp");
        msg.setProtocol("HELLO");
        ((AbstractDedaleAgent) this.myAgent).sendMessage(msg);

    }

    private void sendBlockingInfo() {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setSender(this.myAgent.getAID());
        List<String> receivers = new ArrayList<>(Arrays.asList("Tanker1", "Tanker2", "Collect1", "Collect2", "Collect3",
                "Collect4", "Explo1", "Explo2", "Explo3"));
        receivers.remove(this.myAgent.getLocalName());
        for (String agentName : receivers) {
            msg.addReceiver(new AID(agentName, AID.ISLOCALNAME));
        }
        try {
            msg.setContentObject((Serializable) getRemainingRoute());
        } catch (IOException e) {
            e.printStackTrace();
        }
        msg.setConversationId("Blocked");
        ((AbstractDedaleAgent) this.myAgent).sendMessage(msg);
    }

    private void solveDeadLock() {
        sendBlockingInfo();
        int backup = getBlockingInfo();
        if (backup == 1) {
            this.backing_up = true;
        } else if (backup == -1) {
        } else {
            this.conflict_counter += 1;
        }

        if (this.conflict_counter == 20) {
            this.is_working = false;
            this.conflict_counter = 0;
            System.out.println(this.myAgent.getLocalName() + " - is blocked so abandoning its mission");
        }
        return;
    }

    private String backOff(List<Couple<Location, List<Couple<Observation, Integer>>>> lobs) {

        for (int i = 1; i < lobs.size(); i++) {
            Location node = lobs.get(i).getLeft();
            String s_node = node.toString();
            if (!this.conflict_path.contains(s_node) && !Objects.equals(s_node, this.conflict_node)
                    && (((AbstractDedaleAgent) this.myAgent).moveTo(node))) {
                this.conflict_counter = 0;
                this.backing_up = false;
                this.conflict_node = null;
                if (this.mission_step > 1) {
                    this.mission_step -= 1;
                } else {
                    this.planned_route.add(0, lobs.get(0).getLeft().toString());
                }
                this.backoff_wait_t = 3;
                System.out.println(this.myAgent.getLocalName() + " - I successfully backed off! Resuming mission now.");
                return s_node;
            }
        }

        String s_prev_node = null;
        Location prev_node = null;
        boolean moved = false;

        if (this.mission_step == 1) {
            for (int j = 1; j < lobs.size(); j++) {
                Location node = lobs.get(j).getLeft();
                String s_node = node.toString();
                if (((AbstractDedaleAgent) this.myAgent).moveTo(node)) {
                    prev_node = node;
                    s_prev_node = s_node;
                    moved = true;
                    break;
                }
            }
        } else {
            s_prev_node = this.planned_route.get(this.mission_step - 2);
            moved = ((AbstractDedaleAgent) this.myAgent).moveTo(prev_node);
        }

        if (moved) {
            this.conflict_node = lobs.get(0).getLeft().toString();
            if (this.mission_step == 1) {
                this.planned_route.add(0, s_prev_node);
            } else {
                this.mission_step -= 1;
            }
        }
        return s_prev_node;
    }

    private void shareTreasureInfo() {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setSender(this.myAgent.getAID());
        List<String> receivers = new ArrayList<>(
                Arrays.asList("Tanker1", "Tanker2", "Collect1", "Collect2", "Collect3", "Collect4"));
        receivers.remove(this.myAgent.getLocalName());
        for (String agentName : receivers) {
            msg.addReceiver(new AID(agentName, AID.ISLOCALNAME));
        }
        try {
            msg.setContentObject(this.treasure_types);
            msg.setConversationId("Type");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ((AbstractDedaleAgent) this.myAgent).sendMessage(msg);
        try {
            msg.setContentObject(this.treasure_quantity);
            msg.setConversationId("Quant");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ((AbstractDedaleAgent) this.myAgent).sendMessage(msg);
    }

    private void mergeTreasureInfo() {
        MessageTemplate msgTemplate = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
        ACLMessage msgReceived = this.myAgent.receive(msgTemplate);

        if (msgReceived != null) {
            boolean updated = false;
            String msg_id = msgReceived.getConversationId();
            if (Objects.equals(msg_id, "Type")) {
                HashMap<String, String> treasureType;
                try {
                    treasureType = (HashMap<String, String>) msgReceived.getContentObject();

                    for (HashMap.Entry<String, String> set : treasureType.entrySet()) {
                        if (!this.treasure_types.containsKey(set.getKey())) {
                            this.treasure_types.put(set.getKey(), set.getValue());
                            updated = true;
                        }
                    }

                } catch (UnreadableException e) {
                    e.printStackTrace();
                }

            } else if (msg_id == "Quant") {
                HashMap<String, Integer> treasureQuant;

                try {
                    treasureQuant = (HashMap<String, Integer>) msgReceived.getContentObject();

                    for (HashMap.Entry<String, Integer> set : treasureQuant.entrySet()) {

                        if (!this.treasure_quantity.containsKey(set.getKey())) {
                            this.treasure_quantity.put(set.getKey(), set.getValue());
                            updated = true;

                        } else if (this.treasure_quantity.get(set.getKey()) > set.getValue()) {
                            this.treasure_quantity.put(set.getKey(), set.getValue());
                            updated = true;
                        }
                    }

                } catch (UnreadableException e) {
                    e.printStackTrace();
                }
            }
            if (updated) {
            }
        }
    }

    @Override
    public void onTick() {
        if (this.backoff_wait_t > 0) {
            this.backoff_wait_t -= 1;
            return;
        }
        if (this.stop_patience > 0) {
            this.stop_patience -= 1;
        } else if (this.stop_patience == 0) {
            this.stop_for_help = false;
        }
        Location myPosition = ((AbstractDedaleAgent) this.myAgent).getCurrentPosition();
        String current_position = myPosition.toString();

        String s_myPosition = myPosition.toString();

        if (!Objects.equals(s_myPosition, "")) {
            List<Couple<Location, List<Couple<Observation, Integer>>>> lobs = ((AbstractDedaleAgent) this.myAgent)
                    .observe();

            List<Couple<Observation, Integer>> lObservations = lobs.get(0).getRight();

            for (Couple<Observation, Integer> o : lObservations) {
                switch (o.getLeft()) {
                case DIAMOND:
                case GOLD:

                    if (o.getLeft() == ((AbstractDedaleAgent) this.myAgent).getMyTreasureType()) {
                        boolean unlock = ((AbstractDedaleAgent) this.myAgent).openLock(o.getLeft());
                        if (unlock) {
                        }
                    }

                    int picked = ((AbstractDedaleAgent) this.myAgent).pick();
                    if (picked > 0) {
                        System.out.println(this.myAgent.getLocalName() + " - Agent picked: " + picked + " of "
                                + o.getLeft() + " at position " + myPosition);
                    }

                    if (!this.treasure_quantity.containsKey(s_myPosition)) {
                        this.treasure_quantity.put(s_myPosition, o.getRight() - picked);
                        this.treasure_types.put(s_myPosition, o.getLeft().toString());
                    } else {
                        if (this.treasure_quantity.get(s_myPosition) != o.getRight() - picked) {
                            this.treasure_quantity.put(s_myPosition, o.getRight() - picked);
                        }
                    }
                    break;
                default:
                    break;
                }
            }

            List<Couple<Observation, Integer>> backpack_before = ((AbstractDedaleAgent) this.myAgent)
                    .getBackPackFreeSpace();

            // Random choice of the Tanker to contact is implemented. It can be improved if the Tankers inform the
            // Collectors of their position or their free space. However, the latter one makes the system slower

            List<String> tankers = Arrays.asList("Tanker1", "Tanker2");
            Collections.shuffle(tankers); // Randomly shuffle the list

            boolean contacted = false;
            for (String tanker : tankers) {
                contacted = ((AbstractDedaleAgent) this.myAgent).emptyMyBackPack(tanker);
                if (contacted) {
                    break;
                }
            }

            List<Couple<Observation, Integer>> backpack_after = ((AbstractDedaleAgent) this.myAgent)
                    .getBackPackFreeSpace();
            boolean delivered = false;
            for (int i = 0; i < backpack_after.size(); i++) {
                if (backpack_after.get(i).getRight() > backpack_before.get(i).getRight()) {
                    delivered = true;
                }
            }
            if (delivered && contacted) {
                System.out.println(this.myAgent.getLocalName() + " - The agent delivered his treasure. Backpack at "
                        + ((AbstractDedaleAgent) this.myAgent).getBackPackFreeSpace() + " of capacity");
            }

            String next_node = null;
            if (!this.stop_for_help) {
                if (this.backing_up) {
                    next_node = backOff(lobs);
                } else if (this.is_working && !this.backing_up) {
                    next_node = moveToNextNode(lobs);
                } else {
                    next_node = selectRandomNode(lobs);
                }
            }

            if (next_node != null) {
                this.conflict_counter = 0;
                current_position = next_node;
            }

            if (next_node != null && !this.node_Buffer.contains(next_node)) {

                if (this.node_Buffer.size() == this.BUFFER_SIZE) {
                    this.node_Buffer.remove(0);
                }

                this.node_Buffer.add(next_node);
            }

            shareTreasureInfo();
            mergeTreasureInfo();

            updateAllPotentialTreasures();
            if (!this.is_working) {
                if (!this.potential_treasures.isEmpty()) {
                    askExplorerForHelp();
                    if (GetStopMssg()) {
                        sendTreasuresRequest(current_position);
                    }
                }
                receiveMission();
            }
        }
    }
}
