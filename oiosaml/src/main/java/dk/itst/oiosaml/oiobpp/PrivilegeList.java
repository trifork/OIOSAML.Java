package dk.itst.oiosaml.oiobpp;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = { "privilegeGroup" })
@XmlRootElement(name = "PrivilegeList", namespace="http://digst.dk/oiosaml/basic_privilege_profile")
public class PrivilegeList {

	@XmlElement(name = "PrivilegeGroup", required = true)
	protected List<PrivilegeGroup> privilegeGroup;

	public void setPrivilegeGroup(List<PrivilegeGroup> privilegeGroup) {
		this.privilegeGroup = privilegeGroup;
	}
	
	public List<PrivilegeGroup> getPrivilegeGroup() {
		if (privilegeGroup == null) {
			privilegeGroup = new ArrayList<>();
		}

		return this.privilegeGroup;
	}
}