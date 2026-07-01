package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
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
                When users resize the Android emulator in Android 13 and Chrome OS, an `onConfigurationChanged()` API call occurs. \
                If your `onConfigurationChanged()` method contains any code that can cause a redraw, your app might take a performance \
                hit on large screens. To fix the issue, ensure your `onConfigurationChanged()` method does not contain any calls to \
                UI redraw logic for specific elements.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val CONFIGURATION_CLASS = "android.content.res.Configuration"
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
                val paramType = parameters[0].type.canonicalText
                if (paramType != CONFIGURATION_CLASS) return

                node.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val methodName = node.methodName ?: return super.visitCallExpression(node)
                        if (isRedrawCall(methodName)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Avoid calling `$methodName` inside `onConfigurationChanged` as it can cause a redraw and affect performance on Chrome OS"
                            )
                        }
                        return super.visitCallExpression(node)
                    }
                })
            }
        }
    }

    private fun isRedrawCall(methodName: String): Boolean {
         return when (methodName) {
             "setContentView", "recreate", "inflate", "requestLayout", "invalidate" -> true
             else -> false
         }
    }
}