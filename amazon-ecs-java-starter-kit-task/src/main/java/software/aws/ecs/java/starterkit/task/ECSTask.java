// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package software.aws.ecs.java.starterkit.task;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.aws.ecs.java.starterkit.util.DDBUtil;

public class ECSTask {

	public static void main(String[] args) {

		String regionPassed = System.getenv("region");
		String tableName = System.getenv("workflow_details_ddb_table_name");
		String hashKey = System.getenv("workflow_details_hash_key");
		String rangeKey = System.getenv("workflow_details_range_key");
		String workflowName = System.getenv("workflow_name");
		long workflowRunId = Long.parseLong(System.getenv("workflow_run_id"));
		String taskName = System.getenv("task_name");
		String bucketName = System.getenv("s3_bucket_name");
		String objectKey = System.getenv("object_key");
		String taskMetadataEndpoint = System.getenv("ECS_CONTAINER_METADATA_URI");
		
		long startTime = System.currentTimeMillis();

		// print runtime properties of the task
		printInputParameters(regionPassed, tableName, hashKey, rangeKey, workflowName, workflowRunId, taskName, 
				bucketName, objectKey, taskMetadataEndpoint);
		String destinationKey = objectKey.concat("_").concat(UUID.randomUUID().toString());

		// Create objects
		Region region = Region.regions().stream().filter(r -> r.toString().equalsIgnoreCase(regionPassed)).findFirst()
				.orElse(Region.US_EAST_1);
		S3Client s3 = S3Client.builder().region(region).build();
		DynamoDbClient dynamoDB = DynamoDbClient.builder().region(region).build();
		DDBUtil ddbUtil = new DDBUtil();

		// get Task ARN
		String response = "";
		HttpURLConnection con = getHTTPConnectionForTaskMetadataEndpoint(taskMetadataEndpoint.concat("/task"));
		System.out.println("HTTP Connection: " + con.getURL().toString());
		try {
			response = getFullResponse(con);
			System.out.println("Response from HTTP Connection: " + response);
		} catch (IOException e) {
			e.printStackTrace();
		}
		String taskARN = getTaskARN(response);
		System.out.println("Task ARN: " + taskARN);

		// insert job running status in DynamoDB table
		String status = "Running";
		String insertTime = new Date().toString();
		ddbUtil.insertTaskStatus(dynamoDB, tableName, hashKey, rangeKey, workflowRunId, taskARN, taskName, status, insertTime);

		// perform the task - actual business logic
		boolean objectCopied = copyFile(s3, bucketName, objectKey, destinationKey);
		
		// a random sleep interval from 1 to 3 minutes
		int waitTime  = (1 + new Random().nextInt(3)) * 60000;
		System.out.printf("Task sleeping  for %s seconds \n", waitTime);
		try {
			Thread.sleep(waitTime);
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// update job completion status in DynamoDB table
		String updateTime = new Date().toString();
		if (objectCopied)
			status = "Completed";
		else
			status = "Failed";
		
		long endTime = System.currentTimeMillis();
		long execTimeinSeconds = (endTime - startTime)/1000;
		ddbUtil.updateTaskStatus(dynamoDB, tableName, hashKey, rangeKey, workflowRunId, taskARN, status, updateTime, execTimeinSeconds);
	}

	/**
	 * This method retrieves TaskARN from TaskMetadataEndpoint's response
	 * @param response
	 * @return
	 */
	public static String getTaskARN(String response) {
		String taskARN = null;
		try {
			JsonParser parser = new JsonParser();
			JsonElement rootNode = parser.parse(response);
			if (rootNode.isJsonObject()) {
				JsonElement jsonElement = rootNode.getAsJsonObject().get("TaskARN");
				taskARN = jsonElement.getAsString();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return taskARN;
	}

	/**
	 * This method prints runtime properties sent to the ECS Task
	 * @param regionPassed
	 * @param tableName
	 * @param hashKey
	 * @param rangeKey
	 * @param workflowName
	 * @param workflowRunId
	 * @param taskName
	 * @param bucketName
	 * @param sourceKey
	 * @param taskMetadataEndpoint
	 */
	public static void printInputParameters(String regionPassed, String tableName, String hashKey,
			String rangeKey, String workflowName, long workflowRunId, String taskName, String bucketName,
			String sourceKey, String taskMetadataEndpoint) {
		System.out.println("regionPassed: " + regionPassed);
		System.out.println("tableName: " + tableName);
		System.out.println("hashKey: " + hashKey);
		System.out.println("rangeKey: " + rangeKey);
		System.out.println("workflowName: " + workflowName);
		System.out.println("workflowRunId: " + workflowRunId);
		System.out.println("taskName: " + taskName);
		System.out.println("bucketName: " + bucketName);
		System.out.println("sourceKey: " + sourceKey);
		System.out.println("taskMetadataEndpoint: " + taskMetadataEndpoint);
	}

	/**
	 * This is a representation business logic of the ECS Task. This method creates
	 * a copy of the input object
	 * 
	 * @param s3
	 * @param bucketName
	 * @param objectKey
	 * @param destinationKey
	 */
	public static boolean copyFile(S3Client s3, String bucketName, String objectKey, String destinationKey) {
		// Build S3 object key
		String encodedUrl = null;
		boolean objectCopied = false;
		try {
			encodedUrl = URLEncoder.encode(bucketName + "/" + objectKey, StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException e) {
			System.out.println("URL could not be encoded: " + e.getMessage());
		}
		// Copy object request
		CopyObjectRequest copyReq = CopyObjectRequest.builder().copySource(encodedUrl).destinationBucket(bucketName)
				.destinationKey(destinationKey).build();
		try {
			CopyObjectResponse copyRes = s3.copyObject(copyReq);
			if (copyRes.sdkHttpResponse().isSuccessful()) {
				System.out.println("Copy operation successful");
				objectCopied = true;
			}
			else
				System.out.println("Copy operation not successful");
		} catch (S3Exception e) {
			System.out.println("Exception thrown while copying S3 object");
			System.err.println(e.awsErrorDetails().errorMessage());
		}
		return objectCopied;
	}

	/**
	 * This method gets HTTP Connection from ECS TaskMetadataEndpoint
	 * 
	 * @param taskMetadataEndpoint
	 * @return
	 */
	public static HttpURLConnection getHTTPConnectionForTaskMetadataEndpoint(String taskMetadataEndpoint) {
		URL url = null;
		HttpURLConnection con = null;
		try {
			url = new URL(taskMetadataEndpoint);
			con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("GET");
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (ProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return con;
	}

	/**
	 * This methods gets a response from ECS Task Metadata endpoint
	 * 
	 * @param con
	 * @return
	 * @throws IOException
	 */
	public static String getFullResponse(HttpURLConnection con) throws IOException {
		StringBuilder fullResponseBuilder = new StringBuilder();
		fullResponseBuilder.append(con.getResponseCode()).append(" ").append(con.getResponseMessage()).append("\n");
		con.getHeaderFields().entrySet().stream().filter(entry -> entry.getKey() != null).forEach(entry -> {
			fullResponseBuilder.append(entry.getKey()).append(": ");
			List<String> headerValues = entry.getValue();
			Iterator<String> it = headerValues.iterator();
			if (it.hasNext()) {
				fullResponseBuilder.append(it.next());
				while (it.hasNext()) {
					fullResponseBuilder.append(", ").append(it.next());
				}
			}
			fullResponseBuilder.append("\n");
		});
		Reader streamReader = null;
		if (con.getResponseCode() > 299) {
			streamReader = new InputStreamReader(con.getErrorStream());
		} else {
			streamReader = new InputStreamReader(con.getInputStream());
		}
		BufferedReader in = new BufferedReader(streamReader);
		String inputLine;
		StringBuilder content = new StringBuilder();
		while ((inputLine = in.readLine()) != null) {
			content.append(inputLine);
		}
		in.close();
		return content.toString();
	}
}