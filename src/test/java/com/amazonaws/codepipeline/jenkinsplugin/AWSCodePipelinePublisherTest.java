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
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.amazonaws.services.codepipeline.AWSCodePipelineClient;
import com.amazonaws.services.codepipeline.model.Artifact;
import com.amazonaws.services.codepipeline.model.Job;
import com.amazonaws.services.codepipeline.model.JobData;
import com.amazonaws.services.codepipeline.model.PutJobFailureResultRequest;
import com.amazonaws.services.codepipeline.model.PutJobSuccessResultRequest;

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
        jsonObjectOne.put("output", "output_1");
        final JSONArray jsonArray = new JSONArray();
        jsonArray.add(jsonObjectOne);
        final JSONObject jsonObjectTwo = new JSONObject();
        jsonObjectTwo.put("output", "output_2");
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
                null, //Launcher - unused
                null); //Listener

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
        //when(mockModel.getOutputBuildArtifacts()).thenReturn(outputBuildArtifacts);

        final boolean result = publisher.perform(
                mockBuild,
                null, //Launcher - unused
                null); //Listener

        verify(mockCodePipelineClient, times(1)).putJobFailureResult(any(PutJobFailureResultRequest.class));
        verify(mockModel, times(2)).getActionTypeCategory();
        //verify(mockModel, times(1)).getOutputBuildArtifacts();
        assertFalse(result);

        final String expected1 = "[AWS CodePipeline Plugin] Publishing artifacts\n";
        final String expected2 = "[AWS CodePipeline Plugin] Build Failed. PutJobFailureResult\n";
        assertContainsIgnoreCase(expected1, outContent.toString());
        assertContainsIgnoreCase(expected2, outContent.toString());
    }

    @Test
    public void performBuildWrongNumberOfBuildArtifactsSpecifiedFailure() {
        when(mockBuild.getResult()).thenReturn(Result.SUCCESS);
        final List<Artifact> outputBuildArtifacts = new ArrayList<>();
        outputBuildArtifacts.add(new Artifact());

        //when(mockModel.getOutputBuildArtifacts()).thenReturn(outputBuildArtifacts);
        when(mockJobData.getOutputArtifacts()).thenReturn(outputBuildArtifacts);

        final boolean result = publisher.perform(
                mockBuild,
                null, //Launcher - unused
                null); //Listener

        verify(mockCodePipelineClient, times(1)).putJobFailureResult(any(PutJobFailureResultRequest.class));
        verify(mockModel, never()).getActionTypeCategory();
        //verify(mockModel, times(2)).getOutputBuildArtifacts();
        assertFalse(result);

        final String expected1 = "[AWS CodePipeline Plugin] Publishing artifacts\n";
        final String expected2 = "[AWS CodePipeline Plugin] Build Failed. PutJobFailureResult\n";
        final String expected3 = "[AWS CodePipeline Plugin] Error: number of output locations and number of " +
                "CodePipeline outputs are different. Number of outputs: 2, Number of pipeline artifacts: 1. " +
                "The number of build artifacts should match the number of output artifacts specified";
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
