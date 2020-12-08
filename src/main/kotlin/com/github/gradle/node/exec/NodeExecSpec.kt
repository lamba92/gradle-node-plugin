package com.github.gradle.node.exec

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.kotlin.dsl.invoke
import org.gradle.process.ExecSpec
import java.io.File

data class NodeExecSpec(
        val command: List<String> = listOf(),
        val environment: Map<String, String> = mapOf(),
        var workingDir: File? = null,
        var ignoreExitValue: Boolean = false,
        var execOverrides: Action<ExecSpec>? = null
)

/**
 * Configures the underlying [ExecSpec].
 */
@Suppress("unused")
fun NodeExecSpec.exec(execOverrides: Action<ExecSpec>) {
    this.execOverrides = execOverrides
}

fun Project.nodeExec(action: Action<NodeExecSpec>) =
    NodeExecRunner().execute(this, NodeExecSpec().also { action(it) })
