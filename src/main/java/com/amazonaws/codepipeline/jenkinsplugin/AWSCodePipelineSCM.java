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

import com.amazonaws.regions.Regions;
import com.amazonaws.services.codepipeline.AWSCodePipelineClient;
import com.amazonaws.services.codepipeline.model.AcknowledgeJobRequest;
import com.amazonaws.services.codepipeline.model.AcknowledgeJobResult;
import com.amazonaws.services.codepipeline.model.ActionOwner;
import com.amazonaws.services.codepipeline.model.ActionTypeId;
import com.amazonaws.services.codepipeline.model.Job;
import com.amazonaws.services.codepipeline.model.JobStatus;
import com.amazonaws.services.codepipeline.model.PollForJobsRequest;
import com.amazonaws.services.codepipeline.model.PollForJobsResult;
import hudson.EnvVars;
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
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AWSCodePipelineSCM extends hudson.scm.SCM {
    private final CodePipelineStateModel   model;
    private Job                            job;
    private final boolean clearWorkspace;
    private String  projectName;
    private final String  actionTypeCategory;
    private final String  actionTypeProvider;
    private final String  actionTypeVersion;

    public AWSCodePipelineSCM(
            final boolean clear,
            final String region,
            final String awsAccessKey,
            final String awsSecretKey,
            final String proxyHost,
            final String proxyPort,
            final String category,
            final String provider,
            final String version) {
        final CodePipelineStateService service = new CodePipelineStateService();
        model          = service.getModel();
        clearWorkspace = clear;

        model.setRegion(Validation.sanitize(region));
        model.setProxyHost(Validation.sanitize(proxyHost));
        model.setProxyPort(proxyPort);
        model.setAwsAccessKey(Validation.sanitize(awsAccessKey));
        model.setAwsSecretKey(Validation.sanitize(awsSecretKey));
        actionTypeCategory = Validation.sanitize(category.trim());
        model.setActionTypeCategory(actionTypeCategory);
        actionTypeProvider = Validation.sanitize(provider.trim());
        actionTypeVersion = Validation.sanitize(version.trim());

        model.setCompressionType(CodePipelineStateModel.CompressionType.None);
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new NullChangeLogParser();
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        return false;
    }

    /*
    AWS PollForJobs Wrapper
     */
    @Override
    protected PollingResult compareRemoteRevisionWith(
            final AbstractProject<?, ?> project,
            final Launcher launcher,
            final FilePath filePath,
            final TaskListener listener,
            final SCMRevisionState revisionState)
            throws IOException, InterruptedException {
        validate(listener);

        if (model != null) {
            final ActionTypeId actionTypeId = new ActionTypeId();
            actionTypeId.setCategory(actionTypeCategory);
            actionTypeId.setOwner(ActionOwner.Custom);
            actionTypeId.setProvider(actionTypeProvider);
            actionTypeId.setVersion(actionTypeVersion);
            LoggingHelper.log(listener, "Polling for jobs for action type id: ["
                    + "Owner: %s, Category: %s, Provider: %s, Version: %s, ProjectName: %s]",
                    actionTypeId.getOwner(),
                    actionTypeId.getCategory(),
                    actionTypeId.getProvider(),
                    actionTypeId.getVersion(),
                    projectName);

            return pollForJobs(actionTypeId, listener);
        }
        else {
            final String error = "Invalid State: Category: Model was not created";
            LoggingHelper.log(listener, error);

            return PollingResult.NO_CHANGES;
        }
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(
            final AbstractBuild<?, ?> build,
            final Launcher launcher,
            final TaskListener taskListener)
            throws IOException, InterruptedException {
        getProjectName(build, taskListener);
        return null;
    }

    @Override
    public boolean checkout(
            final AbstractBuild<?, ?> abstractBuild,
            final Launcher launcher,
            final FilePath workspacePath,
            final BuildListener listener,
            final File changelogFile)
            throws IOException, InterruptedException {
        if (job == null) {
            // This is here for if a customer presses BuildNow, it will still attempt a build.
            return true;
        }

        try {
            getProjectName(abstractBuild, listener);
            validate(listener);

            LoggingHelper.log(listener, "Job '%s' received", job.getId());

            workspacePath.act(new DownloadCallable(clearWorkspace, job, model, listener));
        }
        finally {
            cleanUp();
        }

        return true;
    }

    public PollingResult pollForJobs(final ActionTypeId actionType, final TaskListener taskListener) {
        final AWSClients aws = model.getAwsClient();
        final AWSCodePipelineClient codePipelineClient = aws.getCodePipelineClient();
        final PollForJobsRequest request = new PollForJobsRequest();
        request.setActionTypeId(actionType);

        final Map<String, String> queryParam = new HashMap<>();
        queryParam.put("ProjectName", projectName);
        request.setQueryParam(queryParam);
        request.setMaxBatchSize(1);

        final PollForJobsResult result = codePipelineClient.pollForJobs(request);

        if (result.getJobs().size() == 1) {
            job = result.getJobs().get(0);
            model.setJobID(job.getId());
            model.setOutputBuildArtifacts(job.getData().getOutputArtifacts());

            LoggingHelper.log(taskListener, "Received Job request with ID: %s", model.getJobID());

            final AcknowledgeJobRequest acknowledgeJobRequest = new AcknowledgeJobRequest();
            acknowledgeJobRequest.setJobId(job.getId());
            acknowledgeJobRequest.setNonce(job.getNonce());

            final AcknowledgeJobResult acknowledgeJobResult = codePipelineClient.acknowledgeJob(acknowledgeJobRequest);

            if (acknowledgeJobResult.getStatus().equals(JobStatus.InProgress.name())) {
                LoggingHelper.log(taskListener, "Job Acknowledged with ID: %s", acknowledgeJobRequest.getJobId());

                model.setJobID(job.getId());

                return PollingResult.BUILD_NOW;
            }
        }

        LoggingHelper.log(taskListener, "No jobs found.");

        return PollingResult.NO_CHANGES;
    }

    private void cleanUp() {
        job = null;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    public boolean isClearWorkspace() {
        return clearWorkspace;
    }

    public String getAwsAccessKey() {
        return model.getAwsAccessKey();
    }

    public String getAwsSecretKey() {
        return model.getAwsSecretKey();
    }

    public String getRegion() {
        return model.getRegion();
    }

    public String getProxyHost() {
        return model.getProxyHost();
    }

    public int getProxyPort() {
        return model.getProxyPort();
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

    public void getProjectName(final AbstractBuild<?, ?> build, final TaskListener listener)
            throws IOException, InterruptedException {
        // We need to get the project name from the Environment Variables
        if (projectName == null || projectName.isEmpty()) {
            final EnvVars envVars = build.getEnvironment(listener);
            projectName = envVars.get("JOB_NAME");
        }
    }

    private void validate(final TaskListener listener) {
        Validation.validatePlugin(
                actionTypeCategory,
                actionTypeProvider,
                actionTypeVersion,
                projectName,
                model,
                listener);
    }

    /**
     * Descriptor for {@link AWSCodePipelineSCM}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    @Extension
    public static final class DescriptorImpl extends SCMDescriptor<AWSCodePipelineSCM> {
        private final CodePipelineStateModel model = new CodePipelineStateService().getModel();

        public DescriptorImpl() {
            super(AWSCodePipelineSCM.class, null);
            load();
        }

        @Override
        public String getDisplayName() {
            return "AWS CodePipeline";
        }

        @Override
        public SCM newInstance(final StaplerRequest req,
                               final JSONObject formData)
                throws FormException {
            return new AWSCodePipelineSCM(
                    req.getParameter("clearWorkspace") != null,
                    req.getParameter("region"),
                    req.getParameter("awsAccessKey"),
                    req.getParameter("awsSecretKey"),
                    req.getParameter("proxyHost"),
                    req.getParameter("proxyPort"),
                    req.getParameter("category"),
                    req.getParameter("provider"),
                    req.getParameter("version")
            );
        }

        @Override
        public boolean configure(final StaplerRequest req,
                                 final JSONObject formData) throws FormException {
            return true;
        }

        public ListBoxModel doFillRegionItems() {
            final ListBoxModel items = new ListBoxModel();

            for (final Regions region : model.AVAILABLE_REGIONS) {
                items.add(region.toString(), region.getName());
            }

            return items;
        }

        public ListBoxModel doFillCategoryItems() {
            final ListBoxModel items = new ListBoxModel();

            for (final CodePipelineStateModel.CategoryType action : model.ACTION_TYPE) {
                items.add(action.toString(), action.name());
            }

            return items;
        }

        public FormValidation doCategoryCheck(@QueryParameter final String value) {
            if (value == null || value.equals("Please Choose A Category")
                    || value.equals("PleaseChooseACategory")) {
                return FormValidation.error("Please select a Build Type");
            }
            else {
                return FormValidation.ok();
            }
        }

        public FormValidation doVersionCheck(@QueryParameter final String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.error("Please enter a Version");
            }

            try {
                if (Integer.parseInt(value) < 0) {
                    return FormValidation.error("Version must be greater than or equal to 0");
                }
            }
            catch (final Exception ex) {
                return FormValidation.error("Version must be a number");
            }

            return FormValidation.ok();
        }

        public FormValidation doProviderCheck(@QueryParameter final String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.error("Please enter a Provider, typically \"Jenkins\" or your Project Name");
            }
            else {
                return FormValidation.ok();
            }
        }

        public FormValidation doProxyPortCheck(@QueryParameter final String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.ok();
            }
            else {
                final int port;
                try {
                    port = Integer.parseInt(value);
                }
                catch (final Exception ex) {
                    return FormValidation.error("Proxy Port must be a number");
                }

                if (port < 0 || port > 65535) {
                    return FormValidation.error("Proxy Port must be between 0 and 65535");
                }
            }

            return FormValidation.ok();
        }
    }
}