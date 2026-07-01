package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

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

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

    override fun createUastHandler(): UElementHandler = object : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            if (node.name != "onConfigurationChanged") return

            val params = node.uastParameters
            if (params.size != 1) return
            if (params[0].type?.qualifiedName != "android.content.res.Configuration") return

            node.accept(object : AbstractUastVisitor() {
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
        }
    }
}