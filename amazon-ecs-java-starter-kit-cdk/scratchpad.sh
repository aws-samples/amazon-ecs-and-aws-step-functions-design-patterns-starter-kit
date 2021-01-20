# CLI commands

# Set these to your account and region
export AAWS_ACCOUNT_ID=927825637834
export AWS_REGION=us-east-2

# BootStrap CDK
cdk bootstrap aws://${AAWS_ACCOUNT_ID}/$AWS_REGION

# Deploy both stacks
cdk deploy --require-approval never --all --outputs-file outputs.json

# Edit workflow_specs_pattern_1.json and workflow_specs_pattern_2.json based on outputs.json

# Copy jar files to S3 Buckets
aws s3 cp ../amazon-ecs-java-starter-kit-tasklauncher/target/amazon-ecs-java-starter-kit-tasklauncher-1.0.jar s3://${AWS_ACCOUNT_ID}-amazon-ecs-java-starter-kit-pattern-1-bucket/amazon_ecs_java_starter_kit_jar/amazon-ecs-java-starter-kit-tasklauncher-1.0.jar
aws s3 cp ../amazon-ecs-java-starter-kit-tasklauncher/target/amazon-ecs-java-starter-kit-tasklauncher-1.0.jar s3://${AWS_ACCOUNT_ID}-amazon-ecs-java-starter-kit-pattern-2-bucket/amazon_ecs_java_starter_kit_jar/amazon-ecs-java-starter-kit-tasklauncher-1.0.jar

# Launch stepfunctions
aws stepfunctions start-execution --state-machine-arn "arn:aws:states:${AWS_REGION}:${AWS_ACCOUNT_ID}:stateMachine:amazon-ecs-java-starter-kit-pattern-1" --input "$(cat workflow_specs_pattern_1.json )"
aws stepfunctions start-execution --state-machine-arn "arn:aws:states:${AWS_REGION}:${AWS_ACCOUNT_ID}:stateMachine:amazon-ecs-java-starter-kit-pattern-2" --input "$(cat workflow_specs_pattern_2.json )"

# Sample output

REGION}:${AWS_ACCOUNT_ID}:stateMachine:amazon-ecs-java-starter-kit-pattern-1" --input "$(cat workflow_specs_pattern_1.json )"
{
    "executionArn": "arn:aws:states:us-east-2:927825637834:execution:amazon-ecs-java-starter-kit-pattern-1:4ea1f256-a0bb-4692-b63a-6b80edc02cb7",
    "startDate": "2020-12-21T09:54:29.385000-06:00"
}

{
    "executionArn": "arn:aws:states:us-east-2:927825637834:execution:amazon-ecs-java-starter-kit-pattern-2:17e02b1f-636b-4ffc-b061-96c9ab8e27db",
    "startDate": "2020-12-21T10:16:30.717000-06:00"
}



# ============= CLEANUP =============

# Cleanup DDB Tables (if needed)
./delete_ddb_items.sh workflow_details_pattern_1 workflow_summary_pattern_1
./delete_ddb_items.sh workflow_details_pattern_2 workflow_summary_pattern_2

# Cleanup S3 Buckets of copied objects
aws s3 ls s3://${AWS_ACCOUNT_ID}-amazon-ecs-java-starter-kit-pattern-1-bucket/amazon_ecs_java_starter_kit_jar/ | grep _ | awk '{print $NF}' | while read OBJ; do aws s3 rm s3://${AWS_ACCOUNT_ID-}amazon-ecs-java-starter-kit-pattern-2-bucket/amazon_ecs_java_starter_kit_jar/$OBJ;done
aws s3 ls s3://${AWS_ACCOUNT_ID-}-amazon-ecs-java-starter-kit-pattern-2-bucket/amazon_ecs_java_starter_kit_jar/ | grep _ | awk '{print $NF}' | while read OBJ; do aws s3 rm s3://${AWS_ACCOUNT_ID-}amazon-ecs-java-starter-kit-pattern-2-bucket/amazon_ecs_java_starter_kit_jar/$OBJ;done

# Cleanup S3 Buckets
aws s3 rm s3://${AWS_ACCOUNT_ID-}amazon-ecs-java-starter-kit-pattern-1-bucket/amazon_ecs_java_starter_kit_jar/amazon-ecs-java-starter-kit-tasklauncher-1.0.jar
aws s3 rm s3://${AWS_ACCOUNT_ID-}amazon-ecs-java-starter-kit-pattern-2-bucket/amazon_ecs_java_starter_kit_jar/amazon-ecs-java-starter-kit-tasklauncher-1.0.jar

# Cleanup stacks
cdk destroy --force --all

# Delete ECR Repositories
aws ecr delete-repository --force --repository-name amazon-ecs-java-starter-kit-pattern-1
aws ecr delete-repository --force --repository-name amazon-ecs-java-starter-kit-pattern-2
