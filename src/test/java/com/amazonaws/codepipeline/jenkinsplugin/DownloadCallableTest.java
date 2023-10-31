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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.amazonaws.codepipeline.jenkinsplugin.TestUtils.assertContainsIgnoreCase;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

public class DownloadCallableTest {

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
    private static final String S3_OBJECT_KEY = "object.zip";

    private static final boolean CLEAR_WORKSPACE = true;

    @Mock private AWSClientFactory clientFactory;
    @Mock private AWSClients awsClients;
    @Mock private AWSCodePipeline codePipelineClient;
    @Mock private AmazonS3 s3Client;
    @Mock private S3Object s3Object;
    @Mock private Job job;
    @Mock private JobData jobData;
    @Mock private GetJobDetailsResult getJobDetailsResult;
    @Mock private JobDetails jobDetails;
    @Mock private JobData getJobDetailsJobData;
    @Mock private Artifact inputArtifact;
    @Mock private ArtifactLocation artifactLocation;
    @Mock private CodePipelineStateModel model;

    @Captor private ArgumentCaptor<GetJobDetailsRequest> getJobDetailsRequestCaptor;
    @Captor private ArgumentCaptor<AWSCredentialsProvider> credentialsProviderCaptor;

    private File workspace;
    private ByteArrayOutputStream outContent;

    private List<Artifact> inputArtifacts;
    private S3ArtifactLocation s3ArtifactLocation;
    private S3ObjectInputStream s3ObjectInputStream;

    private DownloadCallable downloader;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);

        TestUtils.initializeTestingFolders();
        workspace = Paths.get(TestUtils.TEST_DIR).toFile();
        outContent = TestUtils.setOutputStream();

        inputArtifacts = new ArrayList<>();
        inputArtifacts.add(inputArtifact);

        s3ArtifactLocation = new S3ArtifactLocation();
        s3ArtifactLocation.setBucketName(S3_BUCKET_NAME);
        s3ArtifactLocation.setObjectKey(S3_OBJECT_KEY);

        s3ObjectInputStream = new S3ObjectInputStream(
                new FileInputStream(getClass().getClassLoader().getResource("aws-codedeploy-demo.zip").getFile()),
                null,
                false);

        when(clientFactory.getAwsClient(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString())).thenReturn(awsClients);
        when(awsClients.getCodePipelineClient()).thenReturn(codePipelineClient);
        when(awsClients.getS3Client(any(AWSCredentialsProvider.class))).thenReturn(s3Client);
        when(s3Client.getObject(anyString(), anyString())).thenReturn(s3Object);
        when(s3Object.getKey()).thenReturn(S3_OBJECT_KEY);
        when(s3Object.getObjectContent()).thenReturn(s3ObjectInputStream);

        when(model.getAwsAccessKey()).thenReturn(ACCESS_KEY);
        when(model.getAwsSecretKey()).thenReturn(SECRET_KEY);
        when(model.getProxyHost()).thenReturn(PROXY_HOST);
        when(model.getProxyPort()).thenReturn(PROXY_PORT);
        when(model.getRegion()).thenReturn(REGION);
        when(model.getCompressionType()).thenReturn(CompressionType.Zip);

        when(job.getId()).thenReturn(JOB_ID);
        when(job.getData()).thenReturn(jobData);
        when(jobData.getInputArtifacts()).thenReturn(inputArtifacts);

        when(codePipelineClient.getJobDetails(any(GetJobDetailsRequest.class))).thenReturn(getJobDetailsResult);
        when(getJobDetailsResult.getJobDetails()).thenReturn(jobDetails);
        when(jobDetails.getData()).thenReturn(getJobDetailsJobData);
        when(getJobDetailsJobData.getArtifactCredentials()).thenReturn(JOB_CREDENTIALS);

        when(inputArtifact.getLocation()).thenReturn(artifactLocation);
        when(artifactLocation.getS3Location()).thenReturn(s3ArtifactLocation);

        downloader = new DownloadCallable(CLEAR_WORKSPACE, job, model, clientFactory, PLUGIN_VERSION, null);
    }

    @After
    public void tearDown() throws IOException {
        TestUtils.cleanUpTestingFolders();
        s3ObjectInputStream.close();
    }

    @Test
    public void getsArtifactFromS3() throws InterruptedException {
        // when
        downloader.invoke(workspace, null);

        // then
        final InOrder inOrder = inOrder(clientFactory, awsClients, s3Client, model);
        inOrder.verify(clientFactory).getAwsClient(ACCESS_KEY, SECRET_KEY, PROXY_HOST, PROXY_PORT, REGION, PLUGIN_VERSION);
        inOrder.verify(awsClients).getCodePipelineClient();
        inOrder.verify(awsClients).getS3Client(credentialsProviderCaptor.capture());
        inOrder.verify(s3Client).getObject(S3_BUCKET_NAME, S3_OBJECT_KEY);
        inOrder.verify(model).setCompressionType(CompressionType.Zip);

        final com.amazonaws.auth.AWSSessionCredentials credentials
            = (com.amazonaws.auth.AWSSessionCredentials) credentialsProviderCaptor.getValue().getCredentials();
        assertEquals(JOB_ACCESS_KEY, credentials.getAWSAccessKeyId());
        assertEquals(JOB_SECRET_KEY, credentials.getAWSSecretKey());
        assertEquals(JOB_SESSION_TOKEN, credentials.getSessionToken());

        verify(codePipelineClient).getJobDetails(getJobDetailsRequestCaptor.capture());
        assertEquals(JOB_ID, getJobDetailsRequestCaptor.getValue().getJobId());
    }

    @Test
    public void clearsWorkspace() throws InterruptedException {
        // given
        downloader = new DownloadCallable(true, job, model, clientFactory, PLUGIN_VERSION, null);

        // when
        downloader.invoke(workspace, null);

        // then
        assertFalse(doesWorkspaceFileExist("Dir1"));
        assertFalse(doesWorkspaceFileExist("bbb.txt"));
        assertContainsIgnoreCase("[AWS CodePipeline Plugin] Clearing workspace", outContent.toString());
    }

    @Test
    public void doesNotClearWorkspace() throws InterruptedException {
        // given
        downloader = new DownloadCallable(false, job, model, clientFactory, PLUGIN_VERSION, null);

        // when
        downloader.invoke(workspace, null);

        // then
        assertTrue(doesWorkspaceFileExist("Dir1"));
        assertTrue(doesWorkspaceFileExist("bbb.txt"));
        assertFalse(outContent.toString().toLowerCase().contains("[AWS CodePipeline Plugin] Clearing workspace".toLowerCase()));
    }

    @Test
    public void downloadsAndExtractsInputArchive() throws InterruptedException {
        // when
        downloader.invoke(workspace, null);

        // then
        verify(model).setCompressionType(CompressionType.Zip);

        assertTrue(doesWorkspaceFileExist("appspec.yml"));
        assertTrue(doesWorkspaceFileExist("src", "index.html.haml"));

        assertContainsIgnoreCase("[AWS CodePipeline Plugin] Successfully downloaded artifact from AWS CodePipeline", outContent.toString());
        assertContainsIgnoreCase("[AWS CodePipeline Plugin] Extracting", outContent.toString());
        assertContainsIgnoreCase("[AWS CodePipeline Plugin] Artifact uncompressed successfully", outContent.toString());
    }

    private boolean doesWorkspaceFileExist(final String... path) {
        return Paths.get(TestUtils.TEST_DIR, path).toFile().exists();
    }

}
