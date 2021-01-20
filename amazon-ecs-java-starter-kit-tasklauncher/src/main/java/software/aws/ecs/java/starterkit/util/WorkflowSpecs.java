// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package software.aws.ecs.java.starterkit.util;

import java.util.List;

public class WorkflowSpecs {

	private String workflowName;
	private String region;
	private String clusterName;
	private String containerName;
	private String taskDefinition;
	private String securityGroupId;
	private String subnetIdLiteral;
	private String separator;
	private String ddbTableNameWFSummary;
	private String hashKeyWFSummary;
	private String rangeKeyWFSummary;
	private String ddbTableNameWFDetails;
	private String hashKeyWFDetails;
	private String rangeKeyWFDetails;
	private List<TaskConfig> taskList;
	
	public String getWorkflowName() {
		return workflowName;
	}

	public void setWorkflowName(String workflowName) {
		this.workflowName = workflowName;
	}

	public List<TaskConfig> getTaskList() {
		return taskList;
	}

	public void setTaskList(List<TaskConfig> taskList) {
		this.taskList = taskList;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getClusterName() {
		return clusterName;
	}

	public void setClusterName(String clusterName) {
		this.clusterName = clusterName;
	}

	public String getContainerName() {
		return containerName;
	}

	public void setContainerName(String containerName) {
		this.containerName = containerName;
	}

	public String getTaskDefinition() {
		return taskDefinition;
	}

	public void setTaskDefinition(String taskDefinition) {
		this.taskDefinition = taskDefinition;
	}

	

	public String getSecurityGroupId() {
		return securityGroupId;
	}

	public void setSecurityGroupId(String securityGroupId) {
		this.securityGroupId = securityGroupId;
	}

	public String getSubnetIdLiteral() {
		return subnetIdLiteral;
	}

	public void setSubnetIdLiteral(String subnetIdLiteral) {
		this.subnetIdLiteral = subnetIdLiteral;
	}

	public String getSeparator() {
		return separator;
	}

	public void setSeparator(String separator) {
		this.separator = separator;
	}

	public String getDdbTableNameWFSummary() {
		return ddbTableNameWFSummary;
	}

	public void setDdbTableNameWFSummary(String ddbTableNameWFSummary) {
		this.ddbTableNameWFSummary = ddbTableNameWFSummary;
	}

	public String getHashKeyWFSummary() {
		return hashKeyWFSummary;
	}

	public void setHashKeyWFSummary(String hashKeyWFSummary) {
		this.hashKeyWFSummary = hashKeyWFSummary;
	}

	public String getRangeKeyWFSummary() {
		return rangeKeyWFSummary;
	}

	public void setRangeKeyWFSummary(String rangeKeyWFSummary) {
		this.rangeKeyWFSummary = rangeKeyWFSummary;
	}

	public String getDdbTableNameWFDetails() {
		return ddbTableNameWFDetails;
	}

	public void setDdbTableNameWFDetails(String ddbTableNameWFDetails) {
		this.ddbTableNameWFDetails = ddbTableNameWFDetails;
	}

	public String getHashKeyWFDetails() {
		return hashKeyWFDetails;
	}

	public void setHashKeyWFDetails(String hashKeyWFDetails) {
		this.hashKeyWFDetails = hashKeyWFDetails;
	}

	public String getRangeKeyWFDetails() {
		return rangeKeyWFDetails;
	}

	public void setRangeKeyWFDetails(String rangeKeyWFDetails) {
		this.rangeKeyWFDetails = rangeKeyWFDetails;
	}

}
