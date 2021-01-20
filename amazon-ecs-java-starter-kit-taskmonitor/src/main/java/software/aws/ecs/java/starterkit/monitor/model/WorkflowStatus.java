package software.aws.ecs.java.starterkit.monitor.model;

import java.util.List;

public class WorkflowStatus {

	private String status;
	private List<String> completedTasks;
	private List<String> failedTasks;
	private List<String> runningTasks;
	
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public List<String> getCompletedTasks() {
		return completedTasks;
	}
	public void setCompletedTasks(List<String> completedTasks) {
		this.completedTasks = completedTasks;
	}
	public List<String> getFailedTasks() {
		return failedTasks;
	}
	public void setFailedTasks(List<String> failedTasks) {
		this.failedTasks = failedTasks;
	}
	public List<String> getRunningTasks() {
		return runningTasks;
	}
	public void setRunningTasks(List<String> runningTasks) {
		this.runningTasks = runningTasks;
	}
}
