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

import static com.amazonaws.codepipeline.TestUtils.assertEqualsIgnoreCase;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import com.amazonaws.services.codepipeline.model.Artifact;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CodePipelineStateModelTests {
    private CodePipelineStateModel model;
    private final String projectNameA = "Project_A";
    private final String projectNameB = "Project_B";
    private final String projectNameC = "Project_C";
    private final String projectNameD = "Project_D";
    private final String projectNameE = "Project_E";
    private final String projectAID = UUID.randomUUID().toString();
    private final String projectBID = UUID.randomUUID().toString();
    private final String projectCID = UUID.randomUUID().toString();
    private final String projectDID = UUID.randomUUID().toString();
    private final String projectEID = UUID.randomUUID().toString();
    private List<Artifact> artifactsA;
    private List<Artifact> artifactsB;
    final Artifact a = new Artifact();
    final Artifact b = new Artifact();
    final Artifact c = new Artifact();

    @Before
    public void setUp() throws IOException, InterruptedException {
        model = new CodePipelineStateModel();
        artifactsA = new ArrayList<>();
        artifactsB = new ArrayList<>();
        a.setName("A");
        b.setName("B");
        c.setName("C");
        artifactsA.add(a);
        artifactsB.add(b);
        artifactsB.add(c);

        //addItemsToJobIDs();
        //addArtifactsToModel();
    }

    @Test
    public void testJobIDHashAddSuccess() {
        /*assertNotNull(model.getJobID(projectNameA));
        assertEqualsIgnoreCase(projectAID, model.getJobID(projectNameA));
        assertNotNull(model.getJobID(projectNameB));
        assertEqualsIgnoreCase(projectBID, model.getJobID(projectNameB));
        assertNotNull(model.getJobID(projectNameC));
        assertEqualsIgnoreCase(projectCID, model.getJobID(projectNameC));
        assertNotNull(model.getJobID(projectNameD));
        assertEqualsIgnoreCase(projectDID, model.getJobID(projectNameD));
        assertNotNull(model.getJobID(projectNameE));
        assertEqualsIgnoreCase(projectEID, model.getJobID(projectNameE));
    }

    @Test
    public void testClearingJobSuccess() {
        assertNotNull(model.getJobID(projectNameA));
        assertEqualsIgnoreCase(projectAID, model.getJobID(projectNameA));
        model.clearJob(projectNameA);
        assertNull(model.getJobID(projectNameA));

        assertNotNull(model.getJobID(projectNameB));
        assertEqualsIgnoreCase(projectBID, model.getJobID(projectNameB));
        model.clearJob(projectNameB);
        assertNull(model.getJobID(projectNameB));

        assertNotNull(model.getJobID(projectNameC));
        assertEqualsIgnoreCase(projectCID, model.getJobID(projectNameC));
        model.clearJob(projectNameC);
        assertNull(model.getJobID(projectNameC));

        assertNotNull(model.getJobID(projectNameD));
        assertEqualsIgnoreCase(projectDID, model.getJobID(projectNameD));
        model.clearJob(projectNameD);
        assertNull(model.getJobID(projectNameD));

        assertNotNull(model.getJobID(projectNameE));
        assertEqualsIgnoreCase(projectEID, model.getJobID(projectNameE));
        model.clearJob(projectNameE);
        assertNull(model.getJobID(projectNameE));
    }

    @Test
    public void getBuildArtifactsSingleHashAddSuccess() {
        assertNotNull(model.getOutputBuildArtifacts(projectNameA));
        final List<Artifact> testList = model.getOutputBuildArtifacts(projectNameA);
        assertEquals(a, testList.get(0));
        assertEquals(1, testList.size());
    }

    @Test
    public void getBuildArtifactsListHashAddSuccess() {
        assertNotNull(model.getOutputBuildArtifacts(projectNameB));
        final List<Artifact> testList = model.getOutputBuildArtifacts(projectNameB);
        assertEquals(b, testList.get(0));
        assertEquals(c, testList.get(1));
        assertEquals(2, testList.size());
    }

    @Test
    public void testClearingBuildArtifactsSuccess() {
        model.removeOutputBuildArtifactsByProjectName(projectNameA);
        assertNull(model.getOutputBuildArtifacts(projectNameA));
        assertNotNull(model.getOutputBuildArtifacts(projectNameB));
    }

    private void addItemsToJobIDs() {
        model.setJobID(projectNameA, projectAID);
        model.setJobID(projectNameB, projectBID);
        model.setJobID(projectNameC, projectCID);
        model.setJobID(projectNameD, projectDID);
        model.setJobID(projectNameE, projectEID);
    }

    private void addArtifactsToModel() {
        model.setOutputBuildArtifacts(projectNameA, artifactsA);
        model.setOutputBuildArtifacts(projectNameB, artifactsB);*/
    }
}
