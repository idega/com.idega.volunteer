package com.idega.volunteer.business;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import javax.ejb.FinderException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.idega.company.event.CompanyCreatedEvent;
import com.idega.core.business.DefaultSpringBean;
import com.idega.event.UserCreatedEvent;
import com.idega.idegaweb.IWResourceBundle;
import com.idega.jbpm.bean.VariableInstanceInfo;
import com.idega.jbpm.data.VariableInstanceQuerier;
import com.idega.jbpm.data.dao.BPMDAO;
import com.idega.user.business.GroupBusiness;
import com.idega.user.business.UserBusiness;
import com.idega.user.data.Group;
import com.idega.user.data.User;
import com.idega.util.CoreConstants;
import com.idega.util.IWTimestamp;
import com.idega.util.ListUtil;
import com.idega.util.StringUtil;
import com.idega.util.text.Item;
import com.idega.volunteer.VolunteerConstants;

@Service("volunteerServices")
@Scope(BeanDefinition.SCOPE_SINGLETON)
public class VolunteerServices extends DefaultSpringBean implements ApplicationListener {
	
	@Autowired
	private VariableInstanceQuerier querier;
	
	@Autowired
	private BPMDAO bpmDAO;
	
	public Map<Locale, Map<String, String>> getVolunteers() {
		Map<Locale, Map<String, String>> allVolunteers = new HashMap<Locale, Map<String,String>>();
		
		Map<String, String> volunteers = new HashMap<String, String>();
		allVolunteers.put(getCurrentLocale(), volunteers);
		
		Collection<User> volunteerUsers = getAllVolunteers();
		if (ListUtil.isEmpty(volunteerUsers))
			return allVolunteers;
		
		for (User volunteer: volunteerUsers) {
			volunteers.put(volunteer.getId(), volunteer.getName());
		}
		
		return allVolunteers;
	}
	
	private Collection<User> getAllVolunteers() {
		Collection<User> volunteers = null;
		try {
			GroupBusiness groupBusiness = getServiceInstance(GroupBusiness.class);
			@SuppressWarnings("unchecked")
			Collection<Group> volunteersGroups = groupBusiness.getGroupsByGroupName(VolunteerConstants.GROUP_VOLUNTEERS);
			if (ListUtil.isEmpty(volunteersGroups))
				return Collections.emptyList();
			
			volunteers = new ArrayList<User>();
			for (Group group: volunteersGroups) {
				@SuppressWarnings("unchecked")
				Collection<User> users = groupBusiness.getUsers(group);
				if (ListUtil.isEmpty(users))
					continue;
				
				for (User user: users) {
					volunteers.add(user);
				}
			}
			return volunteers;
		} catch (FinderException e) {
			getLogger().warning("There are no registered volunteers");
		} catch (RemoteException e) {
			getLogger().log(Level.WARNING, "Error getting volunteers", e);
		}
		return Collections.emptyList();
	}
	
