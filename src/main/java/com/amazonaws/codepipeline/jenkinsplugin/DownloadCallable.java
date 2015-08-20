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

import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CompressionType;
import com.amazonaws.services.codepipeline.model.AWSSessionCredentials;
import com.amazonaws.services.codepipeline.model.Artifact;
import com.amazonaws.services.codepipeline.model.Job;
import com.amazonaws.services.codepipeline.model.S3ArtifactLocation;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import hudson.FilePath.FileCallable;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;

public final class DownloadCallable implements FileCallable<Void> {
    private final boolean clearWorkspace;
    private final TaskListener listener;
    private final Job job;
    private final CodePipelineStateModel model;
    private final AWSClientFactory awsClientFactory;

    public DownloadCallable(
            final boolean clearWorkspace,
            final Job job,
            final CodePipelineStateModel model,
            final AWSClientFactory awsClientFactory,
            final TaskListener listener) {
        this.clearWorkspace = clearWorkspace;
        this.listener = listener;
        this.job = job;
        this.model = model;
        this.awsClientFactory = awsClientFactory;
    }

    @Override
    public Void invoke(final File workspace, final VirtualChannel channel) throws InterruptedException {
        clearWorkspaceIfSelected(workspace, listener);

        for (final Artifact artifact : job.getData().getInputArtifacts()) {
            final S3Object sessionObject = getS3Object(artifact, awsClientFactory);

            final CompressionType compressionType =
                    ExtractionTools.getCompressionType(sessionObject, listener);
            model.setCompressionType(compressionType);

            final String downloadedFileName = Paths.get(sessionObject.getKey()).getFileName().toString();

            try {
                downloadAndExtract(sessionObject, workspace, downloadedFileName, listener);
            }
            catch (final Exception ex) {
                final String error = "Failed to acquire artifacts: " + ex.getMessage();
                LoggingHelper.log(listener, error);
                LoggingHelper.log(listener, ex);

                throw new InterruptedException(error);
            }
        }

        return null;
    }

    public void clearWorkspaceIfSelected(final File workspace, final TaskListener listener) {
        if (clearWorkspace) {
            try {
                LoggingHelper.log(listener, "Clearing Workspace '%s' before download", workspace.getAbsolutePath());
                FileUtils.cleanDirectory(workspace);
            } catch (final IOException ex) {
                LoggingHelper.log(listener, "Unable to clear workspace: %s", ex.getMessage());
            }
        }
    }

    public S3Object getS3Object(final Artifact artifact, final AWSClientFactory awsClientFactory) {
        final AWSClients aws = awsClientFactory.getAwsClient(
                model.getAwsAccessKey(),
                model.getAwsSecretKey(),
                model.getProxyHost(),
                model.getProxyPort(),
                model.getRegion());
        final S3ArtifactLocation artifactLocation = artifact.getLocation().getS3Location();
        final AWSSessionCredentials awsSessionCredentials = job.getData().getArtifactCredentials();

        final BasicSessionCredentials basicCredentials = new BasicSessionCredentials(
                awsSessionCredentials.getAccessKeyId(),
                awsSessionCredentials.getSecretAccessKey(),
                awsSessionCredentials.getSessionToken());

        final AmazonS3 client = aws.getS3Client(basicCredentials);
        final String bucketName = artifactLocation.getBucketName();

        return client.getObject(bucketName, artifactLocation.getObjectKey());
    }

    public void downloadAndExtract(
            final S3Object sessionObject,
            final File workspace,
            final String downloadedFileName,
            final TaskListener listener)
            throws Exception {
        downloadArtifacts(
                sessionObject,
                workspace,
                downloadedFileName,
                listener);

        final File fullFilePath = new File(workspace, downloadedFileName);

        try {
            LoggingHelper.log(listener, "File downloaded successfully");
            ExtractionTools.decompressFile(fullFilePath, workspace, model.getCompressionType(), listener);
            LoggingHelper.log(listener, "File uncompressed successfully");
        }
        finally {
            if (fullFilePath != null) {
                try {
                    ExtractionTools.deleteTemporaryCompressedFile(fullFilePath);
                }
                catch (final IOException ex) {
                    LoggingHelper.log(listener, "Could not delete temporary file: %s", ex.getMessage());
                    LoggingHelper.log(listener, ex);
                }
            }
        }
    }

    private static void downloadArtifacts(
            final S3Object sessionObject,
            final File workspace,
            final String downloadedFileName,
            final TaskListener listener)
            throws IOException {
        streamReadAndDownloadObject(
                workspace,
                sessionObject,
                downloadedFileName);

        LoggingHelper.log(listener, "Successfully downloaded the artifacts from CodePipelines");
    }

    private static void streamReadAndDownloadObject(
            final File         workspace,
            final S3Object     sessionObject,
            final String       downloadedFileName)
            throws IOException {
        final File outputFile = new File(workspace, downloadedFileName);

        try (final S3ObjectInputStream objectContents = sessionObject.getObjectContent();
             final OutputStream outputStream = new FileOutputStream(outputFile)) {
            final int BUFFER_SIZE = 8192;
            final byte[] buffer = new byte[BUFFER_SIZE];

            int i;
            while ((i = objectContents.read(buffer)) != -1) {
                outputStream.write(buffer, 0, i);
            }
        }
    }
}
