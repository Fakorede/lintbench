package com.android.tools.lint.checks

import com.android.tools.lint.client.api.*
import com.android.tools.lint.detector.api.*
import com.intellij.psi.*
import org.jetbrains.uast.*

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ChromeOsSourceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Poor performance with APIs inside `onConfigurationChanged()`",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` method contains any code that can cause a redraw, your app might take a performance hit on large screens. To fix the issue, ensure your `onConfigurationChanged()` method does not contain any calls to UI redraw logic for specific elements.
            """.trimIndent(),
            category = Category.CHROME_OS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UCallExpression::class.java)
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("requestLayout", "invalidate", "postInvalidate", "forceLayout")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        checkCallExpression(context, node)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // No-op, managed via visitCallExpression
            }
    
            override fun visitCallExpression(node: UCallExpression) {
                checkCallExpression(context, node)
            }
        }

    private fun checkCallExpression(context: JavaContext, node: UCallExpression) {
        val methodName = node.methodName ?: return
        if (methodName == "requestLayout" || 
            methodName == "invalidate" || 
            methodName == "postInvalidate" || 
            methodName == "forceLayout") {
            
            val containingMethod = getContainingMethod(node) ?: return
            if (isOnConfigurationChanged(containingMethod)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid calling `$methodName` inside `onConfigurationChanged` as it can cause poor performance."
                )
            }
        }
    }

    private fun getContainingMethod(element: UElement): UMethod? {
        var current: UElement? = element.uastParent
        while (current != null) {
            if (current is UMethod) {
                return current
            }
            current = current.uastParent
        }
        return null
    }

    private fun isOnConfigurationChanged(method: UMethod): Boolean {
        if (method.name != "onConfigurationChanged") return false
        val parameters = method.uastParameters
        if (parameters.size != 1) return false
        val type = parameters[0].type.canonicalText
        return type == "android.content.res.Configuration"
    }
}