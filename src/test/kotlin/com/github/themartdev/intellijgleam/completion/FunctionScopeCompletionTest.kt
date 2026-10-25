package com.github.themartdev.intellijgleam.completion

import org.junit.Test

class FunctionScopeCompletionTest : GleamCompletionTestBase() {

    @Test
    fun testEmptyFunction() = validateCompletionContains(
        """
        fn main() {
            <caret>
        }
        """,
        listOf("fn", "if", "let", "case", "assert", "todo", "panic", "use")
    )

    @Test
    fun testCompleteKeyword() = completeBasic(
        """
        fn main() {
            ca<caret>
        }
        """,
        """
        fn main() {
            case
        }
        """
    )

    @Test
    fun testCompletesCallbackWithExpectedArity() {
        myFixture.configureByText(
            "main.gleam",
            """
            fn with_callback(value: #(Int, Int), callback: fn(Int, String) -> Bool) -> Bool {
              callback(1, "value")
            }

            fn main() {
              with_callback(#(1, 2), fn<caret>)
            }
            """.trimIndent(),
        )

        val closureItem = myFixture.completeBasic()
            ?.firstOrNull { it.lookupString == "fn(" }
        assertNotNull("Expected an anonymous function completion", closureItem)

        myFixture.lookup!!.currentItem = closureItem
        myFixture.finishLookup('\n')
        myFixture.checkResult(
            """
            fn with_callback(value: #(Int, Int), callback: fn(Int, String) -> Bool) -> Bool {
              callback(1, "value")
            }

            fn main() {
              with_callback(#(1, 2), fn(arg1, arg2) { <caret>todo })
            }
            """.trimIndent(),
        )
    }

    @Test
    fun testDoesNotOfferClosureForNonFunctionArgument() {
        myFixture.configureByText(
            "main.gleam",
            """
            fn consume(value: Int) { value }
            fn main() { consume(fn<caret>) }
            """.trimIndent(),
        )

        val lookupElements = myFixture.completeBasic()
        assertFalse(lookupElements.orEmpty().any { it.lookupString == "fn(" })
    }

    @Test
    fun testCompletesCallbackFromImportedModule() {
        myFixture.addFileToProject(
            "src/util.gleam",
            """
            pub type Callback = fn(Int, String) -> Bool

            pub fn with_callback(callback: Callback) -> Bool {
              callback(1, "value")
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "main.gleam",
            """
            import util

            fn main() {
              util.with_callback(fn<caret>)
            }
            """.trimIndent(),
        )

        val closureItem = myFixture.completeBasic()
            ?.firstOrNull { it.lookupString == "fn(" }
        assertNotNull("Expected a callback completion from the imported function", closureItem)
    }

    @Test
    fun testCompletesCallbackTypeAlias() {
        myFixture.configureByText(
            "main.gleam",
            """
            type Callback = fn(Int, String) -> Bool

            fn with_callback(callback: Callback) -> Bool {
              callback(1, "value")
            }

            fn main() {
              with_callback(fn<caret>)
            }
            """.trimIndent(),
        )

        val closureItem = myFixture.completeBasic()
            ?.firstOrNull { it.lookupString == "fn(" }
        assertNotNull("Expected an anonymous function completion through a type alias", closureItem)
    }

}
