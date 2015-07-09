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
import com.amazonaws.services.codepipeline.model.AWSSessionCredentials;
import com.amazonaws.services.codepipeline.model.Artifact;
import com.amazonaws.services.codepipeline.model.GetJobDetailsRequest;
import com.amazonaws.services.codepipeline.model.GetJobDetailsResult;
import hudson.Launcher;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The AWS CodePipeline Publisher compresses the artifacts and uploads them to S3.
 * It calls putJobSuccessResult or putJobFailureResult depending on the build result.
 * It only works together with the CodePipeline SCM plugin to get access to the Job Data, Credentials and Proxy.
 */
public class AWSCodePipelinePublisher extends Recorder {
    private final LoggingHelper          logHelper;
    private final CodePipelineStateModel model;
    private final CompressionTools       compressionTools;
    private final UploadTools            uploadTools;

    private final List<OutputTuple> buildOutputs;

    @DataBoundConstructor
    public AWSCodePipelinePublisher(final JSONArray outputLocations) {
        final CodePipelineStateService service = new CodePipelineStateService();
        logHelper        = new LoggingHelper();
        model            = service.getModel();
        compressionTools = new CompressionTools();
        uploadTools      = new UploadTools();
        buildOutputs     = new ArrayList<>();

        if (outputLocations != null) {
            for (final Object aJsonBuildArray : outputLocations) {
                final JSONObject child = (JSONObject) aJsonBuildArray;
                final String output = (String) child.get("output");

                if (output != null) {
                    this.buildOutputs.add(new OutputTuple(Validation.sanitize(output.trim())));
                }
            }
        }

        model.setAllPluginsInstalled(true);
    }

    @Override
    public boolean perform(
            final AbstractBuild action,
            final Launcher launcher,
            final BuildListener listener) {
        final boolean actionSucceeded = action.getResult() == Result.SUCCESS;
        boolean awsStatus = actionSucceeded;
        final AWSClients aws = model.getAwsClient();
        String error = "Failed";

        // This is here if the customer pressed BuildNow, we have nowhere to push
        // or update. But we want to see if we can build what we have. Note: If ClearWorkspace
        // is selected, this will fail.
        if (model.getJobID() == null || model.getJobID().isEmpty()) {
            return actionSucceeded;
        }

        try {
            logHelper.log(listener, "Publishing Artifacts");
            publishArtifacts(action, actionSucceeded, listener);
        }
        catch (final IllegalArgumentException | InterruptedException | IOException ex) {
            error = ex.getMessage();
            logHelper.log(listener, ex.getMessage());
            logHelper.log(listener, ex);
            awsStatus = false;
        }
        finally {
            uploadTools.putJobResult(
                    awsStatus,
                    error,
                    action.getId(),
                    model.getJobID(),
                    aws,
                    listener);
            cleanUp();
        }

        return awsStatus;
    }

    public void publishArtifacts(
            final AbstractBuild build,
            final boolean actionSucceeded,
            final BuildListener listener)
            throws IOException, InterruptedException, IllegalArgumentException {
        if (model.getOutputBuildArtifacts().size() != buildOutputs.size()) {
            final String error = "Error: Number of Output Locations and Number of CodePipeline outputs are " +
                    "different. Number of Outputs: " + buildOutputs.size() +
                    ", Number of Pipeline Artifacts: " + model.getOutputBuildArtifacts().size() +
                    "The Number of Build Artifacts should correspond to the number of Output Artifacts specified";
            throw new IllegalArgumentException(error);
        }

        if (buildOutputs.isEmpty() || !actionSucceeded) {
            // Nothing to Upload
            return;
        }

        uploadArtifacts(build, listener);
    }

    public void uploadArtifacts(final AbstractBuild build, final BuildListener listener)
            throws IOException, InterruptedException {
        final List<Artifact> pipelineOutputArtifacts    = model.getOutputBuildArtifacts();
        final AWSClients aws                            = model.getAwsClient();
        final Iterator<Artifact> artifactIterator       = pipelineOutputArtifacts.iterator();
        final GetJobDetailsRequest getJobDetailsRequest = new GetJobDetailsRequest();
        getJobDetailsRequest.setJobId(model.getJobID());

        final GetJobDetailsResult getJobDetailsResult
                = aws.getCodePipelineClient().getJobDetails(getJobDetailsRequest);
        final AWSSessionCredentials sessionCredentials
                = getJobDetailsResult.getJobDetails().getData().getArtifactCredentials();
        final BasicSessionCredentials temporaryCredentials = new BasicSessionCredentials(
                sessionCredentials.getAccessKeyId(),
                sessionCredentials.getSecretAccessKey(),
                sessionCredentials.getSessionToken());

        for (final OutputTuple directoryToZip : buildOutputs) {
            final File compressedFile = compressionTools.compressFile(build, directoryToZip.getOutput(), listener);
            final Artifact artifact = artifactIterator.next();

            if (compressedFile != null) {
                uploadTools.uploadFile(
                        compressedFile,
                        artifact,
                        model.getCompressionType(),
                        temporaryCredentials,
                        aws,
                        listener);
            }
            else {
                logHelper.log(listener, "Failed to compress file and upload file");
            }
        }
    }

    public void cleanUp() {
        model.clearJob();
        model.setCompressionType(CodePipelineStateModel.CompressionType.None);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.STEP;
    }

    public OutputTuple[] getBuildOutputs() {
        if (buildOutputs.isEmpty()) {
            return null;
        }
        else {
            return buildOutputs.toArray(new OutputTuple[buildOutputs.size()]);
        }
    }


    /**
     * Descriptor for {@link AWSCodePipelinePublisher}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     * See src/main/resources/com/amazonaws/codepipeline/AWSCodePipelinePublisher/*.jelly
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public DescriptorImpl() {
            load();
        }

        public boolean isApplicable(final Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "AWS CodePipeline Publisher";
        }

        @Override
        public boolean configure(
                final StaplerRequest req,
                final JSONObject formData)
                throws FormException {
            req.bindJSON(this, formData);
            save();

            return super.configure(req, formData);
        }
    }

    public static final class OutputTuple {
        private final String outputString;

        public OutputTuple(final String s) {
            outputString = s;
        }

        public String getOutput() {
            return outputString;
        }
    }
}

