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

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Random;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CategoryType;
import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CompressionType;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.codepipeline.AWSCodePipeline;
import com.amazonaws.services.codepipeline.model.AcknowledgeJobRequest;
import com.amazonaws.services.codepipeline.model.AcknowledgeJobResult;
import com.amazonaws.services.codepipeline.model.ActionOwner;
import com.amazonaws.services.codepipeline.model.ActionTypeId;
import com.amazonaws.services.codepipeline.model.InvalidNonceException;
import com.amazonaws.services.codepipeline.model.Job;
import com.amazonaws.services.codepipeline.model.JobStatus;
import com.amazonaws.services.codepipeline.model.PollForJobsRequest;
import com.amazonaws.services.codepipeline.model.PollForJobsResult;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.NullChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import net.sf.json.JSONObject;

public class AWSCodePipelineSCM extends hudson.scm.SCM {

    public static final Regions[] AVAILABLE_REGIONS = {
        Regions.US_EAST_2,
        Regions.US_EAST_1,
        Regions.US_WEST_1,
        Regions.US_WEST_2,
        Regions.AP_SOUTH_1,
        Regions.AP_NORTHEAST_2,
        Regions.AP_SOUTHEAST_1,
        Regions.AP_SOUTHEAST_2,
        Regions.AP_NORTHEAST_1,
        Regions.CA_CENTRAL_1,
        Regions.EU_CENTRAL_1,
        Regions.EU_WEST_1,
        Regions.EU_WEST_2,
        Regions.EU_WEST_3,
        Regions.EU_NORTH_1,
        Regions.SA_EAST_1,
        Regions.GovCloud,
        Regions.AP_EAST_1,
        Regions.EU_SOUTH_1
    };

    public static final CategoryType[] ACTION_TYPE = {
        CategoryType.PleaseChooseACategory,
        CategoryType.Build,
        CategoryType.Test
    };

    private static final Random RANDOM = new Random();

    private Job job;
    private final boolean clearWorkspace;
    //keeping this to avoid "data stored in an older format" jenkins warning
    private final String projectName;
    private final String actionTypeCategory;
    private final String actionTypeProvider;
    private final String actionTypeVersion;

    private final String region;
    private final String awsAccessKey;
    private final Secret awsSecretKey;
    private final String proxyHost;
    private final int proxyPort;

    private final AWSClientFactory awsClientFactory;

    @DataBoundConstructor
    public AWSCodePipelineSCM(
            final String name,
            final boolean clearWorkspace,
            final String region,
            final String awsAccessKey,
            final String awsSecretKey,
            final String proxyHost,
            final String proxyPort,
            final String category,
            final String provider,
            final String version) {

        this(name, clearWorkspace, region, awsAccessKey, awsSecretKey, proxyHost, proxyPort,
                category, provider, version, new AWSClientFactory());
    }

    public AWSCodePipelineSCM(
            final String projectName,
            final boolean clear,
            final String region,
            final String awsAccessKey,
            final String awsSecretKey,
            final String proxyHost,
            final String proxyPort,
            final String category,
            final String provider,
            final String version,
            final AWSClientFactory awsClientFactory) {

        clearWorkspace        = clear;
        this.region           = Validation.sanitize(region.trim());
        this.awsAccessKey     = Validation.sanitize(awsAccessKey.trim());
        this.awsSecretKey     = Secret.fromString(Validation.sanitize(awsSecretKey.trim()));
        this.proxyHost        = Validation.sanitize(proxyHost.trim());
        this.projectName      = null;
        actionTypeCategory    = Validation.sanitize(category.trim());
        actionTypeProvider    = Validation.sanitize(provider.trim());
        actionTypeVersion     = Validation.sanitize(version.trim());
        this.awsClientFactory = awsClientFactory;

        if (proxyPort != null && !proxyPort.isEmpty()) {
            this.proxyPort = Integer.parseInt(proxyPort);
        }
        else {
            this.proxyPort = 0;
        }
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new NullChangeLogParser();
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        return false;
    }