	public List<Item> getSuggestedVolunteers(String types) {
		List<Item> volunteers = new ArrayList<Item>();
		
		List<Long> piIds = bpmDAO.getProcessInstanceIdsByProcessDefinitionNames(Arrays.asList("VolunteerAccount"));
		if (ListUtil.isEmpty(piIds))
			return volunteers;
		
		Collection<VariableInstanceInfo> vars = querier.getVariablesByProcessInstanceIdAndVariablesNames(Arrays.asList(
				"string_volunteerAccountPersonalId",
				"string_volunteerAccountRegistered",
				"string_volunteerAccountAvailable",
				"list_volunteerAccountAreaOfInterests"
			), piIds, false, false, false);
		if (ListUtil.isEmpty(vars))
			return volunteers;
		
		Map<Long, List<VariableInstanceInfo>> groupedVars = querier.getGroupedVariables(vars);
		if (groupedVars == null)
			return volunteers;
		
		Map<Long, Collection<?>> interests = new HashMap<Long, Collection<?>>();
		Map<Long, String> availableVolunteers = new HashMap<Long, String>();
		for (Long piId: groupedVars.keySet()) {
			List<VariableInstanceInfo> variables = groupedVars.get(piId);
			if (ListUtil.isEmpty(variables))
				continue;
			
			for (VariableInstanceInfo var: variables) {
				String name = var.getName();
				if (StringUtil.isEmpty(name))
					continue;
				
				Serializable value = var.getValue();
				if (value == null || StringUtil.isEmpty(value.toString()))
					continue;
				
				if (name.equals("string_volunteerAccountPersonalId")) {
					availableVolunteers.put(piId, value.toString());
				} else if (name.equals("string_volunteerAccountRegistered") && !Boolean.valueOf(value.toString())) {
					availableVolunteers.remove(piId);
					interests.remove(piId);
				} else if (name.equals("string_volunteerAccountAvailable") && !Boolean.valueOf(value.toString())) {
					availableVolunteers.remove(piId);
					interests.remove(piId);
				} else if (name.equals("list_volunteerAccountAreaOfInterests")) {
					if (value instanceof Collection<?>)
						interests.put(piId, (Collection<?>) value);
				}
			}
		}
		if (availableVolunteers == null || availableVolunteers.isEmpty())
			return volunteers;
		
		List<String> suggestedVolunteers = new ArrayList<String>();
		List<String> assignmentTypes = StringUtil.isEmpty(types) ? new ArrayList<String>(0) : Arrays.asList(types.split(CoreConstants.SPACE));
		for (Long piId: availableVolunteers.keySet()) {
			String personalId = availableVolunteers.get(piId);
			if (ListUtil.isEmpty(assignmentTypes)) {
				suggestedVolunteers.add(personalId);
			} else {
				Collection<?> preferences = interests.get(piId);
				if (ListUtil.isEmpty(preferences))
					continue;
				else {
					for (Object preference: preferences) {
						if (assignmentTypes.contains(preference.toString()) && !suggestedVolunteers.contains(personalId))
							suggestedVolunteers.add(personalId);
					}
				}
			}
		}
		
		if (ListUtil.isEmpty(suggestedVolunteers))
			return volunteers;
		
		UserBusiness userBusiness = getServiceInstance(UserBusiness.class);
		for (String personalId: suggestedVolunteers) {
			User user = null;
			try {
				user = userBusiness.getUser(personalId);
			} catch (Exception e) {
				getLogger().log(Level.WARNING, "Error getting user by personal ID: " + personalId, e);
			}
			if (user == null)
				continue;
			
			volunteers.add(new Item(user.getId(), user.getName()));
		}
		
		return volunteers;
	}
	
