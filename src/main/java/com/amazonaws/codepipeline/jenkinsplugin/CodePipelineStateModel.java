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

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.codepipeline.model.Artifact;

import org.apache.commons.lang.StringUtils;

import java.io.Serializable;
import java.util.List;

public final class CodePipelineStateModel implements Serializable {
    private static final long serialVersionUID = 1L;

    public final Regions[] AVAILABLE_REGIONS = { Regions.US_EAST_1 };
    public final CategoryType[]  ACTION_TYPE =
            { CategoryType.PleaseChooseACategory, CategoryType.Build, CategoryType.Test };

    private String region;
    private String proxyHost;
    private int    proxyPort;
    private String awsAccessKey;
    private String awsSecretKey;

    private String jobID;
    private CompressionType compressionType;
    private List<Artifact> outputBuildArtifacts;
    private CategoryType   actionTypeCategory;
    private boolean        allPluginsInstalled = false;

    public CodePipelineStateModel() {
        region               = null;
        proxyHost            = null;
        proxyPort            = 0;
        awsAccessKey         = null;
        awsSecretKey         = null;
        jobID                = null;
        compressionType      = CompressionType.None;
        actionTypeCategory = CategoryType.PleaseChooseACategory;
        outputBuildArtifacts = null;
        setAllPluginsInstalled(false);
    }

    public enum CompressionType {
        None,
        Zip,
        Tar,
        TarGz
    }

    public enum CategoryType {
        PleaseChooseACategory("Please Choose A Category"),
        Build("Build"),
        Test("Test");

        private final String name;

        CategoryType(final String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public static CategoryType fromName(final String name) {
            for (final CategoryType category : values()) {
                if (name.equals(category.getName())) {
                    return category;
                }
            }

            throw new IllegalArgumentException("Cannot create enum from " + name + " value!");
        }
    }

    public AWSClients getAwsClient() {
        final Region awsRegion = Region.getRegion(Regions.fromName(region));
        final AWSClients aws;

        if (StringUtils.isEmpty(awsAccessKey) && StringUtils.isEmpty(awsSecretKey)) {
            aws = AWSClients.fromDefaultCredentialChain(
                    awsRegion,
                    proxyHost,
                    proxyPort);
        }
        else {
            aws = AWSClients.fromBasicCredentials(
                    awsRegion,
                    awsAccessKey,
                    awsSecretKey,
                    proxyHost,
                    proxyPort);
        }
        return aws;
    }

    public CategoryType getActionTypeCategory() {
        return actionTypeCategory;
    }

    public void setActionTypeCategory(final String actionTypeCategory) {
        this.actionTypeCategory = CodePipelineStateModel.CategoryType.fromName(actionTypeCategory);
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(final String region) {
        this.region = trimWhitespace(region);
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(final String proxyHost) {
            this.proxyHost = trimWhitespace(proxyHost);
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(String proxyPort) {
        int portNum = 0;
        proxyPort = trimWhitespace(proxyPort);

        if (proxyPort != null && !proxyPort.isEmpty()) {
            try {
                portNum = Integer.valueOf(proxyPort);
            }
            catch (final NumberFormatException ex) {
                portNum = 0;
            }
        }

        this.proxyPort = portNum;
    }

    public String getAwsAccessKey() {
        return awsAccessKey;
    }

    public void setAwsAccessKey(final String awsAccessKey) {
        this.awsAccessKey = trimWhitespace(awsAccessKey);
    }

    public String getAwsSecretKey() {
        return awsSecretKey;
    }

    public void setAwsSecretKey(final String awsSecretKey) {
        this.awsSecretKey = trimWhitespace(awsSecretKey);
    }

    public String getJobID() {
        return jobID;
    }

    public void setJobID(final String jobID) {
        this.jobID = trimWhitespace(jobID);
    }

    public void clearJob() {
        jobID = null;
    }

    public CompressionType getCompressionType() {
        return compressionType;
    }

    public void setCompressionType(final CompressionType compressionType) {
        if (compressionType == null) {
            this.compressionType = CompressionType.None;
        }
        else {
            this.compressionType = compressionType;
        }
    }

    public List<Artifact> getOutputBuildArtifacts() {
        return outputBuildArtifacts;
    }

    public void setOutputBuildArtifacts(final List<Artifact> outputBuildArtifacts) {
        this.outputBuildArtifacts = outputBuildArtifacts;
    }

    public String trimWhitespace(final String str) {
        String output = null;

        if (str != null) {
            output = str.trim();
        }

        return output;
    }

    public boolean areAllPluginsInstalled() {
        return allPluginsInstalled;
    }

    public void setAllPluginsInstalled(final boolean allPluginsInstalled) {
        this.allPluginsInstalled = allPluginsInstalled;
    }
}
