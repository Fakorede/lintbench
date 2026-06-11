package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class IndentationDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        val SUSPICIOUS_INDENTATION = Issue.create(
            "SuspiciousIndentation",
            "Suspicious indentation suggests a grouping that isn't actually there in the code.",
            "This check looks for cases where the indentation suggests a grouping that isn't actually there in the code. A common example of this would be something like:\n" +
                    "\n" +
                    "```kotlin\n" +
                    "if (column > width)\n" +
                    "line++\n" +
                    "column = 0\n" +
                    "```\n" +
                    "Here, the `column = 0` line will be executed every single time, not just if the condition is true.",
            Severity.WARNING,
            Implementation(IndentationDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitExpression(node: UExpression) {
                val parent = node.uastParent
                if (parent is UBlockExpression && parent.expressions.size > 1) {
                    checkIndentation(context, parent)
                }
            }
        }
    }

    private fun checkIndentation(context: JavaContext, block: UBlockExpression) {
        val expressions = block.expressions
        for (i in 0 until expressions.size - 1) {
            val currentLine = expressions[i]
            val nextLine = expressions[i + 1]

            if (!isControlFlowStatement(currentLine)) {
                val currentIndent = getIndentationLevel(context, currentLine)
                val nextIndent = getIndentationLevel(context, nextLine)

                if (currentIndent == nextIndent) {
                    context.report(
                        SUSPICIOUS_INDENTATION,
                        nextLine,
                        "Suspicious indentation suggests a grouping that isn't actually there in the code."
                    )
                }
            }
        }
    }

    private fun isControlFlowStatement(expression: UExpression): Boolean {
        return expression is UIfExpression || expression is UForExpression ||
                expression is UWhileExpression || expression is USwitchExpression
    }

    private fun getIndentationLevel(context: JavaContext, node: UElement): Int {
        val psi = node.sourcePsi ?: return 0
        val document = context.file.viewProvider.document ?: return 0
        val startOffset = psi.textRange.startOffset
        var lineStartOffset = document.getLineStartOffset(document.getLineNumber(startOffset))
        while (lineStartOffset < startOffset && Character.isWhitespace(document.getText(lineStartOffset)[0])) {
            lineStartOffset++
        }
        return (startOffset - lineStartOffset) / 4 // assuming tab size is 4
    }
}