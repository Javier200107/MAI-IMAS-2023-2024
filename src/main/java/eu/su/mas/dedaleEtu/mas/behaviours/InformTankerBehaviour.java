package eu.su.mas.dedaleEtu.mas.behaviours;

import eu.su.mas.dedale.env.Location;
import eu.su.mas.dedale.mas.AbstractDedaleAgent;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.Objects;

/**
 * This example behaviour try to send a hello message (every 3s maximum) to agents Collect2 Collect1
 * @author hc
 *
 */
public class InformTankerBehaviour extends TickerBehaviour{

	/**
	 * 
	 */
	private static final long serialVersionUID = -2058134622078521998L;

	/**
	 * An agent tries to contact its friend and to give him its current position
	 * @param myagent the agent who posses the behaviour
	 *  
	 */
	public InformTankerBehaviour (final Agent myagent) {
		super(myagent, 600);
	}

	@Override
	public void onTick() {
		Location myPosition=((AbstractDedaleAgent)this.myAgent).getCurrentPosition();

		//A message is defined by : a performative, a sender, a set of receivers, (a protocol),(a content (and/or contentObject))
		// ACLMessage msg=new ACLMessage(ACLMessage.INFORM);
		
		ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
		msg.setSender(this.myAgent.getAID());
		msg.setProtocol("INFORM-TANKERS");	
		if (myPosition!=null && !Objects.equals(myPosition.getLocationId(), "")){
			// System.out.println(this.myAgent.getLocalName() + " is sending info to Tankers");
			msg.setContent("Info about " + this.myAgent.getLocalName()+";"+((AbstractDedaleAgent)this.myAgent).getMyTreasureType()+";"
								+((AbstractDedaleAgent)this.myAgent).getClass()+";"+((AbstractDedaleAgent)this.myAgent).getBackPackFreeSpace());//.get(0).getRight());

			msg.addReceiver(new AID("Tanker1",AID.ISLOCALNAME));
			msg.addReceiver(new AID("Tanker2",AID.ISLOCALNAME));

			//Mandatory to use this method (it takes into account the environment to decide if someone is reachable or not)
			((AbstractDedaleAgent)this.myAgent).sendMessage(msg);
			// System.out.println("Message sent");
		}
	}
}