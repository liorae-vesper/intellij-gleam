package com.github.themartdev.intellijgleam.ide.editor

import com.github.themartdev.intellijgleam.lang.GleamFileType
import com.github.themartdev.intellijgleam.lang.psi.GleamCallArguments
import com.github.themartdev.intellijgleam.lang.psi.GleamCaseSubjects
import com.github.themartdev.intellijgleam.lang.psi.GleamListExpr
import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GleamStructuralSelectionTest : LightPlatformCodeInsightFixture4TestCase() {
    @Test
    fun `call arguments expand as a single selection`() {
        myFixture.configureByText(GleamFileType, "fn main() { pair(first, second) }")
        val arguments = PsiTreeUtil.findChildOfType(myFixture.file, GleamCallArguments::class.java)!!

        val ranges = handler().select(
            arguments,
            myFixture.editor.document.immutableCharSequence,
            arguments.textOffset,
            myFixture.editor,
        )

        assertEquals(listOf("first, second"), ranges.map { myFixture.file.text.substring(it.startOffset, it.endOffset) })
    }

    @Test
    fun `case subjects expand together`() {
        myFixture.configureByText(GleamFileType, "fn main() { case first, second { _, _ -> Nil } }")
        val subjects = PsiTreeUtil.findChildOfType(myFixture.file, GleamCaseSubjects::class.java)!!

        val ranges = handler().select(
            subjects,
            myFixture.editor.document.immutableCharSequence,
            subjects.textOffset,
            myFixture.editor,
        )

        assertEquals(listOf("first, second"), ranges.map { myFixture.file.text.substring(it.startOffset, it.endOffset) })
    }

    @Test
    fun `single argument has no redundant group selection`() {
        myFixture.configureByText(GleamFileType, "fn main() { identity(value) }")
        val arguments = PsiTreeUtil.findChildOfType(myFixture.file, GleamCallArguments::class.java)!!

        val ranges = handler().select(
            arguments,
            myFixture.editor.document.immutableCharSequence,
            arguments.textOffset,
            myFixture.editor,
        )

        assertTrue(ranges.isEmpty())
    }

    @Test
    fun `list items expand as a single selection`() {
        myFixture.configureByText(GleamFileType, "fn main() { [first, second] }")
        val list = PsiTreeUtil.findChildOfType(myFixture.file, GleamListExpr::class.java)!!

        val ranges = handler().select(list, myFixture.editor.document.immutableCharSequence, list.textOffset, myFixture.editor)

        assertEquals(listOf("first, second"), ranges.map { myFixture.file.text.substring(it.startOffset, it.endOffset) })
    }

    private fun handler(): GleamStructuralSelectionHandler =
        ExtendWordSelectionHandler.EP_NAME.extensionList.filterIsInstance<GleamStructuralSelectionHandler>().single()
}
