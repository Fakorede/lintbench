package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiParameter
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("enterPictureInPictureMode", "build")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, visitor: JavaElementVisitor) {
        when (node.methodName) {
            "enterPictureInPictureMode" -> {
                if (node.valueArguments.isEmpty() &&
                    context.evaluator.isMemberInClass(node.resolve(), "android.app.Activity")
                ) {
                    reportIssue(
                        context,
                        node,
                        "Use enterPictureInPictureMode(PictureInPictureParams) and configure the builder with setAutoEnterEnabled(true) and setSourceRectHint(...)"
                    )
                }
            }
            "build" -> {
                if (context.evaluator.isMemberInClass(node.resolve(), BUILDER_CLASS)) {
                    checkBuilder(context, node)
                }
            }
        }
    }

    private fun checkBuilder(context: JavaContext, buildCall: UCallExpression) {
        val top = getTopExpression(buildCall)
        val (base, chainSetters) = getBaseAndSetters(top)
        val found = chainSetters.toMutableSet()

        when {
            base is UCallExpression &&
                base.isConstructorCall() &&
                context.evaluator.isMemberInClass(base.resolve(), BUILDER_CLASS) -> {
                found += collectBuilderSettersInSubtree(context, top)
                if (!found.containsAll(REQUIRED_METHODS)) {
                    reportIssue(context, buildCall)
                }
            }
            base is USimpleNameReferenceExpression -> {
                val exprType = base.getExpressionType() ?: return
                if (!context.evaluator.typeMatches(exprType, BUILDER_CLASS)) return

                val target = base.resolve() ?: return
                if (target !is PsiLocalVariable && target !is PsiParameter) return

                found += collectBuilderSettersInSubtree(context, top)

                val method = buildCall.getParentOfType(UMethod::class.java) ?: return
                found += collectSettersOnVariable(context, method, target)

                if (!found.containsAll(REQUIRED_METHODS)) {
                    reportIssue(context, buildCall)
                }
            }
        }
    }

    private fun getTopExpression(node: UCallExpression): UExpression {
        var current: UElement = node
        while (current.uastParent is UQualifiedReferenceExpression &&
            (current.uastParent as UQualifiedReferenceExpression).selector == current
        ) {
            current = current.uastParent as UQualifiedReferenceExpression
        }
        return current as UExpression
    }

    private fun getBaseAndSetters(top: UExpression): Pair<UExpression?, Set<String>> {
        val setters = mutableSetOf<String>()
        var current: UExpression? = top as? UQualifiedReferenceExpression
        while (current is UQualifiedReferenceExpression) {
            val selector = current.selector
            if (selector is UCallExpression) {
                val name = selector.methodName
                if (name in REQUIRED_METHODS && isValidSetterCall(name, selector)) {
                    setters.add(name)
                }
            }
            current = current.receiver
        }
        return Pair(current, setters)
    }

    private fun collectBuilderSettersInSubtree(context: JavaContext, root: UElement): Set<String> {
        val found = mutableSetOf<String>()
        root.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val name = node.methodName
                if (name in REQUIRED_METHODS &&
                    isValidSetterCall(name, node) &&
                    context.evaluator.isMemberInClass(node.resolve(), BUILDER_CLASS)
                ) {
                    found.add(name)
                }
                return super.visitCallExpression(node)
            }
        })
        return found
    }

    private fun collectSettersOnVariable(
        context: JavaContext,
        method: UMethod,
        target: PsiElement
    ): Set<String> {
        val found = mutableSetOf<String>()
        method.accept(object : AbstractUastVisitor() {
            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
                val selector = node.selector
                if (selector is UCallExpression) {
                    val name = selector.methodName
                    if (name in REQUIRED_METHODS && isValidSetterCall(name, selector)) {
                        val receiver = node.receiver
                        if (receiver is USimpleNameReferenceExpression && receiver.resolve() == target) {
                            found.add(name)
                        }
                    }
                }
                return super.visitQualifiedReferenceExpression(node)
            }
        })
        return found
    }

    private fun isValidSetterCall(name: String, call: UCallExpression): Boolean {
        val arg = call.valueArguments.firstOrNull() ?: return false
        return when (name) {
            "setAutoEnterEnabled" -> arg !is ULiteralExpression || arg.value != false
            "setSourceRectHint" -> arg !is ULiteralExpression || arg.value != null
            else -> false
        }
    }

    private fun reportIssue(context: JavaContext, node: UElement, message: String = MESSAGE) {
        context.report(ISSUE, node, context.getLocation(node), message)
    }

    companion object {
        private const val BUILDER_CLASS = "android.app.PictureInPictureParams.Builder"
        private val REQUIRED_METHODS = setOf("setAutoEnterEnabled", "setSourceRectHint")
        private const val MESSAGE =
            "Picture-in-picture params should be built with setAutoEnterEnabled(true) and setSourceRectHint(...) for smoother transitions on Android 12+"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, apps should call setAutoEnterEnabled(true) and \
                setSourceRectHint(...) on PictureInPictureParams.Builder to ensure smooth \
                picture-in-picture transitions.
            """.trimIndent(),
            category = Category.USABILITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}