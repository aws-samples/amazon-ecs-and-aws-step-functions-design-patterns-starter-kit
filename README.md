# Amazon ECS and AWS Step Functions Design Patterns Starter kit

This starter kit demonstrates how to run [Amazon Elastic Container Service](https://aws.amazon.com/ecs/) (ECS) tasks using [AWS Step Functions](https://aws.amazon.com/step-functions/). We will present following design patterns:

 1. Running ECS tasks using AWS Lambda
 1. Running ECS tasks using Step Functions native integration

We use [AWS Cloud Development Kit](https://aws.amazon.com/cdk/) (CDK) to deploy application resources.

---

## Contents

* [Prerequisites](#prerequisites)
* [ECS Task Business Logic](#ecs-task-business-logic)
* [Workflow Specification](#Workflow-Specification)
* [Architecture Pattern 1: Running ECS tasks using AWS Lambda](#Running-ECS-tasks-using-aws-lambda)
* [Architecture Pattern 2: Running ECS tasks using Step Functions native integration](#Running-ECS-tasks-using-Step-Functions-native-integration)
* [AWS CDK Stacks](#aws-cdk-stacks)
* [Workflow Components](#workflow-components)
* [AWS Components](#aws-components)
* [DynamoDB Tables](#dynamoDB-tables)
* [Build Instructions](#build-instructions)
* [Deployment Instructions](#deployment-instructions)
* [Testing Instructions](#testing-instructions)
* [Cleanup](#cleanup)
* [Contributors](#contributors)

---

## Prerequisites

 1. Docker software is installed on your MacBook / Laptop
 1. Docker daemon is running
 1. You have AWS account credentials

---

## ECS Task Business Logic

We execute a simple business logic within an ECS task and it copies a file from one folder of an S3 bucket to an another folder. We will run multiple instances of the task simultaneously with different runtime parameters.

---

## Workflow Specification

We create 2 Step Functions State machines to demonstrate the design patterns. State machine is executed with a JSON specifications as an input. The specs have two parts - 1) values for ECS cluster, DynamoDB tables, and other AWS resources used in the starter kit. 2) list of ECS tasks to run.  Table below describes the specs.

 | JSON Attribute  | Description   |
 |-------------------  | ------------- |
 | region         | AWS region used |
 | s3BucketName   | Amazon S3 bucket used demonstrate ECS Task Business Logic |
 | subnetIdLiteral   | List of Subnet Ids separated by a separator |
 | separator         | The separator used in subnetIdLiteral |
 | workflowName      | Name of the workflow name for e.g. ```amazon_ecs_starter_kit-pattern-1``` |
 | securityGroupId   | The security group id used to run ECS tasks |
 | ddbTableNameWFSummary | Name of the DynamoDB table for workflow summary |
 | hashKeyWFSummary  | The hash key of workflow summary table |
 | rangeKeyWFSummary  | The sort key of workflow summary table |
 | ddbTableNameWFDetails  | Name of the DynamoDB table for workflow details |
 | hashKeyWFDetails  | The hash key of workflow details table |
 | rangeKeyWFDetails | The sort key of workflow details table |
 | clusterName    | Name of the ECS cluster |
 | containerName  | Name of the container |
 | taskDefinition | Name of the ECS task definition name |
 | taskList       | It is of type JSON Object and has one more ECS tasks. Each task has three attributes - 1) taskName (Name of the ECS task) 2) s3BucketName (S3 bucket name) 3) objectKey (Object key) |

---

## Running ECS tasks using AWS Lambda

As show in the below figure, this pattern uses AWS Lambda function to run ECS tasks. We call the Lambda function as **ECS Task Launcher**. It parses workflow specs, submits ECS tasks to ECS Cluster and invokes second AWS Lambda function called **ECS Task Monitor**.

ECS Task Monitor tracks the completion status of running ECS tasks. Each time it runs, it checks the number of completed tasks versus the total number of tasks submitted and updates the DynamoDB table **workflow_summary**.

The task executed on ECS cluster is called **ECS Task**. It takes the following actions - 1) reads input parameters 2) inserts a record in DynamoDB table for auditing 3) copies the input file to a target folder 4) marks the status of its job to Complete in the the DynamoDB table **workflow_detail**.

![Alt](./Amazon_ECS_Java_Starter_Kit-Architecture_Pattern_1.png)

---

## Running ECS tasks using Step Functions native integration

As shown in the below figure, this pattern uses AWS Step Functions' native service integration with Amazon ECS. The role of ECS Task Monitor and the way ECS Task runs are similar what we discussed for Pattern 1.

![Alt](./Amazon_ECS_Java_Starter_Kit-Architecture_Pattern_2.png)

---

## AWS CDK Stacks

[CdkApp](./amazon-ecs-java-starter-kit-cdk/src/main/java/software/aws/ecs/java/starterkit/cdk/CdkApp.java) runs the following stacks

  | Stack Name    | Purpose   |
  |---------------| --------- |
  | [ECSTaskSubmissionFromLambdaPattern](./amazon-ecs-java-starter-kit-cdk/src/main/java/software/aws/ecs/java/starterkit/cdk/ECSTaskSubmissionFromLambdaPattern.java)         | This stack provisions resources needed to demonstrate Pattern 1 |
  | [ECSTaskSubmissionFromStepFunctionsPattern](./amazon-ecs-java-starter-kit-cdk/src/main/java/software/aws/ecs/java/starterkit/cdk/ECSTaskSubmissionFromStepFunctionsPattern.java)  | This stack provisions resources needed to demonstrate Pattern 2 |

---

## Workflow Components

  | Component    | Type |  Purpose   |
  |----------| ------ | ------------ |
  | Workflow specs  | JSON File | A JSON file with workflow specifications to trigger the workflow |
  | Workflow | AWS Step Functions State machine | ECS workflow written in [Amazon States Language](https://docs.aws.amazon.com/step-functions/latest/dg/concepts-amazon-states-language.html)  |
  | ECSTaskLauncher | AWS Lambda | Lambda function to submit ECS Tasks. **Note:** this is only applicable for ECS task submission from AWS Lambda - Pattern 1. |
  | ECSTask | ECS Task | A containerized job (process) runs as a task on Amazon ECS |
  | ECSTaskMonitor  | AWS Lambda | Lambda function to submit ECS Tasks |

---

### AWS Components

  | Component |  Purpose   |
  |-----------|  --------- |
  | Amazon VPC | A dedicated Amazon Virtual Private Cloud (Amazon VPC) to deploy resources. |
  | Subnets    | The required subnets  |
  | Security Groups | The required subnets |
  | VPC Endpoints   | VPC endpoints for Amazon S3  and Amazon  DynamoDB |
  | ECS Cluster | Amazon ECS cluster |
  | ECR Repository | Amazon ECS container registry to store Docker images for ECS task executable |
  | ECS Task Definition | ECS Task definition |
  | Step Function State machine | State machine workflow |
  | S3 bucket       | Amazon S3 bucket used by Amazon ECS task |
  | DynamoDB Tables | DynamoDB tables used for auditing and tracking. See next section. |

### Amazon DynamoDB Tables

  | Table    | Schema |  Capacity   |
  |----------| ------ | ----------- |
  | workflow_summary | Partition key = workflow_name (String), Sort key = workflow_run_id (Number) | Provisioned read capacity units = 5, Provisioned write capacity units = 5  |
  | workflow_details | Partition key = workflow_run_id (Number), Sort key = ecs_task_id (String) | Provisioned read capacity units = 5, Provisioned write capacity units = 5 |

---

## Build Instructions

1. Clone this repository to your Mac/Laptop

1. Open your IDE for e.g. [Eclipse](https://www.eclipse.org/) or [Spring Tools](https://spring.io/tools) or [Intellij IDEA](https://www.jetbrains.com/idea/)

1. Import the project as a Maven project by pointing to ```<Path_to_cloned_repo>/Amazon-ecs-java-starter-kit/pom.xml``` | This imports 4 module projects.

1. Select parent project **Amazon-ecs-java-starter-kit** and build it using the below instructions

    1. Using standalone Maven, go to project home directory and run command ```mvn -X clean install```
    1. From Eclipse or STS, run command ```-X clean install```. Navigation: Project right click --> Run As --> Maven Build (Option 4)

1. Expected output 1: In your IDE, you will see the following output

    ```bash
    [INFO] Reactor Summary for amazon-ecs-java-starter-kit 1.0:
    [INFO] 
    [INFO] amazon-ecs-java-starter-kit ........................ [SUCCESS [  0.717 s]
    [INFO] amazon-ecs-java-starter-kit-cdk .................... [SUCCESS [ 14.230 s]
    [INFO] amazon-ecs-java-starter-kit-tasklauncher ........... [SUCCESS [  8.418 s]
    [INFO] amazon-ecs-java-starter-kit-task ................... [SUCCESS [ 21.857 s]
    [INFO] amazon-ecs-java-starter-kit-taskmonitor ............ [SUCCESS [  4.587 s]
    [INFO] ------------------------------------------------------------------------
    [INFO] BUILD SUCCESS
    [INFO] ------------------------------------------------------------------------
    [INFO] Total time:  49.979 s
    [INFO] Finished at: 2020-12-21T13:03:30-06:00
    ```

1. Expected output 2: Build process generates the following jar file  in their respective directories

   | Module artifact name                        | Approximate Size |
   |--------------------------------------------------------|-------|
   | ```amazon-ecs-java-starter-kit-cdk-1.0.jar```          | 32 KB |
   | ```amazon-ecs-java-starter-kit-tasklauncher-1.0.jar``` | 21 MB |
   | ```amazon-ecs-java-starter-kit-task-1.0.jar```         | 19 MB |
   | ```amazon-ecs-java-starter-kit-taskmonitor-1.0.jar```  | 21 MB |

---

## Deployment Instructions

 1. In the terminal, go to path ```/<Path_to_your_cloned_rep>/Amazon-ecs-java-starter-kit/amazon-ecs-java-starter-kit-cdk```. Now, you are in the CDK module of this project.

 1. Replace **1234567890** with your AWS Account Id wherever applicable in the following steps.

 1. Set these to your account and region

    ```bash
    export AWS_ACCOUNT_ID=1234567890
    export AWS_REGION=us-east-2
    ```

 1. Bootstrap CDK

    ```bash
    cdk bootstrap aws://${AWS_ACCOUNT_ID}/$AWS_REGION
    ```

 1. Output 1: In the command line, you will get the following output

    ```bash
    (node:63268) ExperimentalWarning: The fs.promises API is experimental
    ⏳  Bootstrapping environment aws://AWS_ACCOUNT_ID/us-west-2...
    ✅  Environment aws://AWS_ACCOUNT_ID/us-west-2 bootstrapped (no changes).
    ```

 1. Output 2: In the AWS console under CloudFormation, you will see a Stack created as follows

    ![Alt](./resources/output_of_bootstrap.png)

 1. Output 3: In the AWS console under S3, you will see a bucket created with name ```cdktoolkit-stagingbucket-*```

 1. Deploy both stacks
  
    ```bash
    cdk deploy --require-approval never --all --outputs-file outputs.json
    ```

 1. Expected output 1: Stack for **amazon-ecs-java-starter-pattern-1** created with the following resources:

    | Resource Type | Resource Details  |
    |---------------|-------------------|
    | VPC           | 1 VPC to launch resources needed by the starter kit |
    | Subnet        | 2 public subnets and 2 private subnets |
    | Route Table   | 1 route table per public, and private subnet |  
    | Security Group | 1 security group per ECR, ECS, and ECS Agent endpoints  |
    | Security Group | 1 security group per ECS Task Launcher and ECS Task Monitor |
    | VPC Endpoint   | 1 VPC endpoint per Amazon DynamoDB, Amazon S3, Amazon ECS, Amazon ECS Agent, and Amazon ECR API. |
    | ECS Cluster    | 1 ECS cluster to run run ECS tasks |
    | ECR Repository | 1 ECR repository to store Docker image for ECS Task binary |
    | ECS Task Definition | 1 ECS task definition for ECS Task |  
    | Amazon DynamoDB | 2 DynamoDB tables - 1) workflow_summary 2) workflow_details |
    | Step Functions state machine | 1 State machine for orchestration |
    | AWS Lambda | Lambda Function to submit ECS tasks |
    | AWS Lambda | Lambda Function to monitor the progress of ECS tasks |
    | Amazon IAM Role | 1 IAM role per Step Functions State machine, ECS Task Launcher, ECS Task Monitor. 2 IAM roles for ECS Task Definition - 1) ECS Task Role 2) ECS Task Execution Role |

 1. Expected output 2: Stack for **amazon-ecs-java-starter-pattern-2** created with the following resources:

    | Resource Type | Resource Details  |
    |---------------|-------------------|
    | VPC           | 1 VPC to launch resources needed by the starter kit |
    | Subnet        | 2 public subnets and 2 private subnets |
    | Route Table   | 1 route table per public, and private subnet |  
    | Security Group | 1 security group per ECR, ECS, and ECS Agent endpoints  |
    | Security Group | 1 security group per ECS Task Launcher and ECS Task Monitor |
    | VPC Endpoint   | 1 VPC endpoint per Amazon DynamoDB, Amazon S3, Amazon ECS, Amazon ECS Agent, and Amazon ECR API. |
    | ECS Cluster    | 1 ECS cluster to run run ECS tasks |
    | ECR Repository | 1 ECR repository to store Docker image for ECS Task binary |
    | ECS Task Definition | 1 ECS task definition for ECS Task |  
    | Amazon DynamoDB | 2 DynamoDB tables - 1) workflow_summary 2) workflow_details |
    | Step Functions state machine | 1 State machine for orchestration |
    | AWS Lambda | Lambda Function to monitor the progress of ECS tasks |
    | Amazon IAM Role | 1 IAM role per Step Functions State machine, and ECS Task Launcher. 2 IAM roles for ECS Task Definition - 1) ECS Task Role 2) ECS Task Execution Role |


 1. Edit file [workflow_specs_pattern_1.json](./amazon-ecs-java-starter-kit-cdk/workflow_specs_pattern_1.json) based on the contents from ```/<Path_to_your_cloned_rep>/Amazon-ecs-java-starter-kit/amazon-ecs-java-starter-kit-cdk/outputs.json```

 1. Edit file [workflow_specs_pattern_2.json](./amazon-ecs-java-starter-kit-cdk/workflow_specs_pattern_2.json) based on the contents from ```/<Path_to_your_cloned_rep>/Amazon-ecs-java-starter-kit/amazon-ecs-java-starter-kit-cdk/outputs.json```

 1. Copy jar file to S3 bucket ```s3://${AWS_ACCOUNT_ID}-amazon-ecs-java-starter-kit-pattern-1-bucket``` to test Pattern 1. Use the following command

    ```bash
    aws s3 cp ../amazon-ecs-java-starter-kit-tasklauncher/target/amazon-ecs-java-starter-kit-tasklauncher-1.0.jar s3://${AWS_ACCOUNT_ID}-amazon-ecs-java-starter-kit-pattern-1-bucket/amazon_ecs_java_starter_kit_jar/
    ```

 1. Copy jar file to S3 bucket ```s3://${AWS_ACCOUNT_ID}-amazon-ecs-java-starter-kit-pattern-2-bucket``` to test Pattern 2. Use the following command

    ```bash
    aws s3 cp ../amazon-ecs-java-starter-kit-tasklauncher/target/amazon-ecs-java-starter-kit-tasklauncher-1.0.jar s3://${AWS_ACCOUNT_ID}-amazon-ecs-java-starter-kit-pattern-2-bucket/amazon_ecs_java_starter_kit_jar/amazon-ecs-java-starter-kit-tasklauncher-1.0.jar
    ```

---

## Testing Instructions

1. Make sure you are still in the path ```/<Path_to_your_cloned_rep>/Amazon-ecs-java-starter-kit/amazon-ecs-java-starter-kit-cdk```

1. **Start Step Functions execution for Pattern 1**

   ```bash
   aws stepfunctions start-execution --state-machine-arn "arn:aws:states:${AWS_REGION}:${AWS_ACCOUNT_ID}:stateMachine:amazon-ecs-java-starter-kit-pattern-1" --input "$(cat workflow_specs_pattern_1.json )"
   ```

1. Expected  output 1:

   ```bash
   {
    "executionArn": "arn:aws:states:us-east-2:1234567890:execution:amazon-ecs-java-starter-kit-pattern-1:4ea1f256-a0bb-4692-b63a-6b80edc02cb7",
    "startDate": "2020-12-21T09:54:29.385000-06:00"
   }
   ```

1. Expected output 2: When everything goes well, your State machine execution status will be successful as follows.

    ![Alt](./Pattern_1_execution_outcome.png)

1. Expected output 3: Under S3 URI ```s3://1234567890-amazon-ecs-java-starter-kit-pattern-1-bucket/amazon_ecs_java_starter_kit_jar/```, you will see a total of 11  objects. One of them is the input **amazon-ecs-java-starter-kit-tasklauncher-1.0.jar** and 10 are its copies created by ECS Tasks.

1. **Start Step Functions execution for Pattern 2**

   ```bash
   aws stepfunctions start-execution --state-machine-arn "arn:aws:states:${AWS_REGION}:${AWS_ACCOUNT_ID}:stateMachine:amazon-ecs-java-starter-kit-pattern-2" --input "$(cat workflow_specs_pattern_2.json )"
   ```

1. Expected  output 1:

   ```bash
   {
    "executionArn": "arn:aws:states:us-east-2:1234567890:execution:amazon-ecs-java-starter-kit-pattern-2:17e02b1f-636b-4ffc-b061-96c9ab8e27db",
    "startDate": "2020-12-21T10:16:30.717000-06:00"
   }
   ```

1. Expected output 2: When everything goes well, your State machine execution status will be successful as follows.

    ![Alt](./Pattern_2_execution_outcome.png)

1. Expected output 3: Under S3 URI ```s3://1234567890-amazon-ecs-java-starter-kit-pattern-2-bucket/amazon_ecs_java_starter_kit_jar/```, you will see a total of 11  objects. One of them is the input **amazon-ecs-java-starter-kit-tasklauncher-1.0.jar** and 10 are its copies created by ECS Tasks.

---

## Cleanup

1. Make sure you are still in the path ```/<Path_to_your_cloned_rep>/Amazon-ecs-java-starter-kit/amazon-ecs-java-starter-kit-cdk```

1. Delete DynamoDB tables - Pattern 1

    ```bash
    ./delete_ddb_items.sh workflow_details_pattern_1 workflow_summary_pattern_1
    ```

1. Delete DynamoDB tables - Pattern 2

    ```bash
    ./delete_ddb_items.sh workflow_details_pattern_2 workflow_summary_pattern_2
    ```

1. Empty S3 bucket ``s3://${AWS_ACCOUNT_ID}-amazon-ecs-java-starter-kit-pattern-1-bucket```

    ```bash
    aws s3 ls s3://${AWS_ACCOUNT_ID}-amazon-ecs-java-starter-kit-pattern-1-bucket/amazon_ecs_java_starter_kit_jar/ | grep _ | awk '{print $NF}' | while read OBJ; do aws s3 rm s3://${AWS_ACCOUNT_ID-}amazon-ecs-java-starter-kit-pattern-2-bucket/amazon_ecs_java_starter_kit_jar/$OBJ;done
    ```

1. Empty S3 bucket ``s3://${AWS_ACCOUNT_ID}-amazon-ecs-java-starter-kit-pattern-2-bucket```

    ```bash
    aws s3 ls s3://${AWS_ACCOUNT_ID-}-amazon-ecs-java-starter-kit-pattern-2-bucket/amazon_ecs_java_starter_kit_jar/ | grep _ | awk '{print $NF}' | while read OBJ; do aws s3 rm s3://${AWS_ACCOUNT_ID-}amazon-ecs-java-starter-kit-pattern-2-bucket/amazon_ecs_java_starter_kit_jar/$OBJ;done
    ```

1. Delete S3 buckets S3 bucket ``s3://${AWS_ACCOUNT_ID}-amazon-ecs-java-starter-kit-pattern-1-bucket```

    ```bash
    aws s3 rm s3://${AWS_ACCOUNT_ID-}amazon-ecs-java-starter-kit-pattern-1-bucket/amazon_ecs_java_starter_kit_jar/amazon-ecs-java-starter-kit-tasklauncher-1.0.jar
    ```

1. Delete S3 buckets S3 bucket ``s3://${AWS_ACCOUNT_ID}-amazon-ecs-java-starter-kit-pattern-2-bucket```

    ```bash
    aws s3 rm s3://${AWS_ACCOUNT_ID-}amazon-ecs-java-starter-kit-pattern-2-bucket/amazon_ecs_java_starter_kit_jar/amazon-ecs-java-starter-kit-tasklauncher-1.0.jar
    ```

1. Delete ECR Repository - Pattern 1

    ```bash
    aws ecr delete-repository --force --repository-name amazon-ecs-java-starter-kit-pattern-1
    ```

1. Delete ECR Repository - Pattern 2

    ```bash
    aws ecr delete-repository --force --repository-name amazon-ecs-java-starter-kit-pattern-2
    ```

1. Cleanup stacks for Pattern 1 and 2

    ```bash
    cdk destroy --force --all
    ```

---

## Contributors

1. **Sarma Palli**, Senior DevOps Cloud Architect, Amazon Web Services
1. **Ravi Itha**, Senior Big Data Consultant, Amazon Web Services

---

## License Summary

This sample code is made available under the MIT license. See the LICENSE file.
