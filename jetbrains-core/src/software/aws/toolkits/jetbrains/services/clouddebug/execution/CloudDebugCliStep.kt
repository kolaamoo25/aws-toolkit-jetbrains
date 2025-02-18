// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.clouddebug.execution

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import org.slf4j.event.Level
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.clouddebug.CliOutputParser
import software.aws.toolkits.jetbrains.services.clouddebug.asLogEvent
import software.aws.toolkits.jetbrains.services.clouddebug.execution.steps.CloudDebugCliValidate
import software.aws.toolkits.jetbrains.utils.execution.steps.CliBasedStep
import software.aws.toolkits.jetbrains.utils.execution.steps.Context
import software.aws.toolkits.jetbrains.utils.execution.steps.StepEmitter
import software.aws.toolkits.resources.message
import java.util.concurrent.atomic.AtomicReference

abstract class CloudDebugCliStep : CliBasedStep() {
    protected fun getCli(context: Context): GeneralCommandLine = context.getRequiredAttribute(CloudDebugCliValidate.EXECUTABLE_ATTRIBUTE).getCommandLine()

    override fun handleErrorResult(exitCode: Int, output: String, stepEmitter: StepEmitter) {
        if (output.isNotEmpty()) {
            stepEmitter.emitMessage("Error details:\n", true)
            CliOutputParser.parseErrorOutput(output)?.run {
                errors.forEach {
                    stepEmitter.emitMessage("\t- $it\n", true)
                }
            } ?: stepEmitter.emitMessage(output, true)
        }

        throw IllegalStateException(message("cloud_debug.step.general.cli_error"))
    }

    override fun createProcessEmitter(stepEmitter: StepEmitter): ProcessListener = CliOutputEmitter(stepEmitter)

    private class CliOutputEmitter(private val messageEmitter: StepEmitter) : ProcessAdapter() {
        val previousLevel = AtomicReference<Level>(null)
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            if (outputType == ProcessOutputTypes.STDERR) {
                val logEvent = event.text.replace("\r", "\n").asLogEvent()
                val level = logEvent.level ?: previousLevel.get()
                messageEmitter.emitMessage(logEvent.text, level == Level.ERROR)
                logEvent.level?.let { previousLevel.set(it) }
            }
            // output to the log for diagnostic and integrations tests
            LOG.debug { event.text.trim() }
        }
    }

    companion object {
        private val LOG = getLogger<CloudDebugCliStep>()
    }
}
