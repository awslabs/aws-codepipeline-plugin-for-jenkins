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

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CategoryType;
import com.amazonaws.codepipeline.jenkinsplugin.CodePipelineStateModel.CompressionType;
import com.amazonaws.services.codepipeline.model.Artifact;

import hudson.Extension;
import hudson.Launcher;
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

/**
 * The AWS CodePipeline Publisher compresses the artifacts and uploads them to S3.
 * It calls putJobSuccessResult or putJobFailureResult depending on the build result.
 * It only works together with the CodePipeline SCM plugin to get access to the Job Data, Credentials and Proxy.
 */
public class AWSCodePipelinePublisher extends Notifier {
    private static final String JELLY_KEY_LOCATION = "location";
    private static final String JELLY_KEY_ARTIFACT_NAME = "artifactName";

    @Deprecated // renamed to outputArtifacts
    private final transient List<OutputTuple> buildOutputs;
    private List<OutputArtifact> outputArtifacts;

    private AWSClientFactory awsClientFactory;

    @DataBoundConstructor
    public AWSCodePipelinePublisher(final JSONArray outputLocations) {
        buildOutputs = new ArrayList<>();
        outputArtifacts = new ArrayList<>();

        if (outputLocations != null) {
            for (final Object outputLocation : outputLocations) {
                final JSONObject jsonObject = (JSONObject) outputLocation;
                if (jsonObject.has(JELLY_KEY_LOCATION) && jsonObject.has(JELLY_KEY_ARTIFACT_NAME)) {
                    final String locationValue = jsonObject.getString(JELLY_KEY_LOCATION);
                    final String artifactName = jsonObject.getString(JELLY_KEY_ARTIFACT_NAME);
                    this.outputArtifacts.add(new OutputArtifact(
                            Validation.sanitize(locationValue.trim()),
                            Validation.sanitize(artifactName.trim())
                    ));
                }
            }
        }

        awsClientFactory = new AWSClientFactory();
        Validation.numberOfOutPutsIsValid(outputArtifacts);
    }

    public AWSCodePipelinePublisher(final JSONArray outputLocations, final AWSClientFactory awsClientFactory) {
        this(outputLocations);
        this.awsClientFactory = awsClientFactory;
    }

