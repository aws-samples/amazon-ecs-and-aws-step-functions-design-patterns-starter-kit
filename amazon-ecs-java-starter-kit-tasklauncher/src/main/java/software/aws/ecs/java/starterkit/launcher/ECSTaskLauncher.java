// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package software.aws.ecs.java.starterkit.launcher;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.aws.ecs.java.starterkit.util.DDBUtil;
import software.aws.ecs.java.starterkit.util.TaskConfig;
import software.aws.ecs.java.starterkit.util.WorkflowSpecs;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ECSTaskLauncher implemented as an AWS Lambda function. It launches ECS tasks
 * based on a Workflow specifications provided as a JSON input.
 * 
 * @author Ravi Itha, Sr. Big Data Consultant
 *
 */
public class ECSTaskLauncher implements RequestHandler<WorkflowSpecs, Map<String, Object>> {

	@Override
	public Map<String, Object> handleRequest(WorkflowSpecs workflowSpecs, Context context) {

		context.getLogger().log("Input event: " + new Gson().toJson(workflowSpecs));
		String regionString = workflowSpecs.getRegion();
		String clusterName = workflowSpecs.getClusterName();
		String containerName = workflowSpecs.getContainerName();
		String taskDefinition = workflowSpecs.getTaskDefinition();
		String securityGroupId = workflowSpecs.getSecurityGroupId();
		String subnetIdLiteral = workflowSpecs.getSubnetIdLiteral();
		String separator = workflowSpecs.getSeparator();
		String ddbTableNameWFSummary = workflowSpecs.getDdbTableNameWFSummary();
		String hashKeyWFSummary = workflowSpecs.getHashKeyWFSummary();
		String rangeKeyWFSummary = workflowSpecs.getRangeKeyWFSummary();
		String ddbTableNameWFDetails = workflowSpecs.getDdbTableNameWFDetails();
		String hashKeyWFDetails = workflowSpecs.getHashKeyWFDetails();
		String rangeKeyWFDetails = workflowSpecs.getRangeKeyWFDetails();

		printEnvVariables(regionString, clusterName, containerName, taskDefinition, securityGroupId, subnetIdLiteral,
				separator, ddbTableNameWFSummary, hashKeyWFSummary, rangeKeyWFSummary, ddbTableNameWFDetails,
				hashKeyWFDetails, rangeKeyWFDetails);

		Collection<String> subnetIds = tokenizeStrings(subnetIdLiteral, separator);
		Collection<String> securityGroupIds = tokenizeStrings(securityGroupId, separator);

		Region region = Region.regions().stream().filter(r -> r.toString().equalsIgnoreCase(regionString)).findFirst()
				.orElse(Region.US_EAST_1);

		List<Task> tasks = new ArrayList<Task>();
		List<String> ecsTaskArns = new ArrayList<String>();
		DDBUtil ddbUtil = new DDBUtil();
		EcsClient ecs = EcsClient.builder().region(region).build();
		DynamoDbClient dynamoDB = DynamoDbClient.builder().region(region).build();

		long workflowRunId = System.currentTimeMillis();

		// TODO: validate the parsing
		List<TaskConfig> taskList = workflowSpecs.getTaskList();
		for (TaskConfig taskConfig : taskList) {
			// Prepare Container Overrides -for each ECS task
			Collection<KeyValuePair> environment = Arrays.asList(
					KeyValuePair.builder().name("region").value(regionString).build(),
					KeyValuePair.builder().name("workflow_details_ddb_table_name").value(ddbTableNameWFDetails).build(),
					KeyValuePair.builder().name("workflow_details_hash_key").value(hashKeyWFDetails).build(),
					KeyValuePair.builder().name("workflow_details_range_key").value(rangeKeyWFDetails).build(),
					KeyValuePair.builder().name("workflow_name").value(workflowSpecs.getWorkflowName()).build(),
					KeyValuePair.builder().name("workflow_run_id").value(Long.toString(workflowRunId)).build(),
					KeyValuePair.builder().name("task_name").value(taskConfig.getTaskName()).build(),
					KeyValuePair.builder().name("s3_bucket_name").value(taskConfig.getS3BucketName()).build(),
					KeyValuePair.builder().name("object_key").value(taskConfig.getObjectKey()).build());

			ContainerOverride co = ContainerOverride.builder().environment(environment).name(containerName).build();
			Collection<ContainerOverride> containerOverrides = Arrays.asList(co);
			TaskOverride overrides = TaskOverride.builder().containerOverrides(containerOverrides).build();

			// Submit ECS Task
			Task task = submitECSTask(ecs, subnetIds, securityGroupIds, overrides, clusterName, taskDefinition);
			tasks.add(task);
			ecsTaskArns.add(task.taskArn());
		}
		// Insert status to DynamoDB Table
		String startTime = new Date().toString();
		ddbUtil.insertWorkflowSummary(dynamoDB, ddbTableNameWFSummary, hashKeyWFSummary, rangeKeyWFSummary,
				workflowSpecs.getWorkflowName(), new Gson().toJson(workflowSpecs), workflowRunId, tasks.size(),
				"Running", startTime);

		/**
		 * Prepare response to AWS Step Functions State Machine. This will model
		 * Iterator design pattern.
		 * 
		 */
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("workflowName", workflowSpecs.getWorkflowName());
		map.put("workflowRunId", workflowRunId);
		map.put("ecsTaskArns", ecsTaskArns);
		return map;
	}

