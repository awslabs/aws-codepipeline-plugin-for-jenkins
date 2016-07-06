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
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
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
import java.util.Collections;
import java.util.UUID;

import org.apache.commons.lang.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
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

@RunWith(AWSCodePipelineSCMTest.class)
@Suite.SuiteClasses({
        AWSCodePipelineSCMTest.PollForJobsTests.class,
        AWSCodePipelineSCMTest.SCMDescriptorTests.class
})
public class AWSCodePipelineSCMTest extends Suite {

    public AWSCodePipelineSCMTest(final Class<?> klass, final RunnerBuilder builder) throws InitializationError {
        super(klass, builder);
    }

    private static class TestBase {
        protected static final String PROJECT_NAME = "Project";
        protected static final boolean CLEAR_WORKSPACE = false;
        protected static final String REGION = "us-east-1";
        protected static final String ACCESS_KEY = "1234";
        protected static final String SECRET_KEY = "4321";
        protected static final String PROXY_HOST = "";
        protected static final int PROXY_PORT = 0;
        protected static final String PLUGIN_VERSION = "aws-codepipeline:unknown";

        protected String jobId;
        protected String jobNonce;

        public void setUp() throws IOException, InterruptedException {
            MockitoAnnotations.initMocks(this);
            jobId = UUID.randomUUID().toString();
            jobNonce = UUID.randomUUID().toString();
        }
    }

    public static class PollForJobsTests extends TestBase {
        private static final ActionTypeId ACTION_TYPE = new ActionTypeId()
                .withCategory("Build")
                .withOwner(ActionOwner.Custom)
                .withProvider("Jenkins-Build")
                .withVersion("1");

        @Mock private AWSClientFactory mockFactory;
        @Mock private AWSClients mockAWSClients;
        @Mock private AWSCodePipelineClient codePipelineClient;
        @Mock private PollForJobsResult pollForJobsResult;
        @Mock private AcknowledgeJobResult acknowledgeJobResult;

        @Captor private ArgumentCaptor<PollForJobsRequest> pollForJobsRequest;
        @Captor private ArgumentCaptor<AcknowledgeJobRequest> acknowledgeJobRequest;

        private AWSCodePipelineSCMTestExtension scm;
        private Job job;
        private ByteArrayOutputStream outContent;
        private java.io.PrintStream originalOut;

        @Before
        public void setUp() throws IOException, InterruptedException {
            super.setUp();

            when(mockFactory.getAwsClient(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString()))
                    .thenReturn(mockAWSClients);

            when(mockAWSClients.getCodePipelineClient()).thenReturn(codePipelineClient);

            job = new Job()
                    .withId(jobId)
                    .withData(new JobData().withOutputArtifacts(new ArrayList<Artifact>()))
                    .withNonce(jobNonce);

            scm = new AWSCodePipelineSCMTestExtension(
                    PROJECT_NAME,
                    CLEAR_WORKSPACE,
                    REGION,
                    ACCESS_KEY,
                    SECRET_KEY,
                    PROXY_HOST,
                    String.valueOf(PROXY_PORT),
                    ACTION_TYPE.getCategory(),
                    ACTION_TYPE.getProvider(),
                    ACTION_TYPE.getVersion());

            originalOut = System.out;
            outContent = TestUtils.setOutputStream();

            when(codePipelineClient.pollForJobs(any(PollForJobsRequest.class))).thenReturn(pollForJobsResult);
            when(codePipelineClient.acknowledgeJob(any(AcknowledgeJobRequest.class))).thenReturn(acknowledgeJobResult);
            when(pollForJobsResult.getJobs()).thenReturn(Collections.singletonList(job));
            when(acknowledgeJobResult.getStatus()).thenReturn("InProgress");
        }

        @Test
        public void returnsBuildNowWhenThereIsAJob() throws InterruptedException {
            assertEquals(PollingResult.BUILD_NOW, scm.pollForJobs(ACTION_TYPE, null));

            final String expectedMessage = String.format(
                    "[AWS CodePipeline Plugin] Received job with ID: %s\n"
                    + "[AWS CodePipeline Plugin] Acknowledged job with ID: %s",
                    jobId, jobId);
            assertContainsIgnoreCase(expectedMessage, outContent.toString());

            final InOrder inOrder = inOrder(mockFactory, mockAWSClients, codePipelineClient);
            inOrder.verify(mockFactory).getAwsClient(ACCESS_KEY, SECRET_KEY, PROXY_HOST, PROXY_PORT, REGION, PLUGIN_VERSION);
            inOrder.verify(mockAWSClients).getCodePipelineClient();
            inOrder.verify(codePipelineClient).pollForJobs(pollForJobsRequest.capture());
            inOrder.verify(codePipelineClient).acknowledgeJob(acknowledgeJobRequest.capture());

            final PollForJobsRequest pollRequest = pollForJobsRequest.getValue();
            assertEquals(ACTION_TYPE, pollRequest.getActionTypeId());
            assertEquals(1, pollRequest.getMaxBatchSize().intValue());
            assertEquals(1, pollRequest.getQueryParam().size());
            assertEquals(PROJECT_NAME, pollRequest.getQueryParam().get("ProjectName"));

            assertEquals(jobId, acknowledgeJobRequest.getValue().getJobId());
            assertEquals(jobNonce, acknowledgeJobRequest.getValue().getNonce());
        }

