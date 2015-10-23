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
import static com.amazonaws.codepipeline.jenkinsplugin.TestUtils.assertEqualsIgnoreCase;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.scm.PollingResult;
import hudson.util.FormValidation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CategoryType;
import com.amazonaws.services.codepipeline.AWSCodePipelineClient;
import com.amazonaws.services.codepipeline.model.AcknowledgeJobRequest;
import com.amazonaws.services.codepipeline.model.AcknowledgeJobResult;
import com.amazonaws.services.codepipeline.model.ActionOwner;
import com.amazonaws.services.codepipeline.model.ActionTypeId;
import com.amazonaws.services.codepipeline.model.Artifact;
import com.amazonaws.services.codepipeline.model.Job;
import com.amazonaws.services.codepipeline.model.JobData;
import com.amazonaws.services.codepipeline.model.PollForJobsRequest;
import com.amazonaws.services.codepipeline.model.PollForJobsResult;

public class AWSCodePipelineSCMTest {

    @Mock
    private CodePipelineStateModel model;
    @Mock
    private AWSClients mockAWSClients;
    @Mock
    private AWSCodePipelineClient codePipelineClient;
    @Mock
    private AbstractBuild<?, ?> mockBuild;
    @Mock
    private EnvVars vars;
    @Mock
    private PollForJobsResult result;
    @Mock
    private AcknowledgeJobResult jobResult;
    @Mock
    private AWSCodePipelineSCM mockSCM;
    @Mock
    private Job mockJob;
    @Mock
    private AWSClientFactory mockFactory;

    private AWSCodePipelineSCMTestExtension scm;
    private AWSCodePipelineSCM.DescriptorImpl impl;
    private ActionTypeId actionTypeId;
    private ByteArrayOutputStream outContent;
    private final String uniqueJobIDRequest = UUID.randomUUID().toString();
    private Job job;
    private List<Job> jobList;

    @Before
    public void setUp() throws IOException, InterruptedException {
        impl = new AWSCodePipelineSCM.DescriptorImpl(false);
        outContent = TestUtils.setOutputStream();

        MockitoAnnotations.initMocks(this);
        when(mockFactory.getAwsClient(
                any(String.class),
                any(String.class),
                any(String.class),
                any(Integer.class),
                any(String.class))).thenReturn(mockAWSClients);
        when(mockAWSClients.getCodePipelineClient()).thenReturn(codePipelineClient);
        when(codePipelineClient.pollForJobs(any(PollForJobsRequest.class))).thenReturn(result);
        when(mockBuild.getEnvironment(any(TaskListener.class))).thenReturn(vars);
        when(vars.get(any(String.class))).thenReturn("Project");
        when(model.getJob()).thenReturn(mockJob);
        when(mockJob.getId()).thenReturn(uniqueJobIDRequest);

        CodePipelineStateService.setModel(model);

        scm = new AWSCodePipelineSCMTestExtension(
                "Project",
                false,
                "us-east-1",
                "", // Access Key
                "", // Secret Key
                "", // ProxyHost
                "0",
                "Build",
                "Jenkins",
                "1");

        final JobData jobData = new JobData();
        jobData.setOutputArtifacts(new ArrayList<Artifact>());
        job = new Job();
        job.setData(jobData);
        job.setId(uniqueJobIDRequest);
        job.setNonce("0");

        setupPollForJobsData();
        actionTypeId = setupActionType();
    }

    @After
    public void tearDown() {
        CodePipelineStateService.removeModel();
    }

    @Test
    public void shouldSucceedPollingForJobs() {
        jobList.add(job);

        final String correctMessage = "[AWS CodePipeline Plugin] Received Job request with ID: " +
                uniqueJobIDRequest + "\n" +
                "[AWS CodePipeline Plugin] Job Acknowledged with ID:";

        final PollingResult result = scm.pollForJobs(actionTypeId, null);

        assertEquals(PollingResult.BUILD_NOW, result);
        assertContainsIgnoreCase(correctMessage, outContent.toString());
        verify(codePipelineClient, times(1)).pollForJobs(any(PollForJobsRequest.class));
        verify(codePipelineClient, times(1)).acknowledgeJob(any(AcknowledgeJobRequest.class));
    }

