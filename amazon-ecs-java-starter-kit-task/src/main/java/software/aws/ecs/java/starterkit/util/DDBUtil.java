// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package software.aws.ecs.java.starterkit.util;

import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeAction;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

public class DDBUtil {

	/**
	 * This method inserts a record to workflow_details table
	 * 
	 * @param ddbClient
	 * @param tableName
	 * @param hashKey
	 * @param rangeKey
	 * @param workflowId
	 * @param ecsTaskId
	 * @param status
	 * @param time
	 * @return
	 */
	public boolean insertTaskStatus(DynamoDbClient ddbClient, String tableName, String hashKey, String rangeKey,
			long workflowId, String ecsTaskId, String taskName, String status, String time) {

		boolean itemInserted = false;
		// Populate item
		HashMap<String, AttributeValue> itemValues = new HashMap<String, AttributeValue>();
		itemValues.put(hashKey, AttributeValue.builder().n(Long.toString(workflowId)).build());
		itemValues.put(rangeKey, AttributeValue.builder().s(ecsTaskId).build());
		itemValues.put("task_name", AttributeValue.builder().s(taskName).build());
		itemValues.put("start_time", AttributeValue.builder().s(time).build());
		itemValues.put("status", AttributeValue.builder().s(status).build());
		// Create a PutItemRequest object
		PutItemRequest request = PutItemRequest.builder().tableName(tableName).item(itemValues).build();
		try {
			ddbClient.putItem(request);
			itemInserted = true;
			System.out.printf("An item added to %s successfully. \n", tableName);

		} catch (DynamoDbException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}
		return itemInserted;
	}

	/**
	 * This method updates a record in workflow_details table
	 * 
	 * @param ddbClient
	 * @param tableName
	 * @param hashKey
	 * @param rangeKey
	 * @param workflowId
	 * @param ecsTaskId
	 * @param status
	 * @param time
	 * @return
	 */
	public boolean updateTaskStatus(DynamoDbClient ddbClient, String tableName, String hashKey, String rangeKey,
			long workflowId, String ecsTaskId, String status, String time, long execTimeinSeconds) {
		boolean operationSuccess = false;
		// populate Hash Key and Range Key
		Map<String, AttributeValue> key = new HashMap<String, AttributeValue>();
		key.put(hashKey, AttributeValue.builder().n(Long.toString(workflowId)).build());
		key.put(rangeKey, AttributeValue.builder().s(ecsTaskId).build());

		AttributeAction action = AttributeAction.PUT;
		Map<String, AttributeValueUpdate> attributeUpdates = new HashMap<String, AttributeValueUpdate>();
		attributeUpdates.put("status", AttributeValueUpdate.builder().action(action)
				.value(AttributeValue.builder().s(status).build()).build());
		attributeUpdates.put("update_time",
				AttributeValueUpdate.builder().action(action).value(AttributeValue.builder().s(time).build()).build());
		attributeUpdates.put("exec_time_in_seconds", AttributeValueUpdate.builder().action(action)
				.value(AttributeValue.builder().n(Long.toString(execTimeinSeconds)).build()).build());

		UpdateItemRequest updateItemRequest = UpdateItemRequest.builder().tableName(tableName).key(key)
				.attributeUpdates(attributeUpdates).build();
		try {
			UpdateItemResponse updateItemResponse = ddbClient.updateItem(updateItemRequest);
			if (updateItemResponse.sdkHttpResponse().isSuccessful()) {
				operationSuccess = true;
				System.out.printf("Update item operation with hash_key: %d and range_key: %s was successful. \n",
						workflowId, ecsTaskId);
			} else
				System.out.printf("Update item operation with hash_key: %d and range_key: %s was not successful. \n",
						workflowId, ecsTaskId);
		} catch (ResourceNotFoundException e) {
			e.printStackTrace();
			System.out.println("Table not found");
		}
		return operationSuccess;
	}

}