	/**
	 * This method runs an ECS Task
	 * 
	 * @param ecs
	 * @param subnetIds
	 * @param securityGroupIds
	 * @param taskOverrides
	 * @param clusterName
	 * @param taskDefinition
	 * @return
	 */
	public Task submitECSTask(EcsClient ecs, Collection<String> subnetIds, Collection<String> securityGroupIds,
			TaskOverride taskOverrides, String clusterName, String taskDefinition) {

		System.out.println("Submitting ECS Tasks");
		List<Task> tasks = null;
		AwsVpcConfiguration awsvpcConfiguration = AwsVpcConfiguration.builder().subnets(subnetIds)
				.securityGroups(securityGroupIds).build();
		NetworkConfiguration networkConfiguration = NetworkConfiguration.builder()
				.awsvpcConfiguration(awsvpcConfiguration).build();
		RunTaskRequest runTaskRequest = RunTaskRequest.builder().cluster(clusterName).taskDefinition(taskDefinition)
				.launchType(LaunchType.FARGATE).networkConfiguration(networkConfiguration).overrides(taskOverrides)
				.build();
		try {
			RunTaskResponse response = ecs.runTask(runTaskRequest);
			// Process the response
			tasks = response.tasks();
			for (Task task : tasks) {
				System.out.println("Task ARN: " + task.taskArn());
				System.out.println("Task Def ARN: " + task.taskDefinitionArn());
				System.out.println("Cluster ARN: " + task.clusterArn());
				System.out.println("Task CPU: " + task.cpu());
				System.out.println("Task Memory: " + task.memory());
				System.out.println("Task Last Status: " + task.lastStatus());
				System.out.println("Task Start Time: " + task.startedAt());
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Cannot run ECS Task.");
		}
		return tasks.get(0);
	}

	/**
	 * This method tokenizes strings using a provided separator
	 * 
	 * @param str
	 * @param separator
	 * @return
	 */
	public static List<String> tokenizeStrings(String str, String separator) {
		List<String> tokenList = Collections.list(new StringTokenizer(str, separator)).stream()
				.map(token -> (String) token).collect(Collectors.toList());
		return tokenList;
	}

	/**
	 * This method prints all input parameters
	 * 
	 * @param regionString
	 * @param clusterName
	 * @param containerName
	 * @param taskDefinition
	 * @param securityGroupId
	 * @param subnetIdLiteral
	 * @param separator
	 * @param ddbTableNameWFSummary
	 * @param hashKeyWFSummary
	 * @param rangeKeyWFSummary
	 * @param ddbTableNameWFDetails
	 * @param hashKeyWFDetails
	 * @param rangeKeyWFDetails
	 */
	public static void printEnvVariables(String regionString, String clusterName, String containerName,
			String taskDefinition, String securityGroupId, String subnetIdLiteral, String separator,
			String ddbTableNameWFSummary, String hashKeyWFSummary, String rangeKeyWFSummary,
			String ddbTableNameWFDetails, String hashKeyWFDetails, String rangeKeyWFDetails) {
		System.out.println("regionString: " + regionString);
		System.out.println("clusterName: " + clusterName);
		System.out.println("containerName: " + containerName);
		System.out.println("taskDefinition: " + taskDefinition);
		System.out.println("securityGroupId: " + securityGroupId);
		System.out.println("subnetIdLiteral: " + subnetIdLiteral);
		System.out.println("separator: " + separator);
		System.out.println("ddbTableNameWFSummary: " + ddbTableNameWFSummary);
		System.out.println("hashKeyWFSummary: " + hashKeyWFSummary);
		System.out.println("rangeKeyWFSummary: " + rangeKeyWFSummary);
		System.out.println("ddbTableNameWFDetails: " + ddbTableNameWFDetails);
		System.out.println("hashKeyWFDetails: " + hashKeyWFDetails);
		System.out.println("rangeKeyWFDetails: " + rangeKeyWFDetails);
	}

	public static WorkflowSpecs parseWorkflowSpecs(String jsonString) {
		WorkflowSpecs workflowSpecs = null;
		try {
			workflowSpecs = new Gson().fromJson(jsonString, WorkflowSpecs.class);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return workflowSpecs;
	}

}
