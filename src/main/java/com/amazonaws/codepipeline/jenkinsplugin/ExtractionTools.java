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

import static org.apache.commons.io.FileUtils.deleteDirectory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CompressionType;
import com.amazonaws.services.s3.model.S3Object;
import hudson.model.TaskListener;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

public final class ExtractionTools {

    private ExtractionTools() { }

    private static void extractZip(
            final File source,
            final File destination)
            throws IOException {

        try (final ArchiveInputStream zipArchiveInputStream
                     = new ZipArchiveInputStream(new FileInputStream(source))) {
            extractArchive(destination, zipArchiveInputStream);
        }
    }

    private static void extractTar(
            final File source,
            final File destination)
            throws IOException {

        try (final ArchiveInputStream tarArchiveInputStream
                     = new TarArchiveInputStream(new FileInputStream(source))) {
            extractArchive(destination, tarArchiveInputStream);
        }
    }

    private static void extractTarGz(
            final File source,
            final File destination)
            throws IOException {

        try (final ArchiveInputStream tarGzArchiveInputStream
                     = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(source)))) {
            extractArchive(destination, tarGzArchiveInputStream);
        }
    }

    private static void extractArchive(
            final File destination,
            final ArchiveInputStream archiveInputStream)
            throws IOException {
        final int BUFFER_SIZE = 8192;
        ArchiveEntry entry = archiveInputStream.getNextEntry();

        while (entry != null) {
            final File destinationFile = new File(destination, entry.getName());
            final File destinationParent  = destinationFile.getParentFile();
            destinationParent.mkdirs();

            if (entry.isDirectory()) {
                destinationFile.mkdir();
            }
            else {
                try (final OutputStream fileOutputStream = new FileOutputStream(destinationFile)) {
                    final byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;

                    while ((bytesRead = archiveInputStream.read(buffer)) != -1) {
                        fileOutputStream.write(buffer, 0, bytesRead);
                    }
                }
            }

            entry = archiveInputStream.getNextEntry();
        }
    }

    public static void deleteTemporaryCompressedFile(
            final File fileToDelete,
            final TaskListener listener)
            throws IOException {

        if (fileToDelete.isDirectory()) {
            deleteDirectory(fileToDelete);
        }
        else {
            if (!fileToDelete.delete()) {
                throw new IOException("Couldn't delete directories");
            }
        }
    }

    public static CodePipelineStateModel.CompressionType getCompressionType(final S3Object sessionObject, final TaskListener l) {
        final String   key = sessionObject.getKey();
        CodePipelineStateModel.CompressionType compressionType = CodePipelineStateModel.CompressionType.None;

        if (endsWithLowerCase(key, ".zip")) {
            compressionType = CodePipelineStateModel.CompressionType.Zip;
        }
        else if (endsWithLowerCase(key, ".tar.gz")) {
            compressionType = CodePipelineStateModel.CompressionType.TarGz;
        }
        else if (endsWithLowerCase(key, ".tar")) {
            compressionType = CodePipelineStateModel.CompressionType.Tar;
        }

        if (compressionType == CodePipelineStateModel.CompressionType.None) {
            final String contentType = sessionObject.getObjectMetadata().getContentType();

            if ("application/zip".equalsIgnoreCase(contentType)) {
                compressionType = CodePipelineStateModel.CompressionType.Zip;
            }
            else if ("application/gzip".equalsIgnoreCase(contentType)
                    || "application/x-gzip".equalsIgnoreCase(contentType)) {
                compressionType = CodePipelineStateModel.CompressionType.TarGz;
            }
            else if ("application/tar".equalsIgnoreCase(contentType)
                    || "application/x-tar".equalsIgnoreCase(contentType)) {
                compressionType = CodePipelineStateModel.CompressionType.Tar;
            }
        }

        LoggingHelper.log(l, "Detected compression type: %s", compressionType.name());
        return compressionType;
    }

    public static boolean endsWithLowerCase(final String input, final String postFix) {
        return input.toLowerCase().endsWith(postFix.toLowerCase());
    }

    public static void decompressFile(
            final File downloadedFile,
            final File workspace,
            final CompressionType compressionType,
            final TaskListener listener) throws IOException {

        LoggingHelper.log(listener, "Extracting '%s' to '%s'",
                downloadedFile.getAbsolutePath(), workspace.getAbsolutePath());

        switch (compressionType) {
            case None:
                // Attempt to decompress with Zip if it is unknown
            case Zip:
                extractZip(downloadedFile, workspace);
                break;
            case Tar:
                extractTar(downloadedFile, workspace);
                break;
            case TarGz:
                extractTarGz(downloadedFile, workspace);
                break;
        }
    }
}
