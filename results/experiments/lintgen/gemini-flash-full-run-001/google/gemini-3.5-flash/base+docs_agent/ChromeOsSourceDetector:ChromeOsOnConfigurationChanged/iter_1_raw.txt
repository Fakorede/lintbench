package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Poor performance with APIs inside `onConfigurationChanged()`",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` method contains any code that can cause a redraw, your app might take a performance hit on large screens. To fix the issue, ensure your `onConfigurationChanged()` method does not contain any calls to UI redraw logic for specific elements.
                """,
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val REDRAW_METHODS = setOf(
            "recreate",
            "setContentView",
            "requestLayout",
            "invalidate",
            "finish"
        )
    }

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name == "onConfigurationChanged" && node.uastParameters.size == 1) {
                    val paramType = node.uastParameters[0].type.canonicalText
                    if (paramType == "android.content.res.Configuration" || paramType == "Configuration") {
                        node.uastBody?.accept(object : AbstractUastVisitor() {
                            override fun visitCallExpression(node: UCallExpression): Boolean {
                                val methodName = node.methodName
                                if (methodName in REDRAW_METHODS) {
                                    context.report(
                                        ISSUE,
                                        node,
                                        context.getLocation(node),
                                        "Avoid calling `$methodName` inside `onConfigurationChanged` as it can cause a redraw and performance hit on Chrome OS"
                                    )
                                }
                                return super.visitCallExpression(node)
                            }
                        })
                    }
                }
            }
        }
    }
}