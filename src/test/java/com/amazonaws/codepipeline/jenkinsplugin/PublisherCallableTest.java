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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CompressionType;
import com.amazonaws.services.codepipeline.AWSCodePipeline;
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
    private static final String ACCESS_KEY = "AKIAIOSFODNN7EXAMPLE";
    private static final String SECRET_KEY = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
    private static final String PROXY_HOST = "localhost";
    private static final int PROXY_PORT = 80;
    private static final String REGION = "us-east-1";

    private static final String JOB_ID = UUID.randomUUID().toString();
    private static final String JOB_ACCESS_KEY = "BPTDIOSFODNN7EXAMPLE";
    private static final String JOB_SECRET_KEY = "xKdpsXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
    private static final String JOB_SESSION_TOKEN = "1231";

    private static AWSSessionCredentials JOB_CREDENTIALS = new AWSSessionCredentials()
            .withAccessKeyId(JOB_ACCESS_KEY)
            .withSecretAccessKey(JOB_SECRET_KEY)
            .withSessionToken(JOB_SESSION_TOKEN);

    private static final String S3_BUCKET_NAME = "bucket";
    private static final String S3_OBJECT_KEY = "object";
    private static final String UPLOAD_ID = "12312";

    private static final String TEST_FILE = "bbb.txt";

    @Mock private AWSClientFactory clientFactory;
    @Mock private AWSClients awsClients;
    @Mock private AWSCodePipeline codePipelineClient;
    @Mock private AmazonS3 s3Client;
    @Mock private Job job;
    @Mock private JobData jobData;
    @Mock private GetJobDetailsResult getJobDetailsResult;
    @Mock private JobDetails jobDetails;
    @Mock private JobData getJobDetailsJobData;
    @Mock private InitiateMultipartUploadResult initiateMultipartUploadResult;
    @Mock private UploadPartResult uploadPartResult;

    @Captor private ArgumentCaptor<GetJobDetailsRequest> getJobDetailsRequestCaptor;
    @Captor private ArgumentCaptor<AWSCredentialsProvider> credentialsProviderCaptor;
    @Captor private ArgumentCaptor<InitiateMultipartUploadRequest> initiateMultipartUploadRequestCaptor;
    @Captor private ArgumentCaptor<UploadPartRequest> uploadPartRequestCaptor;

    private ByteArrayOutputStream outContent;
    private CodePipelineStateModel model;
    private List<OutputArtifact> jenkinsOutputs;
    private List<Artifact> outputArtifacts;

    private S3ArtifactLocation s3ArtifactLocation = new S3ArtifactLocation()
            .withBucketName(S3_BUCKET_NAME).withObjectKey(S3_OBJECT_KEY);
    private S3ArtifactLocation s3ArtifactLocation1 = new S3ArtifactLocation()
            .withBucketName(S3_BUCKET_NAME + "1").withObjectKey(S3_OBJECT_KEY + "1");
    private S3ArtifactLocation s3ArtifactLocation2 = new S3ArtifactLocation()
            .withBucketName(S3_BUCKET_NAME + "2").withObjectKey(S3_OBJECT_KEY + "2");

    private ArtifactLocation artifactLocation = new ArtifactLocation().withS3Location(s3ArtifactLocation);
    private ArtifactLocation artifactLocation1 = new ArtifactLocation().withS3Location(s3ArtifactLocation1);
    private ArtifactLocation artifactLocation2 = new ArtifactLocation().withS3Location(s3ArtifactLocation2);

    private Artifact outputArtifact = new Artifact().withName("dummyArtifact").withLocation(artifactLocation);
    private Artifact outputArtifact1 = new Artifact().withName("dummyArtifact1").withLocation(artifactLocation1);
    private Artifact outputArtifact2 = new Artifact().withName("dummyArtifact2").withLocation(artifactLocation2);
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
        jenkinsOutputs.add(new OutputArtifact("", ""));

        outputArtifacts = new ArrayList<>();
        outputArtifacts.add(outputArtifact);

        when(clientFactory.getAwsClient(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString())).thenReturn(awsClients);
        when(awsClients.getCodePipelineClient()).thenReturn(codePipelineClient);
        when(awsClients.getS3Client(any(AWSCredentialsProvider.class))).thenReturn(s3Client);

        when(job.getId()).thenReturn(JOB_ID);
        when(job.getData()).thenReturn(jobData);
        when(jobData.getOutputArtifacts()).thenReturn(outputArtifacts);

        when(codePipelineClient.getJobDetails(any(GetJobDetailsRequest.class))).thenReturn(getJobDetailsResult);
        when(getJobDetailsResult.getJobDetails()).thenReturn(jobDetails);
        when(jobDetails.getData()).thenReturn(getJobDetailsJobData);
        when(getJobDetailsJobData.getArtifactCredentials()).thenReturn(JOB_CREDENTIALS);

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
    public void uploadsArtifactToS3() throws IOException {
        // when
        publisher.invoke(workspace, null);

        // then
        final InOrder inOrder = inOrder(clientFactory, awsClients, s3Client);
        inOrder.verify(clientFactory).getAwsClient(ACCESS_KEY, SECRET_KEY, PROXY_HOST, PROXY_PORT, REGION, PLUGIN_VERSION);
        inOrder.verify(awsClients).getCodePipelineClient();
        inOrder.verify(awsClients).getS3Client(credentialsProviderCaptor.capture());
        inOrder.verify(s3Client).initiateMultipartUpload(initiateMultipartUploadRequestCaptor.capture());
        inOrder.verify(s3Client).uploadPart(uploadPartRequestCaptor.capture());

        final com.amazonaws.auth.AWSSessionCredentials credentials
            = (com.amazonaws.auth.AWSSessionCredentials) credentialsProviderCaptor.getValue().getCredentials();
        assertEquals(JOB_ACCESS_KEY, credentials.getAWSAccessKeyId());
        assertEquals(JOB_SECRET_KEY, credentials.getAWSSecretKey());
        assertEquals(JOB_SESSION_TOKEN, credentials.getSessionToken());

        verify(codePipelineClient).getJobDetails(getJobDetailsRequestCaptor.capture());
        assertEquals(JOB_ID, getJobDetailsRequestCaptor.getValue().getJobId());

        final InitiateMultipartUploadRequest initRequest = initiateMultipartUploadRequestCaptor.getValue();
        assertEquals(S3_BUCKET_NAME, initRequest.getBucketName());
        assertEquals(S3_OBJECT_KEY, initRequest.getKey());

        final UploadPartRequest uploadRequest = uploadPartRequestCaptor.getValue();
        assertEquals(S3_BUCKET_NAME, uploadRequest.getBucketName());
        assertEquals(S3_OBJECT_KEY, uploadRequest.getKey());
        assertEquals(UPLOAD_ID, uploadRequest.getUploadId());

        assertContainsIgnoreCase("[AWS CodePipeline Plugin] Uploading artifact:", outContent.toString());
        assertContainsIgnoreCase("[AWS CodePipeline Plugin] Upload successful\n", outContent.toString());
    }

    @Test
    public void forDirectoriesUsesZipAsDefaultCompressionType() throws IOException {
        // given
        model.setCompressionType(CompressionType.None);
        jenkinsOutputs.clear();
        jenkinsOutputs.add(new OutputArtifact("", "dummyArtifact"));

        // when
        publisher.invoke(workspace, null);

        // then
        verify(s3Client).initiateMultipartUpload(initiateMultipartUploadRequestCaptor.capture());
        verify(s3Client).uploadPart(uploadPartRequestCaptor.capture());

        assertEquals("application/zip", initiateMultipartUploadRequestCaptor.getValue().getObjectMetadata().getContentType());
        assertTrue(uploadPartRequestCaptor.getValue().getFile().getName().endsWith(".zip"));
    }

    @Test
    public void forDirecotriesUsesCompressionTypeSpecifiedInModel() throws IOException {
        // given
        model.setCompressionType(CompressionType.Tar);
        jenkinsOutputs.clear();
        jenkinsOutputs.add(new OutputArtifact("", "dummyArtifact"));

        // when
        publisher.invoke(workspace, null);

        // then
        verify(s3Client).initiateMultipartUpload(initiateMultipartUploadRequestCaptor.capture());
        verify(s3Client).uploadPart(uploadPartRequestCaptor.capture());

        assertEquals("application/tar", initiateMultipartUploadRequestCaptor.getValue().getObjectMetadata().getContentType());
        assertTrue(uploadPartRequestCaptor.getValue().getFile().getName().endsWith(".tar"));
    }

    @Test
    public void doesNotUseCompressionWhenUploadingNormalFiles() throws IOException {
        // given
        jenkinsOutputs.clear();
        jenkinsOutputs.add(new OutputArtifact(TEST_FILE, "dummyArtifact"));

        // when
        publisher.invoke(workspace, null);

        // then
        verify(s3Client).initiateMultipartUpload(initiateMultipartUploadRequestCaptor.capture());
        verify(s3Client).uploadPart(uploadPartRequestCaptor.capture());

        assertNull(initiateMultipartUploadRequestCaptor.getValue().getObjectMetadata().getContentType());
        assertEquals("bbb.txt", uploadPartRequestCaptor.getValue().getFile().getName());
    }

    @Test
    public void canUploadMultipleOutputArtifacts() throws IOException {
        // given
        jenkinsOutputs.clear();
        jenkinsOutputs.add(new OutputArtifact(TEST_FILE, "dummyArtifact"));
        jenkinsOutputs.add(new OutputArtifact("Dir1", "dummyArtifact1"));
        jenkinsOutputs.add(new OutputArtifact("Dir2", "dummyArtifact2"));

        outputArtifacts.clear();
        outputArtifacts.add(outputArtifact);
        outputArtifacts.add(outputArtifact1);
        outputArtifacts.add(outputArtifact2);

        // when
        publisher.invoke(workspace, null);

        // then
        verify(s3Client, times(3)).initiateMultipartUpload(any(InitiateMultipartUploadRequest.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void failedValidationWhenOnlySomeOutputLocationHasArtifactName() throws IOException {
        // given
        jenkinsOutputs.clear();
        jenkinsOutputs.add(new OutputArtifact(TEST_FILE, "dummyArtifact"));
        jenkinsOutputs.add(new OutputArtifact("Dir1", ""));
        jenkinsOutputs.add(new OutputArtifact("Dir2", ""));

        outputArtifacts.clear();
        outputArtifacts.add(outputArtifact);
        outputArtifacts.add(outputArtifact1);
        outputArtifacts.add(outputArtifact2);

        // when
        publisher.invoke(workspace, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void failedValidationWhenArtifactNameAndOutputArtifactDoesNotMatch() throws IOException {
        // given
        jenkinsOutputs.clear();
        jenkinsOutputs.add(new OutputArtifact(TEST_FILE, "dummyArtifact"));
        jenkinsOutputs.add(new OutputArtifact("Dir1", "dummyArtifact1"));
        jenkinsOutputs.add(new OutputArtifact("Dir2", "dummyArtifact3"));

        outputArtifacts.clear();
        outputArtifacts.add(outputArtifact);
        outputArtifacts.add(outputArtifact1);
        outputArtifacts.add(outputArtifact2);

        // when
        publisher.invoke(workspace, null);
    }

}
