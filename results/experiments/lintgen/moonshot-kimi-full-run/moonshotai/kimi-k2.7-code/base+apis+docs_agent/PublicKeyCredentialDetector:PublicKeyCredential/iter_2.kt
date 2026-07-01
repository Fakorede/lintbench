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
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThrowExpression
import org.w3c.dom.Node

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> =
        listOf("createCredential", "createCredentialAsync")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!isPublicKeyCredentialCreation(context, node, method)) return
        if (isVersionChecked(node)) return

        val message = "Creating public key credentials (passkeys) is only supported on " +
                "Android 9 (API 28) and higher. Add a version check " +
                "(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) before this call."
        context.report(ISSUE, node, context.getLocation(node), message)
    }

    private fun isPublicKeyCredentialCreation(
        context: JavaContext,
        call: UCallExpression,
        method: PsiMethod
    ): Boolean {
        if (method.containingClass?.qualifiedName != "androidx.credentials.CredentialManager") {
            return false
        }

        val arg = call.valueArguments.getOrNull(1) ?: return false
        val argType = context.evaluator.getType(arg)?.canonicalText ?: ""
        return argType.contains("CreatePublicKeyCredentialRequest") ||
                arg.sourcePsi?.text?.contains("CreatePublicKeyCredentialRequest") == true
    }

    private fun isVersionChecked(node: UElement): Boolean {
        var current: UElement? = node
        while (current != null) {
            val parent = current.uastParent
            if (parent is UIfExpression) {
                when (getBranch(current, parent)) {
                    Branch.THEN -> if (isApi28OrHigherCheck(parent.condition)) return true
                    Branch.ELSE -> if (isApi28OrLowerCheck(parent.condition) &&
                            hasUnconditionalExit(parent.thenExpression)
                    ) {
                        return true
                    }
                    else -> {}
                }
            }
            current = parent
        }

        val block = getParentOfType<UBlockExpression>(node) ?: return false
        val statement = getContainingStatement(node, block) ?: return false
        val statements = block.expressions
        val index = statements.indexOfFirst { it === statement }
        if (index > 0) {
            for (i in 0 until index) {
                val stmt = statements[i]
                if (stmt is UIfExpression &&
                    isApi28OrLowerCheck(stmt.condition) &&
                    hasUnconditionalExit(stmt.thenExpression)
                ) {
                    return true
                }
            }
        }

        return false
    }

    private fun getBranch(node: UElement, ifExpr: UIfExpression): Branch? {
        var cur: UElement? = node
        while (cur != null && cur !== ifExpr) {
            if (cur === ifExpr.thenExpression) return Branch.THEN
            if (cur === ifExpr.elseExpression) return Branch.ELSE
            cur = cur.uastParent
        }
        return null
    }

    private fun getContainingStatement(node: UElement, block: UBlockExpression): UExpression? {
        var cur: UElement? = node
        while (cur != null && cur !== block) {
            if (cur.uastParent === block) return cur as? UExpression
            cur = cur.uastParent
        }
        return null
    }

    private inline fun <reified T : UElement> getParentOfType(node: UElement): T? {
        var cur: UElement? = node
        while (cur != null) {
            if (cur is T) return cur
            cur = cur.uastParent
        }
        return null
    }

    private fun hasUnconditionalExit(expression: UExpression?): Boolean = when (expression) {
        is UReturnExpression, is UThrowExpression -> true
        is UBlockExpression -> {
            val last = expression.expressions.lastOrNull()
            last is UReturnExpression || last is UThrowExpression
        }
        else -> false
    }

    private fun isApi28OrHigherCheck(condition: UExpression?): Boolean {
        val text = condition?.sourcePsi?.text ?: return false
        return text.contains("SDK_INT") &&
                (text.contains(">=") || text.contains(">")) &&
                (text.contains("VERSION_CODES.P") || text.contains("28"))
    }

    private fun isApi28OrLowerCheck(condition: UExpression?): Boolean {
        val text = condition?.sourcePsi?.text ?: return false
        return text.contains("SDK_INT") &&
                (text.contains("<") || text.contains("<=")) &&
                (text.contains("VERSION_CODES.P") ||
                        text.contains("28") ||
                        text.contains("VERSION_CODES.O_MR1") ||
                        text.contains("27"))
    }

    private enum class Branch { THEN, ELSE }

    companion object {
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Public key credential creation without API version check",
            explanation = """
                The Credential Manager API supports creating public key credentials (passkeys)
                starting with Android 9 (API level 28). You must verify that
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P (or >= 28) before calling
                createCredential with a CreatePublicKeyCredentialRequest; otherwise the call may
                fail on older devices.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}