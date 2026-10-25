package com.github.themartdev.intellijgleam.ide.editor

import com.github.themartdev.intellijgleam.lang.psi.GleamCallArguments
import com.github.themartdev.intellijgleam.lang.psi.GleamCaseSubjects
import com.github.themartdev.intellijgleam.lang.psi.GleamListExpr
import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

class GleamStructuralSelectionHandler : ExtendWordSelectionHandler {
    override fun canSelect(element: PsiElement): Boolean =
        element is GleamCallArguments || element is GleamCaseSubjects || element is GleamListExpr

    override fun select(
        element: PsiElement,
        text: CharSequence,
        cursorOffset: Int,
        editor: Editor,
    ): List<TextRange> {
        val itemRanges = when (element) {
            is GleamCallArguments -> element.callArgumentList.map { it.textRange }
            is GleamCaseSubjects -> element.expressionList.map { it.textRange }
            is GleamListExpr -> element.expressionList.map { it.textRange }
            else -> return emptyList()
        }

        if (itemRanges.size < 2) return emptyList()
        return listOf(TextRange(itemRanges.first().startOffset, itemRanges.last().endOffset))
    }
}
