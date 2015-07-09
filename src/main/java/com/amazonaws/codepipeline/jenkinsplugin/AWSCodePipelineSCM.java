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

import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.codepipeline.AmazonCodePipelineClient;
import com.amazonaws.services.codepipeline.model.AWSSessionCredentials;
import com.amazonaws.services.codepipeline.model.AcknowledgeJobRequest;
import com.amazonaws.services.codepipeline.model.AcknowledgeJobResult;
import com.amazonaws.services.codepipeline.model.ActionOwner;
import com.amazonaws.services.codepipeline.model.ActionTypeId;
import com.amazonaws.services.codepipeline.model.Artifact;
import com.amazonaws.services.codepipeline.model.Job;
import com.amazonaws.services.codepipeline.model.JobStatus;
import com.amazonaws.services.codepipeline.model.PollForJobsRequest;
import com.amazonaws.services.codepipeline.model.PollForJobsResult;
import com.amazonaws.services.codepipeline.model.S3ArtifactLocation;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.scm.SCMDescriptor;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.scm.ChangeLogParser;
import hudson.scm.NullChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCMRevisionState;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class AWSCodePipelineSCM extends hudson.scm.SCM {
    private final LoggingHelper            logHelper;
    private final DownloadTools            downloader;
    private final UploadTools              uploadTools;
    private final ExtractionTools          extractor;
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
        logHelper      = new LoggingHelper();
        model          = service.getModel();
        downloader     = new DownloadTools();
        uploadTools    = new UploadTools();
        extractor      = new ExtractionTools();
        clearWorkspace = clear;

        model.setRegion(Validation.sanitize(region));
        model.setProxyHost(Validation.sanitize(proxyHost));
        model.setProxyPort(proxyPort);
        model.setAwsAccessKey(Validation.sanitize(awsAccessKey));
        model.setAwsSecretKey(Validation.sanitize(awsSecretKey));
        actionTypeCategory = Validation.sanitize(category.trim());
        model.setActionTypeCategory(CodePipelineStateModel.CategoryType.fromName(actionTypeCategory));
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
            final TaskListener taskListener,
            final SCMRevisionState revisionState)
            throws IOException, InterruptedException {
        if (model != null
                && Validation.isValidConfiguration()
                && Validation.projectNameIsValid(projectName)
                && Validation.actionTypeIsValid(actionTypeCategory, actionTypeProvider, actionTypeVersion)
                && model.areAllPluginsInstalled()) {
            final ActionTypeId actionTypeId = new ActionTypeId();
            actionTypeId.setCategory(actionTypeCategory);
            actionTypeId.setOwner(ActionOwner.Custom);
            actionTypeId.setProvider(actionTypeProvider);
            actionTypeId.setVersion(actionTypeVersion);

            logHelper.log(taskListener, "Polling for jobs for action type id: " +
                    "Owner: " + actionTypeId.getOwner() +
                    ", Category: " + actionTypeId.getCategory() +
                    ", Provider: " + actionTypeId.getProvider() +
                    ", Version: " + actionTypeId.getVersion() +
                    ", ProjectName: " + projectName);

            return pollForJobs(actionTypeId, taskListener);
        }
        else {
            if (model != null) {
                final String error = String.format("Invalid State: Category: '%s' Provider: '%s', Version: '%s', " +
                                "ProjectName: '%s', Publisher Installed: '%s'",
                        actionTypeCategory,
                        actionTypeProvider,
                        actionTypeVersion,
                        projectName,
                        model.areAllPluginsInstalled());
                logHelper.log(taskListener, error);
            }
            else {
                logHelper.log(taskListener, "Model was not created");
            }

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
            final FilePath filePath,
            final BuildListener listener,
            final File file)
            throws IOException, InterruptedException {
        boolean success = false;
        final boolean buildFailed = false;

        if (job == null) {
            // This is here for if a customer presses BuildNow, it will still attempt a build.
            return true;
        }

        getProjectName(abstractBuild, listener);
        Validation.validateProjectName(projectName, listener);
        clearWorkspaceIfSelected(filePath, listener);

        logHelper.log(listener, String.format("Job '%s' received", job.getId()));

        try {
            for (final Artifact artifact : job.getData().getInputArtifacts()) {
                final S3Object sessionObject = getS3Object(artifact);

                if (sessionObject == null) {
                    final String error = "Unable to get Credentials, this can be caused by:\n " +
                            "-AWS CodePipeline plugin not configured properly\n" +
                            "-Incorrect Credentials";
                    logHelper.log(listener, error);

                    uploadTools.putJobResult(
                            buildFailed,
                            error,
                            abstractBuild.getId(),
                            model.getJobID(),
                            model.getAwsClient(),
                            listener);
                    return false;
                }

                final CodePipelineStateModel.CompressionType compressionType =
                        extractor.getCompressionType(sessionObject, listener);
                model.setCompressionType(compressionType);

                final String downloadedFileName = Paths.get(sessionObject.getKey()).getFileName().toString();

                try {
                    downloadAndExtract(sessionObject, filePath, downloadedFileName, listener);
                    success = true;
                }
                catch (final Exception ex) {
                    final String error = "Failed to acquire artifacts: " + ex.getMessage();
                    logHelper.log(listener, error);
                    logHelper.log(listener, ex);

                    uploadTools.putJobResult(
                            buildFailed,
                            error,
                            abstractBuild.getId(),
                            model.getJobID(),
                            model.getAwsClient(),
                            listener);
                }
            }
        }
        finally {
            cleanUp();
        }

        return success;
    }

    public S3Object getS3Object(final Artifact artifact) {
        final AWSClients            aws                     = model.getAwsClient();
        final S3ArtifactLocation    artifactLocation        = artifact.getLocation().getS3Location();
        final AWSSessionCredentials awsSessionCredentials   = job.getData().getArtifactCredentials();

        final BasicSessionCredentials basicCredentials = new BasicSessionCredentials(
                awsSessionCredentials.getAccessKeyId(),
                awsSessionCredentials.getSecretAccessKey(),
                awsSessionCredentials.getSessionToken());

        final AmazonS3Client client = aws.getS3Client(basicCredentials);

        if (client == null) {
            return null;
        }

        final String   bucketName    = artifactLocation.getBucketName();

        return client.getObject(bucketName, artifactLocation.getObjectKey());
    }

    public PollingResult pollForJobs(final ActionTypeId actionType, final TaskListener taskListener) {
        final AWSClients aws = model.getAwsClient();
        final AmazonCodePipelineClient codePipelineClient = aws.getCodePipelineClient();

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

            logHelper.log(taskListener, "Received Job request with ID: " +
                    model.getJobID());

            final AcknowledgeJobRequest acknowledgeJobRequest = new AcknowledgeJobRequest();
            acknowledgeJobRequest.setJobId(job.getId());
            acknowledgeJobRequest.setNonce(job.getNonce());

            final AcknowledgeJobResult acknowledgeJobResult = codePipelineClient.acknowledgeJob(acknowledgeJobRequest);

            if (acknowledgeJobResult.getStatus().equals(JobStatus.InProgress.name())) {
                logHelper.log(taskListener, "Job Acknowledged with ID: " +
                        acknowledgeJobRequest.getJobId());

                model.setJobID(job.getId());

                return PollingResult.BUILD_NOW;
            }
        }

        logHelper.log(taskListener, "No jobs found.");

        return PollingResult.NO_CHANGES;
    }

    public void downloadAndExtract(
            final S3Object sessionObject,
            final FilePath filePath,
            final String downloadedFileName,
            final TaskListener listener)
            throws Exception {
        downloader.attemptArtifactDownload(
                sessionObject,
                filePath,
                downloadedFileName,
                listener);

        String fullFilePath = null;

        try {
            logHelper.log(listener, "File downloaded successfully");

            fullFilePath = extractor.getFullCompressedFilePath(downloadedFileName, filePath.getRemote());
            extractor.decompressFile(fullFilePath, filePath, listener);

            logHelper.log(listener, "File uncompressed successfully");
        }
        finally {
            if (fullFilePath != null) {
                try {
                    extractor.deleteTemporaryCompressedFile(fullFilePath, listener);
                }
                catch (final IOException ex) {
                    logHelper.log(listener, "Could not delete temporary file, " + ex.getMessage());
                    logHelper.log(listener, ex);
                }
            }
        }
    }

    public void clearWorkspaceIfSelected(final FilePath filePath, final TaskListener listener) {
        if (clearWorkspace) {
            try {
                logHelper.log(listener, "Clearing Workspace " + filePath.getRemote() + " before download");
                filePath.deleteContents();
            }
            catch (final IOException ex) {
                logHelper.log(listener, "Unable to clear workspace, " + ex.getMessage());
            }
            catch (final InterruptedException ex) {
                logHelper.log(listener, "Clearing workspace interrupted, " + ex.getMessage());
            }
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return ( DescriptorImpl ) super.getDescriptor();
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

    public int getMaxMappings() {
        // TODO: Pull Max number from CodePipelineFrontEndService
        return 5;
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

    private void cleanUp() {
        job = null;
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

