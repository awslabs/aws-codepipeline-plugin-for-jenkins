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

import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

public class TestUtils {

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
        System.out.println("Initializing Folders");
        File file = new File("TestDir");
        file.mkdirs();
        System.out.println(file.getAbsolutePath());
        file = new File("TestDir/Dir1");
        file.mkdirs();
        file = new File("TestDir/Dir2");
        file.mkdirs();
        file = new File("TestDir/Dir1/SubDir1");
        file.mkdirs();
        file = new File("TestDir/Dir1/SubDir2");
        file.mkdirs();
        file = new File("TestDir/Dir1/out.txt");
        file.createNewFile();
        file = new File("TestDir/bbb.txt");
        file.createNewFile();
        file = new File("TestDir/Dir1/SubDir1/aaa.txt");
        file.createNewFile();
        file = new File("TestDir/Dir2/333.txt");
        file.createNewFile();
        file = new File("TestDir/Dir1/SubDir2/SubDir3");
        file.mkdirs();
        file = new File("TestDir/Dir1/SubDir2/SubDir3/onemoretime.txt");
        file.createNewFile();
    }

    public static void cleanUpTestingFolders() throws IOException {
        final File file = new File("TestDir");
        deleteDirectory(file);
    }

}