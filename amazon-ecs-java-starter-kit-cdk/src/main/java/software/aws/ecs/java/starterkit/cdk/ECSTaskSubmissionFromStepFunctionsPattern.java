// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package software.aws.ecs.java.starterkit.cdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import software.amazon.awscdk.core.Aws;
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
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.FargatePlatformVersion;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.stepfunctions.Chain;
import software.amazon.awscdk.services.stepfunctions.Choice;
import software.amazon.awscdk.services.stepfunctions.Condition;
import software.amazon.awscdk.services.stepfunctions.JsonPath;
import software.amazon.awscdk.services.stepfunctions.Map;
import software.amazon.awscdk.services.stepfunctions.Parallel;
import software.amazon.awscdk.services.stepfunctions.Pass;
import software.amazon.awscdk.services.stepfunctions.StateMachine;
import software.amazon.awscdk.services.stepfunctions.StateMachineType;
import software.amazon.awscdk.services.stepfunctions.Succeed;
import software.amazon.awscdk.services.stepfunctions.Wait;
import software.amazon.awscdk.services.stepfunctions.WaitTime;
import software.amazon.awscdk.services.stepfunctions.tasks.ContainerOverride;
import software.amazon.awscdk.services.stepfunctions.tasks.EcsFargateLaunchTarget;
import software.amazon.awscdk.services.stepfunctions.tasks.EcsRunTask;
import software.amazon.awscdk.services.stepfunctions.tasks.LambdaInvoke;
import software.amazon.awscdk.services.stepfunctions.tasks.TaskEnvironmentVariable;

/**
 *
 * This CDK Application runs a CloudFormation stack to demonstrate pattern
 * ECS Task submission from StepFunctions
 *
 * @author Sarma Palli, Senior DevOps Cloud Architect
 *
 */
public class ECSTaskSubmissionFromStepFunctionsPattern extends Stack {

    public ECSTaskSubmissionFromStepFunctionsPattern(final Construct scope, final String id) {
        this(scope, id, null);
    }

    private TaskEnvironmentVariable EnvVarBuilder(String envKey, String envValue) {
        return TaskEnvironmentVariable.builder().name(envKey).value(envValue).build();
    }

