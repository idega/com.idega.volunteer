package com.idega.volunteer.business;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import javax.ejb.FinderException;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.idega.core.business.DefaultSpringBean;
import com.idega.event.UserCreatedEvent;
import com.idega.user.business.GroupBusiness;
import com.idega.user.data.Group;
import com.idega.user.data.User;
import com.idega.util.ListUtil;
import com.idega.util.StringUtil;
import com.idega.volunteer.VolunteerConstants;

@Service("volunteerServices")
@Scope(BeanDefinition.SCOPE_SINGLETON)
public class VolunteerServices extends DefaultSpringBean implements ApplicationListener {

	public Map<Locale, Map<String, String>> getVolunteerAssignmentTypes() {
		Map<Locale, Map<String, String>> allTypes = new HashMap<Locale, Map<String,String>>();
		
		//	TODO: use real types
		Map<String, String> types = new HashMap<String, String>();
		allTypes.put(getCurrentLocale(), types);
		types.put("1", "Type 1");
		types.put("2", "Type 2");
		types.put("3", "Type 3");
		types.put("4", "Type 4");
		types.put("5", "Type 5");
		
		return allTypes;
	}
	
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
	
	public Map<Locale, Map<String, String>> getSuggestedVolunteers() {
		Map<Locale, Map<String, String>> allVolunteers = new HashMap<Locale, Map<String,String>>();
		
		//	TODO:	use real suggested volunteers
		Map<String, String> volunteers = new HashMap<String, String>();
		allVolunteers.put(getCurrentLocale(), volunteers);
		
		volunteers.put("1", "Suggessted Volunter_1");
		volunteers.put("2", "Suggested Volunteer_2");
		volunteers.put("3", "Suggested Volunteer_3");
		
		return allVolunteers;
	}
	
	@SuppressWarnings("unchecked")
	private void addUserToGroup(String groupName, User user) {
		if (StringUtil.isEmpty(groupName) || user == null)
			return;
		
		GroupBusiness groupBusiness = getServiceInstance(GroupBusiness.class);
		Collection<Group> groups = null;
		try {
			groups = groupBusiness.getGroupsByGroupName(groupName);
		} catch (RemoteException e) {
			getLogger().log(Level.WARNING, "Error getting groups by name: " + groupName);
		}
		if (ListUtil.isEmpty(groups))
			return;
		
		for (Group group: groups) {
			try {
				groupBusiness.addUser(Integer.valueOf(group.getId()), user);
			} catch (Exception e) {
				getLogger().log(Level.WARNING, "Error adding user " + user + " to the group: " + group, e);
			}
		}
	}

	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof UserCreatedEvent) {
			User volunteer = ((UserCreatedEvent) event).getUser();
			addUserToGroup(VolunteerConstants.GROUP_VOLUNTEERS, volunteer);
		}
	}
}