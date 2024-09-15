package com.mycompany.contentmoderation;

import com.mycompany.contentmoderation.entity.User;
import com.mycompany.contentmoderation.record.FileContent;
import com.mycompany.contentmoderation.repository.UserRepository;
import com.mycompany.contentmoderation.service.ContentModerationService;
import com.mycompany.contentmoderation.service.DynamoDBService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ContentModerationServiceTest {

    @InjectMocks
    private ContentModerationService contentModerationService;

    @Mock
    private S3Client s3Client;

    @Mock
    private SqsClient sqsClient;

    @Mock
    private SageMakerRuntimeClient sageMakerRuntimeClient;

    @Mock
    private DynamoDBService dynamoDBService;

    @Mock
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        contentModerationService = new ContentModerationService();
        ReflectionTestUtils.setField(contentModerationService, "s3Client", s3Client);
        ReflectionTestUtils.setField(contentModerationService, "sqsClient", sqsClient);
        ReflectionTestUtils.setField(contentModerationService, "sageMakerRuntimeClient", sageMakerRuntimeClient);
        ReflectionTestUtils.setField(contentModerationService, "dynamoDBService", dynamoDBService);
        ReflectionTestUtils.setField(contentModerationService, "userRepository", userRepository);
        ReflectionTestUtils.setField(contentModerationService, "queueUrl", "test-queue-url");
        ReflectionTestUtils.setField(contentModerationService, "endpointName", "test-endpoint-name");
    }

    @Test
    void testGetFileFromS3WithContentType() throws IOException, IOException {
        // Mock S3 client behavior
        String bucketName = "test-bucket";
        String fileKey = "test-file.txt";
        String contentType = "text/plain";
        String fileContent = "Test file content";
        byte[] contentBytes = fileContent.getBytes(StandardCharsets.UTF_8);

        GetObjectResponse getObjectResponse = GetObjectResponse.builder()
                .contentType(contentType)
                .contentLength((long) contentBytes.length)
                .build();

        try (ResponseInputStream<GetObjectResponse> responseInputStream = new ResponseInputStream<>(getObjectResponse,
                new ByteArrayInputStream(contentBytes))) {
            when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);

            // Call the method being tested
            FileContent result = contentModerationService.getFileFromS3WithContentType(bucketName, fileKey);

            // Verify the results
            assertNotNull(result);
            assertEquals(contentType, result.contentType());
            assertArrayEquals(contentBytes, result.fileContent());
        }
    }

    @Test
    void testValidateFile() {
        // Test logic for rule-based validation (e.g., file type, size)
        assertTrue(contentModerationService.validateFile());
    }

    @Test
    void testInvokeAIModelForModeration_Image() throws Exception {
        // Mock SageMaker client behavior
        String contentType = "image";
        byte[] contentBytes = "test-image-content".getBytes(StandardCharsets.UTF_8);
        InvokeEndpointResponse invokeResponse = mock(InvokeEndpointResponse.class);
        SdkHttpResponse sdkHttpResponse = mock(SdkHttpResponse.class);
        when(invokeResponse.sdkHttpResponse()).thenReturn(sdkHttpResponse);
        when(invokeResponse.sdkHttpResponse().statusCode()).thenReturn(200);

        when(sageMakerRuntimeClient.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(invokeResponse);

        // Call the method being tested
        boolean result = contentModerationService.invokeAIModelForModeration(contentBytes, contentType);

        // Verify the results
        assertTrue(result);
    }

    @Test
    void testInvokeAIModelForModeration_Text() throws Exception {
        // Mock SageMaker client behavior
        String contentType = "text";
        byte[] contentBytes = "test-text-content".getBytes(StandardCharsets.UTF_8);
        InvokeEndpointResponse invokeResponse = mock(InvokeEndpointResponse.class);
        SdkHttpResponse sdkHttpResponse = mock(SdkHttpResponse.class);
        when(invokeResponse.sdkHttpResponse()).thenReturn(sdkHttpResponse);
        when(invokeResponse.sdkHttpResponse().statusCode()).thenReturn(200);

        when(sageMakerRuntimeClient.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(invokeResponse);

        // Call the method being tested
        boolean result = contentModerationService.invokeAIModelForModeration(contentBytes, contentType);

        // Verify the results
        assertTrue(result);
    }

    @Test
    void testUpdateSQLDBWithUserValidation() {
        // Mock repository behavior
        String fileKey = "test-file.txt";
        User user = new User();
        when(userRepository.findByFileKey(fileKey)).thenReturn(user);

        // Test valid case
        contentModerationService.updateSQLDBWithUserValidation(fileKey, true, true);
        assertEquals("VALID", user.getValidationStatus());
        verify(userRepository, times(1)).save(user);

        // Test invalid case
        contentModerationService.updateSQLDBWithUserValidation(fileKey, false, false);
        assertEquals("INVALID", user.getValidationStatus());
        verify(userRepository, times(2)).save(user);
    }

    @Test
    void testSendValidationStatusToSQS() {
        // Mock SQS client behavior
        String fileKey = "test-file.txt";
        SendMessageResponse response = mock(SendMessageResponse.class);
        doReturn(response).when(sqsClient).sendMessage(any(SendMessageRequest.class));

        // Test valid case
        contentModerationService.sendValidationStatusToSQS(fileKey, true);
        verify(sqsClient, times(1)).sendMessage(any(SendMessageRequest.class));

        // Test invalid case
        contentModerationService.sendValidationStatusToSQS(fileKey, false);
        verify(sqsClient, times(2)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testProcessFile() throws Exception {
        // Mock dependencies
        String bucketName = "my-bucket";
        String fileKey = "myfile.txt";
        String sqsMessage = "{\"bucket\":\"" + bucketName + "\",\"key\":\"" + fileKey + "\"}";
        String contentType = "text/plain";
        String fileContent = "Test file content";
        byte[] contentBytes = fileContent.getBytes(StandardCharsets.UTF_8);

        GetObjectResponse getObjectResponse = GetObjectResponse.builder()
                .contentType(contentType)
                .contentLength((long) contentBytes.length)
                .build();

        try (ResponseInputStream<GetObjectResponse> responseInputStream = new ResponseInputStream<>(getObjectResponse,
                new ByteArrayInputStream(contentBytes))) {
            when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);
        }

        InvokeEndpointResponse invokeResponse = mock(InvokeEndpointResponse.class);
        SdkHttpResponse sdkHttpResponse = mock(SdkHttpResponse.class);
        when(invokeResponse.sdkHttpResponse()).thenReturn(sdkHttpResponse);
        when(invokeResponse.sdkHttpResponse().statusCode()).thenReturn(200);


        when(sageMakerRuntimeClient.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(invokeResponse);

        User user = new User();
        when(userRepository.findByFileKey(fileKey)).thenReturn(user);

        SendMessageResponse response = mock(SendMessageResponse.class);
        doReturn(response).when(sqsClient).sendMessage(any(SendMessageRequest.class));

        // Call the method being tested
        contentModerationService.processFile(sqsMessage);

        // Verify interactions with dependencies
        verify(s3Client, times(1)).getObject(any(GetObjectRequest.class));
        verify(sageMakerRuntimeClient, times(1)).invokeEndpoint(any(InvokeEndpointRequest.class));
        verify(dynamoDBService, times(1)).storeMetadata(eq(fileKey), eq(bucketName), anyBoolean(), anyBoolean());
        verify(userRepository, times(1)).save(user);
        verify(sqsClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testExtractBucketName() {
        String sqsMessage = "{\"bucket\":\"my-bucket\",\"key\":\"test-file.txt\"}";
        String expectedBucketName = "my-bucket";
        String actualBucketName = contentModerationService.extractBucketName(sqsMessage);
        assertEquals(expectedBucketName, actualBucketName);
    }

    @Test
    void testExtractFileKey() {
        String sqsMessage = "{\"bucket\":\"test-bucket\",\"key\":\"myfile.txt\"}";
        String expectedFileKey = "myfile.txt";
        String actualFileKey = contentModerationService.extractFileKey(sqsMessage);
        assertEquals(expectedFileKey, actualFileKey);
    }
}