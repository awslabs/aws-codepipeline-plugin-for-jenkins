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

/**
 * Per-thread storage of {@code CodePipelineStateModel}.
 *
 * This class is using ThreadLocal storage to manage the CodePipelineSateModel per thread of execution.
 * This is done for passing data between the SCM and the Publisher modules in the face of concurrent builds.
 * Otherwise, the model gets overwritten.
 */
public class CodePipelineStateService {

    private static final ThreadLocal<CodePipelineStateModel> codePipelineStateModel;

    static {
        codePipelineStateModel = new ThreadLocal<>();
    }

    public static void setModel(final CodePipelineStateModel model) {
        codePipelineStateModel.set(model);
    }

    public static CodePipelineStateModel getModel() {
        return codePipelineStateModel.get();
    }

    public static void removeModel() {
        codePipelineStateModel.remove();
    }

}