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

import java.util.Objects;

import org.joda.time.Duration;
import org.joda.time.Instant;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.codepipeline.AWSCodePipeline;
import com.amazonaws.services.codepipeline.model.GetJobDetailsRequest;
import com.amazonaws.services.codepipeline.model.GetJobDetailsResult;

public final class AWSCodePipelineJobCredentialsProvider implements AWSCredentialsProvider {

    // CodePipeline job credentials are valid for 15 minutes
    private static final Duration CREDENTIALS_DURATION = Duration.standardMinutes(14);

    private final String jobId;
    private final AWSCodePipeline codePipelineClient;

    private volatile AWSSessionCredentials credentials;
    private volatile Instant lastRefreshedInstant;

    public AWSCodePipelineJobCredentialsProvider(final String jobId, final AWSCodePipeline codePipelineClient) {
        this.jobId = Objects.requireNonNull(jobId, "jobId must not be null");
        this.codePipelineClient = Objects.requireNonNull(codePipelineClient, "codePipelineClient must not be null");
    }

    @Override
    public AWSSessionCredentials getCredentials() {
        if (this.credentials == null || this.lastRefreshedInstant.isBefore(Instant.now().minus(CREDENTIALS_DURATION))) {
            refresh();
        }
        return this.credentials;
    }

    @Override
    public synchronized void refresh() {
        final GetJobDetailsRequest getJobDetailsRequest = new GetJobDetailsRequest().withJobId(jobId);
        final GetJobDetailsResult getJobDetailsResult = codePipelineClient.getJobDetails(getJobDetailsRequest);
        final com.amazonaws.services.codepipeline.model.AWSSessionCredentials credentials
            = getJobDetailsResult.getJobDetails().getData().getArtifactCredentials();

        this.lastRefreshedInstant = Instant.now();
        this.credentials = new BasicSessionCredentials(
                credentials.getAccessKeyId(),
                credentials.getSecretAccessKey(),
                credentials.getSessionToken());
    }

}