	public void addUserToGroup(String groupName, User user, String groupNameToRemoveFrom) {
		if (StringUtil.isEmpty(groupName) || user == null)
			return;
		
		GroupBusiness groupBusiness = getServiceInstance(GroupBusiness.class);
		Collection<Group> groups = null;
		if (!StringUtil.isEmpty(groupName)) {
			groups = getGroupsByName(groupBusiness, groupName);
			if (!ListUtil.isEmpty(groups)) {
				for (Group group: groups) {
					try {
						groupBusiness.addUser(Integer.valueOf(group.getId()), user);
					} catch (Exception e) {
						getLogger().log(Level.WARNING, "Error adding user " + user + " to the group: " + group, e);
					}
				}
			}
		}
		
		if (StringUtil.isEmpty(groupNameToRemoveFrom))
			return;
		
		groups = getGroupsByName(groupBusiness, groupNameToRemoveFrom);
		if (ListUtil.isEmpty(groups))
			return;
		UserBusiness userBusiness = getServiceInstance(UserBusiness.class);
		for (Group group: groups) {
			try {
				userBusiness.removeUserFromGroup(user, group, user);
			} catch (Exception e) {
				getLogger().log(Level.WARNING, "Error removing user: " + user + " from group: " + group, e);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private Collection<Group> getGroupsByName(GroupBusiness groupBusiness, String name) {
		Collection<Group> groups = null;
		try {
			groups = groupBusiness.getGroupsByGroupName(name);
		} catch (RemoteException e) {
			getLogger().log(Level.WARNING, "Error getting groups by name: " + name);
		}
		return groups;
	}

	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof CompanyCreatedEvent) {
			CompanyCreatedEvent companyCreated = (CompanyCreatedEvent) event;
			addUserToGroup(VolunteerConstants.GROUP_VOLUNTEERS_ORGANIZATION, companyCreated.getUser(), VolunteerConstants.GROUP_VOLUNTEERS);
			return;
		}
		
		if (event instanceof UserCreatedEvent) {
			User volunteer = ((UserCreatedEvent) event).getUser();
			addUserToGroup(VolunteerConstants.GROUP_VOLUNTEERS, volunteer, null);
			return;
		}
	}
	
	public Map<Locale, Map<String, String>> getVolunteerOrganizationTypes() {
		Map<Locale, Map<String, String>> types = new HashMap<Locale, Map<String,String>>();
		
		Map<String, String> localizedTypes = new HashMap<String, String>();
		types.put(getCurrentLocale(), localizedTypes);
		
		IWResourceBundle iwrb = getResourceBundle(getBundle(VolunteerConstants.IW_BUNDLE_IDENTIFIER));
		localizedTypes.put("management", iwrb.getLocalizedString("org_type.management", "Management"));
		localizedTypes.put("enterprise", iwrb.getLocalizedString("org_type.eterprise", "Enterprise"));
		localizedTypes.put("association", iwrb.getLocalizedString("org_type.association", "Association"));
		
		return types;
	}
	
	public String getCurrentDate() {
		IWTimestamp date = new IWTimestamp(System.currentTimeMillis());
		String dateString = date.getDateString("yyyy-MM-dd");
		return dateString;
	}
	
	public Map<Locale, Map<String, String>> getVolunteerAreasOfInterest() {
		Map<Locale, Map<String, String>> areas = new HashMap<Locale, Map<String,String>>();
		
		Map<String, String> localizedAreas = new LinkedHashMap<String, String>();
		areas.put(getCurrentLocale(), localizedAreas);
		
		IWResourceBundle iwrb = getResourceBundle(getBundle(VolunteerConstants.IW_BUNDLE_IDENTIFIER));
		localizedAreas.put("all", iwrb.getLocalizedString("all", "All"));
		localizedAreas.put("animals", iwrb.getLocalizedString("animals", "Animals"));
		localizedAreas.put("sports", iwrb.getLocalizedString("sports", "Sports"));
		localizedAreas.put("culture", iwrb.getLocalizedString("culture", "Culture"));
		localizedAreas.put("seniors_older", iwrb.getLocalizedString("seniors_older", "Seniors, older"));
		localizedAreas.put("social", iwrb.getLocalizedString("social", "Social care"));
		localizedAreas.put("children_youth", iwrb.getLocalizedString("children_youth", "Children & Youth"));
		localizedAreas.put("school", iwrb.getLocalizedString("school", "School"));
		localizedAreas.put("events", iwrb.getLocalizedString("events", "Events"));
		localizedAreas.put("association_mission", iwrb.getLocalizedString("association_mission", "Association mission"));
		localizedAreas.put("computer_it_help", iwrb.getLocalizedString("computer_it_help", "Computer/IT Help"));
		localizedAreas.put("other", iwrb.getLocalizedString("other", "Other"));
		
		return areas;
	}
	
	public Map<Locale, Map<String, String>> getVolunteerAssignmentAreas() {
		Map<Locale, Map<String, String>> areas = new HashMap<Locale, Map<String,String>>();
		
		Map<String, String> localizedAreas = new LinkedHashMap<String, String>();
		areas.put(getCurrentLocale(), localizedAreas);
		
		IWResourceBundle iwrb = getResourceBundle(getBundle(VolunteerConstants.IW_BUNDLE_IDENTIFIER));
		
		localizedAreas.put("bankeryd", "Bankeryd");
		localizedAreas.put("barnarp", "Barnarp");
		localizedAreas.put("bottnaryd", "Bottnaryd");
		localizedAreas.put("granna", iwrb.getLocalizedString("granna", "Gränna"));
		localizedAreas.put("hovslatt", iwrb.getLocalizedString("hovslatt", "Hovslätt"));
		localizedAreas.put("huskvarna", "Huskvarna");
		localizedAreas.put("jonkoping_soder", iwrb.getLocalizedString("jonkoping_soder", "Jönköping söder"));
		localizedAreas.put("jonkoping_oster", iwrb.getLocalizedString("jonkoping_oster", "Jönköping öster"));
		localizedAreas.put("jonkoping_vaster", iwrb.getLocalizedString("jonkoping_vaster", "Jönköping väster"));
		localizedAreas.put("jonkoping_centrum", iwrb.getLocalizedString("jonkoping_centrum", "Jönköping centrum"));
		localizedAreas.put("kaxholmen", "Kaxholmen");
		localizedAreas.put("lekeryd", "Lekeryd");
		localizedAreas.put("mansarp", iwrb.getLocalizedString("mansarp", "Månsarp"));
		localizedAreas.put("norrahammar", "Norrahammar");
		localizedAreas.put("skarstad", iwrb.getLocalizedString("skarstad", "Skärstad"));
		localizedAreas.put("taberg", "Taberg");
		localizedAreas.put("tenhult", "Tenhult");
		localizedAreas.put("visingso", iwrb.getLocalizedString("visingso", "Visingsö"));
		localizedAreas.put("olmstad", iwrb.getLocalizedString("olmstad", "Ölmstad"));
		localizedAreas.put("oxnehaga", iwrb.getLocalizedString("oxnehaga", "Öxnehaga"));
		localizedAreas.put("hela_kommunen", "Hela kommunen");
		
		return areas;
	}
}