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

import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CompressionType;
import com.amazonaws.services.codepipeline.model.Artifact;
import com.amazonaws.services.s3.AmazonS3;

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

        this.projectName = Objects.requireNonNull(projectName, "projectName must not be null");
        this.model = Objects.requireNonNull(model, "model must not be null");
        this.outputs = Objects.requireNonNull(outputs, "outputs must not be null");
        this.awsClientFactory = Objects.requireNonNull(awsClientFactory, "awsClientFactory must not be null");
        this.pluginVersion = Objects.requireNonNull(pluginVersion, "pluginVersion must not be null");
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

        final AWSCodePipelineJobCredentialsProvider credentialsProvider = new AWSCodePipelineJobCredentialsProvider(
                model.getJob().getId(), awsClients.getCodePipelineClient());
        final AmazonS3 amazonS3 = awsClients.getS3Client(credentialsProvider);

        final Iterator<Artifact> artifactIterator = model.getJob().getData().getOutputArtifacts().iterator();

        for (final OutputArtifact output : outputs) {
            final Artifact artifact = artifactIterator.next();
            final Path pathToUpload = CompressionTools.resolveWorkspacePath(workspace, output.getLocation());

            if (Files.isDirectory(pathToUpload.toRealPath())) {
                uploadDirectory(pathToUpload, artifact, amazonS3);
            } else {
                uploadFile(pathToUpload.toFile(), artifact, CompressionType.None, amazonS3);
            }
        }

        return null;
    }

    private void uploadDirectory(
            final Path path,
            final Artifact artifact,
            final AmazonS3 amazonS3) throws IOException {

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
            uploadFile(fileToUpload, artifact, compressionType, amazonS3);
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
            final AmazonS3 amazonS3) throws IOException {

        PublisherTools.uploadFile(file, artifact, compressionType, model.getEncryptionKey(), amazonS3, listener);
    }

}
