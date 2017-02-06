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

import static org.apache.commons.lang.StringEscapeUtils.escapeHtml;
import static org.apache.commons.lang.StringEscapeUtils.escapeSql;

import java.util.List;

import hudson.model.TaskListener;

public class Validation {
    private static final String  LINE_SEPARATOR = System.lineSeparator();

    // These come from AWS CodePipeline specifications
    public static final int MAX_VERSION_LENGTH = 9;
    public static final int MAX_PROVIDER_LENGTH = 25;
    public static final int MAX_PROJECT_NAME_LENGTH = 50;
    public static final int MAX_ARTIFACTS = 5;

    public static String sanitize(final String string) {
        return escapeSql(escapeHtml(string));
    }

    // The Validations here are CodePipeline specific
    public static void validateProjectName(
            final String projectName,
            final TaskListener listener) throws IllegalArgumentException {

        if (projectName.length() > MAX_PROJECT_NAME_LENGTH) {
            final String error = "Invalid project name: " + projectName + ". The AWS CodePipeline Jenkins plugin supports project names with a maximum of " + MAX_PROJECT_NAME_LENGTH + " characters.";
            LoggingHelper.log(listener, error);
            throw new IllegalArgumentException(error);
        }

        for (final Character c : projectName.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                final String error = "Invalid project name: " + projectName + ". The AWS CodePipeline Jenkins plugin supports project names with alphanumeric characters and the special " +
                        "characters - (minus sign) and _ (underscore).";
                LoggingHelper.log(listener, error);

                throw new IllegalArgumentException(error);
            }
        }
    }

    public static void numberOfOutPutsIsValid(final List<?> artifacts){
        if (artifacts.size() > MAX_ARTIFACTS) {
            throw new IllegalArgumentException("The maximum number of output artifacts allowed is: "
                    + MAX_ARTIFACTS + " You provided: " + artifacts.size());
        }
    }

    public static void validatePlugin(
            final String awsAccessKey,
            final String awsSecretKey,
            final String region,
            final String actionTypeCategory,
            final String actionTypeProvider,
            final String actionTypeVersion,
            final String projectName,
            final TaskListener taskListener) {
        boolean canThrow = false;

        String allErrors = LINE_SEPARATOR + "AWS CodePipeline Jenkins plugin setup error. One or more required configuration parameters have not been specified.";

        if (!actionTypeIsValid(actionTypeCategory, actionTypeProvider, actionTypeVersion)) {
            final String error = "ActionType: " +
                    "Category: " + actionTypeCategory +
                    ", Provider: " + actionTypeProvider +
                    ", Version: " + actionTypeVersion +
                    ".";

            LoggingHelper.log(taskListener, error);
            allErrors += LINE_SEPARATOR + error;
            canThrow = true;
        }

        if (!credentialsAreValid(awsAccessKey, awsSecretKey)) {
            final String error = "The AWS credentials provided are not valid.";
            allErrors += LINE_SEPARATOR + error;
            canThrow = true;
        }

        if (!regionIsValid(region)) {
            final String error = "The specified AWS region is not valid.";
            allErrors += LINE_SEPARATOR + error;
            canThrow = true;
        }

        if (!projectNameIsValid(projectName)) {
            final String error = "Invalid project name: " + projectName + ". The AWS CodePipeline Jenkins plugin supports project names with a maximum of " + MAX_PROJECT_NAME_LENGTH +
                    " characters. Allowed characters include alphanumeric characters and the special characters - (minus sign) and _ (underscore).";
            allErrors += LINE_SEPARATOR + error;

            LoggingHelper.log(taskListener, error);
            canThrow = true;
        }

        if (canThrow) {
            throw new hudson.model.Failure(allErrors);
        }
    }

    private static boolean credentialsAreValid(final String awsAccessKey, final String awsSecretKey) {
        return awsAccessKey != null
                && awsSecretKey != null
                && ((awsAccessKey.isEmpty() && awsSecretKey.isEmpty())
                        || (!awsAccessKey.isEmpty() && !awsSecretKey.isEmpty()));
    }

    private static boolean regionIsValid(final String region) {
        return region != null && !region.isEmpty();
    }

    private static boolean actionTypeIsValid(
            final String actionTypeCategory,
            final String actionTypeProvider,
            final String actionTypeVersion) {
        final boolean actionTypeNotEmpty =
                        actionTypeCategory != null && !actionTypeCategory.isEmpty() &&
                        !actionTypeCategory.equalsIgnoreCase("Please Choose A Category") &&
                        actionTypeProvider != null && !actionTypeProvider.isEmpty() &&
                        actionTypeVersion != null && !actionTypeVersion.isEmpty();

        return actionTypeNotEmpty
                && actionTypeProvider.length() <= MAX_PROVIDER_LENGTH
                && actionTypeVersion.length() <= MAX_VERSION_LENGTH;
    }

    private static boolean projectNameIsValid(final String projectName) {
        return projectName != null
                && !projectName.isEmpty()
                && projectName.length() <= MAX_PROJECT_NAME_LENGTH;
    }

}
