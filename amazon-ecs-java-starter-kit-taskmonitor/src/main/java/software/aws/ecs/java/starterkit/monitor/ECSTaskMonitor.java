// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package software.aws.ecs.java.starterkit.monitor;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.aws.ecs.java.starterkit.monitor.model.Input;
import software.aws.ecs.java.starterkit.monitor.model.WorkflowStatus;
import software.aws.ecs.java.starterkit.util.DDBUtil;

import java.util.*;

public class ECSTaskMonitor implements RequestHandler<Input, Map<String, Object>> {
	@Override
	public Map<String, Object> handleRequest(Input input, Context context) {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		LambdaLogger logger = context.getLogger();
		logger.log("Input event type: " + input.getClass().toString());
		logger.log("Input event: " + gson.toJson(input));
		logger.log("Workflow Run Id: " + input.getIterator().getWorkflowRunId());
		logger.log("Workflow Name: " + input.getIterator().getWorkflowName());
		logger.log("Numm of Task ARNs: " + input.getIterator().getEcsTaskArns().size());
		for (String taskARN : input.getIterator().getEcsTaskArns()) {
			logger.log("Task ARN: " + taskARN);
		}

		String regionString = Optional.ofNullable(System.getenv("region")).orElse(Regions.US_EAST_1.getName());
		String ddbTableNameWFSummary = Optional.ofNullable(System.getenv("workflow_summary_ddb_table_name"))
				.orElse("workflow_summary");
		String hashKeyWFSummary = Optional.ofNullable(System.getenv("workflow_summary_hash_key"))
				.orElse("workflow_name");
		String rangeKeyWFSummary = Optional.ofNullable(System.getenv("workflow_summary_range_key"))
				.orElse("workflow_run_id");
		String ddbTableNameWFDetails = Optional.ofNullable(System.getenv("workflow_details_ddb_table_name"))
				.orElse("workflow_details");
		String hashKeyWFDetails = Optional.ofNullable(System.getenv("workflow_details_hash_key"))
				.orElse("workflow_run_id");
		String rangeKeyWFDetails = Optional.ofNullable(System.getenv("workflow_details_range_key"))
				.orElse("ecs_task_id");

		printEnvVariables(logger, regionString, ddbTableNameWFSummary, hashKeyWFSummary, rangeKeyWFSummary,
				ddbTableNameWFDetails, hashKeyWFDetails, rangeKeyWFDetails);

		List<String> completedTasks = new ArrayList<String>();
		List<String> failedTasks = new ArrayList<String>();
		List<String> runningTasks = new ArrayList<String>();
		WorkflowStatus workflowStatus = new WorkflowStatus();

		Region region = Region.regions().stream().filter(r -> r.toString().equalsIgnoreCase(regionString)).findFirst()
				.orElse(Region.US_EAST_1);
		DDBUtil ddbUtil = new DDBUtil();
		DynamoDbClient dynamoDB = DynamoDbClient.builder().region(region).build();

		// Populate the iterator object
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("workflowName", input.getIterator().getWorkflowName());
		map.put("workflowRunId", input.getIterator().getWorkflowRunId());
		map.put("ecsTaskArns", input.getIterator().getEcsTaskArns());

		// get all completed tasks from Workflow Details table
		List<Map<String, AttributeValue>> tasks = ddbUtil.getWorkflowDetails(dynamoDB, ddbTableNameWFDetails,
				hashKeyWFDetails, input.getIterator().getWorkflowRunId());
		System.out.printf("Number of Tasks retrieved from DDB: %d\n", tasks.size());

		// iterate the items and derive statistics
		for (Map<String, AttributeValue> item : tasks) {
			String status = "";
			String ecsTaskId = "";
			for (Map.Entry<String, AttributeValue> entry : item.entrySet()) {
				logger.log(entry.getKey() + ":" + entry.getValue());
				if (entry.getKey().equalsIgnoreCase("status")) {
					status = entry.getValue().s();
				} else {
					ecsTaskId = entry.getValue().s();
				}
			}
			if (status.equalsIgnoreCase("Completed"))
				completedTasks.add(ecsTaskId);
			else if (status.equalsIgnoreCase("Failed"))
				failedTasks.add(ecsTaskId);
			else
				runningTasks.add(ecsTaskId);
		}
		workflowStatus.setCompletedTasks(completedTasks);
		workflowStatus.setFailedTasks(failedTasks);
		workflowStatus.setRunningTasks(runningTasks);

		if (input.getIterator().getEcsTaskArns().size() == completedTasks.size() + failedTasks.size()) {
			System.out.printf("ECS Workflow Status: Completed tasks = %d, Failed tasks = %d, Running tasks = %d \n",
					completedTasks.size(), failedTasks.size(), runningTasks.size());
			workflowStatus.setStatus("Completed");
			map.put("continue", false);
		} else {
			System.out.printf("ECS Workflow Status: Completed tasks = %d, Failed tasks = %d, Running tasks = %d \n",
					completedTasks.size(), failedTasks.size(), runningTasks.size());
			workflowStatus.setStatus("Running");
			map.put("continue", true);
		}
		// updated workflow summary in DynamoDB
		ddbUtil.updateWorkflowSummary(dynamoDB, ddbTableNameWFSummary, hashKeyWFSummary, rangeKeyWFSummary,
				input.getIterator().getWorkflowName(), input.getIterator().getWorkflowRunId(), workflowStatus.getStatus(), new Date().toString(),
				completedTasks.size(), failedTasks.size(), runningTasks.size());

		return map;
	}

	/**
	 * This method prints environment variables
	 * 
	 * @param regionString
	 * @param ddbTableNameWFSummary
	 * @param hashKeyWFSummary
	 * @param rangeKeyWFSummary
	 * @param ddbTableNameWFDetails
	 * @param hashKeyWFDetails
	 * @param rangeKeyWFDetails
	 */
	public static void printEnvVariables(LambdaLogger logger, String regionString, String ddbTableNameWFSummary, String hashKeyWFSummary,
			String rangeKeyWFSummary, String ddbTableNameWFDetails, String hashKeyWFDetails, String rangeKeyWFDetails) {
		logger.log("regionString: " + regionString);
		logger.log("ddbTableNameWFSummary: " + ddbTableNameWFSummary);
		logger.log("hashKeyWFSummary: " + hashKeyWFSummary);
		logger.log("rangeKeyWFSummary: " + rangeKeyWFSummary);
		logger.log("ddbTableNameWFDetails: " + ddbTableNameWFDetails);
		logger.log("hashKeyWFDetails: " + hashKeyWFDetails);
		logger.log("rangeKeyWFDetails: " + rangeKeyWFDetails);
	}
}
