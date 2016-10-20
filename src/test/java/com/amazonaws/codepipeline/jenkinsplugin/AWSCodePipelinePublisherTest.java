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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import hudson.EnvVars;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CompressionType;
import com.amazonaws.services.codepipeline.AWSCodePipeline;
import com.amazonaws.services.codepipeline.model.ActionContext;
import com.amazonaws.services.codepipeline.model.Artifact;
import com.amazonaws.services.codepipeline.model.FailureType;
import com.amazonaws.services.codepipeline.model.Job;
import com.amazonaws.services.codepipeline.model.JobData;
import com.amazonaws.services.codepipeline.model.PipelineContext;
import com.amazonaws.services.codepipeline.model.PutJobFailureResultRequest;
import com.amazonaws.services.codepipeline.model.PutJobSuccessResultRequest;
import com.amazonaws.services.codepipeline.model.StageContext;
import com.amazonaws.services.s3.model.AmazonS3Exception;

public class AWSCodePipelinePublisherTest {

    private static final String REGION = "us-east-1";
    private static final String ACCESS_KEY = "1234";
    private static final String SECRET_KEY = "4321";
    private static final String PROXY_HOST = "";
    private static final int PROXY_PORT = 0;
    private static final String PLUGIN_VERSION = "aws-codepipeline:unknown";
    private static final String BUILD_ID = "34";

    @Mock private AWSClientFactory mockFactory;
    @Mock private AWSClients mockAWS;
    @Mock private AWSCodePipeline mockCodePipelineClient;
    @Mock private AbstractBuild mockBuild;
    @Mock private AbstractProject<?, ?> mockProject;
    @Mock private EnvVars vars;
    @Mock private Job mockJob;
    @Mock private JobData mockJobData;

    @Captor private ArgumentCaptor<PutJobSuccessResultRequest> putJobSuccessResultRequest;
    @Captor private ArgumentCaptor<PutJobFailureResultRequest> putJobFailureResultRequest;

    private String jobId;
    private CodePipelineStateModel model;
    private JSONArray outputLocations;
    private ByteArrayOutputStream outContent;

    private AWSCodePipelinePublisherMock publisher;

