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

import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CompressionType;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

import java.io.File;

public class ArchiveEntryFactory {
    final CompressionType compressionType;

    public ArchiveEntryFactory(final CompressionType compressionType) {
        this.compressionType = compressionType;
    }

    public ArchiveEntry create(final File file, final String fileName) {
        switch (compressionType) {
            case None:
            case Zip:
                return new ZipArchiveEntry(file, fileName);
            case Tar:
            case TarGz:
                return new TarArchiveEntry(file, fileName);
        }

        return null;
    }
}
