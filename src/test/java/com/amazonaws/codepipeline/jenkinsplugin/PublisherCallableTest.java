/*
 * Copyright 2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static com.amazonaws.codepipeline.jenkinsplugin.TestUtils.assertContainsIgnoreCase;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.After;
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
import com.amazonaws.services.codepipeline.model.AWSSessionCredentials;
import com.amazonaws.services.codepipeline.model.Artifact;
import com.amazonaws.services.codepipeline.model.ArtifactLocation;
import com.amazonaws.services.codepipeline.model.GetJobDetailsRequest;
import com.amazonaws.services.codepipeline.model.GetJobDetailsResult;
import com.amazonaws.services.codepipeline.model.Job;
import com.amazonaws.services.codepipeline.model.JobData;
import com.amazonaws.services.codepipeline.model.JobDetails;
import com.amazonaws.services.codepipeline.model.S3ArtifactLocation;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.model.UploadPartResult;

public class PublisherCallableTest {

    private static final String PROJECT_NAME = "Project";
    private static final String PLUGIN_VERSION = "aws-codepipeline:0.15";
    private static final String ACCESS_KEY = "zadfj";
    private static final String SECRET_KEY = "afjlsf";
    private static final String PROXY_HOST = "localhost";
    private static final int PROXY_PORT = 80;
    private static final String REGION = "us-east-1";

    private static final String JOB_ID = UUID.randomUUID().toString();
    private static final String JOB_ACCESS_KEY = "asdfljk";
    private static final String JOB_SECRET_KEY = "adflakj";
    private static final String JOB_SESSION_TOKEN = "1231";

    private static final String S3_BUCKET_NAME = "bucket";
    private static final String S3_OBJECT_KEY = "object";
    private static final String UPLOAD_ID = "12312";

    private static final String TEST_FILE = "bbb.txt";

    @Mock private AWSClientFactory clientFactory;
    @Mock private AWSClients awsClients;
    @Mock private AWSCodePipelineClient codePipelineClient;
    @Mock private AmazonS3 s3Client;
    @Mock private Job job;
    @Mock private GetJobDetailsResult getJobDetailsResult;
    @Mock private JobDetails jobDetails;
    @Mock private JobData originaJobData;
    @Mock private JobData jobData;
    @Mock private Artifact outputArtifact;
    @Mock private ArtifactLocation artifactLocation;
    @Mock private InitiateMultipartUploadResult initiateMultipartUploadResult;
    @Mock private UploadPartResult uploadPartResult;

    @Captor private ArgumentCaptor<GetJobDetailsRequest> getJobDetailsRequest;
    @Captor private ArgumentCaptor<com.amazonaws.auth.AWSSessionCredentials> sessionCredentials;
    @Captor private ArgumentCaptor<InitiateMultipartUploadRequest> initiateMultipartUploadRequest;
    @Captor private ArgumentCaptor<UploadPartRequest> uploadPartRequest;

    private ByteArrayOutputStream outContent;
    private AWSSessionCredentials jobCredentials;
    private CodePipelineStateModel model;
    private List<OutputArtifact> jenkinsOutputs;
    private List<Artifact> outputArtifacts;
    private S3ArtifactLocation s3ArtifactLocation;
    private File workspace;

    private PublisherCallable publisher;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);

        TestUtils.initializeTestingFolders();
        workspace = Paths.get(TestUtils.TEST_DIR).toFile();
        outContent = TestUtils.setOutputStream();

        try (final PrintWriter writer = new PrintWriter(new File(workspace, TEST_FILE))) {
            writer.println("Some content");
        }

        model = new CodePipelineStateModel();
        model.setJob(job);
        model.setCompressionType(CompressionType.Zip);
        model.setAwsAccessKey(ACCESS_KEY);
        model.setAwsSecretKey(SECRET_KEY);
        model.setProxyHost(PROXY_HOST);
        model.setProxyPort(PROXY_PORT);
        model.setRegion(REGION);

        jenkinsOutputs = new ArrayList<>();
        jenkinsOutputs.add(new OutputArtifact(""));

        outputArtifacts = new ArrayList<>();
        outputArtifacts.add(outputArtifact);

        s3ArtifactLocation = new S3ArtifactLocation();
        s3ArtifactLocation.setBucketName(S3_BUCKET_NAME);
        s3ArtifactLocation.setObjectKey(S3_OBJECT_KEY);

        jobCredentials = new AWSSessionCredentials()
            .withAccessKeyId(JOB_ACCESS_KEY)
            .withSecretAccessKey(JOB_SECRET_KEY)
            .withSessionToken(JOB_SESSION_TOKEN);

        when(clientFactory.getAwsClient(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString())).thenReturn(awsClients);
        when(awsClients.getCodePipelineClient()).thenReturn(codePipelineClient);
        when(awsClients.getS3Client(any(BasicSessionCredentials.class))).thenReturn(s3Client);
        when(codePipelineClient.getJobDetails(any(GetJobDetailsRequest.class))).thenReturn(getJobDetailsResult);

        when(job.getId()).thenReturn(JOB_ID);
        when(job.getData()).thenReturn(originaJobData);
        when(originaJobData.getOutputArtifacts()).thenReturn(outputArtifacts);
        when(getJobDetailsResult.getJobDetails()).thenReturn(jobDetails);
        when(jobDetails.getData()).thenReturn(jobData);
        when(jobData.getArtifactCredentials()).thenReturn(jobCredentials);

        when(outputArtifact.getLocation()).thenReturn(artifactLocation);
        when(artifactLocation.getS3Location()).thenReturn(s3ArtifactLocation);
        when(s3Client.initiateMultipartUpload(any(InitiateMultipartUploadRequest.class))).thenReturn(initiateMultipartUploadResult);
        when(initiateMultipartUploadResult.getUploadId()).thenReturn(UPLOAD_ID);
        when(s3Client.uploadPart(any(UploadPartRequest.class))).thenReturn(uploadPartResult);
        when(uploadPartResult.getPartETag()).thenReturn(new PartETag(1, "asdf"));

        publisher = new PublisherCallable(PROJECT_NAME, model, jenkinsOutputs, clientFactory, PLUGIN_VERSION, null);
    }

    @After
    public void tearDown() throws IOException {
        TestUtils.cleanUpTestingFolders();
    }

    @Test
    public void callsGetJobDetailsToObtainFreshCredentials() throws IOException {
        // when
        publisher.invoke(workspace, null);

        // then
        final InOrder inOrder = inOrder(clientFactory, awsClients, codePipelineClient, s3Client);
        inOrder.verify(clientFactory).getAwsClient(ACCESS_KEY, SECRET_KEY, PROXY_HOST, PROXY_PORT, REGION, PLUGIN_VERSION);
        inOrder.verify(awsClients).getCodePipelineClient();
        inOrder.verify(codePipelineClient).getJobDetails(getJobDetailsRequest.capture());
        inOrder.verify(awsClients).getS3Client(sessionCredentials.capture());
        inOrder.verify(s3Client).initiateMultipartUpload(initiateMultipartUploadRequest.capture());
        inOrder.verify(s3Client).uploadPart(uploadPartRequest.capture());

        assertEquals(JOB_ID, getJobDetailsRequest.getValue().getJobId());

        assertEquals(JOB_ACCESS_KEY, sessionCredentials.getValue().getAWSAccessKeyId());
        assertEquals(JOB_SECRET_KEY, sessionCredentials.getValue().getAWSSecretKey());
        assertEquals(JOB_SESSION_TOKEN, sessionCredentials.getValue().getSessionToken());

        final InitiateMultipartUploadRequest initRequest = initiateMultipartUploadRequest.getValue();
        assertEquals(S3_BUCKET_NAME, initRequest.getBucketName());
        assertEquals(S3_OBJECT_KEY, initRequest.getKey());

        final UploadPartRequest uploadRequest = uploadPartRequest.getValue();
        assertEquals(S3_BUCKET_NAME, uploadRequest.getBucketName());
        assertEquals(S3_OBJECT_KEY, uploadRequest.getKey());
        assertEquals(UPLOAD_ID, uploadRequest.getUploadId());

        assertContainsIgnoreCase("[AWS CodePipeline Plugin] Uploading Artifact:", outContent.toString());
        assertContainsIgnoreCase("[AWS CodePipeline Plugin] Upload Successful\n", outContent.toString());
    }

    @Test
    public void forDirectoriesUsesZipAsDefaultCompressionType() throws IOException {
        // given
        model.setCompressionType(CompressionType.None);

        // when
        publisher.invoke(workspace, null);

        // then
        verify(s3Client).initiateMultipartUpload(initiateMultipartUploadRequest.capture());
        verify(s3Client).uploadPart(uploadPartRequest.capture());
        assertEquals("application/zip", initiateMultipartUploadRequest.getValue().getObjectMetadata().getContentType());
        assertTrue(uploadPartRequest.getValue().getFile().getName().endsWith(".zip"));
    }

    @Test
    public void forDirecotriesUsesCompressionTypeSpecifiedInModel() throws IOException {
        // given
        model.setCompressionType(CompressionType.Tar);

        // when
        publisher.invoke(workspace, null);

        // then
        verify(s3Client).initiateMultipartUpload(initiateMultipartUploadRequest.capture());
        verify(s3Client).uploadPart(uploadPartRequest.capture());
        assertEquals("application/tar", initiateMultipartUploadRequest.getValue().getObjectMetadata().getContentType());
        assertTrue(uploadPartRequest.getValue().getFile().getName().endsWith(".tar"));
    }

    @Test
    public void doesNotUseCompressionWhenUploadingNormalFiles() throws IOException {
        // given
        jenkinsOutputs.clear();
        jenkinsOutputs.add(new OutputArtifact("bbb.txt"));

        // when
        publisher.invoke(workspace, null);

        // then
        verify(s3Client).initiateMultipartUpload(initiateMultipartUploadRequest.capture());
        verify(s3Client).uploadPart(uploadPartRequest.capture());
        assertNull(initiateMultipartUploadRequest.getValue().getObjectMetadata().getContentType());
        assertEquals("bbb.txt", uploadPartRequest.getValue().getFile().getName());
    }

    //@Test
    public void canUploadMultipleOutputArtifacts() throws IOException {
        // given
        jenkinsOutputs.clear();
        jenkinsOutputs.add(new OutputArtifact(TEST_FILE));
        jenkinsOutputs.add(new OutputArtifact("Dir1"));
        jenkinsOutputs.add(new OutputArtifact("Dir2"));

        outputArtifacts.clear();
        outputArtifacts.add(outputArtifact);
        outputArtifacts.add(outputArtifact);
        outputArtifacts.add(outputArtifact);

        // when
        publisher.invoke(workspace, null);

        // then
        verify(s3Client, times(3)).initiateMultipartUpload(any(InitiateMultipartUploadRequest.class));
    }

}
