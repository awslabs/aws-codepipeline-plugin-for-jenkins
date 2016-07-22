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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CompressionToolsTest {

    private CodePipelineStateModel model;
    private String projectName;
    private Path testDir;
    private File compressedFile;

    @Mock
    private AbstractBuild mockBuild;
    @Mock
    private AbstractProject<?, ?> abstractProject;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);

        TestUtils.initializeTestingFolders();
        testDir = Paths.get(TestUtils.TEST_DIR);

        model = new CodePipelineStateModel();

        when(mockBuild.getProject()).thenReturn(abstractProject);
        when(abstractProject.getName()).thenReturn(projectName);
    }

    @After
    public void tearDown() throws IOException {
        TestUtils.cleanUpTestingFolders();

        if (compressedFile != null) {
            if (!compressedFile.delete()) {
                compressedFile.deleteOnExit();
            }
        }
    }

    @Test
    public void succeedsWithZipCompressionType() throws IOException {
        projectName = "ZipProject";
        model.setCompressionType(CodePipelineStateModel.CompressionType.Zip);

        compressedFile = CompressionTools.compressFile(
                projectName,
                testDir,
                model.getCompressionType(),
                null);

        assertTrue(compressedFile.length() > 0);
        assertTrue(compressedFile.getName().contains(projectName));
        assertTrue(compressedFile.getName().contains(".zip"));
    }

    @Test
    public void succeedsWithTarCompressionType() throws IOException {
        projectName = "TarProject";
        model.setCompressionType(CodePipelineStateModel.CompressionType.Tar);

        compressedFile = CompressionTools.compressFile(
                projectName,
                testDir,
                model.getCompressionType(),
                null);

        assertTrue(compressedFile.length() > 0);
        assertTrue(compressedFile.getName().contains(projectName));
        assertTrue(compressedFile.getName().contains(".tar"));
    }

    @Test
    public void succeedsWithTarGzCompressionType() throws IOException {
        projectName = "TarGzProject";
        model.setCompressionType(CodePipelineStateModel.CompressionType.TarGz);

        compressedFile = CompressionTools.compressFile(
                projectName,
                testDir,
                model.getCompressionType(),
                null);

        assertTrue(compressedFile.length() > 0);
        assertTrue(compressedFile.getName().contains(projectName));
        assertTrue(compressedFile.getName().contains(".tar.gz"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void failsWithUnknownCompressionType() throws IOException {
        projectName = "UnkownkCompressionProject";
        model.setCompressionType(CodePipelineStateModel.CompressionType.None);

        CompressionTools.compressFile(
                projectName,
                testDir,
                model.getCompressionType(),
                null);
    }

    @Test
    public void returnsAllFilesInDirectory() throws IOException {
        final List<File> files = CompressionTools.addFilesToCompress(testDir, null);
        assertEquals(5, files.size());
    }

    @Test
    public void followsSymlinks() throws IOException {
        TestUtils.addSymlinkToFolderInsideWorkspace();

        final List<File> files = CompressionTools.addFilesToCompress(testDir, null);
        // Symlink to folder with 3 files
        assertEquals(8, files.size());
    }

    @Test
    public void followsSymlinksToFiles() throws IOException {
        TestUtils.addSymlinkToFileInsideWorkspace();

        final List<File> files = CompressionTools.addFilesToCompress(testDir, null);
        // Symlink to a file
        assertEquals(6, files.size());
    }

    @Test
    public void followsSymlinksOutsideTheWorkspace() throws IOException {
        TestUtils.addSymlinkToFolderOutsideWorkspace();

        final List<File> files = CompressionTools.addFilesToCompress(testDir, null);
        // Symlink to folder outside workspace with 2 files
        assertEquals(7, files.size());
    }

    @Test(expected = IOException.class)
    public void detectsCyclesInWorkspace() throws IOException {
        TestUtils.addSymlinkToCreateCycleInWorkspace();

        try {
            CompressionTools.addFilesToCompress(testDir, null);
        } finally {
            TestUtils.removeSymlinkCycle();
        }
    }

}
