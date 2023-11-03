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

import java.util.Objects;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.codepipeline.AWSCodePipeline;
import com.amazonaws.services.codepipeline.AWSCodePipelineClient;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

public class AWSClients {

    private final AWSCodePipeline codePipelineClient;
    private final ClientConfiguration clientCfg;
    private final String region;

    private final S3ClientFactory s3ClientFactory;

    public AWSClients(
            final String region,
            final AWSCredentials credentials,
            final String proxyHost,
            final int proxyPort,
            final String pluginUserAgentPrefix,
            final CodePipelineClientFactory codePipelineClientFactory,
            final S3ClientFactory s3ClientFactory) {

        if (region == null) {
            this.region = Region.getRegion(Regions.US_EAST_1).getName();
        } else {
            this.region = region;
        }
        this.clientCfg = new ClientConfiguration().withUserAgentPrefix(pluginUserAgentPrefix);

        if (proxyHost != null && proxyPort > 0) {
            clientCfg.setProxyHost(proxyHost);
            clientCfg.setProxyPort(proxyPort);
        }

        this.codePipelineClient = codePipelineClientFactory.getAWSCodePipelineClient(credentials, clientCfg);
        this.codePipelineClient.setEndpoint("https://codepipeline." + this.region + getAwsClientSuffix(this.region));

        this.s3ClientFactory = s3ClientFactory;
    }

    public static AWSClients fromDefaultCredentialChain(
            final String region,
            final String proxyHost,
            final int proxyPort,
            final String pluginUserAgentPrefix) {

        return new AWSClients(region, null, proxyHost, proxyPort, pluginUserAgentPrefix, new CodePipelineClientFactory(), new S3ClientFactory());
    }

    public static AWSClients fromBasicCredentials(
            final String region,
            final String awsAccessKey,
            final String awsSecretKey,
            final String proxyHost,
            final int proxyPort,
            final String pluginUserAgentPrefix) {

        return new AWSClients(
                region,
                new BasicAWSCredentials(awsAccessKey, awsSecretKey),
                proxyHost,
                proxyPort,
                pluginUserAgentPrefix,
                new CodePipelineClientFactory(),
                new S3ClientFactory());
    }

    public AmazonS3 getS3Client(final AWSCredentialsProvider credentialsProvider) {
        Objects.requireNonNull(credentialsProvider, "credentialsProvider must not be null");
        Objects.requireNonNull(region, "region must not be null");

        final AmazonS3 client = s3ClientFactory.getS3Client(credentialsProvider, new ClientConfiguration(clientCfg).withSignerOverride("AWSS3V4SignerType"));
        client.setEndpoint("https://s3." + this.region + getAwsClientSuffix(this.region));

        return client;
    }

    public AWSCodePipeline getCodePipelineClient() {
        return codePipelineClient;
    }

    public static class CodePipelineClientFactory {

        public AWSCodePipeline getAWSCodePipelineClient(final AWSCredentials credentials, final ClientConfiguration clientCfg) {
            if (credentials == null) {
                return new AWSCodePipelineClient(clientCfg);
            } else {
                return new AWSCodePipelineClient(credentials, clientCfg);
            }
        }

    }

    public static class S3ClientFactory {

        public AmazonS3 getS3Client(final AWSCredentialsProvider credentialsProvider, final ClientConfiguration clientCfg) {
            return new AmazonS3Client(credentialsProvider, clientCfg);
        }

    }

    private String getAwsClientSuffix(String region) {
        if (region.equals(Regions.CN_NORTH_1.getName()) || region.equals(Regions.CN_NORTHWEST_1.getName())) {
            return ".amazonaws.com.cn";
        } else {
            return ".amazonaws.com";
        }
    }

}
