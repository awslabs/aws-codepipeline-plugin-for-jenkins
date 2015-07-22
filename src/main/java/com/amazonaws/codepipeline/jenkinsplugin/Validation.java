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

import hudson.model.TaskListener;
import java.util.List;
import static org.apache.commons.lang.StringEscapeUtils.escapeHtml;
import static org.apache.commons.lang.StringEscapeUtils.escapeJava;
import static org.apache.commons.lang.StringEscapeUtils.escapeSql;

public class Validation {
    public static String sanitize(final String string) {
        String sanitized = string;
        sanitized = escapeHtml(sanitized);
        sanitized = escapeJava(sanitized);
        sanitized = escapeSql(sanitized);

        return sanitized;
    }

    // The Validations here are CodePipeline specific
    public static void validateProjectName(
            final String projectName,
            final TaskListener listener)
            throws IllegalArgumentException {
        final int MAX_LENGTH = 20;

        if (projectName.length() > MAX_LENGTH) {
            final String error = "Project Name is too long, AWSCodePipeline Project Names must be less than "
                    + MAX_LENGTH + " characters, you entered " + projectName.length() + " characters";
            LoggingHelper.log(listener, error);
            throw new IllegalArgumentException(error);
        }

        for (final Character c : projectName.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                final String error = "Project Name on AWSCodePipeline's must only contain Alphanumeric characters and '-' or '_'";
                LoggingHelper.log(listener, error);

                throw new IllegalArgumentException(error);
            }
        }
    }

    public static void numberOfOutPutsIsValid(final List<?> artifacts){
        final int maxArtifacts = 5;

        if (artifacts.size() > maxArtifacts) {
            // TODO: pull from client number of artifacts allowed
            throw new IllegalArgumentException("The maximum number of output artifacts allowed is " + maxArtifacts);
        }
    }

    public static void validatePlugin(
            final String actionTypeCategory,
            final String actionTypeProvider,
            final String actionTypeVersion,
            final String projectName,
            final CodePipelineStateModel model,
            final TaskListener taskListener) {
        boolean canThrow = false;

        if (model == null) {
            final String error = "The client was not saved correctly";
            throw new hudson.model.Failure(error);
        }

        String allErrors = "\nPlugin is not setup properly, you may be missing fields in the " +
                "configuration";

        if (!actionTypeIsValid(actionTypeCategory, actionTypeProvider, actionTypeVersion)) {
            final String error = "ActionType: " +
                    "Category: " + actionTypeCategory +
                    ", Provider: " + actionTypeProvider +
                    ", Version: " + actionTypeVersion;

            LoggingHelper.log(taskListener, error);
            allErrors += "\n" + error;
            canThrow = true;
        }

        if (!credentialsAreValid(model)) {
            final String error = "Credentials are not valid";
            allErrors += "\n" + error;
            canThrow = true;
        }

        if (!regionIsValid(model)) {
            final String error = "The Region is not set to a valid region";
            allErrors += "\n" + error;
            canThrow = true;
        }

        if (!projectNameIsValid(projectName)) {
            final String error = "Project Name is not valid, Project Name: " + projectName;
            allErrors += "\n" + error;

            LoggingHelper.log(taskListener, error);
            canThrow = true;
        }

        if (!model.areAllPluginsInstalled()) {
            final String error = "You are missing the AWS CodePipeline Publisher Post-Build Step, " +
                    "make sure you add that to your Post-Build Actions";

            LoggingHelper.log(taskListener, error);
            allErrors += "\n" + error + "\n";
            canThrow = true;
        }

        if (canThrow) {
            throw new hudson.model.Failure(allErrors);
        }
    }

    private static boolean credentialsAreValid(final CodePipelineStateModel model) {
        return
                model.getAwsAccessKey() != null &&
                model.getAwsSecretKey() != null &&
                ((model.getAwsAccessKey().isEmpty() && model.getAwsSecretKey().isEmpty()) ||
                    (!model.getAwsAccessKey().isEmpty() && !model.getAwsSecretKey().isEmpty()));
    }

    private static boolean regionIsValid(final CodePipelineStateModel model) {
        return model.getRegion() != null
                && !model.getRegion().isEmpty();
    }

    private static boolean actionTypeIsValid(
            final String actionTypeCategory,
            final String actionTypeProvider,
            final String actionTypeVersion) {
        return  actionTypeCategory != null && !actionTypeCategory.isEmpty() &&
                (actionTypeCategory.equals("Build") || actionTypeCategory.equals("Test")) &&
                actionTypeProvider != null && !actionTypeProvider.isEmpty() &&
                actionTypeVersion != null && !actionTypeVersion.isEmpty();
    }

    private static boolean projectNameIsValid(final String projectName) {
        return projectName != null && !projectName.isEmpty();
    }
}