    @Test
    public void shouldFailPollingForJobs() {
        jobList.clear();

        final String correctMessage = "[AWS CodePipeline Plugin] No jobs found.\n";
        final PollingResult result = scm.pollForJobs(actionTypeId, null);

        assertEquals(PollingResult.NO_CHANGES, result);
        assertEqualsIgnoreCase(outContent.toString(), correctMessage);
        verify(codePipelineClient, times(1)).pollForJobs(any(PollForJobsRequest.class));
        verify(codePipelineClient, never()).acknowledgeJob(any(AcknowledgeJobRequest.class));
    }

    @Test
    public void categoryCheckShouldSucceedBuild() {
         final CategoryType category = CategoryType.Build;
        assertEquals(FormValidation.ok(), impl.doCheckCategory(category.name()));
    }

    @Test
    public void categoryCheckShouldSucceedTest() {
        final CategoryType category = CategoryType.Test;
        assertEquals(FormValidation.ok(), impl.doCheckCategory(category.name()));
    }

    @Test
    public void categoryCheckShouldFail() {
        final CategoryType category = CategoryType.PleaseChooseACategory;
        assertContainsIgnoreCase(
                "Please select a Category Type",
                impl.doCheckCategory(category.name()).toString());
        assertValidationMessage(FormValidation.error(""), impl.doCheckCategory(category.name()));
    }

    @Test
    public void versionCheckShouldSucceedSimpleVersionNumber() {
        final String version = RandomStringUtils.randomNumeric(1);
        assertEquals(FormValidation.ok(), impl.doCheckVersion(version));
    }

    @Test
    public void versionCheckShouldSucceedWithMaxNumber() {
        final String version = RandomStringUtils.randomNumeric(Validation.MAX_VERSION_LENGTH);
        assertEquals(FormValidation.ok(), impl.doCheckVersion(version));
    }

    @Test
    public void versionCheckShouldFailWithEmpty() {
        final String version = "";
        assertContainsIgnoreCase(
                "Please enter a Version",
                impl.doCheckVersion(version).toString());
        assertValidationMessage(FormValidation.error(""), impl.doCheckVersion(version));
    }

    @Test
    public void versionCheckShouldFailWithNegativeNumber() {
        final String version = "-1";
        assertContainsIgnoreCase(
                "Version must be greater than or equal to 0",
                impl.doCheckVersion(version).toString());
        assertValidationMessage(FormValidation.error(""), impl.doCheckVersion(version));
    }

    @Test
    public void versionCheckShouldFailWithLetters() {
        final String version = RandomStringUtils.randomAlphabetic(5);
        assertContainsIgnoreCase(
                "Version must be a number",
                impl.doCheckVersion(version).toString());
        assertValidationMessage(FormValidation.error(""), impl.doCheckVersion(version));
    }

    @Test
    public void versionCheckShouldFailWithTooLongValue() {
        final String version = RandomStringUtils.randomNumeric(Validation.MAX_VERSION_LENGTH + 1);

        assertContainsIgnoreCase(
                "Version can only be " + Validation.MAX_VERSION_LENGTH +
                        " characters in length, you entered " +
                        version.length(),
                impl.doCheckVersion(version).toString());
        assertValidationMessage(FormValidation.error(""), impl.doCheckVersion(version));
    }

    @Test
    public void providerCheckSucceeds() {
        final String provider = "Jenkins";
        assertEquals(FormValidation.ok(), impl.doCheckProvider(provider));
    }

    @Test
    public void providerCheckFailsEmpty() {
        final String provider = "";
        assertContainsIgnoreCase(
                "Please enter a Provider, typically &quot;Jenkins&quot; or your Project Name",
                impl.doCheckProvider(provider).toString());

        assertValidationMessage(FormValidation.error(""), impl.doCheckProvider(provider));
    }

