package com.idega.volunteer.handler;

import java.util.logging.Level;

import org.jbpm.graph.def.ActionHandler;
import org.jbpm.graph.exe.ExecutionContext;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.idega.core.business.DefaultSpringBean;
import com.idega.user.data.User;
import com.idega.util.expression.ELUtil;
import com.idega.volunteer.VolunteerConstants;

@Service("volunteerAssignmentHandler")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class VolunteerAssignmentHandler extends DefaultSpringBean implements ActionHandler {

	private static final long serialVersionUID = 5485826377418937945L;

	private Long processInstanceId;
	
	public void execute(ExecutionContext executionContext) throws Exception {
		subscribeToTheCase(executionContext);
	}
	
	private void subscribeToTheCase(ExecutionContext context) {
		User volunteerOrganization = getCurrentUser();
		if (volunteerOrganization == null || !getApplication().getAccessController().hasRole(volunteerOrganization, VolunteerConstants.ROLE_VOLUNTEER_ORGANIZATION))
			return;
		
		try {
			VolunteerAccountHandler volunteerHandler = ELUtil.getInstance().getBean(VolunteerAccountHandler.BEAN_NAME);
			volunteerHandler.subscribeToTheCase(volunteerOrganization, getProcessInstanceId());
		} catch (Exception e) {
			getLogger().log(Level.WARNING, "Error subscribing user " + volunteerOrganization + " the the case based on BPM process: " + getProcessInstanceId(), e);
		}
	}
	
	public Long getProcessInstanceId() {
		return processInstanceId;
	}

	public void setProcessInstanceId(Long processInstanceId) {
		this.processInstanceId = processInstanceId;
	}

}