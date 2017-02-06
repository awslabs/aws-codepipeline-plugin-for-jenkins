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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.RandomStringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CategoryType;
import com.amazonaws.services.codepipeline.model.Artifact;

import hudson.model.Failure;

public class ValidationTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void projectNameUnderscoreSuccess() {
        Validation.validateProjectName("okay_project_name", null);
    }

    @Test
    public void projectNameAllCapsSuccess() {
        Validation.validateProjectName("THISISALSOOKAY", null);
    }

    @Test
    public void randomProjectName() {
        Validation.validateProjectName(
                RandomStringUtils.randomAlphanumeric(Validation.MAX_PROJECT_NAME_LENGTH),
                null);
    }

    @Test
    public void projectNameDashesSuccess() {
        Validation.validateProjectName("we-can-have-dashes", null);
    }

    @Test
    public void projectNameNumbersSuccess() {
        Validation.validateProjectName("num8er5arealso0k1", null);
    }

    @Test
    public void projectNameAlphaNumericSuccess() {
        Validation.validateProjectName("lets_JUST-D0-17all23", null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void projectNameTooLongNameFailure() {
        Validation.validateProjectName(
                RandomStringUtils.randomAlphanumeric(Validation.MAX_PROJECT_NAME_LENGTH * 2),
                null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void projectNameNoSpecialCharactersInNameFailure() {
        Validation.validateProjectName("No special Characters @", null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void projectNameAllBadCharactersInNameFailure() {
        Validation.validateProjectName("! @ # $ % ^ & * ():+=?\\,.<> \"\"\"\"'''", null);
    }

    @Test
    public void projectNameAllBadCharactersFailure() {
        final char[] badChars = {'@', '!', '#', '$', '%', '^', '&', '*', '(', ')', '{', '}',
                '[', ']', ',', '.', '<', '>' , '?', '/', '\\', '"', '\'', ':', '+', '='};

        for (final char c : badChars) {
            final String error = "Invalid project name: " + c + ". The AWS CodePipeline Jenkins plugin supports project names with alphanumeric characters and the special characters - (minus sign) " +
                    "and _ (underscore).";
            thrown.expect(IllegalArgumentException.class);
            thrown.expectMessage(error);
            Validation.validateProjectName("" + c, null);
        }
    }

    @Test
    public void numberOfOutputsSuccess() {
        final List<Artifact> artifacts = new ArrayList<>();
        for (int i = 0; i < Validation.MAX_ARTIFACTS; i++) {
            artifacts.add(new Artifact());
        }

        Validation.numberOfOutPutsIsValid(artifacts);
    }

    @Test(expected=Exception.class)
    public void numberOfOutputsFailure() {
        final List<Artifact> artifacts = new ArrayList<>();

        while (artifacts.size() <= Validation.MAX_ARTIFACTS) {
            artifacts.add(new Artifact());
        }

        Validation.numberOfOutPutsIsValid(artifacts);
    }

    @Test
    public void validatePluginAllFieldsCorrectEmptyCredentialsSuccess() {
        Validation.validatePlugin(
                "",
                "",
                "us-east-1",
                CategoryType.Build.getName(),
                "Jenkins-Build",
                "1",
                "ProjectName",
                null);
    }

    @Test
    public void validatePluginAllFieldsCorrectFullCredentialsSuccess() {
        Validation.validatePlugin(
                "access-key",
                "A32KDAFSD-rand-key",
                "us-east-1",
                CategoryType.Build.getName(),
                "Jenkins-Build",
                "1",
                "ProjectName",
                null);
    }

    @Test
    public void validatePluginNoCredentialsFailure() {
        final String error = "AWS CodePipeline Jenkins plugin setup error. One or more required configuration parameters have not been specified." + System.lineSeparator() +
                "The AWS credentials provided are not valid.";

        thrown.expect(Failure.class);
        thrown.expectMessage(error);

        Validation.validatePlugin(
                null,
                null,
                "us-east-1",
                CategoryType.Build.getName(),
                "Jenkins-Build",
                "1",
                "ProjectName",
                null);
    }

    @Test
    public void validatePluginNoRegionFailure() {
        final String error = "AWS CodePipeline Jenkins plugin setup error. One or more required configuration parameters have not been specified." + System.lineSeparator();
        final String regionError =  "The specified AWS region is not valid.";

        thrown.expect(Failure.class);
        thrown.expectMessage(error);
        thrown.expectMessage(regionError);

        Validation.validatePlugin(
                "",
                "",
                "",
                CategoryType.Build.getName(),
                "Jenkins-Build",
                "1",
                "ProjectName",
                null);
    }

    @Test
    public void validatePluginInvalidActionTypeProviderFailure() {
        final String error = "AWS CodePipeline Jenkins plugin setup error. One or more required configuration parameters have not been specified." + System.lineSeparator();

        thrown.expect(Failure.class);
        thrown.expectMessage(error);
        thrown.expectMessage("Category:");
        thrown.expectMessage("Version: 1");
        thrown.expectMessage("Provider:");

        Validation.validatePlugin(
                "",
                "",
                "us-east-1",
                CategoryType.Build.getName(),
                "",
                "1",
                "ProjectName",
                null);
    }

    @Test
    public void validatePluginInvalidActionTypeCategoryFailure() {
        final String error = "AWS CodePipeline Jenkins plugin setup error. One or more required configuration parameters have not been specified." + System.lineSeparator();

        thrown.expect(Failure.class);
        thrown.expectMessage(error);
        thrown.expectMessage("Category: Please Choose A Category");
        thrown.expectMessage("Version: 1");
        thrown.expectMessage("Provider: Jenkins-Build");

        Validation.validatePlugin(
                "",
                "",
                "us-east-1",
                CategoryType.PleaseChooseACategory.getName(),
                "Jenkins-Build",
                "1",
                "ProjectName",
                null);
    }

    @Test
    public void validatePluginInvalidActionTypeVersionFailure() {
        final String error = "AWS CodePipeline Jenkins plugin setup error. One or more required configuration parameters have not been specified." + System.lineSeparator();

        thrown.expect(Failure.class);
        thrown.expectMessage(error);
        thrown.expectMessage("Category: Build");
        thrown.expectMessage("Version:");
        thrown.expectMessage("Provider: Jenkins-Build");

        Validation.validatePlugin(
                "",
                "",
                "us-east-1",
                CategoryType.Build.getName(),
                "Jenkins-Build",
                "",
                "ProjectName",
                null);
    }

    @Test
    public void validatePluginAllFieldsMissingFailure() {
        thrown.expect(Failure.class);
        thrown.expectMessage("AWS CodePipeline Jenkins plugin setup error. One or more required configuration parameters have not been specified.");
        thrown.expectMessage("The specified AWS region is not valid.");
        thrown.expectMessage("The AWS credentials provided are not valid.");

        Validation.validatePlugin(
                null,
                null,
                "",
                CategoryType.Build.getName(),
                "Jenkins-Build",
                "",
                "ProjectName",
                null);
    }

}