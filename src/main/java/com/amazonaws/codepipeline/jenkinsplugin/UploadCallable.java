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

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.codepipeline.jenkinsplugin.AWSCodePipelinePublisher.OutputTuple;
import com.amazonaws.services.codepipeline.model.AWSSessionCredentials;
import com.amazonaws.services.codepipeline.model.Artifact;
import com.amazonaws.services.codepipeline.model.GetJobDetailsRequest;
import com.amazonaws.services.codepipeline.model.GetJobDetailsResult;

import hudson.FilePath.FileCallable;
import hudson.model.BuildListener;
import hudson.remoting.VirtualChannel;

public final class UploadCallable implements FileCallable<Void> {
    private static final long serialVersionUID = 1L;

    private final String projectName;
    private final CodePipelineStateModel model;
    private final List<OutputTuple> outputs;
    private final BuildListener listener;

    public UploadCallable(
            final String projectName,
            final CodePipelineStateModel model,
            final List<OutputTuple> outputs,
            final BuildListener listener) {

        this.projectName = Objects.requireNonNull(projectName);
        this.model = Objects.requireNonNull(model);
        this.outputs = Objects.requireNonNull(outputs);
        this.listener = listener;
    }

    @Override
    public Void invoke(final File workspace, final VirtualChannel channel) throws IOException {
        final AWSClients aws = model.getAwsClient();

        final GetJobDetailsRequest request = new GetJobDetailsRequest();
        request.setJobId(model.getJobID());

        final GetJobDetailsResult result = aws.getCodePipelineClient().getJobDetails(request);
        final AWSSessionCredentials sessionCredentials = result.getJobDetails().getData().getArtifactCredentials();
        final BasicSessionCredentials temporaryCredentials = new BasicSessionCredentials(
                sessionCredentials.getAccessKeyId(),
                sessionCredentials.getSecretAccessKey(),
                sessionCredentials.getSessionToken());

        final Iterator<Artifact> artifactIterator = model.getOutputBuildArtifacts().iterator();

        for (final OutputTuple directoryToZip : outputs) {
            final File compressedFile = CompressionTools.compressFile(
                    projectName,
                    workspace,
                    directoryToZip.getOutput(),
                    model.getCompressionType(),
                    listener);

            final Artifact artifact = artifactIterator.next();

            if (compressedFile != null) {
                UploadTools.uploadFile(
                        compressedFile,
                        artifact,
                        model.getCompressionType(),
                        temporaryCredentials,
                        aws,
                        listener);
            } else {
                LoggingHelper.log(listener, "Failed to compress file and upload file");
            }
        }
        return null;
    }

}
