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
            briefDescription = "Poor performance with APIs inside `onConfigurationChanged()`",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an `onConfigurationChanged()` \
                API call occurs. If your `onConfigurationChanged()` method contains any code that can cause a redraw, \
                your app might take a performance hit on large screens. To fix the issue, ensure your \
                `onConfigurationChanged()` method does not contain any calls to UI redraw logic for specific elements.
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

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name == "onConfigurationChanged" && node.uastParameters.size == 1) {
                    val firstParam = node.uastParameters[0]
                    if (context.evaluator.typeMatches(firstParam.type, "android.content.res.Configuration")) {
                        node.accept(ConfigurationChangedVisitor(context))
                    }
                }
            }
        }
    }

    private class ConfigurationChangedVisitor(private val context: JavaContext) : AbstractUastVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
            val methodName = node.methodName ?: return super.visitCallExpression(node)
            if (isForbiddenMethod(methodName, node)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid calling `$methodName` inside `onConfigurationChanged` as it can cause performance hits during resizes."
                )
            }
            return super.visitCallExpression(node)
        }

        private fun isForbiddenMethod(name: String, node: UCallExpression): Boolean {
            val method = node.resolve()
            if (method == null) {
                // Fallback if we cannot resolve the method
                return name in listOf("recreate", "setContentView", "requestLayout", "invalidate", "inflate", "finish")
            }
            val containingClass = method.containingClass ?: return false
            return when (name) {
                "recreate" -> context.evaluator.inheritsFrom(containingClass, "android.app.Activity", false)
                "setContentView" -> context.evaluator.inheritsFrom(containingClass, "android.app.Activity", false)
                "requestLayout" -> context.evaluator.inheritsFrom(containingClass, "android.view.View", false)
                "invalidate" -> context.evaluator.inheritsFrom(containingClass, "android.view.View", false)
                "inflate" -> {
                    val qName = containingClass.qualifiedName
                    qName == "android.view.LayoutInflater" || qName == "android.view.View"
                }
                "finish" -> context.evaluator.inheritsFrom(containingClass, "android.app.Activity", false)
                else -> false
            }
        }
    }
}