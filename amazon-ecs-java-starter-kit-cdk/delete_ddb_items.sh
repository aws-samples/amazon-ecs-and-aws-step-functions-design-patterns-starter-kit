#!/usr/bin/env bash
set -e
set -x

export AWS_PAGER=""

for TABLE_NAME in $@
do
  HASH_KEY=$(aws dynamodb describe-table \
    --table-name $TABLE_NAME \
    --output text \
    --query 'Table.KeySchema[?KeyType==`HASH`].AttributeName')
  RANGE_KEY=$(aws dynamodb describe-table \
    --table-name $TABLE_NAME \
    --output text \
    --query 'Table.KeySchema[?KeyType==`RANGE`].AttributeName')

  aws dynamodb scan \
    --attributes-to-get $HASH_KEY $RANGE_KEY \
    --table-name $TABLE_NAME --query "Items[*]"  \
    | jq --compact-output '.[]' \
    | tr '\n' '\0' \
    | xargs -0 -t -P 5 -I keyItem \
    aws dynamodb delete-item --table-name $TABLE_NAME --key=keyItem
done
