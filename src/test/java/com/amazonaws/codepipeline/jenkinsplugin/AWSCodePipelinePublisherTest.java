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
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;


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

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class AWSCodePipelinePublisherTest {

    private static final String REGION = "us-east-1";
    private static final String ACCESS_KEY = "1234";
    private static final String SECRET_KEY = "4321";
    private static final String PROXY_HOST = "";
    private static final int PROXY_PORT = 0;
    private static final String PLUGIN_VERSION = "aws-codepipeline/unknown jenkins/" + Jenkins.getVersion();
    private static final String BUILD_ID = "34";

    private static final String PUBLISHING_ARTIFACTS_MESSAGE = "[AWS CodePipeline Plugin] Publishing artifacts";
    private static final String PUT_JOB_FAILURE_MESSAGE = "[AWS CodePipeline Plugin] Build failed, calling PutJobFailureResult";
    private static final String PUT_JOB_SUCCESS_MESSAGE = "[AWS CodePipeline Plugin] Build succeeded, calling PutJobSuccessResult";

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

        outputLocations = generateOutputLocations(Arrays.asList("output_1", "output_2"), Arrays.asList("artifact_1", "artifact_2"));

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
        when(mockJobData.getOutputArtifacts()).thenReturn(generateOutputArtifactsWithNames(Arrays.asList("artifact_1", "artifact_2")));

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

        assertContainsIgnoreCase(PUBLISHING_ARTIFACTS_MESSAGE, outContent.toString());
        assertContainsIgnoreCase(PUT_JOB_SUCCESS_MESSAGE, outContent.toString());
    }

    @Test
    public void putsJobSuccessWhenBuildSucceedsWithOneOutputLocation() {
        // given
        when(mockBuild.getResult()).thenReturn(Result.SUCCESS);

        JSONArray outputs = generateOutputLocations(Arrays.asList(""), Arrays.asList(""));
        publisher = new AWSCodePipelinePublisherMock(outputs, mockFactory);

        when(mockJobData.getOutputArtifacts()).thenReturn(generateOutputArtifactsWithNames(Arrays.asList("artifact_1")));

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

        assertContainsIgnoreCase(PUBLISHING_ARTIFACTS_MESSAGE, outContent.toString());
        assertContainsIgnoreCase(PUT_JOB_SUCCESS_MESSAGE, outContent.toString());
    }

    @Test
    public void putsJobSuccessWhenBuildSucceedsWithNoLocationsSpecified() {
        // given
        when(mockBuild.getResult()).thenReturn(Result.SUCCESS);

        JSONArray outputs = generateOutputLocations(Arrays.asList("", ""), Arrays.asList("", ""));
        publisher = new AWSCodePipelinePublisherMock(outputs, mockFactory);

        when(mockJobData.getOutputArtifacts()).thenReturn(generateOutputArtifactsWithNames(Arrays.asList("artifact_1", "artifact_2")));

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

        assertContainsIgnoreCase(PUBLISHING_ARTIFACTS_MESSAGE, outContent.toString());
        assertContainsIgnoreCase(PUT_JOB_SUCCESS_MESSAGE, outContent.toString());
    }

    @Test
    public void putsJobSuccessWhenBuildSucceedsWithNoArtifactNamesSpecified() {
        // given
        when(mockBuild.getResult()).thenReturn(Result.SUCCESS);

        JSONArray outputs = generateOutputLocations(Arrays.asList("output_1", "output_2"), Arrays.asList("", ""));
        publisher = new AWSCodePipelinePublisherMock(outputs, mockFactory);

        when(mockJobData.getOutputArtifacts()).thenReturn(generateOutputArtifactsWithNames(Arrays.asList("artifact_1", "artifact_2")));

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

        assertContainsIgnoreCase(PUBLISHING_ARTIFACTS_MESSAGE, outContent.toString());
        assertContainsIgnoreCase(PUT_JOB_SUCCESS_MESSAGE, outContent.toString());
    }

    @Test
    public void putsJobFailedWhenBuildFails() {
        // given
        when(mockBuild.getResult()).thenReturn(Result.FAILURE);
        model.setActionTypeCategory("Build");

        when(mockJobData.getOutputArtifacts()).thenReturn(generateOutputArtifactsWithNames(Arrays.asList("artifact_1", "artifact_2")));

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

        assertContainsIgnoreCase(PUBLISHING_ARTIFACTS_MESSAGE, outContent.toString());
        assertContainsIgnoreCase(PUT_JOB_FAILURE_MESSAGE, outContent.toString());
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

        final String expected = "[AWS CodePipeline Plugin] The number of output artifacts in the Jenkins project and in "
                + "the pipeline action do not match. Configure the output locations of your Jenkins project to match "
                + "the pipeline action's output artifacts. Number of output locations in Jenkins project: 2, number of "
                + "output artifacts in the pipeline action: 1 [Pipeline: JenkinsPipeline, stage: Build, action: JenkinsAction].";

        assertContainsIgnoreCase(PUBLISHING_ARTIFACTS_MESSAGE, outContent.toString());
        assertContainsIgnoreCase(PUT_JOB_FAILURE_MESSAGE, outContent.toString());
        assertContainsIgnoreCase(expected, outContent.toString());
    }

    @Test
    public void putsJobFailedWhenNotAllOutputArtifactNamesAreEntered() {
        // given
        when(mockBuild.getResult()).thenReturn(Result.SUCCESS);
        model.setActionTypeCategory("Test");

        JSONArray outputs = generateOutputLocations(Arrays.asList("output_1", "output_2"), Arrays.asList("artifact_1", ""));

        publisher = new AWSCodePipelinePublisherMock(outputs, mockFactory);

        final PipelineContext pipelineContext = new PipelineContext()
                .withPipelineName("JenkinsPipeline")
                .withStage(new StageContext().withName("Build"))
                .withAction(new ActionContext().withName("JenkinsAction"));

        when(mockJobData.getOutputArtifacts()).thenReturn(generateOutputArtifactsWithNames(Arrays.asList("artifact_1", "artifact_2")));
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
        assertTrue(request.getFailureDetails().getMessage().endsWith("Either configure the artifact name of each "
                + "location to match output artifacts for the pipeline action, or leave the field blank. "
                + "[Pipeline: JenkinsPipeline, stage: Build, action: JenkinsAction]."));

        final String expected = "[AWS CodePipeline Plugin] Artifact names in the Jenkins project do not match output "
                + "artifacts in the pipeline action. Either configure the artifact name of each location to match "
                + "output artifacts for the pipeline action, or leave the field blank. "
                + "[Pipeline: JenkinsPipeline, stage: Build, action: JenkinsAction].";

        assertContainsIgnoreCase(PUBLISHING_ARTIFACTS_MESSAGE, outContent.toString());
        assertContainsIgnoreCase(PUT_JOB_FAILURE_MESSAGE, outContent.toString());
        assertContainsIgnoreCase(expected, outContent.toString());
    }

    @Test
    public void putsJobFailedWhenOutputArtifactNamesDoNotMatch() {
        // given
        when(mockBuild.getResult()).thenReturn(Result.SUCCESS);
        model.setActionTypeCategory("Test");

        final PipelineContext pipelineContext = new PipelineContext()
                .withPipelineName("JenkinsPipeline")
                .withStage(new StageContext().withName("Build"))
                .withAction(new ActionContext().withName("JenkinsAction"));

        when(mockJobData.getOutputArtifacts()).thenReturn(generateOutputArtifactsWithNames(Arrays.asList("artifact1", "artifact_2")));
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
        assertTrue(request.getFailureDetails().getMessage().endsWith("Either configure the artifact name of each "
                + "location to match output artifacts for the pipeline action, or leave the field blank. "
                + "[Pipeline: JenkinsPipeline, stage: Build, action: JenkinsAction]."));

        final String expected = "[AWS CodePipeline Plugin] Artifact names in the Jenkins project do not match output "
                + "artifacts in the pipeline action. Either configure the artifact name of each location to match "
                + "output artifacts for the pipeline action, or leave the field blank. "
                + "[Pipeline: JenkinsPipeline, stage: Build, action: JenkinsAction].";

        assertContainsIgnoreCase(PUBLISHING_ARTIFACTS_MESSAGE, outContent.toString());
        assertContainsIgnoreCase(PUT_JOB_FAILURE_MESSAGE, outContent.toString());
        assertContainsIgnoreCase(expected, outContent.toString());
    }

    @Test
    public void putsJobFailedWhenArtifactUploadFails() {
        // given
        when(mockBuild.getResult()).thenReturn(Result.SUCCESS);

        when(mockJobData.getOutputArtifacts()).thenReturn(generateOutputArtifactsWithNames(Arrays.asList("artifact_1", "artifact_2")));

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

        assertContainsIgnoreCase(PUBLISHING_ARTIFACTS_MESSAGE, outContent.toString());
        assertContainsIgnoreCase(PUT_JOB_FAILURE_MESSAGE, outContent.toString());
    }

    @Test
    public void putsJobFailedWhenUploadThrowsError() {
        // given
        when(mockBuild.getResult()).thenReturn(Result.SUCCESS);

        when(mockJobData.getOutputArtifacts()).thenReturn(generateOutputArtifactsWithNames(Arrays.asList("artifact_1", "artifact_2")));

        final AWSCodePipelinePublisher uploadFailurePublisher
                = new AWSCodePipelinePublisherMockError(outputLocations, mockFactory);

        // when
        try {
            assertFalse(uploadFailurePublisher.perform(mockBuild, null, null));
            fail("Expected MockError not thrown");
        } catch (final TestError e) {
            // empty
        }

        // then
        final InOrder inOrder = inOrder(mockFactory, mockAWS, mockCodePipelineClient);
        inOrder.verify(mockFactory).getAwsClient(ACCESS_KEY, SECRET_KEY, PROXY_HOST, PROXY_PORT, REGION, PLUGIN_VERSION);
        inOrder.verify(mockAWS).getCodePipelineClient();
        inOrder.verify(mockCodePipelineClient).putJobFailureResult(putJobFailureResultRequest.capture());

        final PutJobFailureResultRequest request = putJobFailureResultRequest.getValue();
        assertEquals(jobId, request.getJobId());
        assertEquals(BUILD_ID, request.getFailureDetails().getExternalExecutionId());
        assertEquals("Failed to upload output artifact(s): Error", request.getFailureDetails().getMessage());
        assertEquals(FailureType.JobFailed.toString(), request.getFailureDetails().getType());

        assertContainsIgnoreCase(PUBLISHING_ARTIFACTS_MESSAGE, outContent.toString());
        assertContainsIgnoreCase(PUT_JOB_FAILURE_MESSAGE, outContent.toString());
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

    private static class AWSCodePipelinePublisherMockError extends AWSCodePipelinePublisher {
        public AWSCodePipelinePublisherMockError(final JSONArray outputLocations, final AWSClientFactory mockFactory) {
            super(outputLocations, mockFactory);
        }

        @Override
        public void callPublish(final AbstractBuild<?,?> action, final CodePipelineStateModel model, final BuildListener listener) {
            throw new TestError();
        }
    }

    private static class TestError extends Error {
        public TestError() {
            super("Error");
        }
    }

    private List<Artifact> generateOutputArtifactsWithNames(List<String> artifactNames) {
        final List<Artifact> outputArtifacts = new ArrayList<>();
        for (String artifactName : artifactNames) {
            Artifact artifact = new Artifact();
            artifact.setName(artifactName);
            outputArtifacts.add(artifact);
        }
        return outputArtifacts;
    }

    private JSONArray generateOutputLocations(List<String> locations, List<String> artifactNames) {
        assertEquals(locations.size(), artifactNames.size());

        JSONArray outputs = new JSONArray();
        for (int i = 0; i < locations.size(); i++) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("location", locations.get(i));
            jsonObject.put("artifactName", artifactNames.get(i));

            outputs.add(jsonObject);
        }

        return outputs;
    }

}
