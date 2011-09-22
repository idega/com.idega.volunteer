package com.idega.volunteer.presentation;

import is.idega.idegaweb.egov.cases.presentation.PublicCases;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.idega.builder.business.BuilderLogic;
import com.idega.idegaweb.IWBundle;
import com.idega.idegaweb.IWResourceBundle;
import com.idega.jbpm.bean.VariableInstanceInfo;
import com.idega.jbpm.data.VariableInstanceQuerier;
import com.idega.jbpm.data.dao.BPMDAO;
import com.idega.jbpm.identity.BPMUser;
import com.idega.presentation.Block;
import com.idega.presentation.IWContext;
import com.idega.presentation.Layer;
import com.idega.presentation.text.Heading3;
import com.idega.util.CoreConstants;
import com.idega.util.ListUtil;
import com.idega.util.StringUtil;
import com.idega.util.expression.ELUtil;
import com.idega.volunteer.VolunteerConstants;

public class VolunteerAssignmentsViewer extends Block {

	@Autowired
	private VariableInstanceQuerier querier;
	
	@Autowired
	private BPMDAO bpmDAO;
	
	public VolunteerAssignmentsViewer() {
		ELUtil.getInstance().autowire(this);
	}
	
	@Override
	public String getBundleIdentifier() {
		return VolunteerConstants.IW_BUNDLE_IDENTIFIER;
	}
	
	@Override
	public void main(IWContext iwc) throws Exception {
		IWBundle bundle = getBundle(iwc);
		IWResourceBundle iwrb = bundle.getResourceBundle(iwc);
		
		Layer container = new Layer();
		container.setStyleClass("volunteerAssignmentsViewer");
		add(container);
		
		List<Long> piIds = bpmDAO.getProcessInstanceIdsByProcessDefinitionNames(Arrays.asList(VolunteerConstants.PROCESS_VOLUNTEER_ASSIGNMENT));
		if (ListUtil.isEmpty(piIds)) {
			container.add(new Heading3(iwrb.getLocalizedString("there_are_no_volunteer_assignments", "There are no volunteer assignments")));
			return;
		}
		
		Collection<VariableInstanceInfo> vars = querier.getVariablesByProcessInstanceIdAndVariablesNames(Arrays.asList("date_volunteerAssignmentExpireDate"),
				piIds, false, false, false);
		if (ListUtil.isEmpty(vars)) {
			container.add(new Heading3(iwrb.getLocalizedString("there_are_no_volunteer_assignments", "There are no volunteer assignments")));
			return;
		}
		
		String pageUri = BuilderLogic.getInstance().getFullPageUrlByPageType(iwc, BPMUser.defaultAssetsViewPageType, true);
		if (StringUtil.isEmpty(pageUri)) {
			container.add(new Heading3(iwrb.getLocalizedString("volunteer_assignments_can_not_be_viewed", "Sorry, currently assignments for the volunteers can not be viewed")));
			return;
		}
		
		container.add(new Heading3(iwrb.getLocalizedString("volunteer_assignments", "Assignments for volunteers").concat(":")));
		
		PublicCases publicCases = new PublicCases();
		publicCases.setHideEmptySection(true);
		publicCases.setCaseCodes(VolunteerConstants.CASE_TYPE_VOLUNTEER_ASSIGNMENT);
		publicCases.setSpecialBackPage(CoreConstants.PAGES_URI_PREFIX);
		container.add(publicCases);
	}
	
}