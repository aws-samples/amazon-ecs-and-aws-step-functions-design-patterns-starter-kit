// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package software.aws.ecs.java.starterkit.util;

import java.util.HashMap;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

public class DDBUtil {

	/**
	 * This method inserts an item to DynamoDB Table
	 * @param tableName
	 * @param workflowId
	 * @param status
	 * @param numberOfTasks
	 * @return
	 */
	public boolean insertWorkflowSummary(DynamoDbClient dynamoDB, String tableName, String hashKey, String rangeKey,
			String workflowName, String workflowSpecs, long workflowRunId, int numberOfTasks, String status,
			String time) {
		boolean itemInserted = false;
		HashMap<String, AttributeValue> itemValues = new HashMap<String, AttributeValue>();
		itemValues.put(hashKey, AttributeValue.builder().s(workflowName).build());
		itemValues.put(rangeKey, AttributeValue.builder().n(Long.toString(workflowRunId)).build());
		itemValues.put("workflow_specs", AttributeValue.builder().s(workflowSpecs).build());
		itemValues.put("number_of_tasks", AttributeValue.builder().n(Integer.toString(numberOfTasks)).build());
		itemValues.put("status", AttributeValue.builder().s(status).build());
		itemValues.put("start_time", AttributeValue.builder().s(time).build());

		// Create a PutItemRequest object
		PutItemRequest request = PutItemRequest.builder().tableName(tableName).item(itemValues).build();
		try {
			dynamoDB.putItem(request);
			itemInserted = true;
			System.out.printf("An item added to %s successfully. \n", tableName);

		} catch (DynamoDbException e) {
			System.err.println(e.getMessage());
		}
		return itemInserted;
	}

}
