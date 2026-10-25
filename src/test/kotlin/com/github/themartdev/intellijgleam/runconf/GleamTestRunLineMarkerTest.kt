package com.github.themartdev.intellijgleam.runconf

import com.github.themartdev.intellijgleam.ide.runconf.run.GleamRunLineMarker
import com.github.themartdev.intellijgleam.ide.runconf.run.GleamTestConfiguration
import com.github.themartdev.intellijgleam.ide.runconf.run.GleamTestRunConfigurationProducer
import com.github.themartdev.intellijgleam.ide.runconf.run.GleamTestSupport
import com.github.themartdev.intellijgleam.lang.psi.GleamFunctionDeclaration
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GleamTestRunLineMarkerTest : LightPlatformCodeInsightFixture4TestCase() {
    @Test
    fun `unitest test functions have a run gutter marker`() {
        myFixture.addFileToProject(
            "gleam.toml",
            """
            name = "my_app"
            version = "1.0.0"

            [dev-dependencies]
            unitest = "~> 1.0"
            """.trimIndent(),
        )
        val file = myFixture.addFileToProject(
            "test/my_app_test.gleam",
            "pub fn add_test() { assert 1 + 1 == 2 }",
        )

        val declaration = PsiTreeUtil.findChildOfType(file, GleamFunctionDeclaration::class.java)
        assertNotNull("Expected function declaration in test PSI", declaration)
        assertEquals("my_app_test.add_test", GleamTestSupport.selectorFor(declaration!!))
        assertTrue("Expected Unitest dependency in manifest", GleamTestSupport.isUnitestProject(file.virtualFile))
        assertNotNull(markerForFunction(file, "add_test"))
    }

    @Test
    fun `default gleeunit test functions do not get an unsupported single test marker`() {
        myFixture.addFileToProject(
            "gleam.toml",
            """
            name = "my_app"
            version = "1.0.0"

            [dev-dependencies]
            gleeunit = "~> 1.0"
            """.trimIndent(),
        )
        val file = myFixture.addFileToProject(
            "test/my_app_test.gleam",
            "pub fn add_test() { assert 1 + 1 == 2 }",
        )

        assertNull(markerForFunction(file, "add_test"))
    }

    @Test
    fun `run configuration producer targets the selected nested test`() {
        myFixture.addFileToProject(
            "gleam.toml",
            """
            name = "my_app"
            version = "1.0.0"

            [dev-dependencies]
            unitest = "~> 1.0"
            """.trimIndent(),
        )
        val file = myFixture.addFileToProject("test/foo/bar_test.gleam", "pub fn add_test() { assert True }")
        val element = file.findElementAt(file.text.indexOf("add_test"))!!
        val producer = GleamTestRunConfigurationProducer()
        val produced = producer.createConfigurationFromContext(ConfigurationContext(element))

        assertNotNull("Expected a test run configuration from the test function", produced)
        val configuration = produced!!.configuration as com.github.themartdev.intellijgleam.ide.runconf.run.GleamTestConfiguration
        assertEquals("foo/bar_test.add_test", configuration.options.testName)
        assertTrue(producer.isConfigurationFromContext(configuration, ConfigurationContext(element)))
    }

    private fun markerForFunction(file: PsiFile, functionName: String) =
        file.findElementAt(file.text.indexOf(functionName))?.let { GleamRunLineMarker().getInfo(it) }
}
