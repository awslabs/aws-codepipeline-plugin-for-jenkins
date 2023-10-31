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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.codepipeline.AWSCodePipeline;
import com.amazonaws.services.s3.AmazonS3;

public class AWSClientsTest {

    private static final String PROXY_HOST = "sampleproxy.com";
    private static final int PROXY_PORT = 22000;
    private static final String PLUGIN_VERSION = "SomeRandomVersion";

    private final AWSClients.CodePipelineClientFactory codePipelineClientFactory = mock(AWSClients.CodePipelineClientFactory.class);
    private final AWSClients.S3ClientFactory s3ClientFactory = mock(AWSClients.S3ClientFactory.class);

    private final AWSCodePipeline expectedCodePipelineClient;
    private final AmazonS3 expectedS3Client;

    public AWSClientsTest() {
        expectedCodePipelineClient = mock(AWSCodePipeline.class);
        when(codePipelineClientFactory.getAWSCodePipelineClient(any(AWSCredentials.class), any(ClientConfiguration.class))).thenReturn(expectedCodePipelineClient);
        expectedS3Client = mock(AmazonS3.class);
        when(s3ClientFactory.getS3Client(any(AWSCredentialsProvider.class), any(ClientConfiguration.class))).thenReturn(expectedS3Client);
    }

    @Test
    public void createsCodePipelineClientUsingProxyHostAndPort() {
        // when
        final AWSClients awsClients = new AWSClients(Region.getRegion(Regions.US_WEST_2), mock(AWSCredentials.class), PROXY_HOST, PROXY_PORT, PLUGIN_VERSION, codePipelineClientFactory, s3ClientFactory);
        final AWSCodePipeline codePipelineClient = awsClients.getCodePipelineClient();

        // then
        assertEquals(expectedCodePipelineClient, codePipelineClient);
        final ArgumentCaptor<ClientConfiguration> clientConfigurationCaptor = ArgumentCaptor.forClass(ClientConfiguration.class);
        verify(codePipelineClientFactory).getAWSCodePipelineClient(any(AWSCredentials.class), clientConfigurationCaptor.capture());
        final ClientConfiguration clientConfiguration = clientConfigurationCaptor.getValue();
        assertEquals(PROXY_HOST, clientConfiguration.getProxyHost());
        assertEquals(PROXY_PORT, clientConfiguration.getProxyPort());
        verify(codePipelineClient).setRegion(Region.getRegion(Regions.US_WEST_2));
    }

    @Test
    public void createsS3ClientUsingProxyHostAndPort() {
        // when
        final AWSClients awsClients = new AWSClients(Region.getRegion(Regions.US_WEST_2), mock(AWSCredentials.class), PROXY_HOST, PROXY_PORT, PLUGIN_VERSION, codePipelineClientFactory, s3ClientFactory);
        final AmazonS3 s3Client = awsClients.getS3Client(mock(AWSCredentialsProvider.class));

        // then
        assertEquals(expectedS3Client, s3Client);
        final ArgumentCaptor<ClientConfiguration> clientConfigurationCaptor = ArgumentCaptor.forClass(ClientConfiguration.class);
        verify(s3ClientFactory).getS3Client(any(AWSCredentialsProvider.class), clientConfigurationCaptor.capture());
        final ClientConfiguration clientConfiguration = clientConfigurationCaptor.getValue();
        assertEquals(PROXY_HOST, clientConfiguration.getProxyHost());
        assertEquals(PROXY_PORT, clientConfiguration.getProxyPort());
        verify(s3Client).setRegion(Region.getRegion(Regions.US_WEST_2));
    }

    @Test
    public void usesUsEast1AsDefaultRegion() {
        // when
        final AWSClients awsClients = new AWSClients(null, mock(AWSCredentials.class), PROXY_HOST, PROXY_PORT, PLUGIN_VERSION, codePipelineClientFactory, s3ClientFactory);
        final AWSCodePipeline codePipelineClient = awsClients.getCodePipelineClient();
        final AmazonS3 s3Client = awsClients.getS3Client(mock(AWSCredentialsProvider.class));

        // then
        verify(codePipelineClient).setRegion(Region.getRegion(Regions.US_EAST_1));
        verify(s3Client).setRegion(Region.getRegion(Regions.US_EAST_1));

    }

}
