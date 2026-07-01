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

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Poor performance with APIs inside onConfigurationChanged()",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an `onConfigurationChanged()` \
                API call occurs. If your `onConfigurationChanged()` method contains any code that can cause \
                a redraw, your app might take a performance hit on large screens. To fix the issue, ensure \
                your `onConfigurationChanged()` method does not contain any calls to UI redraw logic for \
                specific elements.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != "onConfigurationChanged") return
                val parameters = node.uastParameters
                if (parameters.size != 1) return
                if (!context.evaluator.parameterHasType(node, 0, "android.content.res.Configuration")) return

                node.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val method = node.resolve() ?: return super.visitCallExpression(node)
                        val methodName = method.name
                        val containingClass = method.containingClass ?: return super.visitCallExpression(node)

                        var isViolation = false
                        var message = ""

                        when (methodName) {
                            "recreate" -> {
                                if (context.evaluator.inheritsFrom(containingClass, "android.app.Activity", false)) {
                                    isViolation = true
                                    message = "Avoid calling `recreate()` inside `onConfigurationChanged` as it can trigger expensive redraws."
                                }
                            }
                            "setContentView" -> {
                                if (context.evaluator.inheritsFrom(containingClass, "android.app.Activity", false) ||
                                    context.evaluator.inheritsFrom(containingClass, "android.app.Dialog", false)) {
                                    isViolation = true
                                    message = "Avoid calling `setContentView()` inside `onConfigurationChanged` as it triggers complete UI redraws."
                                }
                            }
                            "inflate" -> {
                                if (context.evaluator.inheritsFrom(containingClass, "android.view.LayoutInflater", false) ||
                                    context.evaluator.inheritsFrom(containingClass, "android.view.View", false)) {
                                    isViolation = true
                                    message = "Avoid inflating layouts inside `onConfigurationChanged` to prevent performance hits."
                                }
                            }
                            "addView", "removeAllViews" -> {
                                if (context.evaluator.inheritsFrom(containingClass, "android.view.ViewGroup", false)) {
                                    isViolation = true
                                    message = "Avoid modifying views via `$methodName()` inside `onConfigurationChanged` to prevent performance hits."
                                }
                            }
                        }

                        if (isViolation) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                message
                            )
                        }

                        return super.visitCallExpression(node)
                    }
                })
            }
        }
    }
}