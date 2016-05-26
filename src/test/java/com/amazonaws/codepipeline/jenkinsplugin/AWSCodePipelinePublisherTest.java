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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.amazonaws.services.codepipeline.AWSCodePipelineClient;
import com.amazonaws.services.codepipeline.model.ActionContext;
import com.amazonaws.services.codepipeline.model.Artifact;
import com.amazonaws.services.codepipeline.model.Job;
import com.amazonaws.services.codepipeline.model.JobData;
import com.amazonaws.services.codepipeline.model.PipelineContext;
import com.amazonaws.services.codepipeline.model.PutJobFailureResultRequest;
import com.amazonaws.services.codepipeline.model.PutJobSuccessResultRequest;
import com.amazonaws.services.codepipeline.model.StageContext;

public class AWSCodePipelinePublisherTest {

    private AWSCodePipelinePublisherTestExtension publisher;
    private ByteArrayOutputStream outContent;

    @Mock
    private AbstractBuild mockBuild;
    @Mock
    private AWSClients mockAWS;
    @Mock
    private CodePipelineStateModel mockModel;
    @Mock
    private AWSCodePipelineClient mockCodePipelineClient;
    @Mock
    private AbstractProject<?, ?> mockProject;
    @Mock
    private EnvVars vars;
    @Mock
    private Job mockJob;
    @Mock
    private JobData mockJobData;
    @Mock
    private AWSClientFactory mockFactory;

    @Before
    public void setUp() throws Throwable {
        outContent =  TestUtils.setOutputStream();
        MockitoAnnotations.initMocks(this);
        final JSONObject jsonObjectOne = new JSONObject();
        jsonObjectOne.put("location", "output_1");
        final JSONArray jsonArray = new JSONArray();
        jsonArray.add(jsonObjectOne);
        final JSONObject jsonObjectTwo = new JSONObject();
        jsonObjectTwo.put("location", "output_2");
        jsonArray.add(jsonObjectTwo);
        publisher = new AWSCodePipelinePublisherTestExtension(jsonArray, mockFactory);

        CodePipelineStateService.setModel(mockModel);

        when(mockFactory.getAwsClient(
                any(String.class),
                any(String.class),
                any(String.class),
                any(Integer.class),
                any(String.class))).thenReturn(mockAWS);
        when(mockModel.getJob()).thenReturn(mockJob);
        when(mockJob.getId()).thenReturn(UUID.randomUUID().toString());
        when(mockJob.getData()).thenReturn(mockJobData);

        when(mockAWS.getCodePipelineClient()).thenReturn(mockCodePipelineClient);
        when(mockBuild.getResult()).thenReturn(Result.SUCCESS);
        when(mockBuild.getProject()).thenReturn(mockProject);
        when(mockBuild.getEnvironment(any(TaskListener.class))).thenReturn(vars);
        when(vars.get(any(String.class))).thenReturn("Project");
        when(mockProject.getName()).thenReturn("Project");
    }

    @Test
    public void performBuildSucceededSuccess() {
        when(mockBuild.getResult()).thenReturn(Result.SUCCESS);
        final List<Artifact> outputBuildArtifacts = new ArrayList<>();
        outputBuildArtifacts.add(new Artifact());
        outputBuildArtifacts.add(new Artifact());

        when(mockJobData.getOutputArtifacts()).thenReturn(outputBuildArtifacts);

        final boolean result = publisher.perform(
                mockBuild,
                null, // Launcher - unused
                null); // Listener

        verify(mockCodePipelineClient, times(1)).putJobSuccessResult(any(PutJobSuccessResultRequest.class));
        verify(mockModel, never()).getActionTypeCategory();
        assertTrue(result);

        final String expected1 = "[AWS CodePipeline Plugin] Publishing artifacts\n";
        final String expected2 = "[AWS CodePipeline Plugin] Build Succeeded. PutJobSuccessResult\n";
        assertContainsIgnoreCase(expected1, outContent.toString());
        assertContainsIgnoreCase(expected2, outContent.toString());
    }

