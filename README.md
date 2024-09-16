content-moderation-service

This service does the following,
1. It consumes the message from SQS about the file uploaded in S3.
2. It download the file from S3 and then it validates based on the
   Rules, AI/ML model and local validation.
3. Then it stores the metadata in Dynamo DB with the information such as rulebasedValidation, AI/ML validation result with file and bucket name.
4. It also saves the data in SQL DB with the user information regarding the user validation status by file.
5. Once all done, then it sends the result message to SQS, so that the next service can pick the data and perform additional checks.  
