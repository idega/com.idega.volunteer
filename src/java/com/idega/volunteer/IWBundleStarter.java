package com.idega.volunteer;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ejb.CreateException;
import javax.ejb.FinderException;

import com.idega.block.process.data.CaseCode;
import com.idega.block.process.data.CaseCodeHome;
import com.idega.builder.business.BuilderLogicWrapper;
import com.idega.business.IBOLookup;
import com.idega.core.accesscontrol.business.AccessController;
import com.idega.data.IDOLookup;
import com.idega.idegaweb.IWApplicationContext;
import com.idega.idegaweb.IWBundle;
import com.idega.idegaweb.IWBundleStartable;
import com.idega.user.business.GroupBusiness;
import com.idega.user.data.Group;
import com.idega.util.ListUtil;
import com.idega.util.expression.ELUtil;

public class IWBundleStarter implements IWBundleStartable {

	public void start(IWBundle starterBundle) {
		CaseCodeHome caseCodeHome = null;
		Collection<CaseCode> caseCodes = null;
		try {
			caseCodeHome = (CaseCodeHome) IDOLookup.getHome(CaseCode.class);
			caseCodes = caseCodeHome.findAllCaseCodes();
		} catch (FinderException e) { 
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		
		createCaseCode(caseCodeHome, caseCodes, VolunteerConstants.CASE_TYPE_VOLUNTEER_ASSIGNMENT, "Case type for volunteers assignments");
		createCaseCode(caseCodeHome, caseCodes, VolunteerConstants.CASE_TYPE_VOLUNTEER_ORGANIZATION, "Case type for volunteer organizations");
		createCaseCode(caseCodeHome, caseCodes, VolunteerConstants.CASE_TYPE_VOLUNTEER_REGISTRATION, "Case type for volunteers registration");
		
		IWApplicationContext iwac = starterBundle.getApplication().getIWApplicationContext();
		createGroup(iwac, VolunteerConstants.GROUP_VOLUNTEERS, "Group of volunteers", Arrays.asList(
				VolunteerConstants.ROLE_VOLUNTEER,
				VolunteerConstants.ROLE_VOLUNTEER_ASSIGNMENT_INVITED
		));
		createGroup(iwac, VolunteerConstants.GROUP_VOLUNTEERS_ADMIN_CENTER, "Group of volunteers administration center", Arrays.asList(
				VolunteerConstants.ROLE_VOLUNTEER_ADMIN_CENTER, VolunteerConstants.ROLE_VOLUNTEER_ASSIGNMENT_HANDLER
		));
		createGroup(iwac, VolunteerConstants.GROUP_VOLUNTEERS_ORGANIZATION, "Group of volunteers organization", Arrays.asList(VolunteerConstants.ROLE_VOLUNTEER_ORGANIZATION));
	}
	
	private void createGroup(IWApplicationContext iwac, String name, String description, List<String> roles) {
		try {
			GroupBusiness groupBusiness = IBOLookup.getServiceInstance(iwac, GroupBusiness.class);
			@SuppressWarnings("unchecked")
			Collection<Group> groups = groupBusiness.getGroupsByGroupName(name);
			if (!ListUtil.isEmpty(groups))
				return;
			
			Group group = groupBusiness.createGroup(name, description, groupBusiness.getGroupTypeHome().getPermissionGroupTypeString(), true);
			AccessController acc = iwac.getIWMainApplication().getAccessController();
			for (String role: roles) {
				acc.addRoleToGroup(role, group, iwac);
			}
			
			BuilderLogicWrapper builderLogic = ELUtil.getInstance().getBean(BuilderLogicWrapper.SPRING_BEAN_NAME_BUILDER_LOGIC_WRAPPER);
			builderLogic.reloadGroupsInCachedDomain(iwac, null);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private void createCaseCode(CaseCodeHome caseCodeHome, Collection<CaseCode> caseCodes, String code, String description) {
		if (!ListUtil.isEmpty(caseCodes)) {
			for (CaseCode caseCode: caseCodes) {
				if (code.equals(caseCode.getCode()))
					return;
			}
		}
		
		try {
			CaseCode caseCode = caseCodeHome.create();
			caseCode.setCode(code);
			caseCode.setDescription(description);
			caseCode.store();
		} catch (CreateException e) {
			Logger.getLogger(getClass().getName()).log(Level.WARNING, "Error creating case code by code: " + code + " and description: " + description, e);
		}
	}

	public void stop(IWBundle starterBundle) {
	}

}