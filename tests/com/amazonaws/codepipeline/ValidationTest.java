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

import com.amazonaws.codepipeline.jenkinsplugin.Validation;
import com.amazonaws.services.codepipeline.model.Artifact;
import hudson.model.Failure;
import org.apache.commons.lang.RandomStringUtils;
import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CategoryType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        final String error = "Project Name on AWSCodePipeline's must only contain Alphanumeric characters and '-' or '_'";
        final char[] badChars = {'@', '!', '#', '$', '%', '^', '&', '*', '(', ')', '{', '}',
                '[', ']', ',', '.', '<', '>' , '?', '/', '\\', '"', '\'', ':', '+', '='};

        for (final char c : badChars) {
            thrown.expect(IllegalArgumentException.class);
            thrown.expectMessage(error);
            Validation.validateProjectName("" + c, null);
        }
    }

    @Test
    public void numberOfOutputsSuccess() {
        final List<Artifact> artifacts =
                IntStream.rangeClosed(1, Validation.MAX_ARTIFACTS)
                .mapToObj(artifact -> new Artifact()
                )
                .collect(Collectors.toList())
                ;

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
        final String error = "Plugin is not setup properly, you may be missing fields in the configuration\n" +
                "Credentials are not valid";

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
        final String error = "Plugin is not setup properly, you may be missing fields in the configuration";
        final String regionError = "The Region is not set to a valid region";

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
        final String error = "Plugin is not setup properly, you may be missing fields in the configuration\n";

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
        final String error = "Plugin is not setup properly, you may be missing fields in the configuration\n";

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
        final String error = "Plugin is not setup properly, you may be missing fields in the configuration\n";

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
        thrown.expectMessage("Plugin is not setup properly, you may be missing fields in the configuration");
        thrown.expectMessage("The Region is not set to a valid region");
        thrown.expectMessage("Credentials are not valid");

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
