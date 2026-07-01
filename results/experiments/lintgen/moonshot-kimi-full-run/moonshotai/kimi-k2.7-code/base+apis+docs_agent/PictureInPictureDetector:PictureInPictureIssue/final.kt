package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElement

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> =
        listOf("build", "enterPictureInPictureMode")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        when (method.name) {
            "build" -> if (isBuilderBuildMethod(method)) {
                checkBuilderBuild(context, node)
            }
            "enterPictureInPictureMode" -> if (isEnterPipMethod(method) && node.valueArguments.isEmpty()) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "For smooth picture-in-picture transitions on Android 12+, use " +
                        "PictureInPictureParams.Builder with setAutoEnterEnabled(true) and " +
                        "setSourceRectHint(...)."
                )
            }
        }
    }

    private fun checkBuilderBuild(context: JavaContext, node: UCallExpression) {
        val chain = node.findTopLevelChain().collectCalls()
        if (chain.any { it.isBuilderConstructor() }) {
            checkConfiguration(context, node, chain)
        } else {
            checkVariableBuilder(context, node)
        }
    }

    private fun checkVariableBuilder(context: JavaContext, node: UCallExpression) {
        val parent = node.uastParent as? UQualifiedReferenceExpression ?: return
        val rootReceiver = parent.findRootReceiver() as? USimpleNameReferenceExpression ?: return
        val variable = rootReceiver.resolve() as? PsiLocalVariable ?: return
        val uVariable = variable.toUElement(ULocalVariable::class.java) ?: return
        val initializer = uVariable.uastInitializer ?: return

        val initChain = initializer.findTopLevelChain().collectCalls()
        val initCall = initChain.firstOrNull()?.takeIf { it.isBuilderConstructor() } ?: return

        val varName = variable.name
        val method = node.getParentOfType(UMethod::class.java, true) ?: return
        val body = method.uastBody as? UBlockExpression ?: return

        val chainCalls = node.findTopLevelChain().collectCalls()
        val siblingCalls = body.expressions.flatMap { it.collectCallsOnVariable(varName) }
        val allCalls = listOf(initCall) + chainCalls + siblingCalls

        checkConfiguration(context, node, allCalls)
    }

    private fun checkConfiguration(context: JavaContext, node: UElement, calls: List<UCallExpression>) {
        val hasAuto = calls.hasAutoEnterEnabled()
        val hasRect = calls.hasSourceRectHint()
        if (!hasAuto || !hasRect) {
            val missing = buildList {
                if (!hasAuto) add("setAutoEnterEnabled(true)")
                if (!hasRect) add("setSourceRectHint(...)")
            }
            val message = "For smooth picture-in-picture transitions on Android 12+, call " +
                missing.joinToString(" and ") +
                " on the PictureInPictureParams.Builder."
            context.report(ISSUE, node, context.getLocation(node), message)
        }
    }

    private fun isBuilderBuildMethod(method: PsiMethod): Boolean {
        val containingClass = method.containingClass ?: return false
        return containingClass.qualifiedName == BUILDER_CLASS
    }

    private fun isEnterPipMethod(method: PsiMethod): Boolean {
        val containingClass = method.containingClass ?: return false
        return containingClass.qualifiedName == "android.app.Activity"
    }

    private fun UCallExpression.isBuilderConstructor(): Boolean {
        return kind == UastCallKind.CONSTRUCTOR_CALL &&
            classReference?.resolve()?.let { (it as? PsiClass)?.qualifiedName == BUILDER_CLASS } == true
    }

    private fun UExpression.findTopLevelChain(): UExpression {
        var current: UExpression = this
        while (true) {
            val parent = current.uastParent
            if (parent is UQualifiedReferenceExpression &&
                (parent.receiver === current || parent.selector === current)) {
                current = parent
            } else {
                break
            }
        }
        return current
    }

    private fun UQualifiedReferenceExpression.findRootReceiver(): UExpression {
        var current: UExpression = this
        while (current is UQualifiedReferenceExpression) {
            current = current.receiver
        }
        return current
    }

    private fun UExpression.collectCalls(): List<UCallExpression> {
        val result = mutableListOf<UCallExpression>()
        var current: UExpression? = this
        while (current != null) {
            when (current) {
                is UQualifiedReferenceExpression -> {
                    val selector = current.selector
                    if (selector is UCallExpression) {
                        result.add(selector)
                    }
                    current = current.receiver
                }
                is UCallExpression -> {
                    result.add(current)
                    current = null
                }
                else -> current = null
            }
        }
        return result
    }

    private fun UExpression.collectCallsOnVariable(varName: String): List<UCallExpression> {
        val root = this.findTopLevelChain()
        val qualified = root as? UQualifiedReferenceExpression ?: return emptyList()
        val rootReceiver = qualified.findRootReceiver() as? USimpleNameReferenceExpression ?: return emptyList()
        return if (rootReceiver.identifier == varName) qualified.collectCalls() else emptyList()
    }

    private fun List<UCallExpression>.hasAutoEnterEnabled(): Boolean =
        any { call ->
            call.methodName == "setAutoEnterEnabled" &&
                call.valueArguments.firstOrNull().let { arg ->
                    arg is ULiteralExpression && arg.value == true
                }
        }

    private fun List<UCallExpression>.hasSourceRectHint(): Boolean =
        any { call ->
            call.methodName == "setSourceRectHint" && call.valueArguments.isNotEmpty()
        }

    companion object {
        private const val BUILDER_CLASS = "android.app.PictureInPictureParams.Builder"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for picture-in-picture uses
                PictureInPictureParams.Builder#setAutoEnterEnabled(true) and
                #setSourceRectHint(...) to produce high-quality transition animations.
            """.trimIndent(),
            moreInfo = "https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition",
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}