package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.AbstractUastVisitor
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Poor performance with APIs inside onConfigurationChanged()",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an \
                `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` \
                method contains any code that can cause a redraw, your app might take a \
                performance hit on large screens. To fix the issue, ensure your \
                `onConfigurationChanged()` method does not contain any calls to UI redraw \
                logic for specific elements.
            """,
            category = Category.PERFORMANCE,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val ON_CONFIGURATION_CHANGED = "onConfigurationChanged"
        private const val CONFIGURATION_TYPE = "android.content.res.Configuration"

        private val REDRAW_METHODS = setOf(
            "invalidate",
            "requestLayout",
            "forceLayout",
            "setContentView",
            "recreate"
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (!isOnConfigurationChanged(node)) return

                node.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(call: UCallExpression): Boolean {
                        if (isRedrawCall(call, context)) {
                            val methodName = call.methodName ?: return super.visitCallExpression(call)
                            context.report(
                                ISSUE,
                                call,
                                context.getLocation(call),
                                "Avoid calling `$methodName()` inside `onConfigurationChanged()` because it can cause a redraw and degrade performance on large screens."
                            )
                        }
                        return super.visitCallExpression(call)
                    }
                })
            }
        }
    }

    private fun isOnConfigurationChanged(method: UMethod): Boolean {
        if (method.name != ON_CONFIGURATION_CHANGED) return false
        val parameters = method.uastParameters
        if (parameters.size != 1) return false
        val type = parameters[0].type?.canonicalText ?: return false
        return type == CONFIGURATION_TYPE
    }

    private fun isRedrawCall(call: UCallExpression, context: JavaContext): Boolean {
        val methodName = call.methodName ?: return false
        if (methodName !in REDRAW_METHODS) return false

        val psiMethod = call.resolve() ?: return false
        val containingClass = psiMethod.containingClass ?: return false
        val evaluator = context.evaluator

        return when (methodName) {
            "setContentView", "recreate" ->
                evaluator.extendsClass(containingClass, "android.app.Activity", false)
            "invalidate", "requestLayout", "forceLayout" ->
                evaluator.extendsClass(containingClass, "android.view.View", false)
            else -> false
        }
    }
}