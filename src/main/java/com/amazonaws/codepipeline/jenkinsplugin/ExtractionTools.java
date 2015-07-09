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
import hudson.FilePath;
import hudson.model.TaskListener;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import static org.apache.commons.io.FileUtils.deleteDirectory;

public class ExtractionTools {
    private final LoggingHelper logHelper;
    private final CodePipelineStateModel model;

    public ExtractionTools() {
        logHelper = new LoggingHelper();
        this.model = new CodePipelineStateService().getModel();
    }

    private void extractZip(
            final String sourcePath,
            final String destination)
            throws IOException {
        final File sourceFile = new File(sourcePath);
        try (ArchiveInputStream zipArchiveInputStream
                     = new ZipArchiveInputStream(new FileInputStream(sourceFile))) {
            extractArchive(destination, zipArchiveInputStream);
        }
    }

    private void extractTar(
            final String sourcePath,
            final String destination)
            throws IOException {
        final File sourceFile = new File(sourcePath);
        try (ArchiveInputStream tarArchiveInputStream
                     = new TarArchiveInputStream(new FileInputStream(sourceFile))) {
            extractArchive(destination, tarArchiveInputStream);
        }
    }

    private void extractTarGz(
            final String sourcePath,
            final String destination)
            throws IOException {
        final File sourceFile = new File(sourcePath);
        try (ArchiveInputStream tarGzArchiveInputStream
                     = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(sourceFile)))) {
            extractArchive(destination, tarGzArchiveInputStream);
        }
    }

    private void extractArchive(final String destination, final ArchiveInputStream archiveInputStream) throws IOException {
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
                try (OutputStream fileOutputStream = new FileOutputStream(destinationFile)) {
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

    public String getFullCompressedFilePath(final String file, final String directory) throws IOException {
        String fullPath = directory;
        fullPath += File.separator;
        fullPath += file;

        return fullPath;
    }

    public void deleteTemporaryCompressedFile(final String fileToDelete, final TaskListener listener) throws IOException {
        final File toDelete = new File(fileToDelete);

        if (toDelete.isDirectory()) {
            deleteDirectory(toDelete);
        }
        else{
            if (!toDelete.delete()) {
                throw new IOException("Couldn't delete directories");
            }
        }
    }

    public CodePipelineStateModel.CompressionType getCompressionType(final S3Object sessionObject, final TaskListener l) {
        final String   key = sessionObject.getKey();
        CodePipelineStateModel.CompressionType compressionType
                = CodePipelineStateModel.CompressionType.None;

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

        logHelper.log(l, "Detected compression type: " + compressionType.name());
        return compressionType;
    }

    public boolean endsWithLowerCase(final String input, final String postFix) {
        return input.toLowerCase().endsWith(postFix.toLowerCase());
    }

    public void decompressFile(
            final String downloadedFileName,
            final FilePath filePath,
            final TaskListener listener) throws IOException{
        logHelper.log(listener, String.format("Extracting '%s' to '%s'", downloadedFileName, filePath.getRemote()));
        switch (model.getCompressionType()) {
            case None:
                // Attempt to decompress with Zip if it is unknown
            case Zip:
                extractZip(downloadedFileName, filePath.getRemote());
                break;
            case Tar:
                extractTar(downloadedFileName, filePath.getRemote());
                break;
            case TarGz:
                extractTarGz(downloadedFileName, filePath.getRemote());
                break;
        }
    }
}
