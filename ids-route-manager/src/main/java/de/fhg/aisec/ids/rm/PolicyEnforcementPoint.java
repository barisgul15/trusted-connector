package de.fhg.aisec.ids.rm;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.management.InstrumentationProcessor;
import org.apache.camel.processor.SendProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fhg.aisec.ids.api.policy.DecisionRequest;
import de.fhg.aisec.ids.api.policy.PDP;
import de.fhg.aisec.ids.api.policy.PolicyDecision;

/**
 * 
 * @author Mathias Morbitzer (mathias.morbitzer@aisec.fraunhofer.de)
 *
 */
public class PolicyEnforcementPoint implements AsyncProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyEnforcementPoint.class);  
    private Processor target;
	private RouteManagerService rm;
    
    public PolicyEnforcementPoint(Processor target, RouteManagerService rm) {
    	this.target = target;
    	this.rm = rm;
    }
    
    @Override
    public void process(Exchange exchange) throws Exception {
    	// Check if environment is usable as expected
    	if (target==null || exchange==null || !(target instanceof InstrumentationProcessor)) {
			LOG.warn("Cannot check data flow policy. Null or no InstrumentationProcessor");
			return;
		}
		
    	// We expect a SendProcessor to retrieve the endpoint URL from
		if ( !(((InstrumentationProcessor) target).getProcessor() instanceof SendProcessor) ) {
			LOG.warn("Not a SendProcessor. Skipping");
			return;
		}
		
		if (rm==null || rm.getPdp()==null) {
			LOG.warn("No policy decision point registered. Leaving events uncontrolled");
			return;
		}

		// get labels from Exchange property
		String exchangeLabelsRaw = (exchange.getProperty("labels") == null) ? "" : exchange.getProperty("labels").toString();
		String[] labelArray = exchangeLabelsRaw.split(",");
		Set<String> exchangeLabels = new HashSet<>(Arrays.asList(labelArray));

		//figure out where the message comes from and where it should go to
		SendProcessor sendprocessor = (SendProcessor) ((InstrumentationProcessor) target).getProcessor();
		String destination = sendprocessor.getEndpoint().getEndpointUri();
		String source = exchange.getFromEndpoint().getEndpointUri();
		
		LOG.info("START: Check if label(s) exist in allow rules for destination " + destination);
		
		// Call PDP to transform labels and decide whether we may forward the Exchange
		PDP pdp = rm.getPdp();
		PolicyDecision decision = pdp.requestDecision(new DecisionRequest(source, destination, exchangeLabels));

		switch (decision.getDecision()) {
		case DON_T_CARE:
		case ALLOW:
			// forward the Exchange
			target.process(exchange);
			break;
		case DENY:
		default:
			exchange.setException(new Exception("Exchange blocked by policy"));
		}

		LOG.info("Stop 'process' with endpoint ..." + exchange.getFromEndpoint().getEndpointUri());
    }

	@Override
	public boolean process(Exchange exchange, AsyncCallback callback) {
		try {
			process(exchange);
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
		return true;
	}
}