    // AWS CodePipeline PollForJobs wrapper
    @Override
    protected PollingResult compareRemoteRevisionWith(
            final AbstractProject<?, ?> project,
            final Launcher launcher,
            final FilePath filePath,
            final TaskListener listener,
            final SCMRevisionState revisionState)
            throws IOException, InterruptedException {

        final ActionTypeId actionTypeId = new ActionTypeId()
                .withCategory(actionTypeCategory)
                .withOwner(ActionOwner.Custom)
                .withProvider(actionTypeProvider)
                .withVersion(actionTypeVersion);

        final String projectName = Validation.sanitize(project.getName().trim());

        LoggingHelper.log(listener, "Polling for jobs for action type id: ["
                + "Owner: %s, Category: %s, Provider: %s, Version: %s, ProjectName: %s]",
                actionTypeId.getOwner(),
                actionTypeId.getCategory(),
                actionTypeId.getProvider(),
                actionTypeId.getVersion(),
                project.getName());

        return pollForJobs(projectName, actionTypeId, listener);
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(
            final AbstractBuild<?, ?> build,
            final Launcher launcher,
            final TaskListener taskListener)
            throws IOException, InterruptedException {
        return null;
    }

    @Override
    public boolean checkout(
            final AbstractBuild<?, ?> abstractBuild,
            final Launcher launcher,
            final FilePath workspacePath,
            final BuildListener listener,
            final File changeLogFile)
            throws IOException, InterruptedException {

        initializeModel();
        final CodePipelineStateModel model = CodePipelineStateService.getModel();

        if (model.getJob() == null) {
            // This is here for if a customer presses BuildNow, it will still attempt a build.
            return true;
        }
        LoggingHelper.log(listener, "Job '%s' received", model.getJob().getId());

        try {
            final AcknowledgeJobResult acknowledgeJobResult = getCodePipelineClient().acknowledgeJob(new AcknowledgeJobRequest()
                    .withJobId(model.getJob().getId())
                    .withNonce(model.getJob().getNonce()));

            if (!acknowledgeJobResult.getStatus().equals(JobStatus.InProgress.name())) {
                model.setSkipPutJobResult(true);
                throw new AbortException(String.format("Failed to acknowledge job with ID: %s", job.getId()));
            }
        } catch (final InvalidNonceException e) {
            model.setSkipPutJobResult(true);
            throw new AbortException(String.format("Job with ID %s was already acknowledged", job.getId()));
        }

        LoggingHelper.log(listener, "Acknowledged job with ID: %s", model.getJob().getId());

        workspacePath.act(new DownloadCallable(
                    clearWorkspace,
                    model.getJob(),
                    model,
                    awsClientFactory,
                    JenkinsMetadata.getPluginUserAgentPrefix(),
                    listener));

        return true;
    }

    public PollingResult pollForJobs(final String projectName, final ActionTypeId actionType, final TaskListener taskListener) throws InterruptedException {
        validate(projectName, taskListener);

        // Wait a bit before polling, so not all Jenkins jobs poll at the same time
        final long jitter = (long) RANDOM.nextInt(55 * 1000);
        Thread.sleep(jitter);

        final PollForJobsResult result = getCodePipelineClient().pollForJobs(new PollForJobsRequest()
                .withActionTypeId(actionType)
                .withMaxBatchSize(1)
                .withQueryParam(Collections.singletonMap("ProjectName", projectName)));

        if (result.getJobs().size() < 1) {
            LoggingHelper.log(taskListener, "No jobs found.");
            return PollingResult.NO_CHANGES;
        }

        job = result.getJobs().get(0);
        LoggingHelper.log(taskListener, "Received job with ID: %s", job.getId());

        return PollingResult.BUILD_NOW;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    public boolean isClearWorkspace() {
        return clearWorkspace;
    }

    public String getAwsAccessKey() {
        return awsAccessKey;
    }

    public Secret getAwsSecretKey() {
        if (StringUtils.isEmpty(Secret.toString(awsSecretKey))) {
            return null;
        }
        return awsSecretKey;
    }

    public String getRegion() {
        return region;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public String getCategory() {
        return actionTypeCategory;
    }

    public String getProvider() {
        return actionTypeProvider;
    }

    public String getVersion() {
        return actionTypeVersion;
    }

    // this is required so we can use JenkinsRule.assertEqualDataBoundBeans in unit tests
    public String getName() {
        return null;
    }

    public void initializeModel() {
        final CodePipelineStateModel model = new CodePipelineStateModel();
        model.setActionTypeCategory(actionTypeCategory);
        model.setAwsAccessKey(awsAccessKey);
        model.setAwsSecretKey(Secret.toString(awsSecretKey));
        model.setCompressionType(CompressionType.None);
        model.setJob(job);
        model.setProxyHost(proxyHost);
        model.setProxyPort(proxyPort);
        model.setRegion(region);
        CodePipelineStateService.setModel(model);
    }

    private void validate(final String projectName, final TaskListener listener) {
        Validation.validatePlugin(
                awsAccessKey,
                Secret.toString(awsSecretKey),
                region,
                actionTypeCategory,
                actionTypeProvider,
                actionTypeVersion,
                projectName,
                listener);
    }

    private AWSCodePipeline getCodePipelineClient() {
        return awsClientFactory.getAwsClient(
                awsAccessKey,
                Secret.toString(awsSecretKey),
                proxyHost,
                proxyPort,
                region,
                JenkinsMetadata.getPluginUserAgentPrefix())
                .getCodePipelineClient();
    }

    /**
     * Descriptor for {@link AWSCodePipelineSCM}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    @Extension
    public static final class DescriptorImpl extends SCMDescriptor<AWSCodePipelineSCM> {
        public DescriptorImpl() {
            super(AWSCodePipelineSCM.class, null);
            load();
        }

        public DescriptorImpl(final boolean shouldLoad) {
            super(AWSCodePipelineSCM.class, null);
            if (shouldLoad) {
                load();
            }
        }

        @Override
        public String getDisplayName() {
            return "AWS CodePipeline";
        }

        @Override
        public SCM newInstance(final StaplerRequest req,
                               final JSONObject formData) throws FormException {
            return new AWSCodePipelineSCM(
                    req.getParameter("name"),
                    req.getParameter("clearWorkspace") != null,
                    req.getParameter("region"),
                    req.getParameter("awsAccessKey"),
                    req.getParameter("awsSecretKey"),
                    req.getParameter("proxyHost"),
                    req.getParameter("proxyPort"),
                    req.getParameter("category"),
                    req.getParameter("provider"),
                    req.getParameter("version"),
                    new AWSClientFactory());
        }

        @Override
        public boolean configure(final StaplerRequest req,
                                 final JSONObject formData) throws FormException {
            return true;
        }

        public ListBoxModel doFillRegionItems() {
            final ListBoxModel items = new ListBoxModel();

            for (final Regions region : AVAILABLE_REGIONS) {
                items.add(region.getDescription() + " " + region.getName(), region.getName());
            }

            return items;
        }

        public ListBoxModel doFillCategoryItems() {
            final ListBoxModel items = new ListBoxModel();

            for (final CategoryType action : ACTION_TYPE) {
                items.add(action.toString(), action.name());
            }

            return items;
        }

        public FormValidation doCheckCategory(@QueryParameter final String value) {
            if (value == null ||
                    value.equalsIgnoreCase("Please Choose A Category") ||
                    value.equalsIgnoreCase("PleaseChooseACategory")) {
                return FormValidation.error("Please select a Category Type");
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckVersion(@QueryParameter final String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.error("Please enter a Version");
            }

            if (value.length() > Validation.MAX_VERSION_LENGTH) {
                return FormValidation.error(
                        String.format("Version can only be %d characters in length, you entered %d",
                                Validation.MAX_VERSION_LENGTH,
                                value.length()));
            }

            return validateIntIsInRange(value, 0, Integer.MAX_VALUE, "Version",
                    "Version must be greater than or equal to 0");
        }

        public FormValidation doCheckProvider(@QueryParameter final String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.error("Please enter a Provider, typically \"Jenkins\" or your Project Name");
            }

            if (value.length() > Validation.MAX_PROVIDER_LENGTH) {
                return FormValidation.error(
                        String.format(
                                "The Provider name is too long, the name should be %d characters, you entered %d characters",
                                Validation.MAX_PROVIDER_LENGTH,
                                value.length()));
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckProxyPort(@QueryParameter final String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.ok();
            }

            return validateIntIsInRange(value, 0, 65535, "Proxy Port",
                    "Proxy Port must be between 0 and 65535");
        }

        private FormValidation validateIntIsInRange(
                final String value,
                final int lowerBound,
                final int upperBound,
                final String propertyName,
                final String errorMessage) {

            try {
                final int intValue = Integer.parseInt(value);

                if (intValue < lowerBound || intValue > upperBound) {
                    return FormValidation.error(errorMessage);
                }

                return FormValidation.ok();
            }
            catch (final NumberFormatException ex) {
                return FormValidation.error(propertyName + " must be a number");
            }
        }
    }

}