    @Test
    public void providerCheckFailsTooLong() {
        String provider = "Jenkins-Build";

        while (provider.length() < Validation.MAX_PROVIDER_LENGTH) {
            provider = provider + provider;
        }

        assertContainsIgnoreCase(
                "The Provider name is too long, the name should be ",
                impl.doCheckProvider(provider).toString());
        assertValidationMessage(FormValidation.error(""), impl.doCheckProvider(provider));
    }

    @Test
    public void proxyPortIsNumberSuccess() {
        final String proxyPort = "0";
        assertEquals(FormValidation.ok(), impl.doCheckProxyPort(proxyPort));
    }

    @Test
    public void proxyPortIsLessThanZeroFailure() {
        final String proxyPort = "-1";
        assertContainsIgnoreCase(
                "Proxy Port must be between 0 and 65535",
                impl.doCheckProxyPort(proxyPort).toString());
        assertValidationMessage(FormValidation.error(""), impl.doCheckProxyPort(proxyPort));
    }

    @Test
    public void proxyPortIsGreaterThan65535Failure() {
        final String proxyPort = "65536";
        assertContainsIgnoreCase(
                "Proxy Port must be between 0 and 65535",
                impl.doCheckProxyPort(proxyPort).toString());
        assertValidationMessage(FormValidation.error(""), impl.doCheckProxyPort(proxyPort));
    }

    @Test
    public void proxyPortIsNotANumberFailure() {
        final String proxyPort = RandomStringUtils.randomAlphabetic(4);
        assertContainsIgnoreCase(
                "Proxy Port must be a number",
                impl.doCheckProxyPort(proxyPort).toString());
        assertValidationMessage(FormValidation.error(""), impl.doCheckProxyPort(proxyPort));
    }

    // -----Setup and Util Methods----- //
    private void assertValidationMessage(final FormValidation expected, final FormValidation actualMessage) {
        // Since FormValidations have "%NUM" syntax for each error, we just take that off to compare the
        // two values, otherwise they would never be equal.
        assertEquals(expected.toString()
                        .substring(0, expected.toString().indexOf('$')),
                actualMessage.toString()
                        .substring(0, actualMessage.toString().indexOf('$')));
    }

    public void setupPollForJobsData() {
        jobList = new ArrayList<>();

        when(codePipelineClient.pollForJobs(any(PollForJobsRequest.class))).thenReturn(result);
        when(codePipelineClient.acknowledgeJob(any(AcknowledgeJobRequest.class))).thenReturn(jobResult);
        when(result.getJobs()).thenReturn(jobList);
        when(jobResult.getStatus()).thenReturn("InProgress");
    }

    public ActionTypeId setupActionType() {
        final ActionTypeId actionTypeId;
        actionTypeId = new ActionTypeId();
        actionTypeId.setCategory("Build");
        actionTypeId.setOwner(ActionOwner.Custom);
        actionTypeId.setProvider("Jenkins-Build");
        actionTypeId.setVersion("1");

        return actionTypeId;
    }

    // Test Extension to "Mock" out the callPublish method, since Mockito can't mock out final
    // methods or classes
    public class AWSCodePipelineSCMTestExtension extends AWSCodePipelineSCM {
        public AWSCodePipelineSCMTestExtension(
                final String projectName,
                final boolean clear,
                final String region,
                final String awsAccessKey,
                final String awsSecretKey,
                final String proxyHost,
                final String proxyPort,
                final String category,
                final String provider,
                final String version) {
            super(
                    projectName,
                    clear,
                    region,
                    awsAccessKey,
                    awsSecretKey,
                    proxyHost,
                    proxyPort,
                    category,
                    provider,
                    version,
                    mockFactory);
        }

        @Override
        public void initializeModel() {
        }
    }

}
