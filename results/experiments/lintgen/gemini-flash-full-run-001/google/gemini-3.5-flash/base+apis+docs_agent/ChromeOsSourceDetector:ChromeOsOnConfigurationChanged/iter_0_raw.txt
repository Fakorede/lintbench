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
                val paramType = parameters[0].type.canonicalText
                if (paramType != "android.content.res.Configuration") return

                node.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val methodName = node.methodName ?: return super.visitCallExpression(node)
                        val resolved = node.resolve() ?: return super.visitCallExpression(node)
                        val evaluator = context.evaluator

                        when (methodName) {
                            "recreate" -> {
                                if (evaluator.isMemberInSubClassOf(resolved, "android.app.Activity", false)) {
                                    report(node, "Avoid calling `recreate()` inside `onConfigurationChanged()` as it triggers expensive activity recreation.")
                                }
                            }
                            "setContentView" -> {
                                if (evaluator.isMemberInSubClassOf(resolved, "android.app.Activity", false) ||
                                    evaluator.isMemberInSubClassOf(resolved, "android.view.Window", false)) {
                                    report(node, "Avoid calling `setContentView()` inside `onConfigurationChanged()` as it triggers expensive layout inflation and redraw.")
                                }
                            }
                            "requestLayout" -> {
                                if (evaluator.isMemberInSubClassOf(resolved, "android.view.View", false)) {
                                    report(node, "Avoid calling `requestLayout()` inside `onConfigurationChanged()` as it triggers a full layout pass.")
                                }
                            }
                            "invalidate" -> {
                                if (evaluator.isMemberInSubClassOf(resolved, "android.view.View", false)) {
                                    report(node, "Avoid calling `invalidate()` inside `onConfigurationChanged()` as it triggers unnecessary redraws.")
                                }
                            }
                        }
                        return super.visitCallExpression(node)
                    }

                    private fun report(call: UCallExpression, message: String) {
                        context.report(
                            ISSUE,
                            call,
                            context.getLocation(call),
                            message
                        )
                    }
                })
            }
        }
    }
}