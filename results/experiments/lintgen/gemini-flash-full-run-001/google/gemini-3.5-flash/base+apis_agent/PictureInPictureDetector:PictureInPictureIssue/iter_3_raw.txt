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

        if (!isPictureInPictureBuilder(context, node)) {
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

    private fun isExpressionPipBuilder(expr: UExpression?): Boolean {
        if (expr == null) return false

        val type = expr.getExpressionType()?.canonicalText
        if (type != null && type.contains("PictureInPictureParams.Builder")) {
            return true
        }

        val src = expr.asSourceString()
        if (src.contains("PictureInPictureParams.Builder")) {
            return true
        }

        val knownMethods = listOf(
            "setAutoEnterEnabled",
            "setSourceRectHint",
            "setAspectRatio",
            "setActions",
            "setCloseAction",
            "setSeamlessResizeEnabled"
        )

        if (expr is UCallExpression) {
            val name = expr.methodName
            if (name in knownMethods) {
                return true
            }
            return isExpressionPipBuilder(expr.receiver)
        } else if (expr is UQualifiedReferenceExpression) {
            val selector = expr.selector
            if (selector is UCallExpression) {
                val name = selector.methodName
                if (name in knownMethods) {
                    return true
                }
            }
            return isExpressionPipBuilder(expr.receiver)
        } else if (expr is UReferenceExpression) {
            val resolved = expr.resolve()
            if (resolved != null) {
                val text = resolved.text
                if (text != null && text.contains("PictureInPictureParams.Builder")) {
                    return true
                }
            }
        }
        return false
    }

    private fun isPictureInPictureBuilder(context: JavaContext, buildCall: UCallExpression): Boolean {
        val containingClass = buildCall.resolve()?.containingClass?.qualifiedName
        if (containingClass == "android.app.PictureInPictureParams.Builder") {
            return true
        }

        val receiverType = buildCall.receiverType?.canonicalText
        if (receiverType != null && receiverType.contains("PictureInPictureParams.Builder")) {
            return true
        }

        if (buildCall.receiver != null) {
            if (isExpressionPipBuilder(buildCall.receiver)) {
                return true
            }
        } else {
            var parent: UElement? = buildCall.uastParent
            while (parent != null) {
                if (parent is UCallExpression) {
                    val name = parent.methodName
                    if (name in listOf("apply", "also", "let", "run")) {
                        val parentReceiver = parent.receiver
                        if (parentReceiver != null && isExpressionPipBuilder(parentReceiver)) {
                            return true
                        }
                    } else if (name == "with") {
                        val args = parent.valueArguments
                        if (args.isNotEmpty() && isExpressionPipBuilder(args[0])) {
                            return true
                        }
                    }
                }
                parent = parent.uastParent
            }
        }
        return false
    }

    private fun verifyBuilder(context: JavaContext, buildCall: UCallExpression): Pair<Boolean, Boolean> {
        var hasSetAutoEnterEnabled = false
        var hasSetSourceRectHint = false

        fun handleCall(call: UCallExpression) {
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

        fun checkChainAndLambdas(expr: UExpression?, onCall: (UCallExpression) -> Unit) {
            if (expr == null) return
            if (expr is UCallExpression) {
                onCall(expr)
                for (arg in expr.valueArguments) {
                    arg.accept(object : AbstractUastVisitor() {
                        override fun visitCallExpression(node: UCallExpression): Boolean {
                            onCall(node)
                            return super.visitCallExpression(node)
                        }
                    })
                }
                checkChainAndLambdas(expr.receiver, onCall)
            } else if (expr is UQualifiedReferenceExpression) {
                val selector = expr.selector
                if (selector is UCallExpression) {
                    onCall(selector)
                    for (arg in selector.valueArguments) {
                        arg.accept(object : AbstractUastVisitor() {
                            override fun visitCallExpression(node: UCallExpression): Boolean {
                                onCall(node)
                                return super.visitCallExpression(node)
                            }
                        })
                    }
                }
                checkChainAndLambdas(expr.receiver, onCall)
            }
        }

        checkChainAndLambdas(buildCall.receiver, ::handleCall)

        var builderVar: PsiElement? = null
        var current: UExpression? = buildCall.receiver
        if (current != null) {
            while (current != null) {
                if (current is UReferenceExpression) {
                    builderVar = current.resolve()
                    break
                } else if (current is UCallExpression) {
                    current = current.receiver
                } else if (current is UQualifiedReferenceExpression) {
                    current = current.receiver
                } else {
                    break
                }
            }
        } else {
            var parent: UElement? = buildCall.uastParent
            while (parent != null) {
                if (parent is UCallExpression) {
                    val name = parent.methodName
                    if (name in listOf("apply", "also", "let", "run")) {
                        val parentReceiver = parent.receiver
                        if (parentReceiver is UReferenceExpression) {
                            builderVar = parentReceiver.resolve()
                            break
                        }
                    } else if (name == "with") {
                        val args = parent.valueArguments
                        if (args.isNotEmpty()) {
                            val firstArg = args[0]
                            if (firstArg is UReferenceExpression) {
                                builderVar = firstArg.resolve()
                                break
                            }
                        }
                    }
                }
                parent = parent.uastParent
            }
        }

        fun isCallOnBuilder(call: UCallExpression, targetVar: PsiElement?): Boolean {
            if (targetVar == null) return false
            val receiver = call.receiver
            if (receiver is UReferenceExpression && receiver.resolve() == targetVar) {
                return true
            }
            var parent: UElement? = call.uastParent
            while (parent != null) {
                if (parent is UCallExpression) {
                    val name = parent.methodName
                    if (name in listOf("apply", "also", "let", "run")) {
                        val parentReceiver = parent.receiver
                        if (parentReceiver is UReferenceExpression && parentReceiver.resolve() == targetVar) {
                            return true
                        }
                    } else if (name == "with") {
                        val args = parent.valueArguments
                        if (args.isNotEmpty()) {
                            val firstArg = args[0]
                            if (firstArg is UReferenceExpression && firstArg.resolve() == targetVar) {
                                return true
                            }
                        }
                    }
                }
                parent = parent.uastParent
            }
            return false
        }

        if (builderVar != null) {
            val enclosingScope = buildCall.getParentOfType(UMethod::class.java)
                ?: buildCall.getParentOfType(UClass::class.java)
            enclosingScope?.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val name = node.methodName
                    if (name == "setAutoEnterEnabled" || name == "setSourceRectHint") {
                        if (isCallOnBuilder(node, builderVar)) {
                            handleCall(node)
                        }
                    }
                    return super.visitCallExpression(node)
                }
            })
        }

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