package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.jetbrains.uast.visitor.UastVisitor

class ChromeOsSourceDetector : Detector(), Detector.UastScanner {

    companion object {
        private val REDRAW_METHODS = setOf(
            "requestLayout",
            "invalidate",
            "postInvalidate",
            "forceLayout",
            "setLayoutParams",
            "setVisibility"
        )

        val ISSUE = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Poor performance with APIs inside onConfigurationChanged()",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an \
                `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` \
                method contains any code that can cause a redraw, your app might take a performance \
                hit on large screens. To fix the issue, ensure your `onConfigurationChanged()` method \
                does not contain any calls to UI redraw logic for specific elements.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun createUastVisitor(context: JavaContext): UastVisitor {
        return object : AbstractUastVisitor() {
            override fun visitMethod(node: UMethod): Boolean {
                if (node.name == "onConfigurationChanged") {
                    val params = node.uastParameters
                    if (params.size == 1 && params[0].type?.canonicalText == "android.content.res.Configuration") {
                        node.uastBody?.accept(object : AbstractUastVisitor() {
                            override fun visitCallExpression(node: UCallExpression): Boolean {
                                val methodName = node.methodName
                                if (methodName in REDRAW_METHODS) {
                                    context.report(
                                        ISSUE,
                                        context.getLocation(node),
                                        "Avoid calling `$methodName()` inside `onConfigurationChanged()` as it may cause poor performance on large screens."
                                    )
                                }
                                return super.visitCallExpression(node)
                            }
                        })
                        return true
                    }
                }
                return super.visitMethod(node)
            }
        }
    }
}