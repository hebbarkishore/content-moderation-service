package com.mycompany.contentmoderation.service;

import com.mycompany.contentmoderation.entity.User;
import com.mycompany.contentmoderation.record.FileContent;
import com.mycompany.contentmoderation.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Base64;

import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointResponse;

@Service
public class ContentModerationService {

    @Autowired
    private S3Client s3Client;

    @Autowired
    private SqsClient sqsClient;

    @Autowired
    private SageMakerRuntimeClient sageMakerRuntimeClient;

    @Autowired
    private DynamoDBService dynamoDBService;

    @Autowired
    private UserRepository userRepository;

    private final String queueUrl = "https://sqs.<region>.amazonaws.com/<account-id>/<queue-name>";
    private final String endpointName = "<your-sagemaker-endpoint-name>";  // Replace with your SageMaker endpoint name


    @JmsListener(destination = "your-sqs-queue-name")
    public void receiveMessage(String message) {
        try {
            processFile(message);
        } catch (Exception e) {
            System.out.println("Error processing message: " + e.getMessage());
        }
    }

    public void processFile(String sqsMessage) throws Exception {
        // Parse SQS message to extract file details (bucket and key)
        String bucketName = extractBucketName(sqsMessage);
        String fileKey = extractFileKey(sqsMessage);

        //Fetch the file from S3
        FileContent fileContent = getFileFromS3WithContentType(bucketName, fileKey);

        // Run rule-based validation on the file (e.g., file type, size)
        boolean ruleBasedValidationPassed = validateFile();

        // Invoke AI/ML model for content moderation (e.g., image, video classification)
        boolean aiValidationPassed = invokeAIModelForModeration(fileContent.fileContent(), fileContent.contentType());

        // Store metadata in DynamoDB
        dynamoDBService.storeMetadata(fileKey, bucketName, ruleBasedValidationPassed, aiValidationPassed);

        // Update validation status in SQL DB
        updateSQLDBWithUserValidation(fileKey, ruleBasedValidationPassed, aiValidationPassed);

        // Send message to SQS with validation status
        sendValidationStatusToSQS(fileKey, ruleBasedValidationPassed && aiValidationPassed);
    }

    public FileContent getFileFromS3WithContentType(String bucketName, String fileKey) throws IOException {
        // Build the GetObjectRequest
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .build();

        // Retrieve the file along with its metadata
        try (ResponseInputStream<GetObjectResponse> s3ObjectResponse = s3Client.getObject(getObjectRequest)) {
            // Extract the content type from the response metadata
            String contentType = s3ObjectResponse.response().contentType();

            // Convert the file content to a byte array
            byte[] fileContent = convertInputStreamToByteArray(s3ObjectResponse);

            // Return the file content and its content type
            return new FileContent(fileContent, contentType);
        }
    }

    public byte[] convertInputStreamToByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, bytesRead);
        }
        return byteArrayOutputStream.toByteArray();
    }

    public boolean validateFile() {
        // Rule-based validation: File type, size, etc.
        // read the file and validate
        return true; // Placeholder for actual validation logic
    }

    public boolean invokeAIModelForModeration(byte[] contentBytes, String contentType) throws Exception {
        String payload = "";
        if ("image".equalsIgnoreCase(contentType)) {
            payload = Base64.getEncoder().encodeToString(contentBytes);
        } else if ("text".equalsIgnoreCase(contentType)) {
            payload = new String(contentBytes);
        }

        InvokeEndpointRequest request = InvokeEndpointRequest.builder()
                .endpointName(endpointName)
                .contentType("application/json")
                .body(SdkBytes.fromUtf8String("{\"input\":\"" + payload + "\"}"))
                .build();

        // Invoke the SageMaker endpoint and handle the response
        InvokeEndpointResponse response = sageMakerRuntimeClient.invokeEndpoint(request);
        return response.sdkHttpResponse().statusCode() == 200;
    }

    public void updateSQLDBWithUserValidation(String fileKey, boolean ruleBasedValidationPassed, boolean aiValidationPassed) {
        User user = userRepository.findByFileKey(fileKey);
        if (user != null) {
            user.setValidationStatus(ruleBasedValidationPassed && aiValidationPassed ? "VALID" : "INVALID");
            userRepository.save(user);
        }
    }

    public void sendValidationStatusToSQS(String fileKey, boolean isValid) {
        String message = isValid ? "VALID" : "INVALID";
        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(String.format("File %s is %s", fileKey, message))
                .build();
        sqsClient.sendMessage(sendMessageRequest);
    }

    public String extractBucketName(String sqsMessage) {
        return "my-bucket";
    }

    public String extractFileKey(String sqsMessage) {
        return "myfile.txt";
    }
}
