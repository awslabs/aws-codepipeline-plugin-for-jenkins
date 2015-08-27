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

import org.apache.commons.lang.Validate;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.codepipeline.AWSCodePipelineClient;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

public class AWSClients {

    private final AWSCodePipelineClient codePipelineClient;
    private final ClientConfiguration   clientCfg;
    private final Region                region;

    public AWSClients(
            final Region region,
            final AWSCredentials credentials,
            final String proxyHost,
            final int proxyPort) {

        this.region = region;
        clientCfg = new ClientConfiguration();

        if (proxyHost != null && proxyPort > 0) {
            clientCfg.setProxyHost(proxyHost);
            clientCfg.setProxyPort(proxyPort);
        }

        if (credentials == null) {
            this.codePipelineClient = new AWSCodePipelineClient(clientCfg);
        }
        else {
            this.codePipelineClient = new AWSCodePipelineClient(credentials, clientCfg);
        }

        if (region == null) {
            this.codePipelineClient.setRegion(Region.getRegion(Regions.US_EAST_1));
        }
        else {
            this.codePipelineClient.setRegion(region);
        }
    }

    public static AWSClients fromDefaultCredentialChain(
            final Region region,
            final String proxyHost,
            final int proxyPort) {
        return new AWSClients(region, null, proxyHost, proxyPort);
    }

    public static AWSClients fromBasicCredentials(
            final Region region,
            final String awsAccessKey,
            final String awsSecretKey,
            final String proxyHost,
            final int proxyPort) {
        return new AWSClients(region, new BasicAWSCredentials(awsAccessKey, awsSecretKey), proxyHost, proxyPort);
    }

    public AmazonS3 getS3Client(final AWSSessionCredentials sessionCredentials) {
        Validate.notNull(sessionCredentials);
        Validate.notNull(region);

        final AmazonS3Client client = new AmazonS3Client(sessionCredentials,
                clientCfg.withSignerOverride("AWSS3V4SignerType"));
        client.setRegion(region);
        return client;
    }

    public AWSCodePipelineClient getCodePipelineClient() {
        return codePipelineClient;
    }

}