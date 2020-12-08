package com.github.gradle.node.yarn.task

import com.github.gradle.node.NodePlugin
import com.github.gradle.node.exec.NodeExecSpec
import com.github.gradle.node.yarn.exec.YarnExecRunner
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property
import org.gradle.process.ExecSpec

open class YarnTask : DefaultTask() {
    @get:Optional
    @get:Input
    val yarnCommand = project.objects.listProperty<String>()

    @get:Optional
    @get:Input
    val args = project.objects.listProperty<String>()

    @get:Input
    val ignoreExitValue = project.objects.property<Boolean>().convention(false)

    @get:Internal
    val workingDir = project.objects.directoryProperty()

    @get:Input
    val environment = project.objects.mapProperty<String, String>()

    @get:Internal
    val execOverrides = project.objects.property<Action<ExecSpec>>()

    init {
        group = NodePlugin.NODE_GROUP
        dependsOn(YarnSetupTask.NAME)
    }

    // For DSL
    @Suppress("unused")
    fun execOverrides(execOverrides: Action<ExecSpec>) {
        this.execOverrides.set(execOverrides)
    }

    @TaskAction
    fun exec() {
        val command = yarnCommand.get().plus(args.get())
        val nodeExecConfiguration =
                NodeExecSpec(command, environment.get(), workingDir.asFile.orNull,
                        ignoreExitValue.get(), execOverrides.orNull)
        val yarnExecRunner = YarnExecRunner()
        yarnExecRunner.executeYarnCommand(project, nodeExecConfiguration)
    }
}
