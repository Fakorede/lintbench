package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.asSourceString
import org.jetbrains.uast.getExpressionType
import org.jetbrains.uast.getParentOfType

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> =
        listOf("createCredential", "createCredentialAsync", "createPublicKeyCredential")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val className = method.containingClass?.qualifiedName ?: return
        if (className !in CREDENTIAL_MANAGER_CLASSES) return

        val isPublicKeyCall = method.name == "createPublicKeyCredential" ||
            node.valueArguments.any { arg ->
                arg.getExpressionType()?.canonicalText == CREATE_PUBLIC_KEY_REQUEST
            }

        if (!isPublicKeyCall) return
        if (isApi28Guarded(node)) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Public key credential creation requires Android 9 (API 28) or higher. " +
                "Add a Build.VERSION.SDK_INT >= Build.VERSION_CODES.P check before this call."
        )
    }

    private fun isApi28Guarded(call: UCallExpression): Boolean {
        if (isWithinVersionCheck(call)) return true
        if (isPrecededByVersionCheckExit(call)) return true
        return false
    }

    private fun isWithinVersionCheck(call: UCallExpression): Boolean {
        var current: UElement? = call
        while (current != null) {
            val parent = current.uastParent
            if (parent is UIfExpression) {
                val inThen = parent.thenExpression?.let { call.isUnder(it) } == true
                val inElse = parent.elseExpression?.let { call.isUnder(it) } == true
                val branch = when {
                    inThen -> true
                    inElse -> false
                    else -> null
                }
                if (branch != null && matchesApi28Check(parent.condition, branch)) {
                    return true
                }
            }
            current = parent
        }
        return false
    }

    private fun isPrecededByVersionCheckExit(call: UCallExpression): Boolean {
        val block = call.getParentOfType<UBlockExpression>(UBlockExpression::class.java, false)
            ?: return false
        val statements = block.expressions
        val callPsi = call.sourcePsi ?: return false
        val callIndex = statements.indexOfFirst { it.sourcePsi == callPsi }
        if (callIndex <= 0) return false

        val prev = statements[callIndex - 1] as? UIfExpression ?: return false
        if (!matchesApi28Check(prev.condition, false)) return false

        return prev.thenExpression?.let { containsExit(it) } == true
    }

    private fun containsExit(expr: UExpression): Boolean {
        return when (expr) {
            is UReturnExpression -> true
            is UThrowExpression -> true
            is UBlockExpression -> expr.expressions.any { containsExit(it) }
            else -> false
        }
    }

    private fun matchesApi28Check(condition: UExpression?, positiveBranch: Boolean): Boolean {
        val binary = condition as? UBinaryExpression ?: return false
        val text = condition.asSourceString()

        if (!text.contains("SDK_INT")) return false

        val isTargetP = text.contains("VERSION_CODES.P")
        val isTarget28 = STANDALONE_28.containsMatchIn(text)
        if (!isTargetP && !isTarget28) return false

        return when {
            text.contains(">=") -> positiveBranch
            text.contains("==") -> positiveBranch
            text.contains("<") && !text.contains("<=") -> !positiveBranch
            else -> false
        }
    }

    private fun UElement.isUnder(ancestor: UElement): Boolean {
        val thisPsi = this.sourcePsi ?: return false
        val ancestorPsi = ancestor.sourcePsi ?: return false
        return PsiTreeUtil.isAncestor(ancestorPsi, thisPsi, false)
    }

    companion object {
        private const val CREATE_PUBLIC_KEY_REQUEST =
            "androidx.credentials.CreatePublicKeyCredentialRequest"

        private val CREDENTIAL_MANAGER_CLASSES = listOf(
            "androidx.credentials.CredentialManager",
            "android.credentials.CredentialManager"
        )

        private val STANDALONE_28 = Regex("""(?<!\w)28(?!\w)""")

        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential without version check",
            explanation = """
                The Credential Manager public key credential (Passkey) creation path is only
                available on Android 9 (API 28) and higher. Calls that may run on older
                devices should be guarded by a Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.P version check.
            """,
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