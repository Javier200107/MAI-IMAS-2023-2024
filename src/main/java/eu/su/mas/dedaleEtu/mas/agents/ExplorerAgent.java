package eu.su.mas.dedaleEtu.mas.agents;

import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedale.mas.agent.behaviours.platformManagment.*;
import eu.su.mas.dedale.mas.agents.dedaleDummyAgents.DummyCollectorAgent;
import eu.su.mas.dedaleEtu.mas.behaviours.RandomWalkBehaviour;
import eu.su.mas.dedaleEtu.mas.behaviours.SayHelloBehaviour;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;

import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;

/**
 * This dummy collector moves randomly, tries all its methods at each time step, store the treasure that match is treasureType
 * in its backpack and intends to empty its backPack in the Tanker agent. @see {@link eu.su.mas.dedaleEtu.mas.behaviours.RandomWalkBehaviour}
 *
 * @author hc
 *
 */
public class ExplorerAgent extends AbstractDedaleAgent{
    /**
     * This method is automatically called when "agent".start() is executed.
     * Consider that Agent is launched for the first time.
     * 			1) set the agent attributes
     *	 		2) add the behaviours
     *
     */
    protected void setup(){

        super.setup();
        
        MapRepresentation map = new MapRepresentation();

        List<Behaviour> lb=new ArrayList<Behaviour>();
        lb.add(new SayHelloBehaviour(this));
        lb.add(new RandomWalkBehaviour(this));

        addBehaviour(new startMyBehaviours(this,lb));


        System.out.println("the  agent "+this.getLocalName()+ " is started");

    }

    /**
     * This method is automatically called after doDelete()
     */
    protected void takeDown(){

    }

}