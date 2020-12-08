package com.github.gradle.node.npm.exec

import com.github.gradle.node.NodeExtension
import com.github.gradle.node.exec.ExecConfiguration
import com.github.gradle.node.exec.ExecRunner
import com.github.gradle.node.exec.NodeExecSpec
import com.github.gradle.node.npm.proxy.NpmProxy.Companion.computeNpmProxyEnvironmentVariables
import com.github.gradle.node.npm.proxy.NpmProxy.Companion.hasProxyConfiguration
import com.github.gradle.node.util.zip
import com.github.gradle.node.variant.VariantComputer
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.io.File

internal class NpmExecRunner {
    private val variantComputer = VariantComputer()

    fun executeNpmCommand(project: Project, nodeExecSpec: NodeExecSpec) {
        val npmExecConfiguration = NpmExecConfiguration("npm"
        ) { variantComputer, nodeExtension, npmBinDir -> variantComputer.computeNpmExec(nodeExtension, npmBinDir) }
        val nodeExtension = NodeExtension[project]
        executeCommand(project, addProxyEnvironmentVariables(nodeExtension, nodeExecSpec),
                npmExecConfiguration)
    }

    private fun addProxyEnvironmentVariables(nodeExtension: NodeExtension,
                                             nodeExecSpec: NodeExecSpec): NodeExecSpec {
        if (nodeExtension.useGradleProxySettings.get()
                && !hasProxyConfiguration(System.getenv())) {
            val npmProxyEnvironmentVariables = computeNpmProxyEnvironmentVariables()
            if (npmProxyEnvironmentVariables.isNotEmpty()) {
                val environmentVariables =
                        nodeExecSpec.environment.plus(npmProxyEnvironmentVariables)
                return nodeExecSpec.copy(environment = environmentVariables)
            }
        }
        return nodeExecSpec
    }

    fun executeNpxCommand(project: Project, nodeExecSpec: NodeExecSpec) {
        val npxExecConfiguration = NpmExecConfiguration("npx") { variantComputer, nodeExtension, npmBinDir ->
            variantComputer.computeNpxExec(nodeExtension, npmBinDir)
        }
        executeCommand(project, nodeExecSpec, npxExecConfiguration)
    }

    private fun executeCommand(project: Project, nodeExecSpec: NodeExecSpec,
                               npmExecConfiguration: NpmExecConfiguration) {
        val execConfiguration =
                computeExecConfiguration(project, npmExecConfiguration, nodeExecSpec).get()
        val execRunner = ExecRunner()
        execRunner.execute(project, execConfiguration)
    }

    private fun computeExecConfiguration(project: Project, npmExecConfiguration: NpmExecConfiguration,
                                         nodeExecSpec: NodeExecSpec): Provider<ExecConfiguration> {
        val nodeExtension = NodeExtension[project]
        val additionalBinPathProvider = computeAdditionalBinPath(project, nodeExtension)
        val executableAndScriptProvider =
                computeExecutable(nodeExtension, npmExecConfiguration)
        return zip(additionalBinPathProvider, executableAndScriptProvider)
                .map { (additionalBinPath, executableAndScript) ->
                    val argsPrefix =
                            if (executableAndScript.script != null) listOf(executableAndScript.script) else listOf()
                    val args = argsPrefix.plus(nodeExecSpec.command)
                    ExecConfiguration(executableAndScript.executable, args, additionalBinPath,
                            nodeExecSpec.environment, nodeExecSpec.workingDir,
                            nodeExecSpec.ignoreExitValue, nodeExecSpec.execOverrides)
                }
    }

    private fun computeExecutable(nodeExtension: NodeExtension, npmExecConfiguration: NpmExecConfiguration):
            Provider<ExecutableAndScript> {
        val nodeDirProvider = variantComputer.computeNodeDir(nodeExtension)
        val npmDirProvider = variantComputer.computeNpmDir(nodeExtension, nodeDirProvider)
        val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)
        val npmBinDirProvider = variantComputer.computeNpmBinDir(npmDirProvider)
        val nodeExecProvider = variantComputer.computeNodeExec(nodeExtension, nodeBinDirProvider)
        val executableProvider =
                npmExecConfiguration.commandExecComputer(variantComputer, nodeExtension, npmBinDirProvider)
        val npmScriptFileProvider =
                variantComputer.computeNpmScriptFile(nodeDirProvider, npmExecConfiguration.command)
        return zip(nodeExtension.download, nodeExtension.nodeProjectDir, executableProvider, nodeExecProvider,
                npmScriptFileProvider).map {
            val (download, nodeProjectDir, executable, nodeExec,
                    npmScriptFile) = it
            if (download) {
                val localCommandScript = nodeProjectDir.dir("node_modules/npm/bin")
                        .file("${npmExecConfiguration.command}-cli.js").asFile
                if (localCommandScript.exists()) {
                    return@map ExecutableAndScript(nodeExec, localCommandScript.absolutePath)
                } else if (!File(executable).exists()) {
                    return@map ExecutableAndScript(nodeExec, npmScriptFile)
                }
            }
            return@map ExecutableAndScript(executable)
        }
    }

    private data class ExecutableAndScript(
            val executable: String,
            val script: String? = null
    )

    private fun computeAdditionalBinPath(project: Project, nodeExtension: NodeExtension): Provider<List<String>> {
        return nodeExtension.download.flatMap { download ->
            if (!download) {
                project.providers.provider { listOf<String>() }
            }
            val nodeDirProvider = variantComputer.computeNodeDir(nodeExtension)
            val nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider)
            val npmDirProvider = variantComputer.computeNpmDir(nodeExtension, nodeDirProvider)
            val npmBinDirProvider = variantComputer.computeNpmBinDir(npmDirProvider)
            zip(npmBinDirProvider, nodeBinDirProvider).map { (npmBinDir, nodeBinDir) ->
                listOf(npmBinDir, nodeBinDir).map { file -> file.asFile.absolutePath }
            }
        }
    }
}
