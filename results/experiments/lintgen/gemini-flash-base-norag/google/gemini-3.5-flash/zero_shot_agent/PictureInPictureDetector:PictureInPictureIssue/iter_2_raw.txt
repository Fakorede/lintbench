package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("build")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (isPipBuilder(context, node, method)) {
            checkBuilder(context, node)
        }
    }

    private fun isPipBuilder(context: JavaContext, node: UCallExpression, method: PsiMethod): Boolean {
        val evaluator = context.evaluator
        if (evaluator.isMemberInClass(method, "android.app.PictureInPictureParams.Builder")) {
            return true
        }
        if (method.containingClass?.qualifiedName == "android.app.PictureInPictureParams.Builder") {
            return true
        }
        if (node.receiverType?.canonicalText == "android.app.PictureInPictureParams.Builder") {
            return true
        }
        return isPipBuilderExpression(node.receiver)
    }

    private fun isPipBuilderExpression(expr: UExpression?, visited: MutableSet<PsiElement> = mutableSetOf()): Boolean {
        if (expr == null) return false
        val exprStr = expr.asSourceString()
        if (exprStr.contains("PictureInPictureParams.Builder")) {
            return true
        }
        var current = expr
        while (current is UCallExpression) {
            current = current.receiver
        }
        if (current is UReferenceExpression) {
            val resolved = current.resolve()
            if (resolved != null && visited.add(resolved)) {
                val uastElement = resolved.toUElementOfType<UVariable>()
                if (uastElement != null && isPipBuilderExpression(uastElement.uastInitializer, visited)) {
                    return true
                }
            }
        }
        return false
    }

    private fun checkBuilder(context: JavaContext, buildCall: UCallExpression) {
        var hasAutoEnter = false
        var hasSourceRect = false

        val builderVariables = mutableSetOf<PsiElement>()
        val visitedVariables = mutableSetOf<PsiElement>()

        fun follow(expr: UExpression?) {
            if (expr == null) return
            var current = expr
            while (current is UCallExpression) {
                val name = current.methodName
                if (name == "setAutoEnterEnabled") {
                    val args = current.valueArguments
                    if (args.isNotEmpty()) {
                        val argVal = ConstantEvaluator.evaluate(context, args[0])
                        if (argVal == true || args[0].asSourceString() == "true") {
                            hasAutoEnter = true
                        }
                    }
                } else if (name == "setSourceRectHint") {
                    hasSourceRect = true
                }
                current = current.receiver
            }

            if (current is UReferenceExpression) {
                val resolved = current.resolve()
                if (resolved != null && visitedVariables.add(resolved)) {
                    builderVariables.add(resolved)
                    val uastElement = resolved.toUElementOfType<UVariable>()
                    if (uastElement != null) {
                        follow(uastElement.uastInitializer)
                    }
                }
            }
        }

        follow(buildCall.receiver)

        if (builderVariables.isNotEmpty()) {
            val surroundingScope = buildCall.getParentOfType<UMethod>(UMethod::class.java)
                ?: buildCall.getParentOfType<UClass>(UClass::class.java)

            surroundingScope?.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val receiver = node.receiver
                    if (receiver is UReferenceExpression) {
                        val resolved = receiver.resolve()
                        if (resolved != null && builderVariables.contains(resolved)) {
                            val name = node.methodName
                            if (name == "setAutoEnterEnabled") {
                                val args = node.valueArguments
                                if (args.isNotEmpty()) {
                                    val argVal = ConstantEvaluator.evaluate(context, args[0])
                                    if (argVal == true || args[0].asSourceString() == "true") {
                                        hasAutoEnter = true
                                    }
                                }
                            } else if (name == "setSourceRectHint") {
                                hasSourceRect = true
                            }
                        }
                    }
                    return super.visitCallExpression(node)
                }
            })
        }

        if (!hasAutoEnter || !hasSourceRect) {
            val missing = mutableListOf<String>()
            if (!hasAutoEnter) missing.add("setAutoEnterEnabled(true)")
            if (!hasSourceRect) missing.add("setSourceRectHint(...)")

            val message = "To support smoother PiP transitions on Android 12 and higher, " +
                    "you should call ${missing.joinToString(" and ")} on the PictureInPictureParams.Builder."

            context.report(
                ISSUE,
                buildCall,
                context.getLocation(buildCall),
                message
            )
        }
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