package com.idega.volunteer.handler;

import java.util.Collection;
import java.util.logging.Level;

import org.jbpm.graph.def.ActionHandler;
import org.jbpm.graph.exe.ExecutionContext;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.idega.core.business.DefaultSpringBean;
import com.idega.user.business.UserBusiness;

@Service("volunteerAssignmentHandler")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class VolunteerAssignmentHandler extends DefaultSpringBean implements ActionHandler {

	private static final long serialVersionUID = 5485826377418937945L;

	private Long processInstanceId;
	
	public void execute(ExecutionContext executionContext) throws Exception {
		Object assignedVolunteers = executionContext.getVariable("list_volunteerAssignmentVolunteers");
		sendNotifications(assignedVolunteers);
		
		Object proposedVolunteers = executionContext.getVariable("list_volunteerAssignmentProposedVolunteers");
		sendNotifications(proposedVolunteers);
	}
	
	private void sendNotifications(Object volunteers) {
		if (!(volunteers instanceof Collection<?>))
			return;
		
		UserBusiness userBusiness = getServiceInstance(UserBusiness.class);
		
		try {
			Thread sender = new Thread(new Runnable() {
				public void run() {
	//				SendMail.send(from, to, cc, bcc, replyTo, host, subject, text);
				}
			});
			sender.start();
		} catch (Exception e) {
			getLogger().log(Level.WARNING, "Errr sending notification mail", e);
		}
	}

	public Long getProcessInstanceId() {
		return processInstanceId;
	}

	public void setProcessInstanceId(Long processInstanceId) {
		this.processInstanceId = processInstanceId;
	}

}