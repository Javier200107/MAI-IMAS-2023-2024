package eu.su.mas.dedaleEtu.mas.behaviours;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import dataStructures.serializableGraph.SerializableSimpleGraph;
import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedale.env.gs.gsLocation;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;

import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation.MapAttribute;
import eu.su.mas.dedaleEtu.mas.knowledge.MapRepresentation;
import eu.su.mas.dedaleEtu.mas.behaviours.ShareMapBehaviour;


import jade.core.AID;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;


/**
 * <pre>
 * This behaviour allows an agent to explore the environment and learn the associated topological map.
 * The algorithm is a pseudo - DFS computationally consuming because its not optimised at all.
 * 
 * When all the nodes around him are visited, the agent randomly select an open node and go there to restart its dfs. 
 * This (non optimal) behaviour is done until all nodes are explored. 
 * 
 * Warning, this behaviour does not save the content of visited nodes, only the topology.
 * Warning, the sub-behaviour ShareMap periodically share the whole map
 * </pre>
 * @author hc
 *
 */
public class ExploCoopBehaviour extends SimpleBehaviour {

	private static final long serialVersionUID = 8567689731496787661L;

	private boolean finished = false;

	private boolean explored = false;

	/**
	 * Current knowledge of the agent regarding the environment
	 */
	private MapRepresentation myMap;

	private List<String> list_agentNames;


    private static final int TICK_TIME = 300;
    private int blocked_counter = 0;
    private int random_tmp_steps = 0;

    private static final int BUFFER_SIZE = 8;
    private List<String> nodeBuffer = new ArrayList<>(BUFFER_SIZE);


	/**
	 *
	 * @param myagent
	 * @param myMap known map of the world the agent is living in
	 * @param agentNames name of the agents to share the map with
	 */
	public ExploCoopBehaviour(final AbstractDedaleAgent myagent, MapRepresentation myMap,List<String> agentNames) {
		super(myagent);
		this.myMap=myMap;
		this.list_agentNames=agentNames;
	}

    private void tmpRandomMovement(List<Couple<Location,List<Couple<Observation,Integer>>>> lobs){
        Location next_node = moveToNextNodeRandom(lobs);

        if (next_node != null && !this.nodeBuffer.contains(next_node)){

            if (this.nodeBuffer.size() == BUFFER_SIZE){
                this.nodeBuffer.remove(0);
            }

            this.nodeBuffer.add(next_node.getLocationId());
        }
        this.random_tmp_steps -= 1;
        if (this.random_tmp_steps == 0){
            System.out.println(this.myAgent.getLocalName() + " - Finished tmp random walk.");
        }
    }

    private Location moveToNextNodeRandom(List<Couple<Location,List<Couple<Observation,Integer>>>> lobs){
        Random r= new Random();
        int moveId=1+r.nextInt(lobs.size()-1);
        Location next_node = lobs.get(moveId).getLeft();
        Location goal_node = next_node;

        if (!this.nodeBuffer.contains(next_node)){
            goal_node = next_node;
        } else {
            for (int i = 1; i < lobs.size(); i++) {
                next_node = lobs.get(i).getLeft();
                if (!this.nodeBuffer.contains(next_node)){
                    goal_node = next_node;
                    break;
                }
            }
        }

        Boolean moved = ((AbstractDedaleAgent)this.myAgent).moveTo(goal_node);
        Integer i = 1;
        while (!moved && i < lobs.size()) {
            goal_node = lobs.get(i).getLeft();
            moved = ((AbstractDedaleAgent)this.myAgent).moveTo(goal_node);
            i = i+1;
            this.nodeBuffer.clear();
        }

        if (!moved) {
            return null;
            
        }

        return goal_node;
    }

