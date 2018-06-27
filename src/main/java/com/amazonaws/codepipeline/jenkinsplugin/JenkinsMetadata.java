/*
 * Copyright 2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import hudson.Plugin;

import hudson.util.VersionNumber;
import jenkins.model.Jenkins;

/**
 * Get information about Jenkins.
 *
 * Note: this should only be invoked from the master node.
 */
public class JenkinsMetadata {
    private final static String PLUGIN = "aws-codepipeline";
    private static final String UNKNOWN = "unknown";

    private JenkinsMetadata() {}

    public static String getPluginUserAgentPrefix() {
        return String.format("%s/%s jenkins/%s",
                PLUGIN,
                getPluginVersion(),
                getJenkinsVersion());
    }

    private static String getJenkinsVersion() {
        final VersionNumber jenkinsVersion = Jenkins.getVersion();
        if (jenkinsVersion != null) {
            return jenkinsVersion.toString();
        } else {
            return UNKNOWN;
        }
    }

    private static String getPluginVersion() {
        final Jenkins instance = Jenkins.getInstance();
        if (instance != null) {
            final Plugin plugin = instance.getPlugin(PLUGIN);
            if (plugin != null) {
                return plugin.getWrapper().getVersion();
            }
        }
        return UNKNOWN;
    }
}