    @Before
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);
        outContent =  TestUtils.setOutputStream();

        final JSONObject jsonObjectOne = new JSONObject();
        jsonObjectOne.put("location", "output_1");
        final JSONObject jsonObjectTwo = new JSONObject();
        jsonObjectTwo.put("location", "output_2");

        outputLocations = new JSONArray();
        outputLocations.add(jsonObjectOne);
        outputLocations.add(jsonObjectTwo);

        publisher = new AWSCodePipelinePublisherMock(outputLocations, mockFactory);

        jobId = UUID.randomUUID().toString();

        model = new CodePipelineStateModel();
        model.setJob(mockJob);
        model.setAwsAccessKey(ACCESS_KEY);
        model.setAwsSecretKey(SECRET_KEY);
        model.setRegion(REGION);
        model.setProxyHost(PROXY_HOST);
        model.setProxyPort(PROXY_PORT);

        CodePipelineStateService.setModel(model);

        when(mockFactory.getAwsClient(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString())).thenReturn(mockAWS);
        when(mockJob.getId()).thenReturn(jobId);
        when(mockJob.getData()).thenReturn(mockJobData);

        when(mockAWS.getCodePipelineClient()).thenReturn(mockCodePipelineClient);

        when(mockBuild.getId()).thenReturn(BUILD_ID);
        when(mockBuild.getResult()).thenReturn(Result.SUCCESS);
        when(mockBuild.getProject()).thenReturn(mockProject);
        when(mockBuild.getEnvironment(any(TaskListener.class))).thenReturn(vars);

        when(vars.get(any(String.class))).thenReturn("Project");
        when(mockProject.getName()).thenReturn("Project");
    }

    @Test
    public void putsJobSuccessWhenBuildSucceeds() {
        // given
        when(mockBuild.getResult()).thenReturn(Result.SUCCESS);

        final List<Artifact> outputBuildArtifacts = new ArrayList<>();
        outputBuildArtifacts.add(new Artifact());
        outputBuildArtifacts.add(new Artifact());
        when(mockJobData.getOutputArtifacts()).thenReturn(outputBuildArtifacts);

        // when
        assertTrue(publisher.perform(mockBuild, null, null));

        // then
        final InOrder inOrder = inOrder(mockFactory, mockAWS, mockCodePipelineClient);
        inOrder.verify(mockFactory).getAwsClient(ACCESS_KEY, SECRET_KEY, PROXY_HOST, PROXY_PORT, REGION, PLUGIN_VERSION);
        inOrder.verify(mockAWS).getCodePipelineClient();
        inOrder.verify(mockCodePipelineClient).putJobSuccessResult(putJobSuccessResultRequest.capture());

        final PutJobSuccessResultRequest request = putJobSuccessResultRequest.getValue();
        assertEquals(jobId, request.getJobId());
        assertEquals(BUILD_ID, request.getExecutionDetails().getExternalExecutionId());
        assertEquals("Finished", request.getExecutionDetails().getSummary());

        final String expected1 = "[AWS CodePipeline Plugin] Publishing artifacts\n";
        final String expected2 = "[AWS CodePipeline Plugin] Build succeeded, calling PutJobSuccessResult\n";
        assertContainsIgnoreCase(expected1, outContent.toString());
        assertContainsIgnoreCase(expected2, outContent.toString());
    }

    @Test
    public void putsJobFailedWhenBuildFails() {
        // given
        when(mockBuild.getResult()).thenReturn(Result.FAILURE);
        model.setActionTypeCategory("Build");

        final List<Artifact> outputBuildArtifacts = new ArrayList<>();
        outputBuildArtifacts.add(new Artifact());
        outputBuildArtifacts.add(new Artifact());
        when(mockJobData.getOutputArtifacts()).thenReturn(outputBuildArtifacts);

        // when
        assertFalse(publisher.perform(mockBuild, null, null));

        // then
        final InOrder inOrder = inOrder(mockFactory, mockAWS, mockCodePipelineClient);
        inOrder.verify(mockFactory).getAwsClient(ACCESS_KEY, SECRET_KEY, PROXY_HOST, PROXY_PORT, REGION, PLUGIN_VERSION);
        inOrder.verify(mockAWS).getCodePipelineClient();
        inOrder.verify(mockCodePipelineClient).putJobFailureResult(putJobFailureResultRequest.capture());

        final PutJobFailureResultRequest request = putJobFailureResultRequest.getValue();
        assertEquals(jobId, request.getJobId());
        assertEquals(BUILD_ID, request.getFailureDetails().getExternalExecutionId());
        assertEquals("Build failed", request.getFailureDetails().getMessage());
        assertEquals(FailureType.JobFailed.toString(), request.getFailureDetails().getType());

        final String expected1 = "[AWS CodePipeline Plugin] Publishing artifacts\n";
        final String expected2 = "[AWS CodePipeline Plugin] Build failed, calling PutJobFailureResult\n";
        assertContainsIgnoreCase(expected1, outContent.toString());
        assertContainsIgnoreCase(expected2, outContent.toString());
    }

    @Test
    public void skipsPutJobResultWhenSkipPutJobFailureFlagIsSetInModel() {
        // given
        when(mockBuild.getResult()).thenReturn(Result.FAILURE);
        model.setActionTypeCategory("Build");
        model.setSkipPutJobResult(true);

        final List<Artifact> outputBuildArtifacts = new ArrayList<>();
        outputBuildArtifacts.add(new Artifact());
        outputBuildArtifacts.add(new Artifact());
        when(mockJobData.getOutputArtifacts()).thenReturn(outputBuildArtifacts);

        // when
        assertFalse(publisher.perform(mockBuild, null, null));

        // then
        final InOrder inOrder = inOrder(mockFactory, mockAWS, mockCodePipelineClient);
        inOrder.verify(mockFactory, never()).getAwsClient(ACCESS_KEY, SECRET_KEY, PROXY_HOST, PROXY_PORT, REGION, PLUGIN_VERSION);
        inOrder.verify(mockAWS, never()).getCodePipelineClient();

        final String expected = String.format(
                "[AWS CodePipeline Plugin] Skipping PutJobFailureResult call for the job with ID %s",
                model.getJob().getId());
        assertContainsIgnoreCase(expected, outContent.toString());
    }

    @Test
    public void putsJobFailedWhenTheNumberOfOutputArtifactsDoNotMatch() {
        // given
        when(mockBuild.getResult()).thenReturn(Result.SUCCESS);
        model.setActionTypeCategory("Test");

        final List<Artifact> outputArtifacts = new ArrayList<>();
        outputArtifacts.add(new Artifact());
        final PipelineContext pipelineContext = new PipelineContext()
                .withPipelineName("JenkinsPipeline")
                .withStage(new StageContext().withName("Build"))
                .withAction(new ActionContext().withName("JenkinsAction"));

        when(mockJobData.getOutputArtifacts()).thenReturn(outputArtifacts);
        when(mockJobData.getPipelineContext()).thenReturn(pipelineContext);

        // when
        assertFalse(publisher.perform(mockBuild, null, null));

        // then
        final InOrder inOrder = inOrder(mockFactory, mockAWS, mockCodePipelineClient);
        inOrder.verify(mockFactory).getAwsClient(ACCESS_KEY, SECRET_KEY, PROXY_HOST, PROXY_PORT, REGION, PLUGIN_VERSION);
        inOrder.verify(mockAWS).getCodePipelineClient();
        inOrder.verify(mockCodePipelineClient).putJobFailureResult(putJobFailureResultRequest.capture());

        final PutJobFailureResultRequest request = putJobFailureResultRequest.getValue();
        assertEquals(jobId, request.getJobId());
        assertEquals(BUILD_ID, request.getFailureDetails().getExternalExecutionId());
        assertEquals(FailureType.JobFailed.toString(), request.getFailureDetails().getType());
        assertTrue(request.getFailureDetails().getMessage().startsWith("Failed to upload output artifact(s): "
                    + "The number of output artifacts in the Jenkins"));

        final String expected1 = "[AWS CodePipeline Plugin] Publishing artifacts\n";
        final String expected2 = "[AWS CodePipeline Plugin] Build failed, calling PutJobFailureResult\n";
        final String expected3 = "[AWS CodePipeline Plugin] The number of output artifacts in the Jenkins "
                + "project and in the AWS CodePipeline pipeline action do not match.  Please configure the output "
                + "locations of your Jenkins project to match the AWS CodePipeline pipeline action's output "
                + "artifacts. Number of output locations in Jenkins project: 2, number of output artifacts in AWS "
                + "CodePipeline pipeline action: 1 [Pipeline: JenkinsPipeline, stage: Build, action: JenkinsAction].";

        assertContainsIgnoreCase(expected1, outContent.toString());
        assertContainsIgnoreCase(expected2, outContent.toString());
        assertContainsIgnoreCase(expected3, outContent.toString());
    }

    @Test
    public void putsJobFailedWhenArtifactUploadFails() {
        // given
        when(mockBuild.getResult()).thenReturn(Result.SUCCESS);

        final List<Artifact> outputBuildArtifacts = new ArrayList<>();
        outputBuildArtifacts.add(new Artifact());
        outputBuildArtifacts.add(new Artifact());
        when(mockJobData.getOutputArtifacts()).thenReturn(outputBuildArtifacts);

        final AWSCodePipelinePublisherMockS3Exception uploadFailurePublisher
            = new AWSCodePipelinePublisherMockS3Exception(outputLocations, mockFactory);

        // when
        assertFalse(uploadFailurePublisher.perform(mockBuild, null, null));

        // then
        final InOrder inOrder = inOrder(mockFactory, mockAWS, mockCodePipelineClient);
        inOrder.verify(mockFactory).getAwsClient(ACCESS_KEY, SECRET_KEY, PROXY_HOST, PROXY_PORT, REGION, PLUGIN_VERSION);
        inOrder.verify(mockAWS).getCodePipelineClient();
        inOrder.verify(mockCodePipelineClient).putJobFailureResult(putJobFailureResultRequest.capture());

        final PutJobFailureResultRequest request = putJobFailureResultRequest.getValue();
        assertEquals(jobId, request.getJobId());
        assertEquals(BUILD_ID, request.getFailureDetails().getExternalExecutionId());
        assertEquals("Failed to upload output artifact(s): S3 root cause", request.getFailureDetails().getMessage());
        assertEquals(FailureType.JobFailed.toString(), request.getFailureDetails().getType());

        final String expected1 = "[AWS CodePipeline Plugin] Publishing artifacts\n";
        final String expected2 = "[AWS CodePipeline Plugin] Build failed, calling PutJobFailureResult\n";
        assertContainsIgnoreCase(expected1, outContent.toString());
        assertContainsIgnoreCase(expected2, outContent.toString());
    }

    @Test
    public void cleanUpSuccess() {
        // given
        model.setCompressionType(CompressionType.Zip);

        // when
        publisher.cleanUp(model);

        // then
        assertNull(model.getJob());
        assertEquals(CompressionType.None, model.getCompressionType());
        assertNull(CodePipelineStateService.getModel());
    }

    // -----Setup and Util Methods----- //

    public class AWSCodePipelinePublisherMock extends AWSCodePipelinePublisher {
        public AWSCodePipelinePublisherMock(final JSONArray outputLocations, final AWSClientFactory mockFactory) {
            super(outputLocations, mockFactory);
        }

        @Override
        public void callPublish(final AbstractBuild<?,?> action, final CodePipelineStateModel model, final BuildListener listener) {
            // Do nothing...
        }
    }

    public class AWSCodePipelinePublisherMockS3Exception extends AWSCodePipelinePublisher {
        public AWSCodePipelinePublisherMockS3Exception(final JSONArray outputLocations, final AWSClientFactory mockFactory) {
            super(outputLocations, mockFactory);
        }

        @Override
        public void callPublish(final AbstractBuild<?,?> action, final CodePipelineStateModel model, final BuildListener listener) {
            throw new AmazonS3Exception("S3 root cause");
        }
    }

}