    public ECSTaskSubmissionFromStepFunctionsPattern(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        SubnetSelection privateSubnets = SubnetSelection.builder().subnetType(SubnetType.PRIVATE).build();
        // VPC
        Vpc vpc = Vpc.Builder.create(this, "StarterKitVPC").cidr("10.120.0.0/16").maxAzs(2)
                .gatewayEndpoints(new HashMap<String, GatewayVpcEndpointOptions>() {
                    private static final long serialVersionUID = 2479535941382804947L;
                    {
                        put("S3EndPoint", GatewayVpcEndpointOptions.builder().service(GatewayVpcEndpointAwsService.S3)
                                .subnets(new ArrayList<SubnetSelection>() {
                                    private static final long serialVersionUID = 1454955270027154519L;
                                    {
                                        add(privateSubnets);
                                    }
                                }).build());
                        put("DDBEndPoint",
                                GatewayVpcEndpointOptions.builder().service(GatewayVpcEndpointAwsService.DYNAMODB)
                                        .subnets(new ArrayList<SubnetSelection>() {
                                            private static final long serialVersionUID = -4389876763264722986L;
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

        // ECS Tasks SecurityGroup
        SecurityGroup ecsSecurityGroup = SecurityGroup.Builder.create(this, "FargateSecurityGroup").vpc(vpc)
                .securityGroupName("amazon-ecs-java-starter-kit-pattern-2").allowAllOutbound(true).build();

        // S3 Bucket
        Bucket s3Bucket = Bucket.Builder.create(this, "S3Bucket")
                .bucketName(this.getAccount() + "-amazon-ecs-java-starter-kit-pattern-2-bucket")
                .blockPublicAccess(BlockPublicAccess.Builder.create()
                        .blockPublicAcls(true)
                        .blockPublicPolicy(true)
                        .ignorePublicAcls(true)
                        .restrictPublicBuckets(true)
                        .build())
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // DynamoDB Tables
        String workflowSummaryPartitionKeyName = "workflow_name";
        String workflowSummarySortKeyName = "workflow_run_id";
        Table workflow_summary = Table.Builder.create(this, "DDBWorkFlowSummary").tableName("workflow_summary_pattern_2")
                .removalPolicy(RemovalPolicy.DESTROY)
                .partitionKey(
                        Attribute.builder().name(workflowSummaryPartitionKeyName).type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name(workflowSummarySortKeyName).type(AttributeType.NUMBER).build())
                .build();

        String workflowDetailsPartitionKeyName = "workflow_run_id";
        String workflowDetailsSortKeyName = "ecs_task_id";
        Table workflow_details = Table.Builder.create(this, "DDBWorkFlowDetails").tableName("workflow_details_pattern_2")
                .removalPolicy(RemovalPolicy.DESTROY)
                .partitionKey(
                        Attribute.builder().name(workflowDetailsPartitionKeyName).type(AttributeType.NUMBER).build())
                .sortKey(Attribute.builder().name(workflowDetailsSortKeyName).type(AttributeType.STRING).build())
                .build();

        // ECS Cluster
        Cluster cluster = Cluster.Builder.create(this, "StarterKitCluster").clusterName("amazon-ecs-java-starter-kit-pattern-2")
                .vpc(vpc).build();

        // ECR Image
        String ecrRepoName = "amazon-ecs-java-starter-kit-pattern-2";
        @SuppressWarnings("deprecation")
        DockerImageAsset dockerImageAsset = DockerImageAsset.Builder.create(this, "StarterKitECRImage")
                .directory("../amazon-ecs-java-starter-kit-task").repositoryName(ecrRepoName).build();

        // Fargate Task Definition
        FargateTaskDefinition fargateTaskDefinition = FargateTaskDefinition.Builder
                .create(this, "StarterKitFargateTaskDefinition").family("amazon-ecs-java-starter-kit-pattern-2").cpu(1024)
                .memoryLimitMiB(2048).build();

        // Container Definition
        ContainerDefinition containerDefinition = fargateTaskDefinition.addContainer("amazon-ecs-java-starter-kit",
                ContainerDefinitionOptions.builder().essential(true)
                        .image(ContainerImage.fromDockerImageAsset(dockerImageAsset))
                        .logging(AwsLogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this, "ECSLogGroup")
                                        .logGroupName("/ecs/amazon-ecs-java-starter-kit-pattern-2")
                                        .removalPolicy(RemovalPolicy.DESTROY).retention(RetentionDays.ONE_DAY).build())
                                .streamPrefix("amazon-ecs-java-starter-kit").build()))
                        .build());

        // Container IAM permissions
        workflow_details.grantReadWriteData(fargateTaskDefinition.getTaskRole());
        s3Bucket.grantReadWrite(fargateTaskDefinition.getTaskRole());

        // TaskMonitor Lambda
        Function taskMonitor = Function.Builder.create(this, "TaskMonitorLambda")
                .functionName("amazon-ecs-java-starter-kit-pattern-2-ecs-task-monitor")
                .code(Code.fromAsset(
                        "../amazon-ecs-java-starter-kit-taskmonitor/target/amazon-ecs-java-starter-kit-taskmonitor-1.0.jar"))
                .handler("software.aws.ecs.java.starterkit.monitor.ECSTaskMonitor").runtime(Runtime.JAVA_8_CORRETTO)
                .timeout(Duration.minutes(5)).memorySize(256).logRetention(RetentionDays.ONE_DAY).vpc(vpc)
                .vpcSubnets(privateSubnets)
                .securityGroups(Collections.singletonList(SecurityGroup.Builder.create(this, "TaskMonitorSG").vpc(vpc)
                        .securityGroupName("amazon-ecs-java-starter-kit-pattern-2-ecs-task-Monitor").allowAllOutbound(true)
                        .build()))
                .environment(new HashMap<String, String>() {
                    private static final long serialVersionUID = -4232375236129537678L;
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
        workflow_details.grantReadWriteData(taskMonitor.getRole());
        workflow_summary.grantReadWriteData(taskMonitor.getRole());

        // Environment variables being passed to ECS Tasks
        ArrayList<TaskEnvironmentVariable> containerEnvVars = new ArrayList<TaskEnvironmentVariable>() {
            private static final long serialVersionUID = -7266629031441090205L;
            {
                add(EnvVarBuilder("region", Aws.REGION));
                add(EnvVarBuilder("workflow_details_ddb_table_name", workflow_details.getTableName()));
                add(EnvVarBuilder("workflow_details_hash_key", workflowDetailsPartitionKeyName));
                add(EnvVarBuilder("workflow_details_range_key", workflowDetailsSortKeyName));
                add(EnvVarBuilder("workflow_name", JsonPath.stringAt("$.workflowName")));
                add(EnvVarBuilder("workflow_run_id", JsonPath.stringAt("$.workflowRunId")));
                add(EnvVarBuilder("task_name", JsonPath.stringAt("$.taskName")));
                add(EnvVarBuilder("s3_bucket_name", JsonPath.stringAt("$.s3BucketName")));
                add(EnvVarBuilder("object_key", JsonPath.stringAt("$.objectKey")));
            }
        };

        // ECS Run Task State
        EcsRunTask ecsRunTask = EcsRunTask.Builder.create(this, "SubmitECSTasks").assignPublicIp(false).cluster(cluster)
                .taskDefinition(fargateTaskDefinition).subnets(privateSubnets)
                .securityGroups(Collections.singletonList(ecsSecurityGroup))
                .launchTarget(EcsFargateLaunchTarget.Builder.create().platformVersion(FargatePlatformVersion.VERSION1_4)
                        .build())
                .containerOverrides(Collections.singletonList(ContainerOverride.builder()
                        .containerDefinition(containerDefinition).environment(containerEnvVars).build()))
                .build();

        // Submit ECS tasks simultaneously using Map state
        Map ecsTasksSubmitter = Map.Builder.create(this, "S3CopyTaskRunner").parameters(new HashMap<String, String>() {
            private static final long serialVersionUID = -2748074145208985587L;
            {
                put("workflowRunId.$", "$.workflowRunId");
                put("workflowName.$", "$.workflowName");
                put("s3BucketName.$", "$.s3BucketName");
                put("taskName.$", "$$.Map.Item.Value.taskName");
                put("objectKey.$", "$$.Map.Item.Value.objectKey");
            }
        }).itemsPath("$.taskList").outputPath("$.[*].Tasks.[*].TaskArn")
                /**
                 * TODO: This line is not supported yet. See https://github.com/aws/aws-cdk/issues/9904
                 * .resultSelection("$.[*].Tasks.[*].TaskArn")
                 */
                .maxConcurrency(5).build().iterator(ecsRunTask);
        /**
         * TODO: Parallel Wrapper to work-around this issue with CDK
         * See https://github.com/aws/aws-cdk/issues/9904, So that we can pass through the input variables
         */
        Parallel parallelWrapper = Parallel.Builder.create(this, "KickOffInParallel").resultPath("$.paralleloutput")
                .build().branch(ecsTasksSubmitter);

        // Pass-Through State for passing through the ECS Task Arns
        Pass passThroughECSTasksArns = Pass.Builder.create(this, "PassThroughECSArns")
                .parameters(new HashMap<String, Object>() {
                    private static final long serialVersionUID = 4528431724317351553L;
                    {
                        put("iterator", new HashMap<String, Object>() {
                            private static final long serialVersionUID = 5348720279215832325L;
                            {
                                put("continue", false);
                                put("workflowRunId.$", "$.workflowRunId");
                                put("workflowName.$", "$.workflowName");
                                put("ecsTaskArns.$", "$.paralleloutput.[0]");
                            }
                        });
                    }
                }).build();

        // Monitor State Lambda invocation
        LambdaInvoke invokeMonitorState = LambdaInvoke.Builder.create(this, "InvokeTaskMonitor")
                .lambdaFunction(taskMonitor).resultPath("$.iterator").payloadResponseOnly(true).build();

        // State for sleeping for sometime
        Chain waitState = Wait.Builder.create(this, "WaitForECS").time(WaitTime.duration(Duration.seconds(120))).build()
                .next(invokeMonitorState);

        // Success State
        Succeed doneState = Succeed.Builder.create(this, "Done").build();

        // State for checking if tasks completed
        Choice checkTasksCompleted = Choice.Builder.create(this, "CheckIfTasksCompleted").build()
                .when(Condition.booleanEquals("$.iterator.continue", true), waitState).otherwise(doneState);

        // StateMachine
        StateMachine.Builder.create(this, "amazon-ecs-java-starter-kit")
                .stateMachineName("amazon-ecs-java-starter-kit-pattern-2").stateMachineType(StateMachineType.STANDARD)
                .definition(Chain.start(parallelWrapper).next(passThroughECSTasksArns).next(invokeMonitorState)
                        .next(checkTasksCompleted))
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
        CfnOutput.Builder.create(this, "s3BucketName").value(s3Bucket.getBucketName()).build();
        CfnOutput.Builder.create(this, "separator").value("$").build();
        CfnOutput.Builder.create(this, "workflowName").value("amazon_ecs_starter_kit_pattern_2").build();
        CfnOutput.Builder.create(this, "workflowRunId").value("100001").build();
    }
}
