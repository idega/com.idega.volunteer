package com.idega.volunteer.handler;

import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;

import org.jbpm.graph.def.ActionHandler;
import org.jbpm.graph.exe.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.idega.block.process.data.Case;
import com.idega.core.business.DefaultSpringBean;
import com.idega.idegaweb.IWResourceBundle;
import com.idega.jbpm.bean.VariableInstanceInfo;
import com.idega.jbpm.data.VariableInstanceQuerier;
import com.idega.user.business.UserBusiness;
import com.idega.user.data.User;
import com.idega.util.CoreUtil;
import com.idega.util.ListUtil;
import com.idega.util.SendMailMessageValue;
import com.idega.util.StringHandler;
import com.idega.util.expression.ELUtil;
import com.idega.volunteer.VolunteerConstants;

@Service("volunteerMatchingHandler")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class VolunteersMatchingHandler extends DefaultSpringBean implements ActionHandler {

	private static final long serialVersionUID = 8674260707721338282L;

	private Long processInstanceId;

	@Autowired
	private VariableInstanceQuerier querier;
	
	private VariableInstanceQuerier getQuerier() {
		if (querier == null)
			ELUtil.getInstance().autowire(this);
		return querier;
	}
	
	public Long getProcessInstanceId() {
		return processInstanceId;
	}

	public void setProcessInstanceId(Long processInstanceId) {
		this.processInstanceId = processInstanceId;
	}

	private void doUnAssignUsers(Collection<?> currentUsers, Collection<?> previousUsers, VolunteerAccountHandler accountHandler,
			VolunteerOrganizationHandler organizationHandler, Case theCase, IWResourceBundle iwrb) throws Exception {
		if (ListUtil.isEmpty(previousUsers))
			return;
		
		String subject = iwrb.getLocalizedString("you_were_unassigned_from_mission", "You were unassigned from the mission");
		String text = iwrb.getLocalizedString("unassignment_from_mission_message", "Hi {0}, \n\nYou were just unassigned from mission with number: {1}.");
		text = StringHandler.replace(text, "{1}", theCase.getCaseIdentifier());
		
		UserBusiness userBusiness = getServiceInstance(UserBusiness.class);
		for (Object previousUser: previousUsers) {
			if (ListUtil.isEmpty(currentUsers) || !currentUsers.contains(previousUser)) {
				User user = null;
				try {
					user = userBusiness.getUser(Integer.valueOf(previousUser.toString()));
				} catch (Exception e) {
					getLogger().log(Level.WARNING, "Error getting user by ID: " + previousUser, e);
				}
				if (user == null)
					continue;
				
				accountHandler.unSubscribeFromTheCase(user, getProcessInstanceId());
				organizationHandler.sendMail(user, new SendMailMessageValue(null, null, null, null, null, subject, StringHandler.replace(text, "{0}", user.getName()), null, null));
			}
		}
	}
	
	public void execute(ExecutionContext executionContext) throws Exception {
		Object assignedUsers = executionContext.getVariable("list_volunteerAssignmentVolunteers");
		if (!(assignedUsers instanceof Collection<?>))
			return;
		
		VolunteerAccountHandler accountHandler = ELUtil.getInstance().getBean(VolunteerAccountHandler.BEAN_NAME);
		VolunteerOrganizationHandler organizationHandler = ELUtil.getInstance().getBean(VolunteerOrganizationHandler.BEAN_NAME);
		
		Case theCase = accountHandler.getCase(getProcessInstanceId());
		IWResourceBundle iwrb = getResourceBundle(getBundle(VolunteerConstants.IW_BUNDLE_IDENTIFIER));
		
		Collection<?> ids = (Collection<?>) assignedUsers;
		
		Collection<VariableInstanceInfo> vars = getQuerier().getVariablesByProcessInstanceIdAndVariablesNames(Arrays.asList("list_volunteerAssignmentVolunteers"),
				Arrays.asList(getProcessInstanceId()), false, false, false);
		Collection<?> previousUsers = null;
		try {
			previousUsers = ListUtil.isEmpty(vars) ? null : (Collection<?>) vars.iterator().next().getValue();
			doUnAssignUsers(ids, previousUsers, accountHandler, organizationHandler, theCase, iwrb);
		} catch (Exception e) {
			getLogger().log(Level.WARNING, "Error unassigning users " + vars + " from the volunteer assignment: " + getProcessInstanceId() + ", " + theCase.getCaseIdentifier(), e);
		}
		
		if (ListUtil.isEmpty(ids))
			return;
		
		String subject = iwrb.getLocalizedString("you_were_assigned_to_mission", "You were assigned to a volunteer mission");
		String text = iwrb.getLocalizedString("assignment_to_mission_message",
				"Hi {0}, \n\nA new volunteer mission was assigned to you. Mission number: {1}. You can find more details about the mission at {2}.");
		text = StringHandler.replace(text, "{1}", theCase.getCaseIdentifier());
		text = StringHandler.replace(text, "{2}", CoreUtil.getIWContext().getDomain().getURL());
		
		UserBusiness userBusiness = getServiceInstance(UserBusiness.class);
		for (Object id: ids) {
			User user = null;
			try {
				user = userBusiness.getUser(Integer.valueOf(id.toString()));
			} catch (Exception e) {
				getLogger().log(Level.WARNING, "Error getting user by ID: " + id, e);
			}
			if (user == null || (!ListUtil.isEmpty(previousUsers) && previousUsers.contains(user.getId())))
				continue;
			
			accountHandler.subscribeToTheCase(user, getProcessInstanceId());
			organizationHandler.sendMail(user, new SendMailMessageValue(null, null, null, null, null, subject, StringHandler.replace(text, "{0}", user.getName()), null, null));
		}
		
	}

}