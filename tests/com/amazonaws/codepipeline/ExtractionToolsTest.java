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
import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CompressionType;
import com.amazonaws.codepipeline.jenkinsplugin.ExtractionTools;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import hudson.model.AbstractBuild;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static com.amazonaws.codepipeline.TestUtils.cleanUpTestingFolders;
import static com.amazonaws.codepipeline.TestUtils.initializeTestingFolders;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class ExtractionToolsTest {
    private CodePipelineStateModel model;
    @Mock
    private S3Object obj;
    @Mock
    private AbstractBuild mockBuild;
    @Mock
    private ObjectMetadata metadata;
    private final String filePath = Paths.get("A", "File").toString();

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        model = new CodePipelineStateModel();
        model.setCompressionType(CompressionType.None);
        initializeTestingFolders();

        when(obj.getObjectMetadata()).thenReturn(metadata);
    }

    @After
    public void cleanUp() throws IOException {
        cleanUpTestingFolders();
    }

    @Test
    public void getCompressionTypeZipSuccess() {
        when(obj.getKey()).thenReturn(Paths.get(filePath, "Yes.zip").toString());

        final CompressionType compressionType =
                ExtractionTools.getCompressionType(obj, null);

        assertEquals(CompressionType.Zip, compressionType);
    }

    @Test
    public void getCompressionTypeTarSuccess() {
        when(obj.getKey()).thenReturn(Paths.get(filePath, "Yes.tar").toString());

        final CompressionType compressionType =
                ExtractionTools.getCompressionType(obj, null);

        assertEquals(CompressionType.Tar, compressionType);
    }

    @Test
    public void getCompressionTypeTarGzSuccess() {
        when(obj.getKey()).thenReturn(Paths.get(filePath, "Yes.tar.gz").toString());

        final CompressionType compressionType =
                ExtractionTools.getCompressionType(obj, null);

        assertEquals(CompressionType.TarGz, compressionType);
    }

    @Test
    public void noCompressionTypeFoundNullFailure() {
        when(obj.getKey()).thenReturn(Paths.get(filePath, "Yes.notanextension").toString());
        when(metadata.getContentType()).thenReturn("notanextension");

        final CompressionType compressionType =
                ExtractionTools.getCompressionType(obj, null);

        assertEquals(CompressionType.None, compressionType);
    }

    @Test
    public void getCompressionTypeZipFromMetadataSuccess() {
        when(obj.getKey()).thenReturn("A" + File.separator + "File" + File.separator + "XK321K");
        when(metadata.getContentType()).thenReturn("application/zip");
        final CompressionType compressionType =
                ExtractionTools.getCompressionType(obj, null);

        assertEquals(CompressionType.Zip, compressionType);
    }

    @Test
    public void getCompressionTypeTarFromMetadataSuccess() {
        when(obj.getKey()).thenReturn("A" + File.separator + "File" + File.separator + "XK321K");
        when(metadata.getContentType()).thenReturn("application/tar");

        final CompressionType compressionType =
                ExtractionTools.getCompressionType(obj, null);

        assertEquals(CompressionType.Tar, compressionType);
    }

    @Test
    public void getCompressionTypeTarGzFromMetadataSuccess() {
        when(obj.getKey()).thenReturn("A" + File.separator + "File" + File.separator + "XK321K");
        when(metadata.getContentType()).thenReturn("application/gzip");
        final CompressionType compressionType = ExtractionTools.getCompressionType(obj, null);

        assertEquals(CompressionType.TarGz, compressionType);
    }

    @Test
    public void getCompressionTypeTarGzXFromMetadataSuccess() {
        when(obj.getKey()).thenReturn("A" + File.separator + "File" + File.separator + "XK321K");
        when(metadata.getContentType()).thenReturn("application/x-gzip");

        final CompressionType compressionType =
                ExtractionTools.getCompressionType(obj, null);

        assertEquals(CompressionType.TarGz, compressionType);
    }

    @Test
    public void getCompressionTypeTarXFromMetadataSuccess() {
        when(obj.getKey()).thenReturn("A" + File.separator + "File" + File.separator + "XK321K");
        when(metadata.getContentType()).thenReturn("application/x-tar");

        final CompressionType compressionType =
                ExtractionTools.getCompressionType(obj, null);

        assertEquals(CompressionType.Tar, compressionType);
    }
}
