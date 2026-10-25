package com.github.themartdev.intellijgleam.ide.editor.templates

import com.github.themartdev.intellijgleam.lang.GleamLanguage
import com.github.themartdev.intellijgleam.lang.GleamFileType
import com.intellij.codeInsight.template.CustomTemplateCallback
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.codeInsight.template.postfix.templates.LanguagePostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GleamTemplatesTest : LightPlatformCodeInsightFixture4TestCase() {
    @Test
    fun `test live template is available at top level`() {
        val template = TemplateSettings.getInstance().getTemplate("test", "Gleam")

        assertNotNull("Expected a Gleam test-function live template", template)
        assertTrue(template!!.string.startsWith("fn test_"))
    }

    @Test
    fun `live template group contains the Gleam declarations and expressions`() {
        val abbreviations = TemplateSettings.getInstance()
            .templatesAsList
            .filter { it.groupName == "Gleam" }
            .map { it.key }
            .toSet()

        assertTrue(
            "Loaded Gleam live-template abbreviations: $abbreviations",
            abbreviations.containsAll(setOf("pubfn", "test", "const", "let", "assert", "todo", "echo")),
        )
    }

    @Test
    fun `let template provides useful initial values`() {
        val template = TemplateSettings.getInstance().getTemplate("let", "Gleam")!!

        assertEquals("value", template.getDefaultValueStringAt(0))
        assertEquals("todo", template.getDefaultValueStringAt(1))
    }

    @Test
    fun `postfix provider registers Gleam expression templates`() {
        val provider = LanguagePostfixTemplate.LANG_EP.forLanguage(GleamLanguage)

        assertNotNull("Expected a postfix template provider for Gleam", provider)
        assertEquals(setOf(".block", ".case", ".echo"), provider!!.templates.map { it.key }.toSet())
    }

    @Test
    fun `case postfix template wraps the preceding expression`() {
        myFixture.configureByText(GleamFileType, "fn main() { value<caret> }")
        val provider = LanguagePostfixTemplate.LANG_EP.forLanguage(GleamLanguage)!!
        val template = provider.templates.single { it.key == ".case" }
        val expressionContext = myFixture.file.findElementAt(myFixture.editor.caretModel.offset - 1)!!
        assertTrue(
            "Expression context was not recognized: ${expressionContext.text} at ${myFixture.editor.caretModel.offset}",
            template.isApplicable(expressionContext, myFixture.editor.document, myFixture.editor.caretModel.offset),
        )

        myFixture.configureByText(
            GleamFileType,
            """
            fn main() {
              value.case<caret>
            }
            """.trimIndent()
        )

        val callback = CustomTemplateCallback(myFixture.editor, myFixture.file)
        val postfixLiveTemplate = PostfixLiveTemplate()
        postfixLiveTemplate.expand(".case", callback)

        val expected =
            """
            fn main() {
              case value {
                _ -> todo
              }
            }
            """.trimIndent()
        assertEquals(expected, myFixture.file.text)
        assertEquals(expected.indexOf("todo"), myFixture.editor.caretModel.offset)
    }

    @Test
    fun `echo postfix template emits an echo expression`() {
        assertEquals(
            """
            fn main() {
              echo value
            }
            """.trimIndent(),
            expandPostfix("echo"),
        )
    }

    @Test
    fun `block postfix template wraps the expression`() {
        assertEquals(
            """
            fn main() {
              {
                value
              }
            }
            """.trimIndent(),
            expandPostfix("block"),
        )
    }

    private fun expandPostfix(suffix: String): String {
        myFixture.configureByText(
            GleamFileType,
            """
            fn main() {
              value.$suffix<caret>
            }
            """.trimIndent(),
        )

        PostfixLiveTemplate().expand(".$suffix", CustomTemplateCallback(myFixture.editor, myFixture.file))
        return myFixture.file.text
    }
}
