package com.github.themartdev.intellijgleam.ide.editor

import com.github.themartdev.intellijgleam.lang.GleamFileType
import com.github.themartdev.intellijgleam.IntellijGleamTest
import com.intellij.openapi.command.WriteCommandAction

class GleamSmartEnterProcessorTest : IntellijGleamTest() {
    fun testCompletesNestedMixedDelimitersInCurrentDeclaration() {
        complete(
            "pub fn main() { let value = [1, (2<caret>",
            "pub fn main() { let value = [1, (2)]}<caret>",
        )
    }

    fun testIgnoresDelimitersInStringAtCaret() {
        complete(
            "pub fn main() { let value = \"text (<caret>",
            "pub fn main() { let value = \"text (<caret>",
        )
    }

    fun testIgnoresDelimitersInCommentAtCaret() {
        complete(
            "pub fn main() { // [(<caret>",
            "pub fn main() { // [(<caret>",
        )
    }

    fun testCompletesNestedBitArrayDelimiters() {
        complete(
            "pub fn main() { let value = <<1, <<2<caret>",
            "pub fn main() { let value = <<1, <<2>>>>}<caret>",
        )
    }

    fun testDoesNotCloseDelimitersFromPreviousDeclaration() {
        complete(
            "pub fn first() { Nil }\npub fn second() { let value = [1<caret>",
            "pub fn first() { Nil }\npub fn second() { let value = [1]}<caret>",
        )
    }

    private fun complete(before: String, after: String) {
        myFixture.configureByText(GleamFileType, before)
        WriteCommandAction.runWriteCommandAction(project) {
            GleamSmartEnterProcessor().process(project, myFixture.editor, myFixture.file)
        }
        val expectedText = after.replace("<caret>", "")
        assertEquals(expectedText, myFixture.editor.document.text)
        assertEquals(after.indexOf("<caret>"), myFixture.editor.caretModel.offset)
    }
}
