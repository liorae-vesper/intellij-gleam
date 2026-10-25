package com.github.themartdev.intellijgleam.ide.editor

import com.github.themartdev.intellijgleam.lang.GleamFileType
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.junit.Test

class GleamCaseClauseMoverTest : LightPlatformCodeInsightFixture4TestCase() {
    @Test
    fun `moving a multiline case clause moves the whole clause`() {
        myFixture.configureByText(
            GleamFileType,
            """
            fn main() {
              case value {
                1 -> <caret>{
                  let result = first
                  result
                }
                2 -> second
              }
            }
            """.trimIndent(),
        )

        myFixture.performEditorAction("MoveStatementDown")

        myFixture.checkResult(
            """
            fn main() {
              case value {
                2 -> second
                1 -> {
                  let result = first
                  result
                }
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `moving a case clause up swaps it with the previous clause`() {
        myFixture.configureByText(
            GleamFileType,
            """
            fn main() {
              case value {
                1 -> first
                2 -> <caret>second
              }
            }
            """.trimIndent(),
        )

        myFixture.performEditorAction("MoveStatementUp")

        myFixture.checkResult(
            """
            fn main() {
              case value {
                2 -> second
                1 -> first
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `first and last clauses stay inside their case expression`() {
        myFixture.configureByText(
            GleamFileType,
            """
            fn main() {
              case value {
                <caret>1 -> first
                2 -> second
              }
            }
            """.trimIndent(),
        )

        myFixture.performEditorAction("MoveStatementUp")
        myFixture.checkResult(
            """
            fn main() {
              case value {
                <caret>1 -> first
                2 -> second
              }
            }
            """.trimIndent(),
        )

        myFixture.configureByText(
            GleamFileType,
            """
            fn main() {
              case value {
                1 -> first
                <caret>2 -> second
              }
            }
            """.trimIndent(),
        )

        myFixture.performEditorAction("MoveStatementDown")
        myFixture.checkResult(
            """
            fn main() {
              case value {
                1 -> first
                <caret>2 -> second
              }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `moving a clause does not cross a comment`() {
        myFixture.configureByText(
            GleamFileType,
            """
            fn main() {
              case value {
                1 -> <caret>first
                // Keep this explanation with the second clause.
                2 -> second
              }
            }
            """.trimIndent(),
        )

        myFixture.performEditorAction("MoveStatementDown")

        myFixture.checkResult(
            """
            fn main() {
              case value {
                1 -> <caret>first
                // Keep this explanation with the second clause.
                2 -> second
              }
            }
            """.trimIndent(),
        )
    }
}
