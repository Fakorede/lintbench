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
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.w3c.dom.Node

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.name != "build") return

        val isPipBuilder = method.containingClass?.qualifiedName == "android.app.PictureInPictureParams.Builder" ||
                context.evaluator.isMemberInClass(method, "android.app.PictureInPictureParams.Builder") ||
                isPipBuilderExpr(node.receiver)

        if (!isPipBuilder) {
            return
        }

        val (hasSetAutoEnterEnabled, hasSetSourceRectHint) = verifyBuilder(context, node)

        if (!hasSetAutoEnterEnabled || !hasSetSourceRectHint) {
            val message = when {
                !hasSetAutoEnterEnabled && !hasSetSourceRectHint -> {
                    "To support smoother transitions into Picture-in-Picture (PiP) mode on Android 12 and higher, call `setAutoEnterEnabled(true)` and `setSourceRectHint(...)` on the `PictureInPictureParams.Builder`."
                }
                !hasSetAutoEnterEnabled -> {
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

    private fun isPipBuilderExpr(expr: UExpression?): Boolean {
        if (expr == null) return false
        val type = expr.getExpressionType()?.canonicalText
        if (type == "android.app.PictureInPictureParams.Builder") return true

        val src = expr.asSourceString()
        if (src.contains("PictureInPictureParams.Builder")) return true

        if (expr is UCallExpression) {
            return isPipBuilderExpr(expr.receiver)
        }
        if (expr is UQualifiedReferenceExpression) {
            return isPipBuilderExpr(expr.selector) || isPipBuilderExpr(expr.receiver)
        }
        if (expr is UReferenceExpression) {
            val resolved = expr.resolve()
            if (resolved is PsiVariable) {
                val uLocal = resolved.toUElement() as? ULocalVariable
                val initializer = uLocal?.uastInitializer
                if (initializer != null) {
                    return isPipBuilderExpr(initializer)
                }
            }
        }
        return false
    }

    private fun verifyBuilder(context: JavaContext, buildCall: UCallExpression): Pair<Boolean, Boolean> {
        var hasSetAutoEnterEnabled = false
        var hasSetSourceRectHint = false

        fun checkCall(call: UCallExpression) {
            val name = call.methodName
            if (name == "setAutoEnterEnabled") {
                val args = call.valueArguments
                if (args.isNotEmpty()) {
                    val arg = args[0]
                    val constant = arg.evaluate()
                    if (constant == true) {
                        hasSetAutoEnterEnabled = true
                    } else {
                        val src = arg.asSourceString()
                        if (src == "true") {
                            hasSetAutoEnterEnabled = true
                        }
                    }
                }
            } else if (name == "setSourceRectHint") {
                hasSetSourceRectHint = true
            }
        }

        fun searchInElement(element: UElement) {
            element.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    checkCall(node)
                    return super.visitCallExpression(node)
                }
            })
        }

        val visitedExpressions = mutableSetOf<UExpression>()

        fun follow(expr: UExpression?) {
            if (expr == null || !visitedExpressions.add(expr)) return

            if (expr is UCallExpression) {
                checkCall(expr)
                val methodName = expr.methodName
                if (methodName == "apply" || methodName == "also" || methodName == "let" || methodName == "run") {
                    val args = expr.valueArguments
                    if (args.isNotEmpty()) {
                        searchInElement(args[0])
                    }
                }
                follow(expr.receiver)
            } else if (expr is UQualifiedReferenceExpression) {
                val selector = expr.selector
                if (selector is UCallExpression) {
                    checkCall(selector)
                    val methodName = selector.methodName
                    if (methodName == "apply" || methodName == "also" || methodName == "let" || methodName == "run") {
                        val args = selector.valueArguments
                        if (args.isNotEmpty()) {
                            searchInElement(args[0])
                        }
                    }
                }
                follow(expr.receiver)
            } else if (expr is UReferenceExpression) {
                val resolved = expr.resolve()
                if (resolved is PsiVariable) {
                    val uLocal = resolved.toUElement() as? ULocalVariable
                    val initializer = uLocal?.uastInitializer
                    if (initializer != null) {
                        follow(initializer)
                    }

                    val enclosingScope = buildCall.getParentOfType(UMethod::class.java)
                        ?: buildCall.getParentOfType(UClass::class.java)
                    enclosingScope?.accept(object : AbstractUastVisitor() {
                        override fun visitSimpleNameReferenceExpression(refNode: USimpleNameReferenceExpression): Boolean {
                            if (refNode.resolve() == resolved) {
                                val parent = refNode.uastParent
                                if (parent is UQualifiedReferenceExpression && parent.receiver == refNode) {
                                    val selector = parent.selector
                                    if (selector is UCallExpression) {
                                        checkCall(selector)
                                        val methodName = selector.methodName
                                        if (methodName == "apply" || methodName == "also" || methodName == "let" || methodName == "run") {
                                            val args = selector.valueArguments
                                            if (args.isNotEmpty()) {
                                                searchInElement(args[0])
                                            }
                                        }
                                    }
                                }
                            }
                            return super.visitSimpleNameReferenceExpression(refNode)
                        }
                    })
                }
            }
        }

        follow(buildCall.receiver)
        return Pair(hasSetAutoEnterEnabled, hasSetSourceRectHint)
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