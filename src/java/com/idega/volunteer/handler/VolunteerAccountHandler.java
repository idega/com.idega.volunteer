package com.idega.volunteer.handler;

import is.idega.idegaweb.egov.cases.business.CasesBusiness;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.logging.Level;

import javax.ejb.CreateException;
import javax.ejb.FinderException;

import org.jbpm.graph.def.ActionHandler;
import org.jbpm.graph.exe.ExecutionContext;
import org.jbpm.taskmgmt.exe.TaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.idega.block.process.business.CaseBusiness;
import com.idega.block.process.data.Case;
import com.idega.core.business.DefaultSpringBean;
import com.idega.data.IDOAddRelationshipException;
import com.idega.data.IDORemoveRelationshipException;
import com.idega.idegaweb.egov.bpm.data.CaseProcInstBind;
import com.idega.idegaweb.egov.bpm.data.dao.CasesBPMDAO;
import com.idega.jbpm.BPMContext;
import com.idega.jbpm.exe.BPMFactory;
import com.idega.jbpm.identity.UserPersonalData;
import com.idega.jbpm.identity.authentication.CreateUserHandler;
import com.idega.user.business.UserBusiness;
import com.idega.user.data.User;
import com.idega.util.ListUtil;
import com.idega.util.StringUtil;
import com.idega.util.expression.ELUtil;

@Scope(BeanDefinition.SCOPE_SINGLETON)
@Service(VolunteerAccountHandler.BEAN_NAME)
public class VolunteerAccountHandler extends DefaultSpringBean implements ActionHandler {

	private static final long serialVersionUID = 8670482203630603777L;

	static final String BEAN_NAME = "volunteerAccountHandler";
	
	private Long processInstanceId;
	
	@Autowired
	private BPMFactory bpmFactory;
	
	@Autowired
	private CasesBPMDAO casesDAO;
	
	@Autowired
	private BPMContext bpmContext;
	
	private BPMFactory getBPMFactory() {
		if (bpmFactory == null)
			ELUtil.getInstance().autowire(this);
		return bpmFactory;
	}
	
	private BPMContext getBPMContext() {
		if (bpmContext == null)
			ELUtil.getInstance().autowire(this);
		return bpmContext;
	}
	
	private CasesBPMDAO getCasesDAO() {
		if (casesDAO == null)
			ELUtil.getInstance().autowire(this);
		return casesDAO;
	}
	
	public Long getProcessInstanceId() {
		return processInstanceId;
	}

	public void setProcessInstanceId(Long processInstanceId) {
		this.processInstanceId = processInstanceId;
	}

	private CreateUserHandler getUserHandler() {
		CreateUserHandler userHandler = ELUtil.getInstance().getBean(CreateUserHandler.BEAN_NAME);
		return userHandler;
	}
	
	public void execute(ExecutionContext executionContext) throws Exception {
		Object registered = executionContext.getVariable("string_volunteerAccountRegistered");
		if (registered == null || Boolean.valueOf(registered.toString())) {
			createOrUpdateUser(executionContext);
		} else if (registered != null && !Boolean.valueOf(registered.toString())) {
			disableUser(executionContext);
		}
	}

	private String getPersonalId(ExecutionContext context) {
		Object personalID = context.getVariable("string_volunteerAccountPersonalId");
		if (personalID instanceof String)
			return (String) personalID;
		
		getLogger().warning("Unknown personal ID: " + personalID);
		return null;
	}
	
	private void createOrUpdateUser(ExecutionContext context) {
		User user = getUser(context);
		if (user == null) {
			user = getCreatedUser(context);
		} else {
			updateUser(context, user);
		}
		
		try {
			subscribeOrUnsubscribe(user, true, getProcessInstanceId());
		} catch (RemoteException e) {
		} catch (FinderException e) {}
		
		VolunteerOrganizationHandler volunteerHandler = ELUtil.getInstance().getBean(VolunteerOrganizationHandler.BEAN_NAME);
		volunteerHandler.enableOrDisableLogin(user, false);
	}
	
	public void unSubscribeFromTheCase(User user, Long proccessInstanceId) throws Exception {
		subscribeOrUnsubscribe(user, false, proccessInstanceId);
	}
	
	public void subscribeToTheCase(User user, Long proccessInstanceId) throws Exception {
		subscribeOrUnsubscribe(user, true, proccessInstanceId);
	}
	
	public Case getCase(Long processInstanceId) {
		CaseProcInstBind caseProcBind = getCasesDAO().getCaseProcInstBindByProcessInstanceId(processInstanceId);
		CaseBusiness caseBusiness = getServiceInstance(CasesBusiness.class);
		try {
			return caseBusiness.getCase(caseProcBind.getCaseId());
		} catch (Exception e) {
			getLogger().log(Level.WARNING, "Error getting case by ID: " + caseProcBind.getCaseId(), e);
		}
		return null;
	}
	
