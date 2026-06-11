package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import java.util.*

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "ChromeOsPerformance",
            briefDescription = "Poor performance with APIs inside `onConfigurationChanged()`",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` method contains any code that can cause a redraw, your app might take a performance hit on large screens.
                
                To fix the issue, ensure your `onConfigurationChanged()` method does not contain any calls to UI redraw logic for specific elements.
            """,
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val REDRAW_METHODS = listOf("invalidate", "requestLayout")
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("onConfigurationChanged")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.name == "onConfigurationChanged") {
            val visitor = object : UastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val methodName = node.methodName ?: return true
                    if (REDRAW_METHODS.contains(methodName)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Avoid calling redraw methods inside `onConfigurationChanged()`"
                        )
                    }
                    return super.visitCallExpression(node)
                }
            }

            node.accept(visitor)
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                super.visitClass(node)

                val visitor = object : UastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val methodName = node.methodName ?: return true
                        if (methodName == "onConfigurationChanged") {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Ensure `onConfigurationChanged()` does not contain any calls to UI redraw logic"
                            )
                        }
                        return super.visitCallExpression(node)
                    }
                }

                node.accept(visitor)
            }
        }
    }
}