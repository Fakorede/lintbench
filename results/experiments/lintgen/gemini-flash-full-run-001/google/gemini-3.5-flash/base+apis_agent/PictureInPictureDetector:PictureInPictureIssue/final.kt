package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.w3c.dom.Node

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "android.app.PictureInPictureParams.Builder")) {
            return
        }

        val config = BuilderConfiguration()
        val variablesToTrack = mutableSetOf<PsiElement>()

        // 1. Analyze the receiver chain of the build() call
        analyzeExpression(node.receiver, config, variablesToTrack)

        // 2. If we found any variables, search the enclosing method for calls on those variables
        if (variablesToTrack.isNotEmpty()) {
            val enclosingMethod = node.getParentOfType(UMethod::class.java)
            if (enclosingMethod != null) {
                enclosingMethod.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val name = node.methodName
                        if (name == "setAutoEnterEnabled" || name == "setSourceRectHint") {
                            if (isCallOnTrackedVariable(node, variablesToTrack)) {
                                checkCall(node, config, variablesToTrack)
                            }
                        }
                        return super.visitCallExpression(node)
                    }
                })
            }
        }

        if (!config.hasSetAutoEnterEnabled || !config.hasSetSourceRectHint) {
            val message = when {
                !config.hasSetAutoEnterEnabled && !config.hasSetSourceRectHint -> {
                    "To support smoother transitions into Picture-in-Picture (PiP) mode on Android 12 and higher, call `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on the `PictureInPictureParams.Builder`."
                }
                !config.hasSetAutoEnterEnabled -> {
                    "To support smoother transitions into Picture-in-Picture (PiP) mode on Android 12 and higher, call `setAutoEnterEnabled(true)` on the `PictureInPictureParams.Builder`."
                }
                else -> {
                    "To support smoother transitions into Picture-in-Picture (PiP) mode on Android 12 and higher, call `setSourceRectHint(...)` on the `PictureInPictureParams.Builder`."
                }
            }
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                message
            )
        }
    }

    private fun analyzeExpression(
        expression: UExpression?,
        config: BuilderConfiguration,
        variablesToTrack: MutableSetOf<PsiElement>
    ) {
        var current = expression
        while (current != null) {
            if (current is UCallExpression) {
                checkCall(current, config, variablesToTrack)
                for (arg in current.valueArguments) {
                    arg.accept(object : AbstractUastVisitor() {
                        override fun visitCallExpression(node: UCallExpression): Boolean {
                            val name = node.methodName
                            if (name == "setAutoEnterEnabled" || name == "setSourceRectHint") {
                                checkCall(node, config, variablesToTrack)
                            }
                            return super.visitCallExpression(node)
                        }
                    })
                }
                current = current.receiver
            } else if (current is UQualifiedReferenceExpression) {
                val selector = current.selector
                if (selector is UCallExpression) {
                    checkCall(selector, config, variablesToTrack)
                    for (arg in selector.valueArguments) {
                        arg.accept(object : AbstractUastVisitor() {
                            override fun visitCallExpression(node: UCallExpression): Boolean {
                                val name = node.methodName
                                if (name == "setAutoEnterEnabled" || name == "setSourceRectHint") {
                                    checkCall(node, config, variablesToTrack)
                                }
                                return super.visitCallExpression(node)
                            }
                        })
                    }
                }
                current = current.receiver
            } else if (current is UReferenceExpression) {
                val resolved = current.resolve()
                if (resolved != null) {
                    variablesToTrack.add(resolved)
                }
                break
            } else {
                break
            }
        }
    }

    private fun checkCall(
        call: UCallExpression,
        config: BuilderConfiguration,
        variablesToTrack: MutableSetOf<PsiElement>
    ) {
        val name = call.methodName
        if (name == "setAutoEnterEnabled") {
            val args = call.valueArguments
            if (args.isNotEmpty()) {
                val arg = args[0]
                val constant = arg.evaluate()
                if (constant == true) {
                    config.hasSetAutoEnterEnabled = true
                } else {
                    val src = arg.asSourceString()
                    if (src == "true") {
                        config.hasSetAutoEnterEnabled = true
                    }
                }
            }
        } else if (name == "setSourceRectHint") {
            config.hasSetSourceRectHint = true
        }
    }

    private fun isCallOnTrackedVariable(
        call: UCallExpression,
        variablesToTrack: Set<PsiElement>
    ): Boolean {
        val receiver = call.receiver
        if (receiver is UReferenceExpression) {
            val resolved = receiver.resolve()
            if (resolved != null && variablesToTrack.contains(resolved)) {
                return true
            }
        }
        var parent: UElement? = call.uastParent
        while (parent != null) {
            val p = parent
            if (p is UCallExpression) {
                val name = p.methodName
                if (name in listOf("apply", "also", "let", "run")) {
                    val parentReceiver = p.receiver
                    if (parentReceiver is UReferenceExpression) {
                        val resolved = parentReceiver.resolve()
                        if (resolved != null && variablesToTrack.contains(resolved)) {
                            return true
                        }
                    }
                } else if (name == "with") {
                    val args = p.valueArguments
                    if (args.isNotEmpty()) {
                        val firstArg = args[0]
                        if (firstArg is UReferenceExpression) {
                            val resolved = firstArg.resolve()
                            if (resolved != null && variablesToTrack.contains(resolved)) {
                                return true
                            }
                        }
                    }
                }
            }
            parent = p.uastParent
        }
        return false
    }

    private class BuilderConfiguration {
        var hasSetAutoEnterEnabled = false
        var hasSetSourceRectHint = false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) has changed. If your app does not use the new approach, your app's transition animations will be of poor quality compared to other apps. The new approach requires calling `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}