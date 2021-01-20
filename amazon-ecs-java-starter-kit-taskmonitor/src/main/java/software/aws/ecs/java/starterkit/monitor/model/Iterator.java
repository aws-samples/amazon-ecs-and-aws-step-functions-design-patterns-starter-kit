package software.aws.ecs.java.starterkit.monitor.model;

import java.util.List;

public class Iterator {
	
	private String workflowName;
	private long workflowRunId;
	private  List<String> ecsTaskArns;
	public String getWorkflowName() {
		return workflowName;
	}
	public void setWorkflowName(String workflowName) {
		this.workflowName = workflowName;
	}
	public long getWorkflowRunId() {
		return workflowRunId;
	}
	public void setWorkflowRunId(long workflowRunId) {
		this.workflowRunId = workflowRunId;
	}
	public List<String> getEcsTaskArns() {
		return ecsTaskArns;
	}
	public void setEcsTaskArns(List<String> ecsTaskArns) {
		this.ecsTaskArns = ecsTaskArns;
	}
}
