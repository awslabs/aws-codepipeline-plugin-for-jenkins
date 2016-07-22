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

import hudson.FilePath.FileCallable;
import hudson.model.BuildListener;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CompressionType;
import com.amazonaws.services.codepipeline.model.Artifact;
import com.amazonaws.services.codepipeline.model.GetJobDetailsRequest;
import com.amazonaws.services.codepipeline.model.GetJobDetailsResult;

public final class PublisherCallable implements FileCallable<Void> {

    private static final long serialVersionUID = 1L;

    private final String projectName;
    private final String pluginVersion;
    private final CodePipelineStateModel model;
    private final AWSClientFactory awsClientFactory;
    private final List<OutputArtifact> outputs;
    private final BuildListener listener;

    public PublisherCallable(
            final String projectName,
            final CodePipelineStateModel model,
            final List<OutputArtifact> outputs,
            final AWSClientFactory awsClientFactory,
            final String pluginVersion,
            final BuildListener listener) {

        this.projectName = Objects.requireNonNull(projectName);
        this.model = Objects.requireNonNull(model);
        this.outputs = Objects.requireNonNull(outputs);
        this.awsClientFactory = Objects.requireNonNull(awsClientFactory);
        this.pluginVersion = Objects.requireNonNull(pluginVersion);
        this.listener = listener;
    }

    @Override
    public Void invoke(final File workspace, final VirtualChannel channel) throws IOException {
        final AWSClients awsClients = awsClientFactory.getAwsClient(
                model.getAwsAccessKey(),
                model.getAwsSecretKey(),
                model.getProxyHost(),
                model.getProxyPort(),
                model.getRegion(),
                pluginVersion);

        final AWSSessionCredentials credentials = getJobCredentials(awsClients);

        final Iterator<Artifact> artifactIterator = model.getJob().getData().getOutputArtifacts().iterator();

        for (final OutputArtifact output : outputs) {
            final Artifact artifact = artifactIterator.next();
            final Path pathToUpload = CompressionTools.resolveWorkspacePath(workspace, output.getLocation());

            if (Files.isDirectory(pathToUpload.toRealPath())) {
                uploadDirectory(pathToUpload, artifact, credentials, awsClients);
            } else {
                uploadFile(pathToUpload.toFile(), artifact, CompressionType.None, credentials, awsClients);
            }
        }

        return null;
    }

    private AWSSessionCredentials getJobCredentials(final AWSClients awsClients) {
        final GetJobDetailsRequest getJobDetailsRequest
            = new GetJobDetailsRequest().withJobId(model.getJob().getId());
        final GetJobDetailsResult getJobDetailsResult
            = awsClients.getCodePipelineClient().getJobDetails(getJobDetailsRequest);
        final com.amazonaws.services.codepipeline.model.AWSSessionCredentials credentials
            = getJobDetailsResult.getJobDetails().getData().getArtifactCredentials();

        return new BasicSessionCredentials(
                credentials.getAccessKeyId(),
                credentials.getSecretAccessKey(),
                credentials.getSessionToken());
    }

    private void uploadDirectory(
            final Path path,
            final Artifact artifact,
            final AWSSessionCredentials credentials,
            final AWSClients awsClients) throws IOException {

        // Default to ZIP compression if we could not detect the compression type
        final CompressionType compressionType = model.getCompressionType() == CompressionType.None
                ? CompressionType.Zip
                : model.getCompressionType();

        final File fileToUpload = CompressionTools.compressFile(
                projectName,
                path,
                compressionType,
                listener);

        try {
            uploadFile(fileToUpload, artifact, compressionType, credentials, awsClients);
        } finally {
            if (!fileToUpload.delete()) {
                fileToUpload.deleteOnExit();
            }
        }
    }

    private void uploadFile(
            final File file,
            final Artifact artifact,
            final CompressionType compressionType,
            final AWSSessionCredentials credentials,
            final AWSClients awsClients) throws IOException {

        PublisherTools.uploadFile(
                file,
                artifact,
                compressionType,
                model.getEncryptionKey(),
                credentials,
                awsClients,
                listener);
    }

}
