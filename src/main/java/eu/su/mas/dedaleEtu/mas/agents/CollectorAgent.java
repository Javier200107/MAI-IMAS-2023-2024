package eu.su.mas.dedaleEtu.mas.agents;

import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedale.mas.agent.behaviours.platformManagment.startMyBehaviours;

import eu.su.mas.dedaleEtu.mas.behaviours.CollectorBehaviour;
import java.util.ArrayList;
import java.util.List;

import jade.core.behaviours.Behaviour;

public class CollectorAgent extends AbstractDedaleAgent {

    /**
     *
     */
    private static final long serialVersionUID = -1784844593772918359L;


    /**
     * This method is automatically called when "agent".start() is executed.
     * Consider that Agent is launched for the first time.
     * 			1) set the agent attributes
     *	 		2) add the behaviours
     *
     */
    protected void setup(){

        super.setup();

        List<Behaviour> l_behaviours = new ArrayList<Behaviour>();
        l_behaviours.add(new CollectorBehaviour(this));

        addBehaviour(new startMyBehaviours(this, l_behaviours));

        System.out.println("Agent "+this.getLocalName()+ " created");

    }
}
