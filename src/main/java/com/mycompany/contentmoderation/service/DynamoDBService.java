package com.mycompany.contentmoderation.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;

@Service
public class DynamoDBService {

    @Autowired
    private DynamoDbClient dynamoDbClient;

    public void storeMetadata(String fileKey, String bucketName, boolean ruleBasedValidationPassed, boolean aiValidationPassed) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("fileKey", AttributeValue.builder().s(fileKey).build());
        item.put("bucketName", AttributeValue.builder().s(bucketName).build());
        item.put("ruleBasedValidation", AttributeValue.builder().bool(ruleBasedValidationPassed).build());
        item.put("aiValidation", AttributeValue.builder().bool(aiValidationPassed).build());

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName("ContentMetadata")
                .item(item)
                .build();

        dynamoDbClient.putItem(putItemRequest);
    }
}