        @Test
        public void returnsNoChangesWhenThereAreNoJobs() throws InterruptedException {
            when(pollForJobsResult.getJobs()).thenReturn(new ArrayList<Job>());

            assertEquals(PollingResult.NO_CHANGES, scm.pollForJobs(ACTION_TYPE, null));

            final String expectedMessage = "[AWS CodePipeline Plugin] No jobs found.\n";
            originalOut.println(expectedMessage);
            originalOut.println(outContent.toString());
            assertEqualsIgnoreCase(expectedMessage, outContent.toString());

            verify(codePipelineClient).pollForJobs(any(PollForJobsRequest.class));
            verify(codePipelineClient, never()).acknowledgeJob(any(AcknowledgeJobRequest.class));
        }

        @Test
        public void returnsNoChangesWhenAcknowledgeJobDoesNotReturnAnInProgressJobStatus() throws InterruptedException {
            when(acknowledgeJobResult.getStatus()).thenReturn("Created");

            assertEquals(PollingResult.NO_CHANGES, scm.pollForJobs(ACTION_TYPE, null));

            final String expectedMessage = String.format(
                    "[AWS CodePipeline Plugin] Received job with ID: %s\n"
                    + "[AWS CodePipeline Plugin] Failed to acknowledge job with ID: %s\n",
                    jobId, jobId);
            originalOut.println(expectedMessage);
            originalOut.println(outContent.toString());
            assertEqualsIgnoreCase(expectedMessage, outContent.toString());

            verify(codePipelineClient).pollForJobs(any(PollForJobsRequest.class));
            verify(codePipelineClient).acknowledgeJob(any(AcknowledgeJobRequest.class));
        }

        // Test Extension to "Mock" out the callPublish method, since Mockito can't mock out final methods or classes
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
                super(projectName, clear, region, awsAccessKey, awsSecretKey, proxyHost, proxyPort,
                        category, provider, version, mockFactory);
            }

