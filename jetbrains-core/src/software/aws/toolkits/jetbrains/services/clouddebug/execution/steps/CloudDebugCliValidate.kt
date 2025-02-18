// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.clouddebug.execution.steps

import software.aws.toolkits.core.utils.AttributeBagKey
import software.aws.toolkits.jetbrains.core.executables.ExecutableInstance
import software.aws.toolkits.jetbrains.core.executables.ExecutableManager
import software.aws.toolkits.jetbrains.core.executables.getExecutable
import software.aws.toolkits.jetbrains.services.clouddebug.CloudDebugExecutable
import software.aws.toolkits.jetbrains.services.clouddebug.CloudDebugResolver
import software.aws.toolkits.jetbrains.utils.execution.steps.Context
import software.aws.toolkits.jetbrains.utils.execution.steps.Step
import software.aws.toolkits.jetbrains.utils.execution.steps.StepEmitter
import software.aws.toolkits.resources.message

class CloudDebugCliValidate : Step() {
    override val stepName = "Checking for cloud-debug validity and updates"

    override fun execute(context: Context, stepEmitter: StepEmitter, ignoreCancellation: Boolean) {
        CloudDebugResolver.validateOrUpdateCloudDebug(context.getRequiredAttribute(Context.PROJECT_ATTRIBUTE), stepEmitter, context)
    }

    companion object {
        /*
         * Load and validate the cloud-debug executable. If it is not found or fails to validate, it throws a RuntimeException.
         */
        fun validateAndLoadCloudDebugExecutable(): ExecutableInstance.Executable =
            ExecutableManager.getInstance().getExecutable<CloudDebugExecutable>().thenApply {
                when (it) {
                    is ExecutableInstance.Executable -> it
                    is ExecutableInstance.UnresolvedExecutable -> throw RuntimeException(message("cloud_debug.step.clouddebug.resolution.fail"))
                    is ExecutableInstance.InvalidExecutable -> throw RuntimeException(it.validationError)
                }
            }.toCompletableFuture().join()

        val EXECUTABLE_ATTRIBUTE = AttributeBagKey.create<ExecutableInstance.Executable>("clouddebug.executable")
    }
}
