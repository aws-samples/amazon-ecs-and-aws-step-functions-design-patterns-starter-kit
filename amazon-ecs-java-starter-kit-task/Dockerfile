FROM amazonlinux:latest

RUN yum -y install perl which unzip aws-cli java-1.8.0-openjdk.x86_64

# Add Java Jar
ADD target/amazon-ecs-java-starter-kit-task-1.0.jar /java-app/
RUN chmod 744 /java-app/*

# Docker execution entry point
ADD runner.sh /java-app/
RUN chmod 755 /java-app/runner.sh

WORKDIR /java-app
USER root
ENV program_executable /java-app/amazon-ecs-java-starter-kit-task-1.0.jar
ENTRYPOINT ["/java-app/runner.sh"]
