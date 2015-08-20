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
package com.amazonaws.codepipeline;

import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel;
import com.amazonaws.codepipeline.jenkinsplugin.CompressionTools;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CompressionToolsTest {
    private CodePipelineStateModel model;
    private String projectName = "";

    @Mock
    private AbstractBuild mockBuild;
    @Mock
    private AbstractProject abstractProject;

    @Before
    public void setUp() throws IOException {
        model = new CodePipelineStateModel();
        MockitoAnnotations.initMocks(this);
        when(mockBuild.getProject()).thenReturn(abstractProject);
        when(abstractProject.getName()).thenReturn(projectName);
        TestUtils.initializeTestingFolders();
    }

    @After
    public void tearDown() throws IOException {
        TestUtils.cleanUpTestingFolders();
    }

    @Test
    public void zipCompressionWithTypeSuccess() throws IOException {
        projectName = "ZipProject";
        model.setCompressionType(CodePipelineStateModel.CompressionType.Zip);
        final File compressedFile = CompressionTools.compressFile(
                "ZipProject",
                new File("TestDir"),
                "",
                model.getCompressionType(),
                null);

        assertTrue(compressedFile.length() > 0);
        assertTrue(compressedFile.getName().contains("ZipProject"));
        assertTrue(compressedFile.getName().contains(".zip"));
    }

    @Test
    public void tarCompressionWithTypeSuccess() throws IOException {
        projectName = "TarProject";
        model.setCompressionType(CodePipelineStateModel.CompressionType.Tar);
        final File compressedFile = CompressionTools.compressFile(
                "TarProject",
                new File("TestDir"),
                "",
                model.getCompressionType(),
                null);

        assertTrue(compressedFile.length() > 0);
        assertTrue(compressedFile.getName().contains("TarProject"));
        assertTrue(compressedFile.getName().contains(".tar"));
    }

    @Test
    public void tarGzCompressionWithTypeSuccess() throws IOException {
        projectName = "TarGzProject";
        model.setCompressionType(CodePipelineStateModel.CompressionType.TarGz);
        final File compressedFile = CompressionTools.compressFile(
                "TarGzProject",
                new File("TestDir"),
                "",
                model.getCompressionType(),
                null);

        assertTrue(compressedFile.length() > 0);
        assertTrue(compressedFile.getName().contains("TarGzProject"));
        assertTrue(compressedFile.getName().contains(".tar.gz"));
    }

    @Test
    public void addFoldersToListSuccess() throws IOException {
        final Path file = Paths.get("TestDir");
        final List<File> files = CompressionTools.addFilesToCompress(file);

        assertEquals(files.size(), 5);
    }
}