    @Test
    public void performBuildFailedSuccess() {
        when(mockBuild.getResult()).thenReturn(Result.FAILURE);
        final List<Artifact> outputBuildArtifacts = new ArrayList<>();
        outputBuildArtifacts.add(new Artifact());
        outputBuildArtifacts.add(new Artifact());

        when(mockJobData.getOutputArtifacts()).thenReturn(outputBuildArtifacts);

        final boolean result = publisher.perform(
                mockBuild,
                null, // Launcher - unused
                null); // Listener

        verify(mockCodePipelineClient, times(1)).putJobFailureResult(any(PutJobFailureResultRequest.class));
        verify(mockModel, times(2)).getActionTypeCategory();
        assertFalse(result);

        final String expected1 = "[AWS CodePipeline Plugin] Publishing artifacts\n";
        final String expected2 = "[AWS CodePipeline Plugin] Build Failed. PutJobFailureResult\n";
        assertContainsIgnoreCase(expected1, outContent.toString());
        assertContainsIgnoreCase(expected2, outContent.toString());
    }

    @Ignore
    @Test
    public void performBuildWrongNumberOfBuildArtifactsSpecifiedFailure() {
        when(mockBuild.getResult()).thenReturn(Result.SUCCESS);

        final List<Artifact> outputArtifacts = new ArrayList<>();
        outputArtifacts.add(new Artifact());
        final PipelineContext pipelineContext = new PipelineContext()
                .withPipelineName("JenkinsPipeline")
                .withStage(new StageContext().withName("Build"))
                .withAction(new ActionContext().withName("JenkinsAction"));

        when(mockJobData.getOutputArtifacts()).thenReturn(outputArtifacts);
        when(mockJobData.getPipelineContext()).thenReturn(pipelineContext);

        final boolean result = publisher.perform(
                mockBuild,
                null, // Launcher - unused
                null); // Listener

        verify(mockCodePipelineClient, times(1)).putJobFailureResult(any(PutJobFailureResultRequest.class));
        verify(mockModel, never()).getActionTypeCategory();
        assertFalse(result);

        final String expected1 = "[AWS CodePipeline Plugin] Publishing artifacts\n";
        final String expected2 = "[AWS CodePipeline Plugin] Build Failed. PutJobFailureResult\n";
        final String expected3 = "[AWS CodePipeline Plugin] Error: the number of output artifacts in the Jenkins "
                + "project and in the AWS CodePipeline pipeline action do not match.  Please configure the output "
                + "locations of your Jenkins project to match the AWS CodePipeline pipeline action's output "
                + "artifacts. Number of output locations in Jenkins project: 2, number of output artifacts in AWS "
                + "CodePipeline pipeline action: 1 [Pipeline: JenkinsPipeline, stage: Build, action: JenkinsAction].";

        assertContainsIgnoreCase(expected1, outContent.toString());
        assertContainsIgnoreCase(expected2, outContent.toString());
        assertContainsIgnoreCase(expected3, outContent.toString());
    }

    @Test
    public void cleanUpSuccess() {
        final CodePipelineStateModel tempModel = new CodePipelineStateModel();
        final Job job = new Job();
        job.setId(UUID.randomUUID().toString());
        tempModel.setJob(job);
        tempModel.setCompressionType(CodePipelineStateModel.CompressionType.Zip);
        publisher = new AWSCodePipelinePublisherTestExtension(null, mockFactory);
        publisher.cleanUp(mockModel);
    }

    // -----Setup and Util Methods----- //

    // Test Extension to "Mock" out the callPublish method, since Mockito can't mock out final
    // methods or classes
    public class AWSCodePipelinePublisherTestExtension extends AWSCodePipelinePublisher{
        public AWSCodePipelinePublisherTestExtension(
                final JSONArray outputLocations,
                final AWSClientFactory mockFactory) {
            super(outputLocations, mockFactory);
        }

        @Override
        public void callPublish(
                final AbstractBuild<?,?> action,
                final CodePipelineStateModel model,
                final BuildListener listener) {
            // Do nothing...
        }
    }

}
