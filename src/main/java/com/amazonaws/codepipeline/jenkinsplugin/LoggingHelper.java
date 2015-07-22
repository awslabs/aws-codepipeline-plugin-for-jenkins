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

public final class LoggingHelper {

    private LoggingHelper() { }

    private static void logConsolePrint(final String message, final Object... params) {
        System.out.println(String.format(message, params));
    }

    public static void log(final TaskListener listener, final String message, final Object... params) {
        final String fullMessage = "[AWS CodePipeline Plugin] " + String.format(message, params);

        if (listener != null) {
            listener.getLogger().println(fullMessage);
        }
        else {
            logConsolePrint(fullMessage);
        }
    }

    public static void log(final TaskListener listener, final Exception ex) {
        if (listener != null) {
            log(listener, "Stacktrace:");
            for (final StackTraceElement trace : ex.getStackTrace()) {
                log(listener, trace.toString());
            }
            log(listener, "\n");
        }
    }
}