	private void subscribeOrUnsubscribe(User user, boolean subscribe, Long processInstanceId) throws RemoteException, FinderException {
		Case theCase = getCase(processInstanceId);
		Collection<User> subscribers = theCase.getSubscribers();
		if (subscribe) {
			try {
				if (ListUtil.isEmpty(subscribers) || !subscribers.contains(user)) {
					theCase.addSubscriber(user);
					theCase.store();
				}
			} catch (IDOAddRelationshipException e) {
				e.printStackTrace();
			}
		} else {
			try {
				if (!ListUtil.isEmpty(subscribers) && subscribers.contains(user)) {
					theCase.removeSubscriber(user);
					theCase.store();
				}
			} catch (IDORemoveRelationshipException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void updateUser(ExecutionContext context, User user) {
		UserPersonalData upd = getUserData(null, context);
		if (upd == null)
			return;
		
		UserBusiness userBusiness = getServiceInstance(UserBusiness.class);
		try {
			userBusiness.updateUser(user, upd.getFullName(), upd.getGenderName(), null);
			userBusiness.updateUserMail(user, upd.getUserEmail());
			userBusiness.updateUserHomePhone(user, upd.getUserPhone());
			userBusiness.updateUsersMainAddressOrCreateIfDoesNotExist(Integer.valueOf(user.getId()), upd.getUserAddress(), null, null, upd.getUserMunicipality(), null, upd.getUserPostalCode());
		} catch (RemoteException e) {
		} catch (CreateException e) {
		}
		
		if (upd.getPersonalId() != null) {
			user.setPersonalID(upd.getPersonalId());
			user.store();
		}
	}
	
	private UserPersonalData getUserData(UserPersonalData upd, ExecutionContext context) {
		if (upd == null)
			upd = new UserPersonalData();
		
		Object name = context.getVariable("string_caseDescription");
		if (name != null)
			upd.setFullName(name.toString());
		
		Object personalId = context.getVariable("string_volunteerAccountPersonalId");
		if (personalId != null)
			upd.setPersonalId(personalId.toString());
		
		Object address = context.getVariable("string_volunteerAccountAddress");
		if (address != null)
			upd.setUserAddress(address.toString());
		
		Object postalBox = context.getVariable("string_volunteerAccountPostBox");
		if (postalBox != null)
			upd.setUserPostalCode(postalBox.toString());
		
		Object city = context.getVariable("string_volunteerAccountCity");
		if (city != null)
			upd.setUserMunicipality(city.toString());
		
		Object phone = context.getVariable("string_volunteerAccountTelephone1");
		if (phone != null)
			upd.setUserPhone(phone.toString());
		
		Object mail = context.getVariable("string_volunteerAccountEmail1");
		if (mail != null)
			upd.setUserEmail(mail.toString());
		
		Object gender = context.getVariable("string_volunteerAccountGender");
		if (gender != null)
			upd.setGenderName(gender.toString());
		
		return upd;
	}
	
	private User getUser(ExecutionContext context) {
		UserBusiness userBusiness = getServiceInstance(UserBusiness.class);
		String personalId = getPersonalId(context);
		if (StringUtil.isEmpty(personalId))
			throw new RuntimeException("Personal ID must be provided!");
		
		try {
			return userBusiness.getUser(personalId);
		} catch (RemoteException e) {
			getLogger().log(Level.WARNING, "Error getting user by personal ID: " + personalId, e);
		} catch (FinderException e) {}
		return null;
	}
	
	private User getCreatedUser(ExecutionContext context) {
		CreateUserHandler userHandler = getUserHandler();
		UserPersonalData upd = new UserPersonalData();
		upd.setCreateWithLogin(true);
		upd = getUserData(upd, context);
		
		User user = null;
		try {
			user = userHandler.createUser(upd);
		} catch (Exception e) {
			getLogger().log(Level.WARNING, "Error creating user", e);
		}

		if (user == null)
			return null;
		
		try {
			assignOwner(user);
		} catch (Exception e) {
			String message = "Error assigning owner (" + user + ") to the start task of a process instance (ID: " + getProcessInstanceId() + ")";
			getLogger().log(Level.WARNING, message, e);
			throw new RuntimeException(message);
		}
		
		VolunteerOrganizationHandler volunteerHandler = ELUtil.getInstance().getBean(VolunteerOrganizationHandler.BEAN_NAME);
		volunteerHandler.sendMessageAccountCreated(user, upd);
		
		return user;
	}
	
	private void assignOwner(User user) throws Exception {
		TaskInstance taskInstance = getBPMFactory()
		        .getProcessManagerByProcessInstanceId(getProcessInstanceId())
		        .getProcessInstance(getProcessInstanceId())
		        .getStartTaskInstance().getTaskInstance();
		
		taskInstance.setActorId(user.getId());
		getBPMContext().saveProcessEntity(taskInstance);
		
		CaseProcInstBind bind = getCasesDAO().getCaseProcInstBindByProcessInstanceId(getProcessInstanceId());
		CaseBusiness caseBusiness = getServiceInstance(CaseBusiness.class);
		Case theCase = caseBusiness.getCase(bind.getCaseId());
		theCase.setOwner(user);
		theCase.store();
	}
	
	private void disableUser(ExecutionContext context) {
		User user = getUser(context);
		if (user == null)
			throw new RuntimeException("User can not be found by personal ID: " + getPersonalId(context));
		
		try {
			subscribeOrUnsubscribe(user, false, getProcessInstanceId());
		} catch (RemoteException e) {
		} catch (FinderException e) {
		}
		
		VolunteerOrganizationHandler volunteerHandler = ELUtil.getInstance().getBean(VolunteerOrganizationHandler.BEAN_NAME);
		volunteerHandler.enableOrDisableLogin(user, true);
	}
}