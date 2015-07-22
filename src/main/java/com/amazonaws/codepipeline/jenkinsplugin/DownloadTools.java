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

import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import hudson.model.TaskListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class DownloadTools {
    private DownloadTools() { }

    public static void attemptArtifactDownload(
            final S3Object     sessionObject,
            final File         workspace,
            final String       downloadedFileName,
            final TaskListener listener)
            throws Exception {

        streamReadAndDownloadObject(
            workspace,
            sessionObject,
            downloadedFileName);

        LoggingHelper.log(listener, "Successfully downloaded the artifacts from CodePipelines");
    }

    public static void streamReadAndDownloadObject(
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
