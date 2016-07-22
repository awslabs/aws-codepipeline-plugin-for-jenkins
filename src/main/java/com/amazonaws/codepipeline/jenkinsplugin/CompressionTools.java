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

import hudson.model.BuildListener;
import hudson.util.IOUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CompressionType;

public final class CompressionTools {

    private CompressionTools() {}

    // Compressing the file to upload to S3 should use the same type of compression as the customer
    // used for the input artifact.
    public static File compressFile(
            final String projectName,
            final Path pathToCompress,
            final CompressionType compressionType,
            final BuildListener listener)
            throws IOException {
        File compressedArtifacts = null;

        try {
            switch (compressionType) {
                case Zip:
                    compressedArtifacts = File.createTempFile(projectName + "-", ".zip");
                    compressZipFile(compressedArtifacts, pathToCompress, listener);
                    break;
                case Tar:
                    compressedArtifacts = File.createTempFile(projectName + "-", ".tar");
                    compressTarFile(compressedArtifacts, pathToCompress, listener);
                    break;
                case TarGz:
                    compressedArtifacts = File.createTempFile(projectName + "-", ".tar.gz");
                    compressTarGzFile(compressedArtifacts, pathToCompress, listener);
                    break;
                case None:
                    throw new IllegalArgumentException("No compression type specified.");
            }
        } catch (final IOException e) {
            if (compressedArtifacts != null) {
                if (!compressedArtifacts.delete()) {
                    compressedArtifacts.deleteOnExit();
                }
            }
            throw e;
        }

        return compressedArtifacts;
    }

    public static void compressZipFile(
            final File temporaryZipFile,
            final Path pathToCompress,
            final BuildListener listener)
            throws IOException {
        try (final ZipArchiveOutputStream zipArchiveOutputStream =
                     new ZipArchiveOutputStream(
                     new BufferedOutputStream(
                     new FileOutputStream(temporaryZipFile)))) {

            compressArchive(
                    pathToCompress,
                    zipArchiveOutputStream,
                    new ArchiveEntryFactory(CompressionType.Zip),
                    CompressionType.Zip,
                    listener);
        }
    }

    public static void compressTarFile(
            final File temporaryTarFile,
            final Path pathToCompress,
            final BuildListener listener)
            throws IOException {
        try (final TarArchiveOutputStream tarArchiveOutputStream =
                     new TarArchiveOutputStream(
                     new BufferedOutputStream(
                     new FileOutputStream(temporaryTarFile)))) {

            compressArchive(
                    pathToCompress,
                    tarArchiveOutputStream,
                    new ArchiveEntryFactory(CompressionType.Tar),
                    CompressionType.Tar,
                    listener);
        }
    }

    public static void compressTarGzFile(
            final File temporaryTarGzFile,
            final Path pathToCompress,
            final BuildListener listener)
            throws IOException {
        try (final TarArchiveOutputStream tarGzArchiveOutputStream =
                new TarArchiveOutputStream(
                new BufferedOutputStream(
                new GzipCompressorOutputStream(
                new FileOutputStream(temporaryTarGzFile))))) {

            compressArchive(
                    pathToCompress,
                    tarGzArchiveOutputStream,
                    new ArchiveEntryFactory(CompressionType.TarGz),
                    CompressionType.TarGz,
                    listener);
        }
    }

    private static void compressArchive(
            final Path pathToCompress,
            final ArchiveOutputStream archiveOutputStream,
            final ArchiveEntryFactory archiveEntryFactory,
            final CompressionType compressionType,
            final BuildListener listener)
            throws IOException {
        final List<File> files = addFilesToCompress(pathToCompress, listener);

        LoggingHelper.log(listener, "Compressing Directory '%s' as a '%s' archive",
                pathToCompress.toString(),
                compressionType.name());

        for (final File file : files) {
            final String newTarFileName = pathToCompress.relativize(file.toPath()).toString();
            final ArchiveEntry archiveEntry = archiveEntryFactory.create(file, newTarFileName);
            archiveOutputStream.putArchiveEntry(archiveEntry);

            try (final FileInputStream fileInputStream = new FileInputStream(file)) {
                IOUtils.copy(fileInputStream, archiveOutputStream);
            }

            archiveOutputStream.closeArchiveEntry();
        }
    }

    public static List<File> addFilesToCompress(final Path pathToCompress, final BuildListener listener) throws IOException {
        final List<File> files = new ArrayList<>();

        if (pathToCompress != null) {
            Files.walkFileTree(
                    pathToCompress,
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE,
                    new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    files.add(file.toFile());
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFileFailed(final Path file, final IOException e) throws IOException {
                    if (e != null) {
                        LoggingHelper.log(listener, "Failed to visit file '%s'. Error: %s.", file.toString(), e.getMessage());
                        LoggingHelper.log(listener, e);
                        throw e;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        return files;
    }

    public static Path resolveWorkspacePath(
            final File workspace,
            final String outputPath)
            throws FileNotFoundException {
        Path path = null;

        if (workspace != null) {
            if (!outputPath.contains(workspace.getAbsolutePath())) {
                path = Paths.get(workspace.getAbsolutePath(), outputPath);
            }
            else {
                path = Paths.get(outputPath);
            }
        }
        else {
            final String attemptPath;
            final URL tmp = CompressionTools.class.getClassLoader().getResource("");
            if (tmp != null) {
                attemptPath = tmp.getPath();
            }
            else {
                attemptPath = "";
            }

            if (attemptPath != null) {
                path = Paths.get(attemptPath);
            }
        }

        if (path == null) {
            throw new FileNotFoundException("Could not resolve path for " + outputPath);
        }

        path.resolve(outputPath);

        return path;
    }

}
