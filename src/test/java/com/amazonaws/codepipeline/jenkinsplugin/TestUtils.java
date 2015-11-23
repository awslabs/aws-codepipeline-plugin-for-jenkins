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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;

public class TestUtils {

    public static final String TEST_DIR = "TestDir";
    private static final String EXTRA_TEST_DIR = "ExtraTestDir";

    public static void assertContainsIgnoreCase(final String strToMatch, final String strToCheck) {
        final String strToMatchLower = strToMatch.toLowerCase();
        final String strToCheckLower = strToCheck.toLowerCase();
        assertTrue(strToCheckLower.contains(strToMatchLower));
    }

    public static void assertEqualsIgnoreCase(final String strToMatch, final String strToCheck) {
        final String strToMatchLower = strToMatch.toLowerCase();
        final String strToCheckLower = strToCheck.toLowerCase();
        assertEquals(strToCheckLower, strToMatchLower);
    }

    public static ByteArrayOutputStream setOutputStream() {
        final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        return outContent;
    }

    public static void initializeTestingFolders() throws IOException {
        // TEST_DIR
        Files.createDirectories(Paths.get(TEST_DIR, "Dir1", "SubDir1"));
        Files.createDirectories(Paths.get(TEST_DIR, "Dir1", "SubDir2", "SubDir3"));
        Files.createDirectories(Paths.get(TEST_DIR, "Dir2"));

        Files.createFile(Paths.get(TEST_DIR, "bbb.txt"));
        Files.createFile(Paths.get(TEST_DIR, "Dir1", "out.txt"));
        Files.createFile(Paths.get(TEST_DIR, "Dir1", "SubDir1", "aaa.txt"));
        Files.createFile(Paths.get(TEST_DIR, "Dir1", "SubDir2", "SubDir3", "onemoretime.txt"));
        Files.createFile(Paths.get(TEST_DIR, "Dir2", "333.txt"));

        // EXTRA_TEST_DIR
        Files.createDirectories(Paths.get(EXTRA_TEST_DIR, "of"));

        Files.createFile(Paths.get(EXTRA_TEST_DIR, "life.txt"));
        Files.createFile(Paths.get(EXTRA_TEST_DIR, "of", "brian.py"));
    }

    public static void addSymlinkToFolderInsideWorkspace() throws IOException {
        Files.createSymbolicLink(Paths.get(TEST_DIR, "SymlinkDir"), Paths.get("Dir1"));
    }

    public static void addSymlinkToFileInsideWorkspace() throws IOException {
        Files.createSymbolicLink(Paths.get(TEST_DIR, "SymlinkFile"), Paths.get("Dir1", "out.txt"));
    }

    public static void addSymlinkToFolderOutsideWorkspace() throws IOException {
        Files.createSymbolicLink(Paths.get(TEST_DIR, "SymlinkDir"), Paths.get("..", EXTRA_TEST_DIR));
    }

    public static void cleanUpTestingFolders() throws IOException {
        for (final String testDir : new String[] { TEST_DIR, EXTRA_TEST_DIR }) {
            final File file = new File(testDir);
            FileUtils.deleteDirectory(file);
        }
    }

}
