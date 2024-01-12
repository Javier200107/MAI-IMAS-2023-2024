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

    private void SendStringMessage(ArrayList<String> Receivers, String message, String protocol) {
        return;
    }


    private ArrayList<String> ReceiveStringMessage(String protocol) {
    	return new ArrayList<String>();
    }

    private Object ReceiveObjectMessage(String protocol, Object performative) {
        return new Object();
    }

    private void SendObjectMessage(ArrayList<String> Receivers, Object message, Object performative) {
        return;
    }
}