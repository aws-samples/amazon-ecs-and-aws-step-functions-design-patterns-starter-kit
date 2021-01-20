// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package software.aws.ecs.java.starterkit.cdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Fn;
import software.amazon.awscdk.core.RemovalPolicy;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.ec2.GatewayVpcEndpointAwsService;
import software.amazon.awscdk.services.ec2.GatewayVpcEndpointOptions;
import software.amazon.awscdk.services.ec2.InterfaceVpcEndpointAwsService;
import software.amazon.awscdk.services.ec2.InterfaceVpcEndpointOptions;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecr.assets.DockerImageAsset;
import software.amazon.awscdk.services.ecs.AwsLogDriver;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinition;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.stepfunctions.Chain;
import software.amazon.awscdk.services.stepfunctions.Choice;
import software.amazon.awscdk.services.stepfunctions.Condition;
import software.amazon.awscdk.services.stepfunctions.StateMachine;
import software.amazon.awscdk.services.stepfunctions.StateMachineType;
import software.amazon.awscdk.services.stepfunctions.Succeed;
import software.amazon.awscdk.services.stepfunctions.TaskInput;
import software.amazon.awscdk.services.stepfunctions.Wait;
import software.amazon.awscdk.services.stepfunctions.WaitTime;
import software.amazon.awscdk.services.stepfunctions.tasks.LambdaInvoke;

/**
 * 
 * This CDK Application runs a CloudFormation stack to demonstrate pattern
 * ECS Task submission from Lambda
 * 
 * @author Sarma Palli, Senior DevOps Cloud Architect
 *
 */
public class ECSTaskSubmissionFromLambdaPattern extends Stack {
	
	public ECSTaskSubmissionFromLambdaPattern(final Construct scope, final String id) {
		this(scope, id, null);
	}

