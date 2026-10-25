package com.github.themartdev.intellijgleam.ide.completion

import com.github.themartdev.intellijgleam.ide.navigation.GleamSymbols
import com.github.themartdev.intellijgleam.lang.psi.GleamCallArgument
import com.github.themartdev.intellijgleam.lang.psi.GleamCallArguments
import com.github.themartdev.intellijgleam.lang.psi.GleamCallExpr
import com.github.themartdev.intellijgleam.lang.psi.GleamFieldAccessExpr
import com.github.themartdev.intellijgleam.lang.psi.GleamFunctionDeclaration
import com.github.themartdev.intellijgleam.lang.psi.GleamFunctionType
import com.github.themartdev.intellijgleam.lang.psi.GleamImportDeclaration
import com.github.themartdev.intellijgleam.lang.psi.GleamReferenceExpr
import com.github.themartdev.intellijgleam.lang.psi.GleamTypeBase
import com.github.themartdev.intellijgleam.lang.psi.GleamTypeDeclaration
import com.github.themartdev.intellijgleam.lang.psi.GleamTypeReference
import com.github.themartdev.intellijgleam.lang.psi.GleamTypes
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext

/** Suggests an anonymous function when a resolvable Gleam call expects a callback. */
object ClosureArgumentCompletionProvider : GleamCompletionProvider() {
    override val pattern = psiElement().inside(GleamCallArguments::class.java)

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val callArguments = PsiTreeUtil.getParentOfType(
            parameters.position,
            GleamCallArguments::class.java,
        ) ?: return
        val call = callArguments.parent as? GleamCallExpr ?: return
        val declaration = resolveFunctionDeclaration(call) ?: return
        val functionParameters = declaration.functionParameters?.functionParameterList ?: return

        val argumentIndex = callArguments.node.getChildren(null)
            .count { it.elementType == GleamTypes.COMMA && it.startOffset < parameters.offset }
        val currentArgument = PsiTreeUtil.getParentOfType(
            parameters.position,
            GleamCallArgument::class.java,
        )
        val currentLabel = currentArgument?.labeledArgument?.identifierDiscardable?.actualElement?.text
        val labeledParameter = currentLabel?.let { label ->
            functionParameters.indexOfFirst { it.label?.text == label }
        } ?: -1
        val parameterIndex = if (labeledParameter >= 0) labeledParameter else argumentIndex
        val expectedType = functionParameters.getOrNull(parameterIndex)
            ?.typeAnnotation
            ?.typeBase
            ?: return
        val functionType = resolveFunctionType(expectedType, declaration.containingFile, mutableSetOf()) ?: return
        val arity = functionType.arity() ?: return
        val argumentNames = (1..arity).map { "arg$it" }
        val arguments = argumentNames.joinToString(", ")
        val insertText = "fn($arguments) { todo }"
        val bodyOffset = insertText.indexOf("todo")

