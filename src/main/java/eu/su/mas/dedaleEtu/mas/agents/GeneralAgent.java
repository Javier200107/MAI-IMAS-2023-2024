package eu.su.mas.dedaleEtu.mas.agents;

import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import eu.su.mas.dedale.env.Observation;

import jade.lang.acl.MessageTemplate;

import jade.core.AID;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.CyclicBehaviour;

import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import jade.domain.FIPAException;
import jade.domain.DFService;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

import dataStructures.tuple.Couple;

public class GeneralAgent extends AbstractDedaleAgent {
	private String type;
	private AID brains;
	
	private MessageTemplate all_senders;
	
	public void setup() {
		super.setup();
		
		System.out.println("Starting "+ getLocalName() + " agent...");
		
		List<Behaviour> lb=new ArrayList<Behaviour>();
		
		all_senders = MessageTemplate.and(
						MessageTemplate.not(MessageTemplate.MatchSender(brains)),
						MessageTemplate.not(MessageTemplate.MatchSender(getAID())));
		
		lb.add(new OneShotBehaviour() {
			public void action () {
				System.out.println("one shot behaviour");
				int sum = sumFreeSpace(getBackPackFreeSpace());
				boolean lock_picking = canPickLock(getMyExpertise());
				
				if (sum == 0) {
					type = "Explorer";
					// TODO: add agent brains
					System.out.println("I am an explorer!");
				}
				else if (lock_picking) {
					type = "Collector";
					// TODO: add collector brains
					System.out.println("I am a collector!");
				}
				else {
					type = "Tanker";
					// TODO: add collector brains
					System.out.println("I am a collector!");
				}

				// Registre al DF
		        DFAgentDescription dfd = new DFAgentDescription();
		        ServiceDescription sd = new ServiceDescription();
		        sd.setType(type);
		        sd.setName(getName());
		        dfd.setName(getAID());
		        dfd.addServices(sd);

		        try {
		            DFService.register(this.myAgent,dfd);
		        } catch (FIPAException e) {
		            doDelete();
		        }
			}
		});
	}
	
	private Integer sumFreeSpace(List<Couple<Observation, Integer>> bp) {
		Integer sum = 0;
		for (Couple<Observation, Integer> b : bp) {
			sum += b.getRight();
		}
		return sum;
	}
	
	private boolean canPickLock(Set <Couple<Observation, Integer>> bp) {
		for (Couple<Observation, Integer> b : bp) {
			if (b.getLeft() == Observation.LOCKPICKING)
				return b.getRight() > 0;
		}
		return false;
	}
}
