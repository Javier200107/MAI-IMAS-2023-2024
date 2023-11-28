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

    public CollectorBehaviour(Agent a) {
        super(a, 600);
        this.current_position = ((AbstractDedaleAgent) this.myAgent).getCurrentPosition();
    }

    @Override
    protected void onTick() {

        this.current_position = ((AbstractDedaleAgent) this.myAgent).getCurrentPosition();
        if (this.current_position != null && !this.current_position.getLocationId().isEmpty()){
            String agentName = this.myAgent.getLocalName();
            AbstractDedaleAgent agent = (AbstractDedaleAgent) this.myAgent;

            System.out.println(agentName + " -- my current position is: " + this.current_position);
            System.out.println(agentName + " - My treasure type is : "+ agent.getMyTreasureType());
            System.out.println(agentName+ " - My current backpack capacity is:"+ agent.getBackPackFreeSpace());

            List<Couple<Location, List<Couple<Observation, Integer>>>> lobs = agent.observe();
            System.out.println(agentName + " -- list of observables: " + lobs);

            //List of observations associated to the currentPosition
            List<Couple<Observation,Integer>> lCurrentObservations= lobs.get(0).getRight();

            for(Couple<Observation, Integer> o:lCurrentObservations){
                System.out.println("o " + o);
                Observation treasureType = o.getLeft();
                int pickedQuantity = 0;
                switch (treasureType) {
                    case DIAMOND:case GOLD:
                        boolean open = false;
                        // Right now, the agent only opens the lock if the treasure type is the same as its own
                        if (treasureType == agent.getMyTreasureType()) {
                            // TODO: check value of open if it is already open
                            open = agent.openLock(treasureType);
                            System.out.println(" - I try to open the safe: "+ open);
                        }else{
                            System.out.println(" - I don't open the safe because the treasure type is not the same as mine");
                        }
                        System.out.println(agentName + " - Value of the treasure on the current position: "+treasureType +": "+ o.getRight());

                        if (open) {
                            pickedQuantity = agent.pick();
                        }

                        break;
                    default:
                        break;
                }
                if (pickedQuantity > 0) {
                    System.out.println(agentName + " - The agent grabbed :"+ pickedQuantity);
                    System.out.println(agentName + " - the remaining backpack capacity is: "+ agent.getBackPackFreeSpace());
                    List<Couple<Location,List<Couple<Observation,Integer>>>> lobs2 = agent.observe();
                    System.out.println("State of the observations after picking " + lobs2);
                }
                //Random move from the current position
                Random r= new Random();
                int moveId=1+r.nextInt(lobs.size()-1);//removing the current position from the list of target to accelerate the tests, but not necessary as to stay is an action

                //The move action (if any) should be the last action of your behaviour
                agent.moveTo(lobs.get(moveId).getLeft());
            }
        }
    }
}


