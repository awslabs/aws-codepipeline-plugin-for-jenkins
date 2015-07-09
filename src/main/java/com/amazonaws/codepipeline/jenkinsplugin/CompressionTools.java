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

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.util.IOUtils;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CompressionTools {
    private final CodePipelineStateModel model;
    private final LoggingHelper logHelper;

    public CompressionTools() {
        logHelper  = new LoggingHelper();
        this.model = new CodePipelineStateService().getModel();
    }

    // Compressing the file to upload to S3 should use the same type of compression as the customer
    // used to zip it up.
    public File compressFile(
            final AbstractBuild build,
            final String directoryToZip,
            final BuildListener listener)
            throws IOException, InterruptedException {
        final String projectName = build.getProject().getName();
        final FilePath filePath  = build.getWorkspace();
        File compressedArtifacts = null;

        switch (model.getCompressionType()) {
            case None:
                // Zip it up if we don't know since zip is the default format for AWS CodePipelines
            case Zip:
                compressedArtifacts = File.createTempFile(projectName + "-", ".zip");
                compressZipFile(compressedArtifacts, filePath, directoryToZip, listener);
                break;
            case Tar:
                compressedArtifacts = File.createTempFile(projectName + "-", ".tar");
                compressTarFile(compressedArtifacts, filePath, directoryToZip, listener);
                break;
            case TarGz:
                compressedArtifacts = File.createTempFile(projectName + "-", ".tar.gz");
                compressTarGzFile(compressedArtifacts, filePath, directoryToZip, listener);
                break;
        }

        return compressedArtifacts;
    }

    public void compressZipFile(
            final File temporaryZipFile,
            final FilePath filePath,
            final String directoryToZip,
            final BuildListener listener)
            throws IOException, InterruptedException {
        try (ZipArchiveOutputStream zipArchiveOutputStream =
                     new ZipArchiveOutputStream(
                     new BufferedOutputStream(
                     new FileOutputStream(temporaryZipFile)))) {

            compressArchive(
                    filePath,
                    directoryToZip,
                    zipArchiveOutputStream,
                    new ArchiveEntryFactory(CodePipelineStateModel.CompressionType.Zip),
                    listener);
        }
    }

    public void compressTarFile(
            final File temporaryTarFile,
            final FilePath filePath,
            final String directoryToZip,
            final BuildListener listener)
            throws IOException, InterruptedException {
        try (TarArchiveOutputStream tarArchiveOutputStream =
                     new TarArchiveOutputStream(
                     new BufferedOutputStream(
                     new FileOutputStream(temporaryTarFile)))) {
            compressArchive(
                    filePath,
                    directoryToZip,
                    tarArchiveOutputStream,
                    new ArchiveEntryFactory(CodePipelineStateModel.CompressionType.Tar),
                    listener);
        }
    }

    public void compressTarGzFile(
            final File temporaryTarGzFile,
            final FilePath filePath,
            final String directoryToZip,
            final BuildListener listener)
            throws IOException {
        try (TarArchiveOutputStream tarGzArchiveOutputStream =
                new TarArchiveOutputStream(
                new BufferedOutputStream(
                new GzipCompressorOutputStream(
                new FileOutputStream(temporaryTarGzFile))))) {
            compressArchive(
                    filePath,
                    directoryToZip,
                    tarGzArchiveOutputStream,
                    new ArchiveEntryFactory(CodePipelineStateModel.CompressionType.TarGz),
                    listener);
        }
    }

    private void compressArchive(
            final FilePath filePath,
            final String directoryToZip,
            final ArchiveOutputStream archiveOutputStream,
            final ArchiveEntryFactory archiveEntryFactory,
            final BuildListener listener)
            throws IOException {

        logHelper.log(listener, String.format("Compressing Directory '%s' as a '%s' archive",
                directoryToZip,
                model.getCompressionType().name()));

        String toCompress = resolveCompressionPath(directoryToZip, filePath);
        final File directoryToCompress = new File(toCompress);
        final List<File> files = addFilesToCompress(directoryToCompress);

        for (final File file : files) {
            if (!toCompress.endsWith(File.separator)) {
                toCompress += File.separator;
            }

            final String newTarFileName = file.toString().replace(toCompress, "");
            final ArchiveEntry archiveEntry = archiveEntryFactory.create(file, newTarFileName);
            archiveOutputStream.putArchiveEntry(archiveEntry);

            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                IOUtils.copy(fileInputStream, archiveOutputStream);
            }

            archiveOutputStream.closeArchiveEntry();
        }
    }

    public List<File> addFilesToCompress(final File fileToCompress) throws IOException {
        final List<File> files = new ArrayList<>();

        if (fileToCompress == null) {
            return files;
        }

        if (fileToCompress.isDirectory()) {
            final File[] fileList = fileToCompress.listFiles();

            if (fileList != null) {
                for (final File file : fileList) {
                    files.addAll(addDirectoryToCompress(file));
                }
            }
            else {
                throw new FileNotFoundException("Unable to List Files in directory "
                        + fileToCompress.getCanonicalPath());
            }
        }
        else {
            files.add(fileToCompress);
        }

        return files;
    }

    public List<File> addDirectoryToCompress(final File directoryToCompress) throws IOException {
        final List<File> files = new ArrayList<>();

        if (directoryToCompress == null) {
            return files;
        }

        if (directoryToCompress.isDirectory()) {
            final File[] fileList = directoryToCompress.listFiles();

            if (fileList != null) {
                for (final File file : fileList) {
                    if (file.isDirectory()) {
                        files.addAll(addDirectoryToCompress(file));
                    }
                    else {
                        files.add(file);
                    }
                }
            }
            else {
                throw new FileNotFoundException("Unable to List Files in directory "
                        + directoryToCompress.getCanonicalPath());
            }
        }
        else {
            files.add(directoryToCompress);
        }

        return files;
    }

    public String resolveCompressionPath(
            final String userOutputPath,
            final FilePath filePath)
            throws FileNotFoundException {
        Path path = null;
        final String directoryToCompress;

        if (filePath != null) {
            if (!userOutputPath.contains(filePath.getRemote())) {
                path = Paths.get(filePath.getRemote(), userOutputPath);
            }
            else {
                path = Paths.get(userOutputPath);
            }
        }
        else {
            final String attemptPath;
            final URL tmp = this.getClass().getClassLoader().getResource("");
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
            throw new FileNotFoundException("Could not resolve path for " + userOutputPath);
        }

        path.resolve(userOutputPath);
        directoryToCompress = path.toString();

        return directoryToCompress;
    }
}
