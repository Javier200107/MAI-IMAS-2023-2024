package eu.su.mas.dedaleEtu.mas.behaviours;

import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;
import jade.core.Agent;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import jade.core.behaviours.TickerBehaviour;

import java.util.List;
import java.util.Random;

public class CollectorBehaviour extends TickerBehaviour {
    private Location current_position;
    private final AbstractDedaleAgent agent;
    private final String agentName;

    public CollectorBehaviour(Agent a) {
        super(a, 600);
        this.agent = (AbstractDedaleAgent) this.myAgent;
        this.agentName = this.agent.getLocalName();
        this.current_position = this.agent.getCurrentPosition();
    }

    @Override
    protected void onTick() {
        this.current_position = agent.getCurrentPosition();

        if (this.current_position != null && !this.current_position.getLocationId().isEmpty()){

            System.out.println(agentName + " -- my current position is: " + this.current_position);
            System.out.println(agentName + " - My treasure type is : "+ agent.getMyTreasureType());
            System.out.println(agentName+ " - My current backpack capacity is:"+ agent.getBackPackFreeSpace());

            List<Couple<Location, List<Couple<Observation, Integer>>>> lobs = agent.observe();
            System.out.println(agentName + " -- list of observables: " + lobs);

            //List of observations associated to the currentPosition
            List<Couple<Observation,Integer>> lCurrentObservations= lobs.get(0).getRight();
            //TODO: check what happens if lock already unlocked
            int pickedQuantity = 0;
            for(Couple<Observation, Integer> o:lCurrentObservations){
                Observation treasureType = o.getLeft();
                switch (treasureType) {
                    case DIAMOND:case GOLD:
                        boolean open = agent.openLock(treasureType);
                        System.out.println(" - I try to open the safe: "+ open);
                        System.out.println(agentName + " - Value of the treasure on the current position: "+treasureType +": "+ o.getRight());

                        if (open) {
                            pickedQuantity = agent.pick();
                        }

                        break;
                    default:
                        break;
                }
            }
            if (pickedQuantity > 0) {
                System.out.println(agentName + " - The agent grabbed :"+ pickedQuantity);
                System.out.println(agentName + " - the remaining backpack capacity is: "+ agent.getBackPackFreeSpace());
                // List<Couple<Location,List<Couple<Observation,Integer>>>> lobs2 = agent.observe();
                // System.out.println("State of the observations after picking " + lobs2);

                // Try to store picked treasure in the tankers
                System.out.println(agentName + " - The agent tries to transfer its load to Tanker1 (if reachable); success ? : "+ agent.emptyMyBackPack("Tanker1"));
                System.out.println(agentName + " - The agent tries to transfer its load to Tanker2 (if reachable); success ? : "+ agent.emptyMyBackPack("Tanker2"));
                System.out.println(agentName + " - The agent tries to transfer its load to Tanker3 (if reachable); success ? : "+ agent.emptyMyBackPack("Tanker3"));
                System.out.println(agentName + " - My current backpack capacity is:"+ agent.getBackPackFreeSpace());
            }
            //Random move from the current position
            Random r= new Random();
            int moveId=1+r.nextInt(lobs.size()-1);//removing the current position from the list of target to accelerate the tests, but not necessary as to stay is an action

            //The move action (if any) should be the last action of your behaviour
            agent.moveTo(lobs.get(moveId).getLeft());
        }
    }
}


