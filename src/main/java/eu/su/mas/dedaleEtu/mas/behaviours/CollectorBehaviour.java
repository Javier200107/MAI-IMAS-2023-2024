package eu.su.mas.dedaleEtu.mas.behaviours;

import dataStructures.tuple.Couple;
import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.env.Observation;
import eu.su.mas.dedaleEtu.princ.Utils;
import jade.core.AID;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

public class CollectorBehaviour extends TickerBehaviour {
    private static final long serialVersionUID = 9088209402507795289L;
    private static final int BUFFER_SIZE = 8;
    private static final int TICKER_TIME = Utils.TICK_TIME;

    private List<String> node_Buffer = new ArrayList<>(BUFFER_SIZE);
    private List<String> potential_treasures = new ArrayList<>();
    private HashMap<String, Integer> treasure_quantity = new HashMap<String, Integer>();
    private HashMap<String, String> treasure_types = new HashMap<String, String>();

    private boolean is_working = false;
    private boolean backing_up = false;
    private int backoff_wait = 0;
    private int mission_step = 0;
    private String conflict_node = null;
    private int conflict_counter = 0; // Counter to keep track of how many times the agent has been blocked
    private List<String> conflict_path = new ArrayList<>();

    private boolean stop_for_help = false;
    private int stop_patience = 0;

    private List<String> planned_route = new ArrayList<>(Arrays.asList("-116657", "-116656", "-116655", "-116654", "-116653", "-116652", "-116071", "-121367", "-121366", "-121365", "-121364", "-121363", "-121362", "-121361", "-121360", "-121359", "-121358", "-117834"));


    public CollectorBehaviour (final AbstractDedaleAgent myagent) {
        super(myagent, TICKER_TIME);
    }

    private List<String> getRemainingRoute(){
        List<String> remaining = new ArrayList<String>();
        for (int i = this.mission_step; i < this.planned_route.size(); i++) {
            remaining.add(this.planned_route.get(i));
        }
        return remaining;
    }

    private void updatePotentialTreasures(){
        List<String> treasures = new ArrayList<>();
        for (HashMap.Entry<String, String> node : this.treasure_types.entrySet()) {
            if (((AbstractDedaleAgent) this.myAgent).getMyTreasureType().toString() == node.getValue() &&
                    this.treasure_quantity.get(node.getKey()) > 0
            ){
                treasures.add(node.getKey());
            }
        }
        this.potential_treasures = treasures;
    }

