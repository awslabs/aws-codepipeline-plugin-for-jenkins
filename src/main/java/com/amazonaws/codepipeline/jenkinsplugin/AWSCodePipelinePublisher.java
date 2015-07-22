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

import hudson.Launcher;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The AWS CodePipeline Publisher compresses the artifacts and uploads them to S3.
 * It calls putJobSuccessResult or putJobFailureResult depending on the build result.
 * It only works together with the CodePipeline SCM plugin to get access to the Job Data, Credentials and Proxy.
 */
public class AWSCodePipelinePublisher extends Notifier {
    private final CodePipelineStateModel model;

    private final List<OutputTuple> buildOutputs;

    @DataBoundConstructor
    public AWSCodePipelinePublisher(final JSONArray outputLocations) {
        final CodePipelineStateService service = new CodePipelineStateService();
        model            = service.getModel();
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
        Validation.numberOfOutPutsIsValid(buildOutputs);
    }

    @Override
    public boolean perform(
            final AbstractBuild<?,?> action,
            final Launcher launcher,
            final BuildListener listener) {

        final boolean actionSucceeded = action.getResult() == Result.SUCCESS;
        boolean awsStatus = actionSucceeded;
        String error = "Failed";

        final AWSClients aws = model.getAwsClient();

        if (!actionSucceeded) {
            if (model.getActionTypeCategory() == CodePipelineStateModel.CategoryType.Build) {
                error = "Build failed";
            }
            else if (model.getActionTypeCategory() == CodePipelineStateModel.CategoryType.Test) {
                error = "Tests failed";
            }
        }

        // This is here if the customer pressed BuildNow, we have nowhere to push
        // or update. But we want to see if we can build what we have.
        if (model.getJobID() == null || model.getJobID().isEmpty()) {
            return actionSucceeded;
        }

        try {
            LoggingHelper.log(listener, "Publishing artifacts");

            if (model.getOutputBuildArtifacts().size() != buildOutputs.size()) {
                throw new IllegalArgumentException(String.format(
                            "Error: number of output locations and number of CodePipeline outputs are "
                            + "different. Number of outputs: %d, Number of pipeline artifacts: %d. "
                            + "The number of build artifacts should match the number of output artifacts specified",
                            buildOutputs.size(),
                            model.getOutputBuildArtifacts().size()));
            }

            if (!buildOutputs.isEmpty() && actionSucceeded) {
                action.getWorkspace().act(new UploadCallable(
                            action.getProject().getName(),
                            model,
                            buildOutputs,
                            listener));
            }
        }
        catch (final IllegalArgumentException | InterruptedException | IOException ex) {
            error = ex.getMessage();
            LoggingHelper.log(listener, ex.getMessage());
            LoggingHelper.log(listener, ex);
            awsStatus = false;
        }
        finally {
            UploadTools.putJobResult(
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

    public static final class OutputTuple implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String outputString;

        public OutputTuple(final String s) {
            outputString = s;
        }

        public String getOutput() {
            return outputString;
        }
    }
}