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
            final TaskListener listener) throws
            IllegalArgumentException {
        final LoggingHelper logHelper = new LoggingHelper();
        final int MAX_LENGTH = 20;

        if (projectName.length() > MAX_LENGTH) {
            final String error = "Project Name is too long, AWSCodePipeline Project Names must be less than "
                    + MAX_LENGTH + " characters, you entered " + projectName.length() + " characters";
            logHelper.log(listener, error);
            throw new IllegalArgumentException(error);
        }

        for (final Character c : projectName.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                final String error = "Project Name on AWSCodePipeline's must only contain Alphanumeric characters and '-' or '_'";
                logHelper.log(listener, error);
                throw new IllegalArgumentException(error);
            }
        }
    }

    public static boolean actionTypeIsValid(
            final String actionTypeCategory,
            final String actionTypeProvider,
            final String actionTypeVersion) {
        return  actionTypeCategory != null && !actionTypeCategory.isEmpty() &&
                (actionTypeCategory.equals("Build") || actionTypeCategory.equals("Test")) &&
                actionTypeProvider != null && !actionTypeProvider.isEmpty() &&
                actionTypeVersion != null && !actionTypeVersion.isEmpty();
    }

    public static boolean projectNameIsValid(final String projectName) {
        return projectName != null && !projectName.isEmpty();
    }

    public static void validatePlugin(
            final String actionTypeCategory,
            final String actionTypeProvider,
            final String actionTypeVersion,
            final String projectName,
            final TaskListener taskListener) {
        final CodePipelineStateModel model = new CodePipelineStateService().getModel();
        final LoggingHelper logHelper = new LoggingHelper();

        boolean canThrow = false;

        if (model != null) {
            String allErrors = "\nPlugin is not setup properly, you may be missing fields in the " +
                    "configuration";

            if (!actionTypeIsValid(actionTypeCategory, actionTypeProvider, actionTypeVersion)) {
                final String error = "ActionType: " +
                        "Category: " + actionTypeCategory +
                        ", Provider: " + actionTypeProvider +
                        ", Version: " + actionTypeVersion;
                logHelper.log(taskListener, error);
                allErrors += "\n" + error;
                canThrow = true;
            }

            if (isValidConfiguration()) {
                final String error = "Credentials are not valid";
                allErrors += "\n" + error;
                canThrow = true;
            }

            if (!projectNameIsValid(projectName)) {
                final String error = "Project Name is not valid, Project Name: " + projectName;
                logHelper.log(taskListener, error);
                allErrors += "\n" + error;
                canThrow = true;
            }

            if (!model.areAllPluginsInstalled()) {
                final String error = "You are missing the AWS CodePipeline Publisher Post-Build Step, " +
                        "make sure you add that to your Post-Build Actions";
                logHelper.log(taskListener, error);

                allErrors += "\n" + error + "\n";
                canThrow = true;
            }

            if (canThrow) {
                throw new hudson.model.Failure(allErrors);
            }
        }
    }

    public static boolean isValidConfiguration() {
        final CodePipelineStateModel model = new CodePipelineStateService().getModel();

        final boolean credentialsAreValid = model.getAwsAccessKey() != null
                && model.getAwsSecretKey() != null
                && ((model.getAwsAccessKey().isEmpty() && model.getAwsSecretKey().isEmpty())
                    || (!model.getAwsAccessKey().isEmpty() && !model.getAwsSecretKey().isEmpty()));

        final boolean modelIsValid  = model.getRegion() != null
                && !model.getRegion().isEmpty();

        return modelIsValid && credentialsAreValid;
    }
}
