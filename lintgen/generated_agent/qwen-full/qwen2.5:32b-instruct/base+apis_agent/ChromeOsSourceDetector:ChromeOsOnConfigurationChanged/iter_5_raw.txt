package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

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
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("onConfigurationChanged")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.name == "onConfigurationChanged") {
            val visitor = object : UElementHandler() {
                override fun visitCallExpression(node: UCallExpression) {
                    val methodName = node.methodName
                    if (methodName != null && isRedrawMethod(methodName)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Avoid calling UI redraw methods inside `onConfigurationChanged()`"
                        )
                    }
                }

                private fun isRedrawMethod(name: String): Boolean {
                    val redrawMethods = listOf("invalidate", "requestLayout", "postInvalidate")
                    return redrawMethods.contains(name)
                }
            }
            node.accept(visitor)
        }
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.extendsList.any { it.text == "android.app.Activity" } ||
            declaration.extendsList.any { it.text == "androidx.fragment.app.Fragment" }) {
            val visitor = object : UElementHandler() {
                override fun visitCallExpression(node: UCallExpression) {
                    if (node.methodName == "onConfigurationChanged") {
                        node.accept(this)
                    }
                }

                private fun isRedrawMethod(name: String): Boolean {
                    val redrawMethods = listOf("invalidate", "requestLayout", "postInvalidate")
                    return redrawMethods.contains(name)
                }
            }
            declaration.accept(visitor)
        }
    }
}