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

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (!isOnConfigurationChanged(node)) {
                    return
                }

                val body = node.uastBody ?: return
                body.accept(object : AbstractUastVisitor() {
                    private var methodDepth = 0

                    override fun visitMethod(node: UMethod): Boolean {
                        methodDepth++
                        return super.visitMethod(node)
                    }

                    override fun afterVisitMethod(node: UMethod) {
                        methodDepth--
                        super.afterVisitMethod(node)
                    }

                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val methodName = node.methodName
                        if (methodDepth == 0 && methodName in REDRAW_METHODS) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Avoid calling `$methodName()` inside `onConfigurationChanged()` " +
                                    "because it can trigger a UI redraw and cause performance issues " +
                                    "on large screens (Chrome OS and Android 13+)."
                            )
                        }
                        return super.visitCallExpression(node)
                    }
                })
            }
        }
    }

    private fun isOnConfigurationChanged(node: UMethod): Boolean {
        if (node.name != "onConfigurationChanged") return false
        val params = node.uastParameters
        if (params.size != 1) return false
        val paramType = params[0].type
        return paramType.canonicalText == "android.content.res.Configuration" ||
            paramType.presentableText == "Configuration"
    }

    companion object {
        private val REDRAW_METHODS = setOf(
            "invalidate",
            "requestLayout",
            "forceLayout",
            "setText",
            "setTextSize",
            "setTextColor",
            "setTypeface",
            "setImageDrawable",
            "setImageBitmap",
            "setImageResource",
            "setImageURI",
            "setBackground",
            "setBackgroundDrawable",
            "setBackgroundResource",
            "setBackgroundColor",
            "setPadding",
            "setPaddingRelative",
            "setLayoutParams",
            "setVisibility",
            "setAlpha",
            "setTranslationX",
            "setTranslationY",
            "setScaleX",
            "setScaleY",
            "setRotation",
            "setRotationX",
            "setRotationY"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Avoid UI redraw calls in onConfigurationChanged()",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an \
                `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` \
                method contains any code that can cause a redraw, your app might take a \
                performance hit on large screens. To fix the issue, ensure your \
                `onConfigurationChanged()` method does not contain any calls to UI redraw \
                logic for specific elements.
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