            @Override
            public void initializeModel() {
            }
        }
    }

    public static class SCMDescriptorTests extends TestBase {
        @Mock
        private AbstractBuild<?, ?> mockBuild;
        @Mock
        private EnvVars envVars;
        @Mock
        private CodePipelineStateModel model;
        @Mock
        private Job mockJob;

        private AWSCodePipelineSCM.DescriptorImpl descriptor;

        @Before
        public void setUp() throws IOException, InterruptedException {
            super.setUp();

            descriptor = new AWSCodePipelineSCM.DescriptorImpl(false);

            when(mockBuild.getEnvironment(any(TaskListener.class))).thenReturn(envVars);
            when(envVars.get(any(String.class))).thenReturn("Project");
            when(model.getJob()).thenReturn(mockJob);
            when(mockJob.getId()).thenReturn(jobId);

            CodePipelineStateService.setModel(model);
        }

        @After
        public void tearDown() {
            CodePipelineStateService.removeModel();
        }

        @Test
        public void doCheckCategorySucceedsWithBuildCategory() {
            assertEquals(FormValidation.ok(), descriptor.doCheckCategory(CategoryType.Build.name()));
        }

        @Test
        public void doCheckCategorySucceedsWithTestCategory() {
            assertEquals(FormValidation.ok(), descriptor.doCheckCategory(CategoryType.Test.name()));
        }

        @Test
        public void doCheckCategoryFailsIfNoCategoryIsChosen() {
            final CategoryType category = CategoryType.PleaseChooseACategory;
            assertContainsIgnoreCase(
                    "Please select a Category Type",
                    descriptor.doCheckCategory(category.name()).toString());
            assertValidationMessage(FormValidation.error(""), descriptor.doCheckCategory(category.name()));
        }

        @Test
        public void versionCheckShouldSucceedSimpleVersionNumber() {
            final String version = RandomStringUtils.randomNumeric(1);
            assertEquals(FormValidation.ok(), descriptor.doCheckVersion(version));
        }

        @Test
        public void versionCheckShouldSucceedWithMaxNumber() {
            final String version = RandomStringUtils.randomNumeric(Validation.MAX_VERSION_LENGTH);
            assertEquals(FormValidation.ok(), descriptor.doCheckVersion(version));
        }

        @Test
        public void versionCheckShouldFailWithEmpty() {
            final String version = "";
            assertContainsIgnoreCase(
                    "Please enter a Version",
                    descriptor.doCheckVersion(version).toString());
            assertValidationMessage(FormValidation.error(""), descriptor.doCheckVersion(version));
        }

        @Test
        public void versionCheckShouldFailWithNegativeNumber() {
            final String version = "-1";
            assertContainsIgnoreCase(
                    "Version must be greater than or equal to 0",
                    descriptor.doCheckVersion(version).toString());
            assertValidationMessage(FormValidation.error(""), descriptor.doCheckVersion(version));
        }

        @Test
        public void versionCheckShouldFailWithLetters() {
            final String version = RandomStringUtils.randomAlphabetic(5);
            assertContainsIgnoreCase(
                    "Version must be a number",
                    descriptor.doCheckVersion(version).toString());
            assertValidationMessage(FormValidation.error(""), descriptor.doCheckVersion(version));
        }

        @Test
        public void versionCheckShouldFailWithTooLongValue() {
            final String version = RandomStringUtils.randomNumeric(Validation.MAX_VERSION_LENGTH + 1);

            assertContainsIgnoreCase(
                    "Version can only be " + Validation.MAX_VERSION_LENGTH +
                            " characters in length, you entered " +
                            version.length(),
                    descriptor.doCheckVersion(version).toString());
            assertValidationMessage(FormValidation.error(""), descriptor.doCheckVersion(version));
        }

        @Test
        public void providerCheckSucceeds() {
            final String provider = "Jenkins";
            assertEquals(FormValidation.ok(), descriptor.doCheckProvider(provider));
        }

        @Test
        public void providerCheckFailsEmpty() {
            final String provider = "";
            assertContainsIgnoreCase(
                    "Please enter a Provider, typically &quot;Jenkins&quot; or your Project Name",
                    descriptor.doCheckProvider(provider).toString());

            assertValidationMessage(FormValidation.error(""), descriptor.doCheckProvider(provider));
        }

        @Test
        public void providerCheckFailsTooLong() {
            String provider = "Jenkins-Build";

            while (provider.length() < Validation.MAX_PROVIDER_LENGTH) {
                provider = provider + provider;
            }

            assertContainsIgnoreCase(
                    "The Provider name is too long, the name should be ",
                    descriptor.doCheckProvider(provider).toString());
            assertValidationMessage(FormValidation.error(""), descriptor.doCheckProvider(provider));
        }

        @Test
        public void proxyPortIsNumberSuccess() {
            final String proxyPort = "0";
            assertEquals(FormValidation.ok(), descriptor.doCheckProxyPort(proxyPort));
        }

        @Test
        public void proxyPortIsLessThanZeroFailure() {
            final String proxyPort = "-1";
            assertContainsIgnoreCase(
                    "Proxy Port must be between 0 and 65535",
                    descriptor.doCheckProxyPort(proxyPort).toString());
            assertValidationMessage(FormValidation.error(""), descriptor.doCheckProxyPort(proxyPort));
        }

        @Test
        public void proxyPortIsGreaterThan65535Failure() {
            final String proxyPort = "65536";
            assertContainsIgnoreCase(
                    "Proxy Port must be between 0 and 65535",
                    descriptor.doCheckProxyPort(proxyPort).toString());
            assertValidationMessage(FormValidation.error(""), descriptor.doCheckProxyPort(proxyPort));
        }

        @Test
        public void proxyPortIsNotANumberFailure() {
            final String proxyPort = RandomStringUtils.randomAlphabetic(4);
            assertContainsIgnoreCase(
                    "Proxy Port must be a number",
                    descriptor.doCheckProxyPort(proxyPort).toString());
            assertValidationMessage(FormValidation.error(""), descriptor.doCheckProxyPort(proxyPort));
        }

        private void assertValidationMessage(final FormValidation expected, final FormValidation actualMessage) {
            // Since FormValidations have "%NUM" syntax for each error, we just take that off to compare the
            // two values, otherwise they would never be equal.
            assertEquals(expected.toString().substring(0, expected.toString().indexOf('$')),
                    actualMessage.toString().substring(0, actualMessage.toString().indexOf('$')));
        }
    }

}
