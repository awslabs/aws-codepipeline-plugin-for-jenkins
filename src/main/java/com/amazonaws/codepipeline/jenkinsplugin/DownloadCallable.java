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
import java.nio.file.Paths;

import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.codepipeline.model.AWSSessionCredentials;
import com.amazonaws.services.codepipeline.model.Artifact;
import com.amazonaws.services.codepipeline.model.Job;
import com.amazonaws.services.codepipeline.model.S3ArtifactLocation;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;

import hudson.FilePath.FileCallable;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.apache.commons.io.FileUtils;

public final class DownloadCallable implements FileCallable<Void> {
    private final boolean clearWorkspace;
    private final TaskListener listener;
    private Job job;
    private final CodePipelineStateModel model;

    public DownloadCallable(
            final boolean clearWorkspace,
            final Job job,
            final CodePipelineStateModel model,
            final TaskListener listener) {
        this.clearWorkspace = clearWorkspace;
        this.listener = listener;
        this.job = job;
        this.model = model;
    }

    @Override
    public Void invoke(final File workspace, final VirtualChannel channel) throws InterruptedException {
        clearWorkspaceIfSelected(workspace, listener);

        for (final Artifact artifact : job.getData().getInputArtifacts()) {
            final S3Object sessionObject = getS3Object(artifact);

            final CodePipelineStateModel.CompressionType compressionType =
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

    public S3Object getS3Object(final Artifact artifact) {
        final AWSClients aws = model.getAwsClient();
        final S3ArtifactLocation artifactLocation = artifact.getLocation().getS3Location();
        final AWSSessionCredentials awsSessionCredentials = job.getData().getArtifactCredentials();

        final BasicSessionCredentials basicCredentials = new BasicSessionCredentials(
                awsSessionCredentials.getAccessKeyId(),
                awsSessionCredentials.getSecretAccessKey(),
                awsSessionCredentials.getSessionToken());

        final AmazonS3 client = aws.getS3Client(basicCredentials);
        final String bucketName     = artifactLocation.getBucketName();

        return client.getObject(bucketName, artifactLocation.getObjectKey());
    }

    public void downloadAndExtract(
            final S3Object sessionObject,
            final File workspace,
            final String downloadedFileName,
            final TaskListener listener)
            throws Exception {

        DownloadTools.attemptArtifactDownload(
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
                    ExtractionTools.deleteTemporaryCompressedFile(fullFilePath, listener);
                }
                catch (final IOException ex) {
                    LoggingHelper.log(listener, "Could not delete temporary file: %s", ex.getMessage());
                    LoggingHelper.log(listener, ex);
                }
            }
        }
    }
}
