package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class IndentationDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        val SUSPICIOUS_INDENTATION = Issue.create(
            "SuspiciousIndentation",
            "Suspicious indentation suggests a grouping that isn't actually there in the code.",
            "This check looks for cases where the indentation suggests a grouping that isn't actually there in the code. A common example of this would be something like:\n" +
                    "```kotlin\n" +
                    "if (column > width)\n" +
                    "line++\n" +
                    "column = 0\n" +
                    "```\n" +
                    "Here, the `column = 0` line will be executed every single time, not just if the condition is true.",
            Category.CORRECTNESS,
            5,
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
                    checkSuspiciousIndentation(context, node, parent)
                }
            }

            private fun checkSuspiciousIndentation(
                context: JavaContext,
                node: UExpression,
                block: UBlockExpression
            ) {
                val lines = node.sourcePsi?.text?.split("\n") ?: return

                if (lines.size > 1) {
                    val firstLine = lines[0]
                    val secondLine = lines[1]

                    val firstIndentation = firstLine.length - firstLine.trimStart().length
                    val secondIndentation = secondLine.length - secondLine.trimStart().length

                    if (firstIndentation < secondIndentation && block.expressions.size > 1) {
                        context.report(
                            SUSPICIOUS_INDENTATION,
                            node,
                            context.getLocation(node),
                            "Suspicious indentation suggests a grouping that isn't actually there in the code."
                        )
                    }
                }
            }
        }
    }
}