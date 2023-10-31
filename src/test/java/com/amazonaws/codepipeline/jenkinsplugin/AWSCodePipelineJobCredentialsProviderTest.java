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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.joda.time.DateTimeUtils;
import org.joda.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.amazonaws.services.codepipeline.AWSCodePipeline;
import com.amazonaws.services.codepipeline.model.AWSSessionCredentials;
import com.amazonaws.services.codepipeline.model.GetJobDetailsRequest;
import com.amazonaws.services.codepipeline.model.GetJobDetailsResult;
import com.amazonaws.services.codepipeline.model.JobData;
import com.amazonaws.services.codepipeline.model.JobDetails;

public class AWSCodePipelineJobCredentialsProviderTest {

    private static final String JOB_ID = UUID.randomUUID().toString();
    private static final String JOB_ACCESS_KEY = "BPTDIOSFODNN7EXAMPLE";
    private static final String JOB_SECRET_KEY = "xKdpsXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
    private static final String JOB_SESSION_TOKEN = "1231";

    private static AWSSessionCredentials JOB_CREDENTIALS = new AWSSessionCredentials()
            .withAccessKeyId(JOB_ACCESS_KEY)
            .withSecretAccessKey(JOB_SECRET_KEY)
            .withSessionToken(JOB_SESSION_TOKEN);

    @Mock private AWSCodePipeline codePipelineClient;
    @Mock private GetJobDetailsResult getJobDetailsResult;
    @Mock private JobDetails jobDetails;
    @Mock private JobData jobData;

    @Captor private ArgumentCaptor<GetJobDetailsRequest> getJobDetailsRequestCaptor;

    private AWSCodePipelineJobCredentialsProvider credentialsProvider;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        DateTimeUtils.setCurrentMillisFixed(0L);

        when(codePipelineClient.getJobDetails(any(GetJobDetailsRequest.class))).thenReturn(getJobDetailsResult);
        when(getJobDetailsResult.getJobDetails()).thenReturn(jobDetails);
        when(jobDetails.getData()).thenReturn(jobData);
        when(jobData.getArtifactCredentials()).thenReturn(JOB_CREDENTIALS);

        credentialsProvider = new AWSCodePipelineJobCredentialsProvider(JOB_ID, codePipelineClient);
    }

    @After
    public void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test(expected = NullPointerException.class)
    public void throwsOnNullJobId() {
        new AWSCodePipelineJobCredentialsProvider(null, codePipelineClient);
    }

    @Test(expected = NullPointerException.class)
    public void throwsOnNullCodePipelineClient() {
        new AWSCodePipelineJobCredentialsProvider(JOB_ID, null);
    }

    @Test
    public void callsGetJobDetailsToObtainJobCredentials() {
        // when
        final com.amazonaws.auth.AWSSessionCredentials credentials = credentialsProvider.getCredentials();

        // then
        verify(codePipelineClient).getJobDetails(getJobDetailsRequestCaptor.capture());

        assertEquals(JOB_ID, getJobDetailsRequestCaptor.getValue().getJobId());

        assertEquals(JOB_ACCESS_KEY, credentials.getAWSAccessKeyId());
        assertEquals(JOB_SECRET_KEY, credentials.getAWSSecretKey());
        assertEquals(JOB_SESSION_TOKEN, credentials.getSessionToken());
    }

    @Test
    public void returnsCachedCredentialsForFourteenMinutes() {
        // given
        final com.amazonaws.auth.AWSSessionCredentials firstCredentials = credentialsProvider.getCredentials();
        DateTimeUtils.setCurrentMillisFixed(Duration.standardMinutes(14).getMillis());

        // when
        final com.amazonaws.auth.AWSSessionCredentials secondCredentials = credentialsProvider.getCredentials();

        // then
        verify(codePipelineClient, times(1)).getJobDetails(getJobDetailsRequestCaptor.capture());

        assertSame(firstCredentials, secondCredentials);
    }

    @Test
    public void refreshesCredentialsAfterFourteenMinutes() {
        // given
        final com.amazonaws.auth.AWSSessionCredentials firstCredentials = credentialsProvider.getCredentials();
        DateTimeUtils.setCurrentMillisFixed(Duration.standardMinutes(14).getMillis());
        final com.amazonaws.auth.AWSSessionCredentials secondCredentials = credentialsProvider.getCredentials();
        DateTimeUtils.setCurrentMillisFixed(Duration.standardMinutes(14).getMillis() + 1);

        // when
        final com.amazonaws.auth.AWSSessionCredentials thirdCredentials = credentialsProvider.getCredentials();

        // then
        verify(codePipelineClient, times(2)).getJobDetails(getJobDetailsRequestCaptor.capture());

        assertSame(firstCredentials, secondCredentials);
        assertNotSame(firstCredentials, thirdCredentials);
    }

    @Test
    public void refreshesCredentialsWhenRefreshIsCalled() {
        // when
        credentialsProvider.refresh();

        // then
        verify(codePipelineClient).getJobDetails(getJobDetailsRequestCaptor.capture());

        assertEquals(JOB_ID, getJobDetailsRequestCaptor.getValue().getJobId());
    }

}
