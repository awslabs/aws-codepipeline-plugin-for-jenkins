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
import static org.apache.commons.lang.StringEscapeUtils.escapeJava;
import static org.apache.commons.lang.StringEscapeUtils.escapeSql;
import hudson.model.TaskListener;

import java.util.List;

public class Validation {

    // These come from AWS CodePipeline specifications
    public static final int MAX_VERSION_LENGTH = 9;
    public static final int MAX_PROVIDER_LENGTH = 25;
    public static final int MAX_PROJECT_NAME_LENGTH = 20;
    public static final int MAX_ARTIFACTS = 5;

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
        if (projectName.length() > MAX_PROJECT_NAME_LENGTH) {
            final String error = "Project Name is too long, AWSCodePipeline Project Names must be less than "
                    + MAX_PROJECT_NAME_LENGTH + " characters, you entered " + projectName.length() + " characters";
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
        if (artifacts.size() > MAX_ARTIFACTS) {
            throw new IllegalArgumentException("The maximum number of output artifacts allowed is: " + MAX_ARTIFACTS
            + " You provided: " + artifacts.size());
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

        if (!credentialsAreValid(awsAccessKey, awsSecretKey)) {
            final String error = "Credentials are not valid";
            allErrors += "\n" + error;
            canThrow = true;
        }

        if (!regionIsValid(region)) {
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

        if (canThrow) {
            throw new hudson.model.Failure(allErrors);
        }
    }

    private static boolean credentialsAreValid(final String awsAccessKey, final String awsSecretKey) {
        return awsAccessKey != null &&
                awsSecretKey != null &&
               ((awsAccessKey.isEmpty() && awsSecretKey.isEmpty()) ||
                    (!awsAccessKey.isEmpty() && !awsSecretKey.isEmpty()));
    }

    private static boolean regionIsValid(final String region) {
        return region != null
                && !region.isEmpty();
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

        return actionTypeNotEmpty &&
                actionTypeProvider.length() <= MAX_PROVIDER_LENGTH &&
                actionTypeVersion.length() <= MAX_VERSION_LENGTH;
    }

    private static boolean projectNameIsValid(final String projectName) {
        return projectName != null && !projectName.isEmpty() && projectName.length() <= MAX_PROJECT_NAME_LENGTH;
    }

}