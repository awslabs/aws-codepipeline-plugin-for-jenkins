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

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.codepipeline.AmazonCodePipelineClient;
import com.amazonaws.services.s3.AmazonS3Client;

public class AWSClients {
    private static CodePipelineStateModel    model;
    private final  AmazonCodePipelineClient  codePipelineClient;
    private final  ClientConfiguration       clientCfg;

    public AWSClients(
            final String region,
            final AWSCredentials credentials,
            final String proxyHost,
            final int proxyPort) {
        final CodePipelineStateService service = new CodePipelineStateService();

        model       = service.getModel();
        clientCfg   = new ClientConfiguration();

        if (proxyHost != null && proxyPort > 0) {
            clientCfg.setProxyHost(proxyHost);
            clientCfg.setProxyPort(proxyPort);
        }

        if (credentials == null) {
            this.codePipelineClient = new AmazonCodePipelineClient(clientCfg);
        }
        else {
            this.codePipelineClient = new AmazonCodePipelineClient(credentials, clientCfg);
        }

        if (region == null) {
            this.codePipelineClient.setRegion(Region.getRegion(Regions.US_EAST_1));
        }
        else {
            this.codePipelineClient.setRegion(Region.getRegion(Regions.fromName(region)));
        }
    }

    public static AWSClients fromDefaultCredentialChain(
            final String region,
            final String proxyHost,
            final int proxyPort) {
        return new AWSClients(region, null, proxyHost, proxyPort);
    }

    public static AWSClients fromBasicCredentials(
            final String region,
            final String awsAccessKey,
            final String awsSecretKey,
            final String proxyHost,
            final int proxyPort) {
        return new AWSClients(region, new BasicAWSCredentials(awsAccessKey, awsSecretKey), proxyHost, proxyPort);
    }

    public AmazonS3Client getS3Client(final AWSSessionCredentials sessionCredentials) {
        AmazonS3Client client = null;

        if (sessionCredentials != null
                && model != null
                && model.getRegion() != null
                && !model.getRegion().isEmpty()) {
            client = new AmazonS3Client(sessionCredentials, clientCfg.withSignerOverride("AWSS3V4SignerType"));
            final Regions regions = Regions.fromName(model.getRegion());
            client.configureRegion(regions);
        }

        return client;
    }

    public AmazonCodePipelineClient getCodePipelineClient() {
        return codePipelineClient;
    }
}
