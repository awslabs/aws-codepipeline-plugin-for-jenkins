/*
 * Copyright 2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.codepipeline.jenkinsplugin;

import static com.amazonaws.codepipeline.jenkinsplugin.TestUtils.assertContainsIgnoreCase;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CompressionType;
import com.amazonaws.services.codepipeline.AWSCodePipelineClient;
import com.amazonaws.services.codepipeline.model.Artifact;
import com.amazonaws.services.codepipeline.model.ArtifactLocation;
import com.amazonaws.services.codepipeline.model.EncryptionKey;
import com.amazonaws.services.codepipeline.model.EncryptionKeyType;
import com.amazonaws.services.codepipeline.model.PutJobFailureResultRequest;
import com.amazonaws.services.codepipeline.model.PutJobSuccessResultRequest;
import com.amazonaws.services.codepipeline.model.S3ArtifactLocation;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.SSEAwsKeyManagementParams;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.model.UploadPartResult;

public class PublisherToolsTest {

    private static Path PATH_TO_COMPRESS = Paths.get(TestUtils.TEST_DIR);

    private ByteArrayOutputStream outContent;

    @Mock private AWSClients mockAWS;
    @Mock private AWSCodePipelineClient mockCodePipelineClient;
    @Mock private Artifact mockArtifact;
    @Mock private BasicSessionCredentials mockCredentials;
    @Mock private AmazonS3 mockS3Client;
    @Mock private ArtifactLocation mockLocation;
    @Mock private S3ArtifactLocation s3ArtifactLocation;
    @Mock private InitiateMultipartUploadResult mockUploadResult;
    @Mock private UploadPartResult mockPartRequest;
    @Mock private EncryptionKey mockEncryptionKey;

    @Captor private ArgumentCaptor<InitiateMultipartUploadRequest> initiateCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mockAWS.getCodePipelineClient()).thenReturn(mockCodePipelineClient);
        when(mockAWS.getS3Client(any(BasicSessionCredentials.class))).thenReturn(mockS3Client);
        when(mockS3Client.initiateMultipartUpload(any(InitiateMultipartUploadRequest.class)))
                .thenReturn(mockUploadResult);
        when(mockS3Client.uploadPart(any(UploadPartRequest.class))).thenReturn(mockPartRequest);
        when(mockUploadResult.getUploadId()).thenReturn("123");
        when(mockArtifact.getLocation()).thenReturn(mockLocation);
        when(mockLocation.getS3Location()).thenReturn(s3ArtifactLocation);
        when(s3ArtifactLocation.getBucketName()).thenReturn("Bucket");
        when(s3ArtifactLocation.getObjectKey()).thenReturn("Key");

        outContent = TestUtils.setOutputStream();
    }

    @Test
    public void putJobResultBuildSucceededSuccess() {
        final String message = "[AWS CodePipeline Plugin] Build Succeeded. PutJobSuccessResult\n";

        PublisherTools.putJobResult(
                true, // Build Succeeded
                "",   // Error Message
                "0",  // ActionID
                "1",  // Job ID
                mockAWS,
                null); // Listener

        verify(mockCodePipelineClient, times(1)).putJobSuccessResult(any(PutJobSuccessResultRequest.class));
        assertEquals(outContent.toString().toLowerCase(), message.toLowerCase());
    }

    @Test
    public void putJobResultBuildFailureSuccess() {
        final String message = "[AWS CodePipeline Plugin] Build Failed. PutJobFailureResult\n";

        PublisherTools.putJobResult(
                false, // Build Succeeded
                "Generic Error",   // Error Message
                "0",  // ActionID
                "1",  // Job ID
                mockAWS,
                null); // Listener

        final ArgumentCaptor<PutJobFailureResultRequest> failureRequest =
                ArgumentCaptor.forClass(PutJobFailureResultRequest.class);
        verify(mockCodePipelineClient, times(1)).putJobFailureResult(failureRequest.capture());
        assertEquals("Generic Error".toLowerCase(),
                failureRequest.getValue().getFailureDetails().getMessage().toLowerCase());
        assertEquals(outContent.toString().toLowerCase(), message.toLowerCase());
    }

    @Test
    public void uploadFileSuccess() throws IOException {
        TestUtils.initializeTestingFolders();

        final File compressedFile = CompressionTools.compressFile(
                "ZipProject",
                PATH_TO_COMPRESS,
                CompressionType.Zip,
                null);

        PublisherTools.uploadFile(
                compressedFile,
                mockArtifact,
                CompressionType.Zip,
                null, // No custom encryption key
                mockCredentials,
                mockAWS,
                null); // Listener

        final InOrder inOrder = inOrder(mockS3Client);
        inOrder.verify(mockS3Client, times(1)).initiateMultipartUpload(initiateCaptor.capture());
        // Total size is less than 5MB, should only be one upload
        inOrder.verify(mockS3Client, times(1)).uploadPart(any(UploadPartRequest.class));
        inOrder.verify(mockS3Client, times(1)).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));

        assertContainsIgnoreCase("[AWS CodePipeline Plugin] Uploading Artifact:", outContent.toString());
        assertContainsIgnoreCase("[AWS CodePipeline Plugin] Upload Successful\n", outContent.toString());

        final InitiateMultipartUploadRequest request = initiateCaptor.getValue();
        final SSEAwsKeyManagementParams encryptionParams = request.getSSEAwsKeyManagementParams();
        assertNotNull(encryptionParams);
        assertNull(encryptionParams.getAwsKmsKeyId());
        assertEquals("aws:kms", encryptionParams.getEncryption());

        compressedFile.delete();
        TestUtils.cleanUpTestingFolders();
    }

    @Test
    public void uploadWithCustomKmsEncryptionKey() throws IOException {
        TestUtils.initializeTestingFolders();

        when(mockEncryptionKey.getId()).thenReturn("KMS-KEY-ARN");
        when(mockEncryptionKey.getType()).thenReturn(EncryptionKeyType.KMS.toString());

        final File compressedFile = CompressionTools.compressFile(
                "ZipProject",
                PATH_TO_COMPRESS,
                CompressionType.Zip,
                null);

        PublisherTools.uploadFile(
                compressedFile,
                mockArtifact,
                CompressionType.Zip,
                mockEncryptionKey,
                mockCredentials,
                mockAWS,
                null); // Listener

        verify(mockS3Client).initiateMultipartUpload(initiateCaptor.capture());

        assertContainsIgnoreCase("[AWS CodePipeline Plugin] Upload Successful\n", outContent.toString());

        final InitiateMultipartUploadRequest request = initiateCaptor.getValue();
        final SSEAwsKeyManagementParams encryptionParams = request.getSSEAwsKeyManagementParams();
        assertNotNull(encryptionParams);
        assertEquals("KMS-KEY-ARN", encryptionParams.getAwsKmsKeyId());
        assertEquals("aws:kms", encryptionParams.getEncryption());

        compressedFile.delete();
        TestUtils.cleanUpTestingFolders();
    }

    @Test
    public void uploadWithUnknownEncryptionKeyType() throws IOException {
        TestUtils.initializeTestingFolders();

        when(mockEncryptionKey.getId()).thenReturn("KMS-KEY-ARN");
        when(mockEncryptionKey.getType()).thenReturn("Custom");

        final File compressedFile = CompressionTools.compressFile(
                "ZipProject",
                PATH_TO_COMPRESS,
                CompressionType.Zip,
                null);

        PublisherTools.uploadFile(
                compressedFile,
                mockArtifact,
                CompressionType.Zip,
                mockEncryptionKey,
                mockCredentials,
                mockAWS,
                null); // Listener

        verify(mockS3Client).initiateMultipartUpload(initiateCaptor.capture());

        assertContainsIgnoreCase("[AWS CodePipeline Plugin] Upload Successful\n", outContent.toString());

        final InitiateMultipartUploadRequest request = initiateCaptor.getValue();
        final SSEAwsKeyManagementParams encryptionParams = request.getSSEAwsKeyManagementParams();
        assertNotNull(encryptionParams);
        assertNull(encryptionParams.getAwsKmsKeyId());
        assertEquals("aws:kms", encryptionParams.getEncryption());

        compressedFile.delete();
        TestUtils.cleanUpTestingFolders();
    }

    @Test
    public void createObjectMetadataForZipCompressionType() {
        final ObjectMetadata metadata = PublisherTools.createObjectMetadata(CompressionType.Zip);

        assertEquals("application/zip", metadata.getContentType());
    }

    @Test
    public void createObjectMetadataForTarCompressionType() {
        final ObjectMetadata metadata = PublisherTools.createObjectMetadata(CompressionType.Tar);

        assertEquals("application/tar", metadata.getContentType());
    }

    @Test
    public void createObjectMetadataForTarGzCompressionType() {
        final ObjectMetadata metadata = PublisherTools.createObjectMetadata(CompressionType.TarGz);

        assertEquals("application/gzip", metadata.getContentType());
    }

    @Test
    public void createObjectMetadataForUnknownCompressionType() {
        final ObjectMetadata metadata = PublisherTools.createObjectMetadata(CompressionType.None);

        assertNull(metadata.getContentType());
    }

}
