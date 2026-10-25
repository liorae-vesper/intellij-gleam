package com.github.themartdev.intellijgleam.ide.editor

import com.github.themartdev.intellijgleam.lang.lexer.GleamLexer
import com.github.themartdev.intellijgleam.lang.psi.GLEAM_COMMENTS
import com.github.themartdev.intellijgleam.lang.psi.GLEAM_ESCAPE
import com.github.themartdev.intellijgleam.lang.psi.GLEAM_STRINGS
import com.github.themartdev.intellijgleam.lang.psi.GleamTypes
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

/** Completes unmatched Gleam delimiters before the caret via Edit | Complete Statement. */
class GleamSmartEnterProcessor : SmartEnterProcessor() {
    override fun process(project: Project, editor: Editor, psiFile: PsiFile): Boolean {
        val document = editor.document
        val caretOffset = editor.caretModel.offset.coerceIn(0, document.textLength)
        val tokens = lex(document.charsSequence)
        val declarationStart = topLevelDeclarationStart(document.charsSequence, tokens, caretOffset)

        if (isCaretInCommentOrString(tokens, caretOffset)) return false

        val openDelimiters = mutableListOf<Delimiter>()
        for ((type, start, _) in tokens) {
            if (start < declarationStart) continue
            when {
                type in OPEN_TO_CLOSE -> openDelimiters.add(Delimiter(type, start))
                type in CLOSE_TO_OPEN -> {
                    val matchingOpen = openDelimiters.indexOfLast { it.type == CLOSE_TO_OPEN[type] }
                    if (matchingOpen >= 0) openDelimiters.removeAt(matchingOpen)
                }
            }
        }

        val missingClosers = openDelimiters
            .asSequence()
            .filter { it.offset < caretOffset }
            .sortedByDescending { it.offset }
            .map { OPEN_TO_CLOSE.getValue(it.type).toString() }
            .toList()
        if (missingClosers.isEmpty()) return false

        val closingText = missingClosers.joinToString("")
        document.insertString(caretOffset, closingText)
        editor.caretModel.moveToOffset(caretOffset + closingText.length)
        return true
    }

    private fun topLevelDeclarationStart(text: CharSequence, tokens: List<Token>, caretOffset: Int): Int {
        var declarationStart = 0
        for ((type, start, _) in tokens) {
            if (start >= caretOffset) break
            if (type !in TOP_LEVEL_DECLARATION_STARTS) continue
            val lineStart = text.lastIndexOf('\n', start - 1).let { if (it < 0) 0 else it + 1 }
            if (text.subSequence(lineStart, start).any { it != ' ' && it != '\t' }) continue
            declarationStart = start
        }
        return declarationStart
    }

    private fun isCaretInCommentOrString(tokens: List<Token>, caretOffset: Int): Boolean {
        var insideString = false
        for ((type, start, end) in tokens) {
            if (type in GLEAM_COMMENTS && start <= caretOffset && caretOffset <= end) return true
            if (start >= caretOffset) break
            when {
                type == GleamTypes.OPEN_QUOTE -> insideString = true
                type == GleamTypes.CLOSE_QUOTE -> insideString = false
                type in GLEAM_STRINGS || type in GLEAM_ESCAPE || type == GleamTypes.UNICODE_CODEPOINT -> insideString = true
            }
        }
        return insideString
    }

    private fun lex(text: CharSequence): List<Token> {
        val lexer = GleamLexer()
        lexer.start(text)
        val tokens = mutableListOf<Token>()
        while (lexer.tokenType != null) {
            tokens.add(Token(lexer.tokenType!!, lexer.tokenStart, lexer.tokenEnd))
            lexer.advance()
        }
        return tokens
    }

    private data class Token(val type: IElementType, val start: Int, val end: Int)
    private data class Delimiter(val type: IElementType, val offset: Int)

    private companion object {
        val TOP_LEVEL_DECLARATION_STARTS = setOf(
            GleamTypes.IMPORT,
            GleamTypes.CONST,
            GleamTypes.TYPE,
            GleamTypes.FN,
            GleamTypes.PUB,
            GleamTypes.OPAQUE,
            GleamTypes.ANNOTATION_MARK,
        )
        val OPEN_TO_CLOSE = mapOf(
            GleamTypes.LBRACE to GleamTypes.RBRACE,
            GleamTypes.LBRACK to GleamTypes.RBRACK,
            GleamTypes.LPAREN to GleamTypes.RPAREN,
            GleamTypes.LT_LT to GleamTypes.GT_GT,
        )
        val CLOSE_TO_OPEN = OPEN_TO_CLOSE.entries.associate { (open, close) -> close to open }
    }
}
