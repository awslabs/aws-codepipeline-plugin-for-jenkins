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
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CategoryType;
import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CompressionType;
import com.amazonaws.services.codepipeline.model.EncryptionKey;
import com.amazonaws.services.codepipeline.model.Job;
import com.amazonaws.services.codepipeline.model.JobData;

public class CodePipelineStateModelTests {

    private CodePipelineStateModel model;

    @Mock
    private Job mockJob;
    @Mock
    private JobData mockJobData;
    @Mock
    private EncryptionKey mockEncryptionKey;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        model = new CodePipelineStateModel();
    }

    @Test
    public void testConstructorSetsDefaultValues() {
        assertEquals(CompressionType.None, model.getCompressionType());
        assertEquals(CategoryType.PleaseChooseACategory, model.getActionTypeCategory());
    }

    @Test
    public void testProxyPort() {
        model.setProxyPort(8000);
        assertEquals(8000, model.getProxyPort());
    }

    @Test
    public void testProxyHost() {
        model.setProxyHost("localhost");
        assertEquals("localhost", model.getProxyHost());
    }

    @Test
    public void testRegion() {
        model.setRegion("us-east-1");
        assertEquals("us-east-1", model.getRegion());
    }

    @Test
    public void testAwsAccessKey() {
        model.setAwsAccessKey("xxxx");
        assertEquals("xxxx", model.getAwsAccessKey());
    }

    @Test
    public void testAwsSecretKey() {
        model.setAwsSecretKey("1234");
        assertEquals("1234", model.getAwsSecretKey());
    }

    @Test
    public void testActionTypeCategory() {
        for (final CategoryType categoryType : CategoryType.values()) {
            model.setActionTypeCategory(categoryType.getName());
            assertEquals(categoryType, model.getActionTypeCategory());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testActionTypeCategoryThrowsForUnknownCategory() {
        model.setActionTypeCategory("spam");
    }

    @Test
    public void testJob() {
        model.setJob(mockJob);
        assertEquals(mockJob, model.getJob());
    }

    @Test
    public void testEncryptionKey() {
        when(mockJob.getData()).thenReturn(mockJobData);
        when(mockJobData.getEncryptionKey()).thenReturn(mockEncryptionKey);

        model.setJob(mockJob);
        assertEquals(mockEncryptionKey, model.getEncryptionKey());
    }

    @Test(expected = NullPointerException.class)
    public void testEncryptionKeyThrowsWhenJobIsNull() {
        model.clearJob();
        model.getEncryptionKey();
    }

    @Test(expected = NullPointerException.class)
    public void testEncryptionKeyThrowsWhenJobDataIsNull() {
        when(mockJob.getData()).thenReturn(null);

        model.getEncryptionKey();
    }

    @Test
    public void testCompressionType() {
        for (final CompressionType compressionType : CompressionType.values()) {
            model.setCompressionType(compressionType);
            assertEquals(compressionType, model.getCompressionType());
        }
    }

    @Test
    public void testCompressionTypeIsSetToNoneForNullValues() {
        model.setCompressionType(null);
        assertEquals(CompressionType.None, model.getCompressionType());
    }

}