    @Override
    public boolean perform(
            final AbstractBuild<?,?> action,
            final Launcher launcher,
            final BuildListener listener) {
        final CodePipelineStateModel model = CodePipelineStateService.getModel();

        final boolean actionSucceeded = action.getResult() == Result.SUCCESS;
        boolean awsStatus = actionSucceeded;
        String error = "Failed";

        if (model == null) {
            LoggingHelper.log(listener, "Error with Model Thread Handling");
            return false;
        }

        if (model.isSkipPutJobResult()) {
            LoggingHelper.log(
                    listener,
                    String.format("Skipping PutJobFailureResult call for the job with ID %s", model.getJob().getId()));
            return false;
        }

        final AWSClients awsClients = awsClientFactory.getAwsClient(
                model.getAwsAccessKey(),
                model.getAwsSecretKey(),
                model.getProxyHost(),
                model.getProxyPort(),
                model.getRegion(),
                JenkinsMetadata.getPluginVersion());

        if (!actionSucceeded) {
            if (model.getActionTypeCategory() == CategoryType.Build) {
                error = "Build failed";
            } else if (model.getActionTypeCategory() == CategoryType.Test) {
                error = "Tests failed";
            }
        }

        // This is here if the customer pressed BuildNow, we have nowhere to push
        // or update. But we want to see if we can build what we have.
        if (model.getJob() == null) {
            LoggingHelper.log(listener, "No Job, returning early");
            return actionSucceeded;
        }

        try {
            LoggingHelper.log(listener, "Publishing artifacts");

            final List<Artifact> artifactsFromModel = model.getJob().getData().getOutputArtifacts();

            if (artifactsFromModel.size() != outputArtifacts.size()) {
                throw new IllegalArgumentException(String.format(
                        "The number of output artifacts in the Jenkins project and in the pipeline action do not match. "
                                + "Configure the output locations of your Jenkins project to match the pipeline "
                                + "action's output artifacts. Number of output locations in Jenkins project: %d, "
                                + "number of output artifacts in the pipeline action: %d " + pipelineContextString(model),
                        outputArtifacts.size(),
                        model.getJob().getData().getOutputArtifacts().size()));
            }

            final Set<String> artifactNamesFromModel = getArtifactNamesFromModel(artifactsFromModel);
            final Set<String> artifactNamesFromProject = PublisherCallable.getArtifactNamesFromProject(outputArtifacts);

            if (!artifactNamesFromProject.isEmpty() && !artifactNamesFromProject.equals(artifactNamesFromModel)) {
                throw new IllegalArgumentException(String.format(
                        "Artifact names in the Jenkins project do not match output artifacts in the pipeline action. "
                                + "Either configure the artifact name of each location to match output artifacts for "
                                + "the pipeline action, or leave the field blank. " + pipelineContextString(model)));
            }

            if (!outputArtifacts.isEmpty() && actionSucceeded) {
                callPublish(action, model, listener);
            }
        } catch (final AmazonServiceException ex) {
            error = "Failed to upload output artifact(s): " + ex.getErrorMessage();
            LoggingHelper.log(listener, ex.getMessage());
            LoggingHelper.log(listener, ex);
            awsStatus = false;
        } catch (final RuntimeException | InterruptedException | IOException ex) {
            error = "Failed to upload output artifact(s): " + ex.getMessage();
            LoggingHelper.log(listener, ex.getMessage());
            LoggingHelper.log(listener, ex);
            awsStatus = false;
        } finally {
            PublisherTools.putJobResult(
                    awsStatus,
                    error,
                    action.getId(),
                    model.getJob().getId(),
                    awsClients.getCodePipelineClient(),
                    listener);
            cleanUp(model);
        }

        return awsStatus;
    }

    public void cleanUp(final CodePipelineStateModel model) {
        model.clearJob();
        model.setCompressionType(CompressionType.None);
        CodePipelineStateService.removeModel();
    }

    public void callPublish(
            final AbstractBuild<?,?> action,
            final CodePipelineStateModel model,
            final BuildListener listener)
            throws IOException, InterruptedException {

        action.getWorkspace().act(new PublisherCallable(
                action.getProject().getName(),
                model,
                outputArtifacts,
                awsClientFactory,
                JenkinsMetadata.getPluginVersion(),
                listener));
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.STEP;
    }

    public OutputArtifact[] getOutputArtifacts() {
        if (outputArtifacts.isEmpty()) {
            return null;
        } else {
            return outputArtifacts.toArray(new OutputArtifact[outputArtifacts.size()]);
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

    // Retain backwards compatibility: migrate from OutputTuple to OutputArtifact
    // https://wiki.jenkins-ci.org/display/JENKINS/Hint+on+retaining+backward+compatibility
    protected Object readResolve() {
        if (buildOutputs != null) {
            if (outputArtifacts == null) {
                outputArtifacts = new ArrayList<>();
            }
            for (final OutputTuple tuple : buildOutputs) {
                outputArtifacts.add(new OutputArtifact(tuple.getOutput(), ""));
            }
        }
        return this;
    }

    @Deprecated // plugin now uses OutputArtifact
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

    private Set<String> getArtifactNamesFromModel(final List<Artifact> artifactsFromModel) {
        Set<String> artifactNames = new HashSet<>();
        for (final Artifact artifact : artifactsFromModel) {
            if (!artifact.getName().isEmpty()) {
                artifactNames.add(artifact.getName());
            }
        }
        return artifactNames;
    }

    private String pipelineContextString(final CodePipelineStateModel model) {
        return String.format("[Pipeline: %s, stage: %s, action: %s].",
                model.getJob().getData().getPipelineContext().getPipelineName(),
                model.getJob().getData().getPipelineContext().getStage().getName(),
                model.getJob().getData().getPipelineContext().getAction().getName());
    }
}
