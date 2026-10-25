package com.github.themartdev.intellijgleam.ide.editor.templates

import com.github.themartdev.intellijgleam.lang.psi.GleamExpression
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelectorBase
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

class GleamPostfixTemplateProvider : PostfixTemplateProvider {
    private val selector = GleamExpressionSelector()

    override fun getTemplates(): Set<PostfixTemplate> = setOf(
        GleamStringBasedPostfixTemplate(
            ".case",
            "case \$expr\$ {\n  _ -> \$END\$todo\n}",
            this,
            selector,
        ),
        GleamStringBasedPostfixTemplate(
            ".echo",
            "echo \$expr\$",
            this,
            selector,
        ),
        GleamStringBasedPostfixTemplate(
            ".block",
            "{\n  \$expr\$\n}",
            this,
            selector,
        ),
    )

    override fun isTerminalSymbol(currentChar: Char): Boolean = currentChar == '.'

    override fun preExpand(file: PsiFile, editor: Editor) = Unit

    override fun afterExpand(file: PsiFile, editor: Editor) = Unit

    override fun preCheck(file: PsiFile, editor: Editor, currentOffset: Int): PsiFile = file
}

private class GleamExpressionSelector : PostfixTemplateExpressionSelectorBase(
    Condition { it is GleamExpression },
) {
    override fun getBorderOffsetFilter(offset: Int): Condition<PsiElement> =
        Condition { it.textRange.endOffset in (offset - 1)..offset }

    override fun getNonFilteredExpressions(
        context: PsiElement,
        document: Document,
        offset: Int,
    ): List<PsiElement> {
        var ancestor: PsiElement? = context
        while (ancestor != null) {
            val expressions = PsiTreeUtil.collectElements(ancestor) {
                it is GleamExpression && it.textRange.endOffset in (offset - 1)..offset
            }
            if (expressions.isNotEmpty()) {
                return listOf(expressions.maxBy { it.textRange.length })
            }
            ancestor = ancestor.parent
        }
        return emptyList()
    }
}

private class GleamStringBasedPostfixTemplate(
    key: String,
    private val templateText: String,
    provider: PostfixTemplateProvider,
    selector: PostfixTemplateExpressionSelector,
) : StringBasedPostfixTemplate(key.removePrefix("."), key, key, selector, provider) {
    override fun getElementToRemove(element: PsiElement): PsiElement = element

    override fun getTemplateString(element: PsiElement): String = templateText
}
