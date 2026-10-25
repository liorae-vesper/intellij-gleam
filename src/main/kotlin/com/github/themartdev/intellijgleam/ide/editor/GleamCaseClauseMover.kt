package com.github.themartdev.intellijgleam.ide.editor

import com.github.themartdev.intellijgleam.lang.psi.GleamCaseClause
import com.github.themartdev.intellijgleam.lang.psi.GleamCaseClauses
import com.intellij.codeInsight.editorActions.moveUpDown.LineRange
import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

class GleamCaseClauseMover : StatementUpDownMover() {
    override fun checkAvailable(
        editor: Editor,
        file: PsiFile,
        info: MoveInfo,
        down: Boolean,
    ): Boolean {
        if (file.textLength == 0) return false

        val caretLine = editor.caretModel.logicalPosition.line
        val caretOffset = editor.caretModel.offset.coerceAtMost(file.textLength - 1)
        val element = file.findElementAt(caretOffset) ?: return false
        val clause = PsiTreeUtil.getParentOfType(element, GleamCaseClause::class.java) ?: return false
        val currentRange = LineRange(clause, clause, editor.document)

        // Only intercept movement when the caret is on the clause header. Body lines should
        // keep the IDE's normal statement movement behavior.
        if (caretLine != currentRange.startLine) return false

        val clauses = clause.parent as? GleamCaseClauses ?: return false

        val siblings = clauses.caseClauseList
        val clauseIndex = siblings.indexOf(clause)
        val targetIndex = clauseIndex + if (down) 1 else -1
        val target = siblings.getOrNull(targetIndex) ?: return info.prohibitMove()
        val targetRange = LineRange(target, target, editor.document)

        if (currentRange.startLine < targetRange.endLine && targetRange.startLine < currentRange.endLine) {
            return info.prohibitMove()
        }

        val gap = if (down) {
            file.text.substring(clause.textRange.endOffset, target.textRange.startOffset)
        } else {
            file.text.substring(target.textRange.endOffset, clause.textRange.startOffset)
        }
        if (gap.any { !it.isWhitespace() }) return info.prohibitMove()

        info.toMove = currentRange
        info.toMove2 = targetRange
        return true
    }
}
