package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> =
        listOf("createCredential", "createCredentialAsync")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod?) {
        if (!isPublicKeyCredentialCreation(node, method)) return
        if (isVersionChecked(node)) return

        val message = "Creating public key credentials (passkeys) is only supported on " +
                "Android 9 (API 28) and higher. Add a version check " +
                "(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) before this call."
        context.report(ISSUE, node, context.getLocation(node), message)
    }

    private fun isPublicKeyCredentialCreation(call: UCallExpression, method: PsiMethod?): Boolean {
        val receiverClass = method?.containingClass?.qualifiedName
            ?: call.receiver?.getExpressionType()?.canonicalText
            ?: return false
        if (receiverClass != "androidx.credentials.CredentialManager") return false

        val requestType = if (method != null) {
            method.parameterList.parameters.getOrNull(1)?.type?.canonicalText
        } else {
            call.getArgumentForParameter(1)?.getExpressionType()?.canonicalText
        } ?: return false

        return requestType.contains("CreatePublicKeyCredentialRequest")
    }

    private fun isVersionChecked(node: UElement): Boolean {
        // Direct if-guard: if (SDK_INT >= P) { ... call ... }
        var current: UElement? = node
        while (true) {
            val ifExpr = current?.getParentOfType(UIfExpression::class.java, false) ?: break
            when (getBranch(node, ifExpr)) {
                Branch.THEN -> if (isApi28OrHigherCheck(ifExpr.condition)) return true
                Branch.ELSE -> if (isApi28OrLowerCheck(ifExpr.condition)) return true
                else -> {}
            }
            current = ifExpr
        }

        // Early-return guard: if (SDK_INT < P) return; ... call ...
        val block = node.getParentOfType(UBlockExpression::class.java, false) ?: return false
        val statement = getContainingStatement(node, block) ?: return false
        val callIndex = block.expressions.indexOfFirst { it === statement }
        if (callIndex > 0) {
            for (i in 0 until callIndex) {
                val stmt = block.expressions[i]
                if (stmt is UIfExpression &&
                    isApi28OrLowerCheck(stmt.condition) &&
                    hasUnconditionalReturn(stmt.thenExpression)
                ) {
                    return true
                }
            }
        }

        return false
    }

    private fun getBranch(node: UElement, ifExpr: UIfExpression): Branch? {
        var current: UElement? = node
        while (current != null && current != ifExpr) {
            if (current === ifExpr.thenExpression) return Branch.THEN
            if (current === ifExpr.elseExpression) return Branch.ELSE
            current = current.uastParent
        }
        return null
    }

    private fun getContainingStatement(node: UElement, block: UBlockExpression): UExpression? {
        var current: UElement? = node
        while (current != null && current != block) {
            if (current.uastParent === block) return current as? UExpression
            current = current.uastParent
        }
        return null
    }

    private fun hasUnconditionalReturn(expression: UExpression?): Boolean = when (expression) {
        is UReturnExpression, is UThrowExpression -> true
        is UBlockExpression -> {
            val last = expression.expressions.lastOrNull()
            last is UReturnExpression || last is UThrowExpression
        }
        else -> false
    }

    private fun isApi28OrHigherCheck(condition: UExpression?): Boolean {
        if (condition == null) return false
        val text = condition.sourcePsi?.text ?: condition.asSourceString()
        return text.contains("SDK_INT") &&
                (text.contains(">=") || text.contains(">")) &&
                (text.contains("VERSION_CODES.P") || text.contains("28"))
    }

    private fun isApi28OrLowerCheck(condition: UExpression?): Boolean {
        if (condition == null) return false
        val text = condition.sourcePsi?.text ?: condition.asSourceString()
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