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

import hudson.model.TaskListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;

import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CompressionType;
import com.amazonaws.services.s3.model.S3Object;

public final class ExtractionTools {

    private ExtractionTools() {}

    private static void extractZip(final File source, final File destination) throws IOException {
        try (final ZipFile zipFile = new ZipFile(source, StandardCharsets.UTF_8.name(), true)) {
            extractZipFile(destination, zipFile);
        }
    }

    private static void extractTar(final File source, final File destination) throws IOException {
        try (final ArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new FileInputStream(source))) {
            extractArchive(destination, tarArchiveInputStream);
        }
    }

    private static void extractTarGz(final File source, final File destination) throws IOException {
        try (final ArchiveInputStream tarGzArchiveInputStream
                = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(source)))) {
            extractArchive(destination, tarGzArchiveInputStream);
        }
    }

    // Use of ZipFile is recommended, ZipArchiveInputStream has many limitations
    // https://commons.apache.org/proper/commons-compress/zip.html
    private static void extractZipFile(final File destination, final ZipFile zipFile) throws IOException {
        final Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();

        while (entries.hasMoreElements()) {
            final ZipArchiveEntry entry = entries.nextElement();
            final File entryDestination = new File(destination, entry.getName());

            if (entry.isDirectory()) {
                entryDestination.mkdirs();
            } else {
                entryDestination.getParentFile().mkdirs();
                final InputStream in = zipFile.getInputStream(entry);
                try (final OutputStream out = new FileOutputStream(entryDestination)) {
                    IOUtils.copy(in, out);
                    IOUtils.closeQuietly(in);
                }
            }
        }
    }

    private static void extractArchive(final File destination, final ArchiveInputStream archiveInputStream)
            throws IOException {
        final int BUFFER_SIZE = 8192;
        ArchiveEntry entry = archiveInputStream.getNextEntry();

        while (entry != null) {
            final File destinationFile = new File(destination, entry.getName());

            if (entry.isDirectory()) {
                destinationFile.mkdir();
            } else {
                destinationFile.getParentFile().mkdirs();
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

    public static void deleteTemporaryCompressedFile(final File fileToDelete) throws IOException {
        if (fileToDelete.isDirectory()) {
            FileUtils.deleteDirectory(fileToDelete);
        } else {
            if (!fileToDelete.delete()) {
                fileToDelete.deleteOnExit();
            }
        }
    }

    public static CompressionType getCompressionType(final S3Object sessionObject, final TaskListener l) {
        final String key = sessionObject.getKey();
        CompressionType compressionType = CompressionType.None;

        if (endsWithLowerCase(key, ".zip")) {
            compressionType = CompressionType.Zip;
        } else if (endsWithLowerCase(key, ".tar.gz")) {
            compressionType = CompressionType.TarGz;
        } else if (endsWithLowerCase(key, ".tar")) {
            compressionType = CompressionType.Tar;
        }

        if (compressionType == CompressionType.None) {
            final String contentType = sessionObject.getObjectMetadata().getContentType();

            if ("application/zip".equalsIgnoreCase(contentType)) {
                compressionType = CompressionType.Zip;
            } else if ("application/gzip".equalsIgnoreCase(contentType)
                    || "application/x-gzip".equalsIgnoreCase(contentType)) {
                compressionType = CompressionType.TarGz;
            } else if ("application/tar".equalsIgnoreCase(contentType)
                    || "application/x-tar".equalsIgnoreCase(contentType)) {
                compressionType = CompressionType.Tar;
            }
        }

        LoggingHelper.log(l, "Detected compression type: %s", compressionType.name());
        return compressionType;
    }

    public static boolean endsWithLowerCase(final String input, final String postFix) {
        return input.toLowerCase().endsWith(postFix.toLowerCase());
    }

    public static void decompressFile(
            final File compressedFile,
            final File destination,
            final CompressionType compressionType,
            final TaskListener listener) throws IOException {

        LoggingHelper.log(listener, "Extracting '%s' to '%s'",
                compressedFile.getAbsolutePath(), destination.getAbsolutePath());

        switch (compressionType) {
            case None:
                // Attempt to decompress with Zip if it is unknown
            case Zip:
                extractZip(compressedFile, destination);
                break;
            case Tar:
                extractTar(compressedFile, destination);
                break;
            case TarGz:
                extractTarGz(compressedFile, destination);
                break;
        }
    }

}
