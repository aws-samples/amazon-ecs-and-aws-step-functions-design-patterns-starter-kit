#!/bin/bash
set -x
set -e
export AWS_REGION=$1
export AWS_ACCOUNT_ID=$2
export ECR_REPO_NAME=$3
export ECS_TASK_BINARY=$4
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com
echo "Copying ECS Task binary from ->" $ECS_TASK_BINARY
cp $ECS_TASK_BINARY .
docker build -t $ECR_REPO_NAME .
docker tag amazon-ecs-java-starter-kit-ecr:latest $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPO_NAME:version1.8
docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPO_NAME:version1.8
rm -rfr *.jar