	public ECSTaskSubmissionFromLambdaPattern(final Construct scope, final String id, final StackProps props) {
		super(scope, id, props);

		SubnetSelection privateSubnets = SubnetSelection.builder().subnetType(SubnetType.PRIVATE).build();
		// VPC
		Vpc vpc = Vpc.Builder.create(this, "StarterKitVPC").cidr("10.110.0.0/16").maxAzs(2)
				.gatewayEndpoints(new HashMap<String, GatewayVpcEndpointOptions>() {
					private static final long serialVersionUID = -2650622903964203923L;
					{
						put("S3EndPoint", GatewayVpcEndpointOptions.builder().service(GatewayVpcEndpointAwsService.S3)
								.subnets(new ArrayList<SubnetSelection>() {
									private static final long serialVersionUID = 1687752884565877349L;
									{
										add(privateSubnets);
									}
								}).build());
						put("DDBEndPoint",
								GatewayVpcEndpointOptions.builder().service(GatewayVpcEndpointAwsService.DYNAMODB)
										.subnets(new ArrayList<SubnetSelection>() {
											private static final long serialVersionUID = -746781466183802042L;
											{
												add(privateSubnets);
											}
										}).build());
					}
				}).build();
		vpc.addInterfaceEndpoint("ECSEndPoint", InterfaceVpcEndpointOptions.builder()
				.service(InterfaceVpcEndpointAwsService.ECS).subnets(privateSubnets).build());
		vpc.addInterfaceEndpoint("ECSAgentEndPoint", InterfaceVpcEndpointOptions.builder()
				.service(InterfaceVpcEndpointAwsService.ECS_AGENT).subnets(privateSubnets).build());
		vpc.addInterfaceEndpoint("ECREndPoint", InterfaceVpcEndpointOptions.builder()
				.service(InterfaceVpcEndpointAwsService.ECR).subnets(privateSubnets).build());

		// S3 Bucket
		Bucket bucket = Bucket.Builder.create(this, "S3Bucket")
				.bucketName(this.getAccount() + "-amazon-ecs-java-starter-kit-pattern-1-bucket")
				.removalPolicy(RemovalPolicy.DESTROY).build();

		// DynamoDB Tables
		String workflowSummaryPartitionKeyName = "workflow_name";
		String workflowSummarySortKeyName = "workflow_run_id";
		Table workflow_summary = Table.Builder.create(this, "DDBWorkFlowSummary").tableName("workflow_summary_pattern_1")
				.removalPolicy(RemovalPolicy.DESTROY)
				.partitionKey(
						Attribute.builder().name(workflowSummaryPartitionKeyName).type(AttributeType.STRING).build())
				.sortKey(Attribute.builder().name(workflowSummarySortKeyName).type(AttributeType.NUMBER).build())
				.build();

		String workflowDetailsPartitionKeyName = "workflow_run_id";
		String workflowDetailsSortKeyName = "ecs_task_id";
		Table workflow_details = Table.Builder.create(this, "DDBWorkFlowDetails").tableName("workflow_details_pattern_1")
				.removalPolicy(RemovalPolicy.DESTROY)
				.partitionKey(
						Attribute.builder().name(workflowDetailsPartitionKeyName).type(AttributeType.NUMBER).build())
				.sortKey(Attribute.builder().name(workflowDetailsSortKeyName).type(AttributeType.STRING).build())
				.build();

		// ECS Cluster
		Cluster cluster = Cluster.Builder.create(this, "StarterKitCluster").clusterName("amazon-ecs-java-starter-kit-pattern-1")
				.vpc(vpc).build();

		// ECR Image
		String ecrRepoName = "amazon-ecs-java-starter-kit-pattern-1";
		@SuppressWarnings("deprecation")
		DockerImageAsset dockerImageAsset = DockerImageAsset.Builder.create(this, "StarterKitECRImage")
				.directory("../amazon-ecs-java-starter-kit-task").repositoryName(ecrRepoName).build();

		// Fargate Task Definition
		FargateTaskDefinition fargateTaskDefinition = FargateTaskDefinition.Builder
				.create(this, "StarterKitFargateTaskDefinition").family("amazon-ecs-java-starter-kit-pattern-1").cpu(1024)
				.memoryLimitMiB(2048).build();

		// Container Definition
		ContainerDefinition containerDefinition = ContainerDefinition.Builder
				.create(this, "amazon-ecs-java-starter-kit").taskDefinition(fargateTaskDefinition).essential(true)
				.image(ContainerImage.fromDockerImageAsset(dockerImageAsset))
				.logging(AwsLogDriver.awsLogs(AwsLogDriverProps.builder()
						.logGroup(LogGroup.Builder.create(this, "ECSLogGroup")
								.logGroupName("/ecs/amazon-ecs-java-starter-kit-pattern-1").removalPolicy(RemovalPolicy.DESTROY)
								.retention(RetentionDays.ONE_DAY).build())
						.streamPrefix("amazon-ecs-java-starter-kit").build()))
				.build();

		// Container IAM permissions
		workflow_details.grantReadWriteData(fargateTaskDefinition.getTaskRole());
		bucket.grantReadWrite(fargateTaskDefinition.getTaskRole());

		// TaskLauncher Lambda
		Function taskLauncher = Function.Builder.create(this, "TaskLauncherLambda")
				.functionName("amazon-ecs-java-starter-kit-pattern-1-ecs-task-launcher")
				.code(Code.fromAsset(
						"../amazon-ecs-java-starter-kit-tasklauncher/target/amazon-ecs-java-starter-kit-tasklauncher-1.0.jar"))
				.handler("software.aws.ecs.java.starterkit.launcher.ECSTaskLauncher").runtime(Runtime.JAVA_8_CORRETTO)
				.timeout(Duration.minutes(5)).memorySize(256).logRetention(RetentionDays.ONE_DAY).vpc(vpc)
				.vpcSubnets(privateSubnets)
				.securityGroups(Collections.singletonList(SecurityGroup.Builder.create(this, "TaskLauncherSG").vpc(vpc)
						.securityGroupName("amazon-ecs-java-starter-kit-pattern-1-ecs-task-launcher").allowAllOutbound(true)
						.build()))
				.build();

		// Permissions to run ECS Task
		taskLauncher.getRole().addToPrincipalPolicy(PolicyStatement.Builder.create()
				.actions(Collections.singletonList("ecs:RunTask")).resources(new ArrayList<String>() {
					private static final long serialVersionUID = 1133379137898541366L;

					{
						add(fargateTaskDefinition.getTaskDefinitionArn());
						add(cluster.getClusterArn());
					}
				}).build());
		taskLauncher.getRole().addToPrincipalPolicy(PolicyStatement.Builder.create()
				.actions(Collections.singletonList("iam:PassRole")).resources(new ArrayList<String>() {
					private static final long serialVersionUID = 306435034815975097L;

					{
						add(fargateTaskDefinition.getTaskRole().getRoleArn());
						add(fargateTaskDefinition.getExecutionRole().getRoleArn());
					}
				}).build());

		// TaskMonitor Lambda
		Function taskMonitor = Function.Builder.create(this, "TaskMonitorLambda")
				.functionName("amazon-ecs-java-starter-kit-pattern-1-ecs-task-monitor")
				.code(Code.fromAsset(
						"../amazon-ecs-java-starter-kit-taskmonitor/target/amazon-ecs-java-starter-kit-taskmonitor-1.0.jar"))
				.handler("software.aws.ecs.java.starterkit.monitor.ECSTaskMonitor").runtime(Runtime.JAVA_8_CORRETTO)
				.timeout(Duration.minutes(5)).memorySize(256).logRetention(RetentionDays.ONE_DAY).vpc(vpc)
				.vpcSubnets(privateSubnets)
				.securityGroups(Collections.singletonList(SecurityGroup.Builder.create(this, "TaskMonitorSG").vpc(vpc)
						.securityGroupName("amazon-ecs-java-starter-kit-pattern-1-ecs-task-Monitor").allowAllOutbound(true)
						.build()))
				.environment(new HashMap<String, String>() {
					private static final long serialVersionUID = -8778366953471384771L;
					{
						put("region", getRegion());
						put("workflow_summary_ddb_table_name", workflow_summary.getTableName());
						put("workflow_summary_hash_key", workflowSummaryPartitionKeyName);
						put("workflow_summary_range_key", workflowSummarySortKeyName);
						put("workflow_details_ddb_table_name", workflow_details.getTableName());
						put("workflow_details_hash_key", workflowDetailsPartitionKeyName);
						put("workflow_details_range_key", workflowDetailsSortKeyName);
					}
				}).build();

		// IAM permissions for Lambdas
		workflow_details.grantReadWriteData(taskLauncher.getRole());
		workflow_details.grantReadWriteData(taskMonitor.getRole());
		workflow_summary.grantReadWriteData(taskLauncher.getRole());
		workflow_summary.grantReadWriteData(taskMonitor.getRole());

		// Monitor State in StateMachine
		LambdaInvoke invokeMonitorState = LambdaInvoke.Builder.create(this, "InvokeTaskMonitor")
				.payloadResponseOnly(true).lambdaFunction(taskMonitor)
				.payload(TaskInput.fromObject(new HashMap<String, String>() {
					private static final long serialVersionUID = -4995384961752093935L;
					{
						put("iterator.$", "$.iterator");
					}
				})).resultPath("$.iterator").payloadResponseOnly(true).build();

		// StateMachine
		StateMachine.Builder.create(this, "amazon-ecs-java-starter-kit-state-machine")
				.stateMachineName("amazon-ecs-java-starter-kit-pattern-1").stateMachineType(StateMachineType.STANDARD)
				.definition(Chain
						.start(LambdaInvoke.Builder.create(this, "InvokeTaskLauncher").lambdaFunction(taskLauncher)
								.resultPath("$.iterator").payloadResponseOnly(true).build())
						.next(invokeMonitorState)
						.next(Choice.Builder.create(this, "CheckIfTasksCompleted").build().when(
								Condition.booleanEquals("$.iterator.continue", true),
								Wait.Builder.create(this, "WaitForECS").time(WaitTime.duration(Duration.seconds(120)))
										.build().next(invokeMonitorState))
								.otherwise(Succeed.Builder.create(this, "Done").build())))
				.build();

		// Outputs
		CfnOutput.Builder.create(this, "region").value(this.getRegion()).build();
		CfnOutput.Builder.create(this, "clusterName").value(cluster.getClusterName()).build();
		CfnOutput.Builder.create(this, "containerName").value(containerDefinition.getContainerName()).build();
		CfnOutput.Builder.create(this, "taskDefinition")
				.value(Fn.select(1, Fn.split("/", fargateTaskDefinition.getTaskDefinitionArn()))).build();
		CfnOutput.Builder.create(this, "securityGroupId").value(vpc.getVpcDefaultSecurityGroup()).build();
		CfnOutput.Builder.create(this, "subnetIdLiteral").value(vpc.getPrivateSubnets().get(0).getSubnetId()).build();
		CfnOutput.Builder.create(this, "ddbTableNameWFSummary").value(workflow_summary.getTableName()).build();
		CfnOutput.Builder.create(this, "hashKeyWFSummary").value(workflowSummaryPartitionKeyName).build();
		CfnOutput.Builder.create(this, "rangeKeyWFSummary").value(workflowSummarySortKeyName).build();
		CfnOutput.Builder.create(this, "ddbTableNameWFDetails").value(workflow_details.getTableName()).build();
		CfnOutput.Builder.create(this, "hashKeyWFDetails").value(workflowDetailsPartitionKeyName).build();
		CfnOutput.Builder.create(this, "rangeKeyWFDetails").value(workflowDetailsSortKeyName).build();
		CfnOutput.Builder.create(this, "s3BucketName").value(bucket.getBucketName()).build();
		CfnOutput.Builder.create(this, "workflowName").value("amazon_ecs_starter_kit-pattern-1").build();
		CfnOutput.Builder.create(this, "separator").value("$").build();
	}
}