package eu.su.mas.dedaleEtu.mas.behaviours;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedaleEtu.princ.Utils;

import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;

import jade.core.behaviours.TickerBehaviour;

import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.core.Agent;

public class SharePath extends TickerBehaviour {
    /**
     * When an agent choose to move
     *
     */
    private static final long serialVersionUID = 9088209402507795289L;

    private MapRepresentation myMap;

    ArrayList<String> lastSender;

    public SharePath(Agent myagent, MapRepresentation myMap) {
        super(myagent, 300);
        this.myMap = myMap;
        this.myAgent = myagent;

    }

    @Override
    public void onTick() {

        // Receiving a handshake message with the keyword "HELLO"
        ArrayList<String> handshakeMessage = receiveStringMessage("HELLO");
        if (handshakeMessage != null) {
            // Store the sender of the last received message
            this.lastSender = new ArrayList<>(Arrays.asList(handshakeMessage.get(1)));
            // Responding to the handshake message
            sendStringMessage(this.lastSender, "I'm Here", "STOP");
        }

        // Listening for a path request message with specific format and type
        ArrayList<String> requestedPoints = (ArrayList<String>) receiveObjectMessage("SHARE-POINTS",
                ACLMessage.REQUEST);
        if (requestedPoints != null) {
            List<String> shortestTreasurePath = new ArrayList<>();
            List<String> currentPath = new ArrayList<>();
            Integer minimumPathSize = 500; // Initial large value for comparison
            // Iterating through all requested points to find the shortest path
            for (Integer i = 0; i < requestedPoints.size() - 1; i++) {
                try {
                    // Getting the shortest path between two points
                    currentPath = this.myMap.getShortestPath(requestedPoints.get(0), requestedPoints.get(i + 1));
                    // Updating the shortest path if a shorter one is found
                    if (minimumPathSize >= currentPath.size()) {
                        shortestTreasurePath = currentPath;
                        minimumPathSize = currentPath.size();
                    }
                } catch (Exception e) {
                    // Logging in case path calculation fails
                    System.out.println(this.myAgent.getLocalName() + " - I could not find a solution for the path: "
                            + requestedPoints.get(0) + ":" + requestedPoints.get(i + 1));
                }
            }

            // Sending the shortest path to the requesting agent
            if (!shortestTreasurePath.isEmpty()) {
                sendObjectMessage(this.lastSender, shortestTreasurePath, ACLMessage.INFORM);
            }
        }
    }

    /**
     * Sends a string message to multiple receivers using a specified protocol.
     *
     * @param receivers The list of receiver agent names.
     * @param message   The message content to be sent.
     * @param protocol  The communication protocol used for the message.
     */
    private void sendStringMessage(ArrayList<String> receivers, String message, String protocol) {
        // Creating a new ACL message with the INFORM performative
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        // Setting the protocol of the message
        msg.setProtocol(protocol);
        // Setting the sender of the message
        msg.setSender(this.myAgent.getAID());

        // Adding each receiver to the message
        for (String agentName : receivers) {
            msg.addReceiver(new AID(agentName, AID.ISLOCALNAME));
        }
        // Setting the content of the message
        msg.setContent(message);
        // Sending the message through the agent's communication interface
        ((AbstractDedaleAgent) this.myAgent).sendMessage(msg);
    }

    /**
     * Receives a string message that matches a specific protocol.
     *
     * @param protocol The communication protocol to match in the received messages.
     * @return An ArrayList containing the message content and the sender's local name, or null if no message is
     *         received.
     */
    private ArrayList<String> receiveStringMessage(String protocol) {
        // Creating a message template to filter messages based on protocol and performative
        MessageTemplate msgTemplate = MessageTemplate.and(MessageTemplate.MatchProtocol(protocol),
                MessageTemplate.MatchPerformative(ACLMessage.INFORM));

        // Receiving the message that fits the template
        ACLMessage receivedMessage = this.myAgent.receive(msgTemplate);

        // Checking if a message was received
        if (receivedMessage != null) {
            // Extracting the content of the message
            String messageContent = receivedMessage.getContent();
            // Preparing the result with the message content and sender's local name
            ArrayList<String> result = new ArrayList<>(
                    Arrays.asList(messageContent, receivedMessage.getSender().getLocalName()));
            return result;
        }
        // Return null if no message is received
        return null;
    }

    /**
     * Receives an object message that matches a specific protocol and performative.
     *
     * @param protocol     The communication protocol to match in the received messages.
     * @param performative The performative type of the message to be received.
     * @return An Object which is typically an ArrayList of Lists, or null if no message is received.
     */
    private Object receiveObjectMessage(String protocol, Object performative) {
        // Creating a message template to filter messages based on protocol and performative
        MessageTemplate msgTemplate = MessageTemplate.and(MessageTemplate.MatchProtocol(protocol),
                MessageTemplate.MatchPerformative((Integer) performative));

        // Receiving the message that fits the template
        ACLMessage receivedMessage = this.myAgent.receive(msgTemplate);

        // Variable to hold the paths received
        ArrayList<List> receivedPaths = null;

        // Checking if a message was received
        if (receivedMessage != null) {
            try {
                // Attempting to retrieve the object from the message
                receivedPaths = (ArrayList<List>) receivedMessage.getContentObject();
            } catch (UnreadableException e) {
                // Printing the stack trace in case of an exception
                e.printStackTrace();
            }
            return receivedPaths;
        }
        // Return null if no message is received
        return null;
    }

    /**
     * Sends an object message to multiple receivers using a specified performative.
     *
     * @param receivers    The list of receiver agent names.
     * @param message      The object message content to be sent.
     * @param performative The performative type of the message.
     */
    private void sendObjectMessage(ArrayList<String> receivers, Object message, Object performative) {
        // Creating a new ACL message with a specified performative
        ACLMessage msg = new ACLMessage((Integer) performative);
        // Setting the protocol of the message
        msg.setProtocol("SHARE-PATH");
        // Setting the sender of the message
        msg.setSender(this.myAgent.getAID());

        // Adding each receiver to the message
        for (String agentName : receivers) {
            msg.addReceiver(new AID(agentName, AID.ISLOCALNAME));
        }

        try {
            // Setting the object content of the message
            msg.setContentObject((Serializable) message);
        } catch (IOException e) {
            // Throwing a runtime exception in case of an IOException
            throw new RuntimeException(e);
        }

        // Sending the message through the agent's communication interface
        ((AbstractDedaleAgent) this.myAgent).sendMessage(msg);
    }

}