        result.addElement(
            LookupElementBuilder.create("fn(")
                .withPresentableText("fn")
                .withTailText(" (${pluralize(arity, "parameter")})", true)
                .withTypeText("anonymous function")
                .withInsertHandler { insertionContext, _ ->
                    insertionContext.document.replaceString(
                        insertionContext.startOffset,
                        insertionContext.tailOffset,
                        insertText,
                    )
                    insertionContext.editor.caretModel.moveToOffset(
                        insertionContext.startOffset + bodyOffset,
                    )
                },
        )
    }

    private fun resolveFunctionDeclaration(call: GleamCallExpr): GleamFunctionDeclaration? {
        val file = call.containingFile
        val imports = file.children.filterIsInstance<GleamImportDeclaration>()
        val (modulePath, functionName) = when (val callee = call.expression) {
            is GleamReferenceExpr -> {
                val name = callee.identifier.text
                val local = file.children
                    .filterIsInstance<GleamFunctionDeclaration>()
                    .firstOrNull { it.functionNameDefinition?.identifier?.text == name }
                if (local != null) return local

                val import = imports.firstOrNull { declaration ->
                    declaration.unqualifiedImports?.unqualifiedImportList
                        ?.any { it.nameOrAlias?.text == name } == true
                } ?: return null
                import.modulePath?.text to name
            }

            is GleamFieldAccessExpr -> {
                val moduleAlias = (callee.expression as? GleamReferenceExpr)?.identifier?.text ?: return null
                val import = imports.firstOrNull {
                    it.modulePath != null && it.nameOrAlias?.text == moduleAlias
                } ?: return null
                import.modulePath?.text to (callee.label?.text ?: return null)
            }

            else -> return null
        }
        val resolvedModulePath = modulePath ?: return null
        return findFunctionDeclaration(call, resolvedModulePath, functionName)
    }

    private fun findFunctionDeclaration(
        context: GleamCallExpr,
        modulePath: String,
        functionName: String,
    ): GleamFunctionDeclaration? {
        val fileName = "${modulePath.substringAfterLast('/')}.gleam"
        val scope = GlobalSearchScope.allScope(context.project)
        val candidateFiles = FilenameIndex.getVirtualFilesByName(fileName, scope)
        val psiManager = PsiManager.getInstance(context.project)
        return candidateFiles
            .asSequence()
            .filter { GleamSymbols.moduleNameOf(it) == modulePath }
            .mapNotNull { psiManager.findFile(it) }
            .flatMap { psiFile -> psiFile.children.asSequence() }
            .filterIsInstance<GleamFunctionDeclaration>()
            .firstOrNull {
                it.visibilityModifier?.text == "pub" &&
                    it.functionNameDefinition?.identifier?.text == functionName
            }
    }

    private fun resolveFunctionType(
        type: GleamTypeBase,
        contextFile: PsiFile,
        aliasesInProgress: MutableSet<String>,
    ): GleamFunctionType? {
        type.functionType?.let { return it }
        val typeReference = type.typeReference ?: return null
        val (modulePath, typeName) = resolveTypeName(contextFile, typeReference) ?: return null
        val declaration = if (modulePath == null) {
            contextFile.children
                .filterIsInstance<GleamTypeDeclaration>()
                .firstOrNull { it.typeDeclarationName?.upIdentifier?.text == typeName }
                ?: return null
        } else {
            findTypeDeclaration(contextFile.project, modulePath, typeName) ?: return null
        }
        val aliasId = "${declaration.containingFile.virtualFile?.path}:$typeName"
        if (!aliasesInProgress.add(aliasId)) return null
        return declaration.typeBase?.let {
            resolveFunctionType(it, declaration.containingFile, aliasesInProgress)
        }
    }

    private fun resolveTypeName(
        file: PsiFile,
        typeReference: GleamTypeReference,
    ): Pair<String?, String>? {
        val qualifiedName = typeReference.qualifiedTypeName
        if (qualifiedName != null) {
            val moduleAlias = qualifiedName.identifierDiscardable.actualElement?.text ?: return null
            val typeName = qualifiedName.upIdentifier.text
            val modulePath = file.children
                .filterIsInstance<GleamImportDeclaration>()
                .firstOrNull { it.modulePath != null && it.nameOrAlias?.text == moduleAlias }
                ?.modulePath
                ?.text
                ?: return null
            return modulePath to typeName
        }

        val typeName = typeReference.unqualifiedTypeName?.upIdentifier?.text ?: return null
        val localType = file.children
            .filterIsInstance<GleamTypeDeclaration>()
            .any { it.typeDeclarationName?.upIdentifier?.text == typeName }
        if (localType) return null to typeName

        val importedModule = file.children
            .filterIsInstance<GleamImportDeclaration>()
            .firstOrNull { declaration ->
                declaration.unqualifiedImports?.typeUnqualifiedImportList
                    ?.any { it.nameOrAlias?.text == typeName } == true
            }
            ?.modulePath
            ?.text
            ?: return null
        return importedModule to typeName
    }

    private fun findTypeDeclaration(
        project: Project,
        modulePath: String,
        typeName: String,
    ): GleamTypeDeclaration? {
        val fileName = "${modulePath.substringAfterLast('/')}.gleam"
        val scope = GlobalSearchScope.allScope(project)
        val psiManager = PsiManager.getInstance(project)
        return FilenameIndex.getVirtualFilesByName(fileName, scope)
            .asSequence()
            .filter { GleamSymbols.moduleNameOf(it) == modulePath }
            .mapNotNull { psiManager.findFile(it) }
            .flatMap { psiFile -> psiFile.children.asSequence() }
            .filterIsInstance<GleamTypeDeclaration>()
            .firstOrNull {
                it.visibilityModifier?.text == "pub" &&
                    it.typeDeclarationName?.upIdentifier?.text == typeName
            }
    }

    private fun GleamFunctionType.arity(): Int? {
        val parameterTypes = functionParameterTypes ?: return 0
        return parameterTypes.typeBaseList.size
    }

    private fun pluralize(count: Int, noun: String): String =
        if (count == 1) "$count $noun" else "$count ${noun}s"
}
