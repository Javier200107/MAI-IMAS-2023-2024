package eu.su.mas.dedaleEtu.mas.behaviours;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import eu.su.mas.dedale.mas.AbstractDedaleAgent;


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

    ArrayList<String> last_sender;


    public SharePath (Agent myagent, MapRepresentation myMap) {
        super(myagent, 300);
        this.myMap=myMap;
        this.myAgent=myagent;

    }

    @Override
    public void onTick() {
    	return;
    }

    /**********************************************************
     * 	           SENDING AND RECEIVING MESSAGES
     *    @param Receivers
     *    @param message
     **********************************************************/
    private void SendStringMessage(ArrayList<String> Receivers, String message, String protocol) {
        return;
    }

    /*********************************************************
     * 	           RECEIVING MESSAGES
     * @return message
     *******************************************************/


    private ArrayList<String> ReceiveStringMessage(String protocol) {
    	return new ArrayList<String>();
    }

    /*********************************************************
     * 	           RECEIVING OBJECTS
     * @return message
     *******************************************************/
    private Object ReceiveObjectMessage(String protocol, Object performative) {
        // Receive path from explorer
        MessageTemplate msgTemplate = MessageTemplate.and(
                MessageTemplate.MatchProtocol(protocol),
                MessageTemplate.MatchPerformative((Integer) performative));
        ACLMessage msgReceived = this.myAgent.receive(msgTemplate);
        ArrayList<List> paths = null;
        if (msgReceived != null) {
            try {
                paths = (ArrayList<List>) msgReceived.getContentObject();
            } catch (UnreadableException e) {
                e.printStackTrace();
            }
            return paths;
        }
        return null;
    }

    /*********************************************************
     * 	           SENDING OBJECTS
     * @return message
     *******************************************************/
    private void SendObjectMessage(ArrayList<String> Receivers, Object message, Object performative) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setProtocol("SHARE-PATH");
        msg.setPerformative((Integer) performative);
        msg.setSender(this.myAgent.getAID());
        for (String agentName : Receivers) {
            msg.addReceiver(new AID(agentName,AID.ISLOCALNAME));
        }
        try {
            msg.setContentObject((Serializable) message);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ((AbstractDedaleAgent)this.myAgent).sendMessage(msg);
    }
}