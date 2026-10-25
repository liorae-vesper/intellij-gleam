package com.github.themartdev.intellijgleam.ide.runconf.run

import com.github.themartdev.intellijgleam.lang.psi.GleamFunctionDeclaration
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil.findConfigurationType
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

class GleamTestRunConfigurationProducer : LazyRunConfigurationProducer<GleamTestConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory =
        GleamTestConfigurationFactory(findConfigurationType(GleamRunConfigurationType::class.java))

    override fun setupConfigurationFromContext(
        configuration: GleamTestConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement?>,
    ): Boolean {
        val location = context.location ?: return false
        val declaration = PsiTreeUtil.getParentOfType(location.psiElement, GleamFunctionDeclaration::class.java)
            ?: return false
        val virtualFile = declaration.containingFile.virtualFile ?: return false
        if (!GleamTestSupport.isUnitestProject(virtualFile)) return false
        val selector = GleamTestSupport.selectorFor(declaration) ?: return false

        sourceElement.set(declaration)
        configuration.name = "Run ${selector.substringAfterLast('.')}"
        configuration.options.testName = selector
        return true
    }

    override fun isConfigurationFromContext(
        configuration: GleamTestConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val location = context.location ?: return false
        val declaration = PsiTreeUtil.getParentOfType(location.psiElement, GleamFunctionDeclaration::class.java)
            ?: return false
        val virtualFile = declaration.containingFile.virtualFile ?: return false
        if (!GleamTestSupport.isUnitestProject(virtualFile)) return false
        return configuration.options.testName == GleamTestSupport.selectorFor(declaration)
    }
}
