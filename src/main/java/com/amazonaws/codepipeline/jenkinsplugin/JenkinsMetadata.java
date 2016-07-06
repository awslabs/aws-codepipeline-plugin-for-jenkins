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

import jenkins.model.Jenkins;

/**
 * Get information about Jenkins.
 *
 * Note: this should only be invoked from the master node.
 */
public class JenkinsMetadata {

    private JenkinsMetadata() {}

    public static String getPluginVersion() {
        final Jenkins instance = Jenkins.getInstance();
        if (instance != null) {
            final Plugin plugin = instance.getPlugin("aws-codepipeline");
            if (plugin != null) {
                return "aws-codepipeline:" + plugin.getWrapper().getVersion();
            }
        }
        return "aws-codepipeline:unknown";
    }

}
