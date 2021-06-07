// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.ecs.exec

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.panel
import software.aws.toolkits.jetbrains.core.help.HelpIds
import software.aws.toolkits.resources.message
import javax.swing.JComponent

class TaskRoleNotFoundWarningDialog(project: Project) : DialogWrapper(project) {
    private val warningIcon = JBLabel(Messages.getWarningIcon())
    private val component by lazy {
        panel {
            row {
                warningIcon(grow)
                right {
                    label(message("ecs.execute_command_task_role_invalid_warning"))
                }
            }
        }
    }

    init {
        super.init()
        title = message("ecs.execute_command_task_role_invalid_warning_title")
    }

    override fun createCenterPanel(): JComponent? = component

    override fun getHelpId(): String? = HelpIds.ECS_EXEC_PERMISSIONS_REQUIRED.id
}
