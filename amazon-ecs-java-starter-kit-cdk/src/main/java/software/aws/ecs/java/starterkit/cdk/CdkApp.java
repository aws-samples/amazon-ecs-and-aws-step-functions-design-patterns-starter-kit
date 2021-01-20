// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package software.aws.ecs.java.starterkit.cdk;

import software.amazon.awscdk.core.App;

/**
 * 
 * This CDK Application runs CloudFormation stacks. This is the entry point of
 * the application.
 * 
 * @author Sarma Palli, Senior DevOps Cloud Architect
 *
 */
public class CdkApp {
	public static void main(final String[] args) {
		App app = new App();
		new ECSTaskSubmissionFromLambdaPattern(app, "amazon-ecs-java-starter-pattern-1");
		new ECSTaskSubmissionFromStepFunctionsPattern(app, "amazon-ecs-java-starter-pattern-2");
		app.synth();
	}
}