	@Override
	public void action() {

		if(this.myMap==null) {
			this.myMap= new MapRepresentation();
			this.myAgent.addBehaviour(new ShareMapBehaviour(this.myAgent, TICK_TIME,this.myMap,list_agentNames));
			//this.myAgent.addBehaviour(new SharePath(this.myAgent, this.myMap));
		}
		Location myPosition=((AbstractDedaleAgent)this.myAgent).getCurrentPosition();

		if (myPosition!=null){
			List<Couple<Location,List<Couple<Observation,Integer>>>> lobs=((AbstractDedaleAgent)this.myAgent).observe();
            
            if (this.random_tmp_steps > 0){
                tmpRandomMovement(lobs);
                return;
            }

			try {
				this.myAgent.doWait(TICK_TIME);
			} catch (Exception e) {
				e.printStackTrace();
			}

			this.myMap.addNode(myPosition.getLocationId(), MapAttribute.closed);

			if (!lobs.get(0).getRight().isEmpty() && !lobs.get(0).getRight().get(0).getLeft().equals(Observation.LOCKSTATUS)){
                Observation treasure_observed = lobs.get(0).getRight().get(0).getLeft();
                System.out.println(this.myAgent.getLocalName()+" - I try to open the safe" + lobs.get(0).getLeft() + " : " + treasure_observed + ", " +((AbstractDedaleAgent) this.myAgent).openLock(treasure_observed));
			}

			String nextNode=null;
			Iterator<Couple<Location, List<Couple<Observation, Integer>>>> iter=lobs.iterator();
			while(iter.hasNext()){
				Couple<Location, List<Couple<Observation, Integer>>> CurrentNode=iter.next();
				Location nodeId = CurrentNode.getLeft();

				boolean isNewNode=this.myMap.addNewNode(nodeId.getLocationId());
				if (myPosition!=nodeId) {
					this.myMap.addEdge(myPosition.getLocationId(), nodeId.getLocationId());
					if (nextNode==null && isNewNode) nextNode=nodeId.getLocationId();
				}
			}

			if (!this.myMap.hasOpenNode() || explored){
				explored=true;
                nextNode = moveToNextNodeRandom(lobs).getLocationId();
                if (nextNode != null && !this.nodeBuffer.contains(nextNode)){

                    if (this.nodeBuffer.size() == this.BUFFER_SIZE){
                        this.nodeBuffer.remove(0);
                    }

                    this.nodeBuffer.add(nextNode);
                }
			}else{
				if (nextNode==null){
					nextNode=this.myMap.getShortestPathToClosestOpenNode(myPosition.getLocationId()).get(0);
                     
				}

				MessageTemplate msgTemplate=MessageTemplate.and(
						MessageTemplate.MatchProtocol("SHARE-TOPO"),
						MessageTemplate.MatchPerformative(ACLMessage.INFORM));
				ACLMessage msgReceived=this.myAgent.receive(msgTemplate);
				if (msgReceived!=null) {
					String mclass;
					try {
						mclass = msgReceived.getContentObject().getClass().getName();

					} catch (UnreadableException e) {
						throw new RuntimeException(e);
					}

					if (mclass == "dataStructures.serializableGraph.SerializableSimpleGraph") {
						SerializableSimpleGraph<String, MapAttribute> sgreceived = null;
						try {

							sgreceived = (SerializableSimpleGraph<String, MapAttribute>) msgReceived.getContentObject();
						} catch (UnreadableException e) {
							e.printStackTrace();
						}

						this.myMap.mergeMap(sgreceived);
					}
				}

				if (!explored && !((AbstractDedaleAgent)this.myAgent).moveTo(new gsLocation(nextNode))){
                    this.blocked_counter += 1;
                    if (this.blocked_counter > 5){
                        System.out.println(this.myAgent.getLocalName() + " - I was blocked for too long. Doing a random walk.");
                        this.blocked_counter = 0;
                        this.random_tmp_steps = 10;
                    }
                } else {
                    this.blocked_counter = 0;
                }
			}

		}
	}

	@Override
	public boolean done() {
	return finished;
	}

}