    private void receiveMission() {
        MessageTemplate msgTemplate=MessageTemplate.and(
                MessageTemplate.MatchProtocol("SHARE-PATH"),
                MessageTemplate.MatchPerformative(ACLMessage.INFORM));
        ACLMessage msgReceived=this.myAgent.receive(msgTemplate);

        if (msgReceived!=null) {
            List<String> mission;
            try {
                mission = (List<String>) msgReceived.getContentObject();
                System.out.println(this.myAgent.getLocalName() + " - Got a mission! Starting it..");

                this.planned_route = mission;
                this.stop_for_help = false;
                this.stop_patience = 0;
                this.is_working = true;

            } catch (UnreadableException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean GetStopMssg() {
        MessageTemplate msgTemplate=MessageTemplate.and(
                MessageTemplate.MatchProtocol("STOP"),
                MessageTemplate.MatchPerformative(ACLMessage.INFORM));
        ACLMessage msgReceived=this.myAgent.receive(msgTemplate);

        if (msgReceived!=null) {
            this.stop_for_help = true;
            this.stop_patience = 4;
            return true;
        }
        return false;
    }

    private void requestExplorerHelp(){
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setSender(this.myAgent.getAID());
        List<String> receivers = new ArrayList<>(Arrays.asList("Explo1", "Explo2", "Explo3"));
        for (String agentName : receivers) {
            msg.addReceiver(new AID(agentName,AID.ISLOCALNAME));
        }
        msg.setContent("NeedHelp");
        msg.setProtocol("HELLO");
        ((AbstractDedaleAgent)this.myAgent).sendMessage(msg);

    }

    private void sendTreasureRequest(String current_node){
        List<String> receivers = new ArrayList<>(Arrays.asList("Explo1", "Explo2", "Explo3"));
        List<String> request_nodes = new ArrayList<>(this.potential_treasures);
        request_nodes.add(0, current_node);

        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.setSender(this.myAgent.getAID());
        for (String agentName : receivers) {
            msg.addReceiver(new AID(agentName, AID.ISLOCALNAME));
        }

        try {
            msg.setContentObject((Serializable) request_nodes);
        } catch (IOException e) {
            e.printStackTrace();
        }
        msg.setProtocol("SHARE-POINTS");
        ((AbstractDedaleAgent)this.myAgent).sendMessage(msg);
    }

    private void sendBlockingInfo(){
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setSender(this.myAgent.getAID());
        List<String> receivers = new ArrayList<>(Arrays.asList("Tanker1", "Tanker2", "Collect1", "Collect2", "Collect3", "Collect4", "Explo1", "Explo2", "Explo3"));
        receivers.remove(this.myAgent.getLocalName());
        for (String agentName : receivers) {
            msg.addReceiver(new AID(agentName,AID.ISLOCALNAME));
        }
        try {
            msg.setContentObject((Serializable) getRemainingRoute());
        } catch (IOException e) {
            e.printStackTrace();
        }
        msg.setConversationId("Blocked");
        ((AbstractDedaleAgent)this.myAgent).sendMessage(msg);
    }

    private int getBlockingInfo(){
        MessageTemplate msgTemplate=MessageTemplate.MatchPerformative(ACLMessage.INFORM);
        ACLMessage msgReceived=this.myAgent.receive(msgTemplate);
        int backup = 0;
        // 0 = no action, 1 backup, -1 winning

        if (msgReceived!=null) {
            String msg_id = msgReceived.getConversationId();
            if (msg_id == "Blocked") {
                List<String> agentPath;
                try {
                    agentPath = (List<String>) msgReceived.getContentObject();
                    this.conflict_path = agentPath;
                    int remaining_len = getRemainingRoute().size();
                    // Agent with more nodes to follow is backing off
                    if (remaining_len > agentPath.size()){
                        backup = 1;
                        // If tied, compare their name strings
                    } else if (remaining_len == agentPath.size() && this.myAgent.getLocalName().compareTo(msgReceived.getSender().getLocalName()) < 0) {
                        backup = 1;
                    } else {
                        backup = -1;
                    }
                } catch (UnreadableException e) {
                    e.printStackTrace();
                }
            }
        }
        return backup;
    }

    private void solveDeadLock(){
        sendBlockingInfo();
        int backup = getBlockingInfo();
        if (backup == 1){
            this.backing_up = true;
        } else if (backup == -1) {
        } else {
            this.conflict_counter += 1;
        }

        if (this.conflict_counter == 20){
            this.is_working = false;
            this.conflict_counter = 0;
            System.out.println(this.myAgent.getLocalName() + " - is blocked so abandoning its mission");
        }
        return;
    }

    private String backOff(List<Couple<Location,List<Couple<Observation,Integer>>>> lobs){

        for (int i = 1; i < lobs.size(); i++) {
            Location node = lobs.get(i).getLeft();
            String s_node = node.toString();
            if (!this.conflict_path.contains(s_node) && !Objects.equals(s_node, this.conflict_node) && (((AbstractDedaleAgent)this.myAgent).moveTo(node))){
                this.conflict_counter = 0;
                this.backing_up = false;
                this.conflict_node = null;
                if (this.mission_step > 1){
                    this.mission_step -= 1;
                } else {
                    this.planned_route.add(0, lobs.get(0).getLeft().toString());
                }
                this.backoff_wait = 3;
                System.out.println(this.myAgent.getLocalName() + " - I successfully backed off! Resuming mission now.");
                return s_node;
            }
        }

        String s_prev_node = null;
        Location prev_node = null;
        boolean moved = false;

        if (this.mission_step == 1){
            for (int j = 1; j < lobs.size(); j++) {
                Location node = lobs.get(j).getLeft();
                String s_node = node.toString();
                if (((AbstractDedaleAgent)this.myAgent).moveTo(node)){
                    prev_node = node;
                    s_prev_node = s_node;
                    moved = true;
                    break;
                }
            }
        } else {
            s_prev_node = this.planned_route.get(this.mission_step-2);
            moved = ((AbstractDedaleAgent)this.myAgent).moveTo(prev_node);
        }

        if (moved) {
            this.conflict_node = lobs.get(0).getLeft().toString();
            if (this.mission_step == 1){
                this.planned_route.add(0, s_prev_node);
            } else {
                this.mission_step -= 1;
            }
        }
        return s_prev_node;
    }

    private String moveToNode(List<Couple<Location,List<Couple<Observation,Integer>>>> lobs){
        String s_next_node = this.planned_route.get(this.mission_step);
        boolean valid = false;
        Location next_node = null;
        for (int i = 0; i < lobs.size(); i++) {
            if (lobs.get(i).getLeft().toString().equals(s_next_node)){
                valid = true;
                next_node = lobs.get(i).getLeft();
                break;
            }
        }

        if (!valid){
            System.out.println(this.myAgent.getLocalName() + " - The following node from the mission is not valid!! Aborting");
            this.is_working = false;
            this.mission_step = 0;
            this.planned_route = null;
            return null;
        }

        Boolean moved = ((AbstractDedaleAgent)this.myAgent).moveTo(next_node);
        if (!moved) {
            solveDeadLock();
            return null;
        }
        this.mission_step += 1;
        if (this.mission_step == planned_route.size()){
            System.out.println(this.myAgent.getLocalName() + " -- Finished mission: final node was a treasure :)");
            this.is_working = false;
            this.mission_step = 0;
            this.planned_route = null;
        }

        return s_next_node;
    }

    private String moveToNextNodeRandom(List<Couple<Location,List<Couple<Observation,Integer>>>> lobs){
        Random r= new Random();
        int moveId=1+r.nextInt(lobs.size()-1);
        Location next_node = lobs.get(moveId).getLeft();
        Location goal_node = next_node;
        String s_next_node = lobs.get(moveId).getLeft().toString();
        String s_goal_node = s_next_node;

        if (!this.node_Buffer.contains(s_next_node)){
            s_goal_node = s_next_node;
            goal_node = next_node;
        } else {
            for (int i = 1; i < lobs.size(); i++) {
                s_next_node = lobs.get(i).getLeft().toString();
                next_node = lobs.get(i).getLeft();
                if (!this.node_Buffer.contains(s_next_node)){
                    s_goal_node = s_next_node;
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
            this.node_Buffer.clear();
        }

        if (!moved) {
            return null;

        }
        return s_goal_node;
    }

    private void shareTreasureInfo(){
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setSender(this.myAgent.getAID());
        List<String> receivers = new ArrayList<>(Arrays.asList("Tanker1", "Tanker2", "Collect1", "Collect2", "Collect3", "Collect4"));
        receivers.remove(this.myAgent.getLocalName());
        for (String agentName : receivers) {
            msg.addReceiver(new AID(agentName,AID.ISLOCALNAME));
        }
        try {
            msg.setContentObject(this.treasure_types);
            msg.setConversationId("Type");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ((AbstractDedaleAgent)this.myAgent).sendMessage(msg);
        try {
            msg.setContentObject(this.treasure_quantity);
            msg.setConversationId("Quant");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ((AbstractDedaleAgent)this.myAgent).sendMessage(msg);
    }

    private void mergeTreasureInfo(){
        MessageTemplate msgTemplate=MessageTemplate.MatchPerformative(ACLMessage.INFORM);
        ACLMessage msgReceived=this.myAgent.receive(msgTemplate);

        if (msgReceived!=null) {
            Boolean updated = false;
            String msg_id = (String) msgReceived.getConversationId();
            if (msg_id == "Type") {
                HashMap<String, String> treasureType;
                try {
                    treasureType = (HashMap<String, String>) msgReceived.getContentObject();

                    for (HashMap.Entry<String, String> set : treasureType.entrySet()) {
                        if (!this.treasure_types.containsKey(set.getKey())) {
                            this.treasure_types.put(set.getKey(), set.getValue());
                            updated = true;
                        }
                    }

                } catch (UnreadableException e) {
                    e.printStackTrace();
                }

            } else if (msg_id == "Quant") {
                HashMap<String, Integer> treasureQuant;

                try {
                    treasureQuant = (HashMap<String, Integer>) msgReceived.getContentObject();

                    for (HashMap.Entry<String, Integer> set : treasureQuant.entrySet()) {

                        if (!this.treasure_quantity.containsKey(set.getKey())) {
                            this.treasure_quantity.put(set.getKey(), set.getValue());
                            updated = true;

                        } else if (this.treasure_quantity.get(set.getKey()) > set.getValue()) {
                            this.treasure_quantity.put(set.getKey(), set.getValue());
                            updated = true;
                        }
                    }

                } catch (UnreadableException e) {
                    e.printStackTrace();
                }
            }
            if (updated){
            }
        }
    }

    @Override
    public void onTick() {
        if (this.backoff_wait > 0){
            this.backoff_wait -= 1;
            return;
        }
        if (this.stop_patience > 0){
            this.stop_patience -= 1;
        }else if (this.stop_patience == 0){
            this.stop_for_help = false;
        }
        Location myPosition=((AbstractDedaleAgent)this.myAgent).getCurrentPosition();
        String current_position = myPosition.toString();

        String s_myPosition = myPosition.toString();


        if (s_myPosition!=""){
            List<Couple<Location,List<Couple<Observation,Integer>>>> lobs=((AbstractDedaleAgent)this.myAgent).observe();//myPosition

            List<Couple<Observation,Integer>> lObservations= lobs.get(0).getRight();

            for(Couple<Observation,Integer> o:lObservations){
                switch (o.getLeft()) {
                    case DIAMOND:case GOLD:

                        if (o.getLeft() == ((AbstractDedaleAgent) this.myAgent).getMyTreasureType()){
                            Boolean unlock = ((AbstractDedaleAgent) this.myAgent).openLock(o.getLeft());
                            if (unlock) {
                            }
                        }

                        int grabbed = ((AbstractDedaleAgent) this.myAgent).pick();
                        if (grabbed > 0) {
                            System.out.println(this.myAgent.getLocalName()+" - The agent grabbed: "+ grabbed + " of " + o.getLeft() + " at " + myPosition);
                        }

                        if (!this.treasure_quantity.containsKey(s_myPosition)) {
                            this.treasure_quantity.put(s_myPosition, o.getRight() - grabbed);
                            this.treasure_types.put(s_myPosition, o.getLeft().toString());
                        } else {
                            if (this.treasure_quantity.get(s_myPosition) != o.getRight() - grabbed) {
                                this.treasure_quantity.put(s_myPosition, o.getRight() - grabbed);
                            }
                        }
                        break;
                    default:
                        break;
                }
            }

            List<Couple<Observation, Integer>> backpack_before = ((AbstractDedaleAgent) this.myAgent).getBackPackFreeSpace();
            Boolean contacted = ((AbstractDedaleAgent)this.myAgent).emptyMyBackPack("Tanker1") || ((AbstractDedaleAgent)this.myAgent).emptyMyBackPack("Tanker2");
            List<Couple<Observation, Integer>> backpack_after = ((AbstractDedaleAgent) this.myAgent).getBackPackFreeSpace();
            Boolean delivered = false;
            for (int i = 0; i < backpack_after.size(); i++) {
                if (backpack_after.get(i).getRight() > backpack_before.get(i).getRight()){
                    delivered = true;
                }
            }
            if (delivered && contacted) {
                System.out.println(this.myAgent.getLocalName()+" - The agent delivered his treasure. Backpack at " + ((AbstractDedaleAgent) this.myAgent).getBackPackFreeSpace() + " of capacity");
            }

            String next_node = null;
            if (!this.stop_for_help){
                if (this.backing_up){
                    next_node = backOff(lobs);
                } else if (this.is_working && !this.backing_up){
                    next_node = moveToNode(lobs);
                } else {
                    next_node = moveToNextNodeRandom(lobs);
                }
            }

            if (next_node != null){
                this.conflict_counter = 0;
                current_position = next_node;
            }

            // Add node to buffer if not already there
            if (next_node != null && !this.node_Buffer.contains(next_node)){

                if (this.node_Buffer.size() == this.BUFFER_SIZE){
                    this.node_Buffer.remove(0);
                }

                this.node_Buffer.add(next_node);
            }

            // Share treasure info with other agents
            shareTreasureInfo();
            mergeTreasureInfo();

            updatePotentialTreasures();
            // If not on mission, check if there are any treasures to pick up
            if (!this.is_working){
                if (!this.potential_treasures.isEmpty()){
                    requestExplorerHelp();
                    if (GetStopMssg()){
                        sendTreasureRequest(current_position);
                    }
                }
                receiveMission();
            }
        }
    }
}


