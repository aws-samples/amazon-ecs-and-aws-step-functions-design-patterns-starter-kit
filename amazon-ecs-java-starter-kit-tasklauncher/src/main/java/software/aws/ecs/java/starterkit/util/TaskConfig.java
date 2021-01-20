// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package software.aws.ecs.java.starterkit.util;

public class TaskConfig {
	
	private String taskName;
	private String s3BucketName;
	private String objectKey;
	
	public String getTaskName() {
		return taskName;
	}
	public void setTaskName(String taskName) {
		this.taskName = taskName;
	}
	public String getS3BucketName() {
		return s3BucketName;
	}
	public void setS3BucketName(String s3BucketName) {
		this.s3BucketName = s3BucketName;
	}
	public String getObjectKey() {
		return objectKey;
	}
	public void setObjectKey(String objectKey) {
		this.objectKey = objectKey;
	}
	
}
