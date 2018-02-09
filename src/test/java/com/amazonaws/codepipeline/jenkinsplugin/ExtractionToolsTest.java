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
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import hudson.model.AbstractBuild;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.tools.zip.ZipOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CompressionType;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;

@RunWith(ExtractionToolsTest.class)
@Suite.SuiteClasses({
        ExtractionToolsTest.GetCompressionTypeTest.class,
        ExtractionToolsTest.DecompressFileTest.class
})
public class ExtractionToolsTest extends Suite {

    private static final String FILE_PATH = Paths.get("A", "File").toString();

    public ExtractionToolsTest(final Class<?> klass, final RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static class TestBase {
        public void setUp() throws IOException {
            MockitoAnnotations.initMocks(this);
            TestUtils.initializeTestingFolders();
        }

        public void tearDown() throws IOException {
            TestUtils.cleanUpTestingFolders();
        }
    }

    public static class GetCompressionTypeTest extends TestBase {
        private CodePipelineStateModel model;

        @Mock
        private S3Object obj;
        @Mock
        private AbstractBuild<?, ?> mockBuild;
        @Mock
        private ObjectMetadata metadata;

        @Before
        public void setUp() throws IOException {
            super.setUp();

            model = new CodePipelineStateModel();
            model.setCompressionType(CompressionType.None);

            when(obj.getObjectMetadata()).thenReturn(metadata);
        }

        @After
        public void tearDown() throws IOException {
            super.tearDown();
        }

        @Test
        public void getCompressionTypeZipSuccess() {
            when(obj.getKey()).thenReturn(Paths.get(FILE_PATH, "Yes.zip").toString());

            final CompressionType compressionType =
                    ExtractionTools.getCompressionType(obj, null);

            assertEquals(CompressionType.Zip, compressionType);
        }

        @Test
        public void getCompressionTypeTarSuccess() {
            when(obj.getKey()).thenReturn(Paths.get(FILE_PATH, "Yes.tar").toString());

            final CompressionType compressionType =
                    ExtractionTools.getCompressionType(obj, null);

            assertEquals(CompressionType.Tar, compressionType);
        }

        @Test
        public void getCompressionTypeTarGzSuccess() {
            when(obj.getKey()).thenReturn(Paths.get(FILE_PATH, "Yes.tar.gz").toString());

            final CompressionType compressionType =
                    ExtractionTools.getCompressionType(obj, null);

            assertEquals(CompressionType.TarGz, compressionType);
        }

        @Test
        public void noCompressionTypeFoundNullFailure() {
            when(obj.getKey()).thenReturn(Paths.get(FILE_PATH, "Yes.notanextension").toString());
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

    public static class DecompressFileTest extends TestBase {
        private static final String ARCHIVE_PREFIX = "decompress-test";

        private Path testDir;
        private Path compressedFile;
        private Path decompressDestination;

        @Before
        public void setUp() throws IOException {
            super.setUp();

            testDir = Paths.get(TestUtils.TEST_DIR);
            decompressDestination = Files.createTempDirectory(ARCHIVE_PREFIX);

            try (final PrintWriter writer = new PrintWriter(
                        Paths.get(TestUtils.TEST_DIR, "bbb.txt").toFile())) {
                writer.println("Some test data for the file");
            }
        }

        @After
        public void tearDown() throws IOException {
            super.tearDown();

            if (compressedFile != null) {
                Files.deleteIfExists(compressedFile);
            }
            FileUtils.deleteDirectory(decompressDestination.toFile());
        }

        @Test
        public void canDecompressZipFile() {
            try {
                compressedFile = Files.createTempFile(ARCHIVE_PREFIX, ".zip");
                try (final ZipArchiveOutputStream outputStream = new ZipArchiveOutputStream(
                            new BufferedOutputStream(new FileOutputStream(compressedFile.toFile())))) {
                    // Deflated is the default compression method
                    zipDirectory(testDir, outputStream, ZipOutputStream.DEFLATED);
                }

                ExtractionTools.decompressFile(
                        compressedFile.toFile(),
                        decompressDestination.toFile(),
                        CompressionType.Zip,
                        null);

                assertEquals(getFileNames(testDir), getFileNames(decompressDestination));
            } catch (final IOException e) {
                fail(e.getMessage());
            }
        }

        @Test
        public void canDecompressZipFileWithStoredCompressionMethod() {
            try {
                compressedFile = Files.createTempFile(ARCHIVE_PREFIX, ".zip");
                try (final ZipArchiveOutputStream outputStream = new ZipArchiveOutputStream(
                            new BufferedOutputStream(new FileOutputStream(compressedFile.toFile())))) {
                    zipDirectory(testDir, outputStream, ZipOutputStream.STORED);
                }

                ExtractionTools.decompressFile(
                        compressedFile.toFile(),
                        decompressDestination.toFile(),
                        CompressionType.Zip,
                        null);

                assertEquals(getFileNames(testDir), getFileNames(decompressDestination));
            } catch (final IOException e) {
                fail(e.getMessage());
            }
        }

        // e.g.: zip -r --exclude *.git* - . | aws s3 cp - s3://code-pipeline/aws-codedeploy-demo.zip
        @Test
        public void canDecompressZipFileCreatedFromCommandLine() {
            try {
                final String filePath = getClass().getClassLoader().getResource("aws-codedeploy-demo.zip").getFile();
                final String osAppropriatePath = System.getProperty("os.name").contains("indow") ? filePath.substring(1) : filePath;

                final Path cliCompressedFile = Paths.get(osAppropriatePath);

                ExtractionTools.decompressFile(
                        cliCompressedFile.toFile(),
                        decompressDestination.toFile(),
                        CompressionType.Zip,
                        null);

                final Set<String> files = new HashSet<>(Arrays.asList(".DS_Store", "appspec.yml",
                            "aws-codepipeline-jenkins-aws-codedeploy_linux.zip", "Gemfile", "LICENSE",
                            "Rakefile", "README.md", "install_dependencies", "start_server", "stop_server",
                            "index.html.haml", "jenkins_sample_test.rb"));

                assertEquals(files, getFileNames(decompressDestination));
            } catch (final IOException e) {
                fail(e.getMessage());
            }
        }

        @SuppressWarnings("unchecked")
        private static Set<String> getFileNames(final Path dir) {
            final Collection<File> files = FileUtils.listFiles(dir.toFile(), null, true);
            final Set<String> fileNames = new HashSet<>();
            for (final File file : files) {
                fileNames.add(file.getName());
            }
            return fileNames;
        }

        private static void zipDirectory(
                final Path directory,
                final ZipArchiveOutputStream out,
                final int compressionMethod) throws IOException {

            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    final String entryName = directory.relativize(file).toString();
                    final ZipArchiveEntry archiveEntry = new ZipArchiveEntry(file.toFile(), entryName);
                    final byte[] contents = Files.readAllBytes(file);

                    out.setMethod(compressionMethod);
                    archiveEntry.setMethod(compressionMethod);

                    if (compressionMethod == ZipOutputStream.STORED) {
                        archiveEntry.setSize(contents.length);

                        final CRC32 crc32 = new CRC32();
                        crc32.update(contents);
                        archiveEntry.setCrc(crc32.getValue());
                    }

                    out.putArchiveEntry(archiveEntry);
                    out.write(contents);
                    out.closeArchiveEntry();

                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

}
