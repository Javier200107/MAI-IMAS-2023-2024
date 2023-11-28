package eu.su.mas.dedaleEtu.mas.behaviours;


import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.List;

public class TankerBehaviour extends TickerBehaviour {
    /**
     * When an agent choose to migrate all its components should be serializable
     */
    private static final long serialVersionUID = 9088209402507795289L;
    private final AbstractDedaleAgent agent;
    private final String agentName;

    public TankerBehaviour(final AbstractDedaleAgent myagent) {
        super(myagent, 600);
        this.agent = (AbstractDedaleAgent) this.myAgent;
        this.agentName = this.agent.getLocalName();
    }

    @Override
    public void onTick() {
        //Example to retrieve the current position
        Location myPosition = ((AbstractDedaleAgent) this.myAgent).getCurrentPosition();

        if (myPosition != null) {
            //List of observable from the agent's current position
            List<Couple<Location, List<Couple<Observation, Integer>>>> lobs = agent.observe();//myPosition
            System.out.println(agentName + " -- list of observables: " + lobs);
            System.out.println(agentName + " -- My current backpack capacity is:"+ agent.getBackPackFreeSpace());

        }

        // At each time step, the agent receives information about status of other agents
        // and the treasure they have collected

        // 1) receive the message
        // Dont filter the message, receive everything
        MessageTemplate msgTemplate = MessageTemplate.and(
                MessageTemplate.MatchProtocol("INFORM-TANKERS"),
                MessageTemplate.MatchPerformative(ACLMessage.INFORM));
        ACLMessage msg = agent.receive(msgTemplate);
        // Print the message
        // System.out.println("Try to receive");
        if (msg != null) {
            System.out.println(agentName + " -- received message: " + msg.getContent());
        }

    }
}