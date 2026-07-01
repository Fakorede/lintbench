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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement?>> = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != "onConfigurationChanged") return
                if (node.uastParameters.size != 1) return

                node.uastBody?.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(call: UCallExpression): Boolean {
                        val methodName = call.methodName ?: return super.visitCallExpression(call)
                        if (methodName in PROBLEMATIC_METHODS) {
                            context.report(
                                ISSUE,
                                call,
                                context.getLocation(call),
                                "Avoid calling `$methodName()` inside `onConfigurationChanged()`; " +
                                        "it can trigger a UI redraw or activity lifecycle change " +
                                        "and cause performance issues on Chrome OS and large-screen " +
                                        "devices."
                            )
                        }
                        return super.visitCallExpression(call)
                    }
                })
            }
        }
    }

    companion object {
        private val PROBLEMATIC_METHODS = setOf(
            "finish",
            "invalidate",
            "postInvalidate",
            "requestLayout",
            "forceLayout"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Poor performance with APIs inside onConfigurationChanged()",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an \
                `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` \
                method contains any code that can cause a redraw or activity lifecycle change, \
                your app might take a performance hit on large screens. To fix the issue, \
                ensure your `onConfigurationChanged()` method does not contain any calls to \
                UI redraw logic or activity finish/recreate for specific elements.
            """,
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}