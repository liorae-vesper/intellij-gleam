package com.github.themartdev.intellijgleam.ide.runconf.run

import com.github.themartdev.intellijgleam.lang.psi.GleamFunctionDeclaration
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore

/** Test discovery helpers for the Unitest runner, which supports filtering to one function. */
object GleamTestSupport {
    private val unitestDependency = Regex("(?m)^\\s*unitest\\s*=")

    fun isUnitestProject(file: VirtualFile): Boolean {
        val manifest = packageManifest(file)?.second ?: return false
        return unitestDependency.containsMatchIn(manifest.contentsToByteArray().toString(Charsets.UTF_8))
    }

    /** Returns the Unitest selector (`module.function_test`) for a public test function. */
    fun selectorFor(declaration: GleamFunctionDeclaration): String? {
        val name = declaration.functionNameDefinition?.identifier?.text ?: return null
        if (declaration.visibilityModifier?.text != "pub" || !name.endsWith("_test")) return null

        val virtualFile = declaration.containingFile.virtualFile ?: return null
        val packageRoot = packageManifest(virtualFile)?.first ?: return null
        val relativePath = VfsUtilCore.getRelativePath(virtualFile, packageRoot, '/') ?: return null
        if (!relativePath.startsWith("test/") || !relativePath.endsWith(".gleam")) return null

        val module = relativePath.removePrefix("test/").removeSuffix(".gleam")
        return "$module.$name"
    }

    private fun packageManifest(file: VirtualFile): Pair<VirtualFile, VirtualFile>? {
        var directory = if (file.isDirectory) file else file.parent
        while (directory != null) {
            directory.findChild("gleam.toml")?.let { return directory to it }
            directory = directory.parent
        }
        return null
    }
}
