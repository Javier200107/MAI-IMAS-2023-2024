package eu.su.mas.dedaleEtu.mas.agents;

import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedale.mas.agent.behaviours.platformManagment.*;
import eu.su.mas.dedale.mas.agents.dedaleDummyAgents.DummyCollectorAgent;
import eu.su.mas.dedaleEtu.mas.behaviours.CollectorBehaviour;
import eu.su.mas.dedaleEtu.mas.behaviours.InformTankerBehaviour;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import eu.su.mas.dedaleEtu.mas.behaviours.RandomWalkBehaviour;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;

/**
 * This dummy collector moves randomly, tries all its methods at each time step, store the treasure that match is treasureType
 * in its backpack and intends to empty its backPack in the Tanker agent. @see {@link eu.su.mas.dedaleEtu.mas.behaviours.RandomWalkBehaviour}
 *
 * @author hc
 *
 */
public class CollectorAgent extends AbstractDedaleAgent{

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

        List<Behaviour> lb=new ArrayList<Behaviour>();
        lb.add(new CollectorBehaviour(this));
        lb.add(new InformTankerBehaviour(this));
        //lb.add(new RandomWalkExchangeBehaviour(this));

        addBehaviour(new startMyBehaviours(this,lb));


        System.out.println("the  agent "+this.getLocalName()+ " is started");

    }

    /**
     * This method is automatically called after doDelete()
     */
    protected void takeDown(){

    }

}