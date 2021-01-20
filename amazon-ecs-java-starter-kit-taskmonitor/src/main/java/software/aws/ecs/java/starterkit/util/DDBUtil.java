// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package software.aws.ecs.java.starterkit.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeAction;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate;
import software.amazon.awssdk.services.dynamodb.model.Condition;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

public class DDBUtil {

	/**
	 * This method gets the status of ECS tasks for a given workflow_run_id
	 * @param dynamoDB
	 * @param tableName
	 * @param hashKey
	 * @param hashKeyValue
	 * @param statusKey
	 * @param statusKeyValue
	 * @return
	 */
	public List<Map<String, AttributeValue>> getWorkflowDetails(DynamoDbClient dynamoDB, String tableName,
			String hashKey, long hashKeyValue, String statusKey, String statusKeyValue) {

		String keyConditionExpression = "#part_key = :workflowRunId";
		String filterExpression = "#status = :required_status";

		Map<String, String> expressionAttributeNames = new HashMap<String, String>();
		expressionAttributeNames.put("#part_key", hashKey);
		expressionAttributeNames.put("#status", statusKey);

		Map<String, AttributeValue> expressionAttributeValues = new HashMap<String, AttributeValue>();
		expressionAttributeValues.put(":workflowRunId", AttributeValue.builder().n(Long.toString(hashKeyValue)).build());
		expressionAttributeValues.put(":required_status", AttributeValue.builder().s(statusKeyValue).build());

		QueryRequest queryRequest = QueryRequest.builder().tableName(tableName)
				.keyConditionExpression(keyConditionExpression).filterExpression(filterExpression)
				.expressionAttributeNames(expressionAttributeNames).expressionAttributeValues(expressionAttributeValues)
				.build();
		QueryResponse response = dynamoDB.query(queryRequest);
		List<Map<String, AttributeValue>> items = response.items();
		return items;
	}
	
	/**
	 * This method gets the status of ECS tasks for a given workflow_run_id. It
	 * retrieves data for all attributes.
	 * @param dynamoDB
	 * @param tableName
	 * @param hashKey
	 * @param hashKeyValue
	 * @return
	 */
	public List<Map<String, AttributeValue>> getWorkflowDetails(DynamoDbClient dynamoDB,
			String tableName, String hashKey, long hashKeyValue) {

		String keyConditionExpression = "#part_key = :workflowRunId";
		Map<String, String> expressionAttributeNames = new HashMap<String, String>();
		expressionAttributeNames.put("#part_key", hashKey);
		Map<String, AttributeValue> expressionAttributeValues = new HashMap<String, AttributeValue>();
		expressionAttributeValues.put(":workflowRunId",
				AttributeValue.builder().n(Long.toString(hashKeyValue)).build());
		
		Collection<String> attributesToGet = Arrays.asList("status");
		
		QueryRequest queryRequest = QueryRequest.builder().tableName(tableName)
				.keyConditionExpression(keyConditionExpression).expressionAttributeNames(expressionAttributeNames)
				.expressionAttributeValues(expressionAttributeValues).build();
		
		Map<String, Condition> keyConditions = new HashMap<String, Condition>();
		Collection<AttributeValue> attributeValueList = Arrays.asList(AttributeValue.builder().n(Long.toString(hashKeyValue)).build());
		keyConditions.put(hashKey, Condition.builder().attributeValueList(attributeValueList).comparisonOperator("EQ").build());
		
		QueryRequest queryRequest2 = QueryRequest.builder().tableName(tableName).keyConditions(keyConditions).attributesToGet(attributesToGet).build();
		dynamoDB.query(queryRequest2);

		QueryResponse response = dynamoDB.query(queryRequest);
		List<Map<String, AttributeValue>> items = response.items();
		return items;
	}
	
	/**
	 * This method gets the status of ECS tasks for a given workflow_run_id. It
	 * retrieves data for only few attributes.
	 * 
	 * @param dynamoDB
	 * @param tableName
	 * @param hashKey
	 * @param hashKeyValue
	 * @param rangeKey
	 * @return
	 */
	public List<Map<String, AttributeValue>> getWorkflowDetails(DynamoDbClient dynamoDB,
			String tableName, String hashKey, long hashKeyValue, String rangeKey) {

		Collection<String> attributesToGet = Arrays.asList("status", rangeKey);
		Map<String, Condition> keyConditions = new HashMap<String, Condition>();
		Collection<AttributeValue> attributeValueList = Arrays
				.asList(AttributeValue.builder().n(Long.toString(hashKeyValue)).build());
		keyConditions.put(hashKey,
				Condition.builder().attributeValueList(attributeValueList).comparisonOperator("EQ").build());

		QueryRequest queryRequest = QueryRequest.builder().tableName(tableName).keyConditions(keyConditions)
				.attributesToGet(attributesToGet).build();

		QueryResponse response = dynamoDB.query(queryRequest);
		List<Map<String, AttributeValue>> items = response.items();
		return items;
	}
	
	/**
	 * This method updates the status of Workflow Summary 
	 * @param dynamoDB
	 * @param tableName
	 * @param hashKey
	 * @param rangeKey
	 * @param workflowName
	 * @param workflowRunId
	 * @param status
	 * @param time
	 * @return
	 */
	public boolean updateWorkflowSummary(DynamoDbClient dynamoDB, String tableName, String hashKey, String rangeKey,
			String workflowName, long workflowRunId, String status, String time, int completedTasks, int failedTasks,
			int runningTasks) {
		boolean operationSuccess = false;

		// populate Hash Key and Range Key
		Map<String, AttributeValue> key = new HashMap<String, AttributeValue>();
		key.put(hashKey, AttributeValue.builder().s(workflowName).build());
		key.put(rangeKey, AttributeValue.builder().n(Long.toString(workflowRunId)).build());

		AttributeAction action = AttributeAction.PUT;
		Map<String, AttributeValueUpdate> attributeUpdates = new HashMap<String, AttributeValueUpdate>();
		attributeUpdates.put("status", AttributeValueUpdate.builder().action(action)
				.value(AttributeValue.builder().s(status).build()).build());
		attributeUpdates.put("update_time",
				AttributeValueUpdate.builder().action(action).value(AttributeValue.builder().s(time).build()).build());
		attributeUpdates.put("completed_tasks",
				AttributeValueUpdate.builder().action(action).value(AttributeValue.builder().n(Integer.toString(completedTasks)).build()).build());
		attributeUpdates.put("failed_tasks",
				AttributeValueUpdate.builder().action(action).value(AttributeValue.builder().n(Integer.toString(failedTasks)).build()).build());
		attributeUpdates.put("running_tasks",
				AttributeValueUpdate.builder().action(action).value(AttributeValue.builder().n(Integer.toString(runningTasks)).build()).build());

		UpdateItemRequest updateItemRequest = UpdateItemRequest.builder().tableName(tableName).key(key)
				.attributeUpdates(attributeUpdates).build();
		try {
			UpdateItemResponse updateItemResponse = dynamoDB.updateItem(updateItemRequest);
			if (updateItemResponse.sdkHttpResponse().isSuccessful()) {
				operationSuccess = true;
				System.out.printf("Update item operation with hash_key: %s and range_key: %d was successful. \n",
						workflowName, workflowRunId);
			} else
				System.out.printf("Update item operation with hash_key: %s and range_key: %d was not successful. \n",
						workflowName, workflowRunId);
		} catch (ResourceNotFoundException e) {
			e.printStackTrace();
			System.out.println("Table not found");
		}
		return operationSuccess;
	}
	
}
