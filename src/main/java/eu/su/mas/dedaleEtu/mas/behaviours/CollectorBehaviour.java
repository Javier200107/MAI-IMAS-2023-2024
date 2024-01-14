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
    private List<String> prevNodes = new ArrayList<>(BUFFER_SIZE);
    private HashMap<String, String> treasureTypes = new HashMap<String, String>();
    private boolean backingUp = false;
    private HashMap<String, Integer> treasureQuantity = new HashMap<String, Integer>();
    private int backoffWaitTime = 0;
    private List<String> potentialTreasures = new ArrayList<>();
    private String conflictLocation = null;
    private boolean isWorking = false;
    private int missionStep = 0;
    private int stopPatience = 0;
    private boolean stopForHelp = false;
    private List<String> plannedRoute = new ArrayList<>();
    private List<String> conflictPath = new ArrayList<>();
    private int conflictCounter = 0;

    public CollectorBehaviour(final AbstractDedaleAgent myagent) {
        super(myagent, TICKER_TIME);
    }

    private List<String> getRemainingRoute() {
        List<String> remaining = new ArrayList<String>();
        for (int i = this.missionStep; i < this.plannedRoute.size(); i++) {
            remaining.add(this.plannedRoute.get(i));
        }
        return remaining;
    }

    /*
     * If collector passes by a treasure of its own type, saves the node to send it to an Explorer and ask for the route
     */
    private void updateAllPotentialTreasures() {
        List<String> treasures = new ArrayList<>();
        for (HashMap.Entry<String, String> node : this.treasureTypes.entrySet()) {
            if (((AbstractDedaleAgent) this.myAgent).getMyTreasureType().toString() == node.getValue()
                    && this.treasureQuantity.get(node.getKey()) > 0) {
                treasures.add(node.getKey());
            }
        }
        this.potentialTreasures = treasures;
    }

    /**
     * Moves to the next node in the planned route.
     *
     * @param observedLocations A list of observed locations and their associated observations.
     * @return The string representation of the next node if the move is successful, otherwise null.
     */
    private String moveToNextNode(List<Couple<Location, List<Couple<Observation, Integer>>>> observedLocations) {
        if (plannedRoute == null || plannedRoute.isEmpty() || missionStep >= plannedRoute.size()) {
            System.out.println(this.myAgent.getLocalName() + " - No goal.");
            resetMission();
            return null;
        }

        String targetNodeString = plannedRoute.get(missionStep);
        Location targetNode = findLocationFromString(observedLocations, targetNodeString);

        if (targetNode == null) {
            System.out.println(this.myAgent.getLocalName() + " - Invalid goal. Abort.");
            resetMission();
            return null;
        }

        if (!moveAgentTo(targetNode)) {
            resolveDeadlock();
            return null;
        }

        missionStep++;
        if (missionStep == plannedRoute.size()) {
            System.out.println(this.myAgent.getLocalName() + " -- Completed goal.");
            resetMission();
        }

        return targetNodeString;
    }

    /**
     * Resets the mission-related variables.
     */
    private void resetMission() {
        isWorking = false;
        missionStep = 0;
        plannedRoute = null;
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
        if (this.prevNodes.contains(selectedLocationString)) {
            for (int i = 0; i < locationObservations.size(); i++) {
                Location alternativeLocation = locationObservations.get(i).getLeft();
                String alternativeLocationString = alternativeLocation.toString();

                if (!this.prevNodes.contains(alternativeLocationString)) {
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
            this.prevNodes.clear();
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
                System.out.println(this.myAgent.getLocalName() + " - Start goal.");

                this.plannedRoute = mission;
                this.stopForHelp = false;
                this.stopPatience = 0;
                this.isWorking = true;

            } catch (UnreadableException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean checkForStopMessage() {
        // Define a message template for the "STOP" protocol
        MessageTemplate stopMessageTemplate = MessageTemplate.and(MessageTemplate.MatchProtocol("STOP"),
                MessageTemplate.MatchPerformative(ACLMessage.INFORM));
        ACLMessage receivedStopMessage = this.myAgent.receive(stopMessageTemplate);

        // Check if a stop message was received
        if (receivedStopMessage != null) {
            this.stopForHelp = true;
            this.stopPatience = 4;
            return true;
        }

        return false;
    }

    private int analyzeBlockingSituation() {
        // Listen for inform messages
        MessageTemplate informMessageTemplate = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
        ACLMessage receivedMessage = this.myAgent.receive(informMessageTemplate);

        // Define the backup status codes
        final int NO_ACTION = 0;
        final int BACKUP = 1;
        final int WINNING = -1;
        int backupStatus = NO_ACTION;

        if (receivedMessage != null) {
            String conversationId = receivedMessage.getConversationId();

            // Process only "Blocked" messages
            if ("Blocked".equals(conversationId)) {
                try {
                    List<String> senderPath = (List<String>) receivedMessage.getContentObject();
                    this.conflictPath = senderPath;
                    int remainingPathLength = getRemainingRoute().size();

                    // Determine backup status based on path length and agent names
                    if (remainingPathLength > senderPath.size()) {
                        backupStatus = BACKUP;
                    } else if (remainingPathLength == senderPath.size()
                            && this.myAgent.getLocalName().compareTo(receivedMessage.getSender().getLocalName()) < 0) {
                        backupStatus = BACKUP;
                    } else {
                        backupStatus = WINNING;
                    }
                } catch (UnreadableException e) {
                    e.printStackTrace();
                }
            }
        }
        return backupStatus;
    }

    private void dispatchTreasureLocationsRequest(String currentLocation) {
        List<String> explorerAgents = Arrays.asList("Explo1", "Explo2", "Explo3");
        List<String> treasureLocations = new ArrayList<>(this.potentialTreasures);
        treasureLocations.add(0, currentLocation);

        ACLMessage treasureRequestMessage = new ACLMessage(ACLMessage.REQUEST);
        treasureRequestMessage.setSender(this.myAgent.getAID());
        treasureRequestMessage.setProtocol("SHARE-POINTS");

        // Add explorer agents as message receivers
        for (String agentName : explorerAgents) {
            treasureRequestMessage.addReceiver(new AID(agentName, AID.ISLOCALNAME));
        }

        // Set the content of the message
        try {
            treasureRequestMessage.setContentObject((Serializable) treasureLocations);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Send the message
        ((AbstractDedaleAgent) this.myAgent).sendMessage(treasureRequestMessage);
    }

    private void requestAssistanceFromExplorers() {
        // Create a new INFORM message
        ACLMessage helpRequestMessage = new ACLMessage(ACLMessage.INFORM);
        helpRequestMessage.setSender(this.myAgent.getAID());
        helpRequestMessage.setProtocol("HELLO");
        helpRequestMessage.setContent("NeedHelp");

        // Define the list of explorer agents to receive the message
        List<String> explorerAgents = Arrays.asList("Explo1", "Explo2", "Explo3");
        for (String agentName : explorerAgents) {
            helpRequestMessage.addReceiver(new AID(agentName, AID.ISLOCALNAME));
        }

        // Send the message
        ((AbstractDedaleAgent) this.myAgent).sendMessage(helpRequestMessage);
    }

    private void broadcastBlockingInformation() {
        // Create a new INFORM message
        ACLMessage blockingInfoMessage = new ACLMessage(ACLMessage.INFORM);
        blockingInfoMessage.setSender(this.myAgent.getAID());
        blockingInfoMessage.setConversationId("Blocked");

        // List of all potential receiver agents
        List<String> allReceivers = Arrays.asList("Tanker1", "Tanker2", "Collect1", "Collect2", "Collect3", "Collect4",
                "Explo1", "Explo2", "Explo3");

        // Remove the current agent from the receivers list
        List<String> receivers = new ArrayList<>(allReceivers);
        receivers.remove(this.myAgent.getLocalName());

        // Add each receiver to the message
        for (String agentName : receivers) {
            blockingInfoMessage.addReceiver(new AID(agentName, AID.ISLOCALNAME));
        }

        // Set the content of the message
        try {
            blockingInfoMessage.setContentObject((Serializable) getRemainingRoute());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Send the message
        ((AbstractDedaleAgent) this.myAgent).sendMessage(blockingInfoMessage);
    }

    private void resolveDeadlock() {
        // Broadcast current blocking situation
        broadcastBlockingInformation();

        // Analyze the deadlock situation and determine the course of action
        int actionRequired = analyzeBlockingSituation();
        if (actionRequired == 1) {
            this.backingUp = true;
        } else if (actionRequired == -1) {
            // Handle the winning situation (no specific action here)
        } else {
            // Increment conflict counter in case of a stalemate
            this.conflictCounter += 1;
        }

        // Check if the deadlock has persisted for too long
        if (this.conflictCounter == 20) {
            this.isWorking = false;
            this.conflictCounter = 0;
            System.out.println(this.myAgent.getLocalName() + " - agent blocked");
        }
    }

    private String retreatFromConflict(List<Couple<Location, List<Couple<Observation, Integer>>>> observedLocations) {
        // Attempt to move to a non-conflicting location
        for (int i = 1; i < observedLocations.size(); i++) {
            Location alternativeLocation = observedLocations.get(i).getLeft();
            String locationString = alternativeLocation.toString();
            if (!this.conflictPath.contains(locationString) && !Objects.equals(locationString, this.conflictLocation)
                    && ((AbstractDedaleAgent) this.myAgent).moveTo(alternativeLocation)) {

                resetConflictStatus();
                updateMissionAfterRetreat(observedLocations, i);
                System.out.println(this.myAgent.getLocalName() + " - Restart goal.");
                return locationString;
            }
        }

        // Backup plan: either move back along the planned route or to a new location
        return handleNoImmediateRetreatOption(observedLocations);
    }

    private void resetConflictStatus() {
        this.conflictCounter = 0;
        this.backingUp = false;
        this.conflictLocation = null;
        this.backoffWaitTime = 3;
    }

    private void updateMissionAfterRetreat(List<Couple<Location, List<Couple<Observation, Integer>>>> observedLocations,
            int retreatIndex) {
        if (this.missionStep > 1) {
            this.missionStep -= 1;
        } else {
            this.plannedRoute.add(0, observedLocations.get(0).getLeft().toString());
        }
    }

    private String handleNoImmediateRetreatOption(
            List<Couple<Location, List<Couple<Observation, Integer>>>> observedLocations) {
        Location previousLocation = null;
        String previousLocationString = null;
        boolean hasMoved = false;

        if (this.missionStep == 1) {
            // Try moving to a different location
            for (int j = 1; j < observedLocations.size(); j++) {
                Location location = observedLocations.get(j).getLeft();
                String locationString = location.toString();
                if (((AbstractDedaleAgent) this.myAgent).moveTo(location)) {
                    previousLocation = location;
                    previousLocationString = locationString;
                    hasMoved = true;
                    break;
                }
            }
        } else {
            // Move back along the planned route
            previousLocationString = this.plannedRoute.get(this.missionStep - 2);
            hasMoved = ((AbstractDedaleAgent) this.myAgent).moveTo(previousLocation);
        }

        if (hasMoved) {
            handleSuccessfulMove(observedLocations, previousLocationString);
        }
        return previousLocationString;
    }

    private void handleSuccessfulMove(List<Couple<Location, List<Couple<Observation, Integer>>>> observedLocations,
            String locationString) {
        this.conflictLocation = observedLocations.get(0).getLeft().toString();
        if (this.missionStep == 1) {
            this.plannedRoute.add(0, locationString);
        } else {
            this.missionStep -= 1;
        }
    }

    private void distributeTreasureInformation() {
        // Convert array to a modifiable list
        List<String> treasureInfoReceivers = new ArrayList<>(
                Arrays.asList("Tanker1", "Tanker2", "Collect1", "Collect2", "Collect3", "Collect4"));

        // Remove the current agent's name from the list
        treasureInfoReceivers.remove(this.myAgent.getLocalName());

        // Share treasure types information
        shareSpecificTreasureInfo(treasureInfoReceivers, this.treasureTypes, "Type");

        // Share treasure quantity information
        shareSpecificTreasureInfo(treasureInfoReceivers, this.treasureQuantity, "Quant");
    }

    private void shareSpecificTreasureInfo(List<String> receivers, Serializable treasureInfo, String conversationId) {
        ACLMessage treasureInfoMessage = new ACLMessage(ACLMessage.INFORM);
        treasureInfoMessage.setSender(this.myAgent.getAID());
        treasureInfoMessage.setConversationId(conversationId);

        // Add receivers to the message
        for (String agentName : receivers) {
            treasureInfoMessage.addReceiver(new AID(agentName, AID.ISLOCALNAME));
        }

        // Set the content of the message
        try {
            treasureInfoMessage.setContentObject(treasureInfo);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Send the message
        ((AbstractDedaleAgent) this.myAgent).sendMessage(treasureInfoMessage);
    }

    private void integrateTreasureInformation() {
        MessageTemplate informMessageTemplate = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
        ACLMessage receivedMessage = this.myAgent.receive(informMessageTemplate);

        if (receivedMessage != null) {
            String messageId = receivedMessage.getConversationId();
            boolean updated = false;

            if ("Type".equals(messageId)) {
                updated = updateTreasureTypes(receivedMessage);
            } else if ("Quant".equals(messageId)) {
                updated = updateTreasureQuantities(receivedMessage);
            }

            // Perform additional actions if the information was updated
            if (updated) {
                // Actions to perform when new information is integrated
            }
        }
    }

    private boolean updateTreasureTypes(ACLMessage message) {
        try {
            HashMap<String, String> receivedTreasureTypes = (HashMap<String, String>) message.getContentObject();
            for (Map.Entry<String, String> entry : receivedTreasureTypes.entrySet()) {
                if (!this.treasureTypes.containsKey(entry.getKey())) {
                    this.treasureTypes.put(entry.getKey(), entry.getValue());
                    return true;
                }
            }
        } catch (UnreadableException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean updateTreasureQuantities(ACLMessage message) {
        try {
            HashMap<String, Integer> receivedTreasureQuantities = (HashMap<String, Integer>) message.getContentObject();
            for (Map.Entry<String, Integer> entry : receivedTreasureQuantities.entrySet()) {
                Integer currentQuantity = this.treasureQuantity.get(entry.getKey());
                if (currentQuantity == null || currentQuantity > entry.getValue()) {
                    this.treasureQuantity.put(entry.getKey(), entry.getValue());
                    return true;
                }
            }
        } catch (UnreadableException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void onTick() {

        checkBackoff();

        Location myPosition = ((AbstractDedaleAgent) this.myAgent).getCurrentPosition();
        String thisPosition = myPosition.toString();

        String s_myPosition = myPosition.toString();

        if (!Objects.equals(s_myPosition, "")) {
            List<Couple<Location, List<Couple<Observation, Integer>>>> lobs = ((AbstractDedaleAgent) this.myAgent)
                    .observe();

            List<Couple<Observation, Integer>> lObservations = lobs.get(0).getRight();

            updateTreasureInformation(lObservations, s_myPosition, myPosition.toString());

            emptyMyBackPack();

            updateAllPotentialTreasures(lobs, thisPosition);

            distributeTreasureInformation();
            integrateTreasureInformation();

            updateAllPotentialTreasures();

            checkForMissionRequest(thisPosition);

        }
    }

    private void updateTreasureInformation(List<Couple<Observation, Integer>> lObservations, String s_myPosition,
            String myPosition) {
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
                    System.out.println(this.myAgent.getLocalName() + " - Pick: " + picked + " on " + o.getLeft()
                            + ". Position= " + myPosition);
                }

                if (!this.treasureQuantity.containsKey(s_myPosition)) {
                    this.treasureQuantity.put(s_myPosition, o.getRight() - picked);
                    this.treasureTypes.put(s_myPosition, o.getLeft().toString());
                } else {
                    if (this.treasureQuantity.get(s_myPosition) != o.getRight() - picked) {
                        this.treasureQuantity.put(s_myPosition, o.getRight() - picked);
                    }
                }
                break;
            default:
                break;
            }
        }
    }

    private void emptyMyBackPack() {
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

        List<Couple<Observation, Integer>> backpack_after = ((AbstractDedaleAgent) this.myAgent).getBackPackFreeSpace();
        boolean delivered = false;
        for (int i = 0; i < backpack_after.size(); i++) {
            if (backpack_after.get(i).getRight() > backpack_before.get(i).getRight()) {
                delivered = true;
            }
        }
        if (delivered && contacted) {
            System.out.println(this.myAgent.getLocalName() + " - Agent deliverd. Backpack after: "
                    + ((AbstractDedaleAgent) this.myAgent).getBackPackFreeSpace() + " of capacity");
        }
    }

    private void updateAllPotentialTreasures(List<Couple<Location, List<Couple<Observation, Integer>>>> lobs,
            String thisPosition) {
        String next_node = null;
        if (!this.stopForHelp) {
            if (this.backingUp) {
                next_node = retreatFromConflict(lobs);
            } else if (this.isWorking && !this.backingUp) {
                next_node = moveToNextNode(lobs);
            } else {
                next_node = selectRandomNode(lobs);
            }
        }

        if (next_node != null) {
            this.conflictCounter = 0;
            thisPosition = next_node;
        }

        if (next_node != null && !this.prevNodes.contains(next_node)) {

            if (this.prevNodes.size() == this.BUFFER_SIZE) {
                this.prevNodes.remove(0);
            }

            this.prevNodes.add(next_node);
        }
    }

    private void checkForMissionRequest(String thisPosition) {
        if (!this.isWorking) {
            if (!this.potentialTreasures.isEmpty()) {
                requestAssistanceFromExplorers();
                if (checkForStopMessage()) {
                    dispatchTreasureLocationsRequest(thisPosition);
                }
            }
            receiveMission();
        }
    }

    private void checkBackoff() {
        if (this.backoffWaitTime > 0) {
            this.backoffWaitTime -= 1;
            return;
        }
        if (this.stopPatience > 0) {
            this.stopPatience -= 1;
        } else if (this.stopPatience == 0) {
            this.stopForHelp = false;
        }
    }

}
