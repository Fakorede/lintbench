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
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("createCredential", "createCredentialAsync")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "androidx.credentials.CredentialManager")) {
            return
        }

        if (context.mainProject.minSdk >= MIN_API) {
            return
        }

        if (!hasPublicKeyCredentialRequest(node)) {
            return
        }

        if (isInsideVersionCheck(node)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Creating a public key credential is only supported on Android 9 (API 28) and higher. " +
                    "Check `Build.VERSION.SDK_INT` before calling `createCredential()`."
        )
    }

    private fun hasPublicKeyCredentialRequest(node: UCallExpression): Boolean {
        return node.valueArguments.any { arg ->
            arg.getExpressionType()?.canonicalText?.removeSuffix("?") == REQUEST_TYPE
        }
    }

    private fun isInsideVersionCheck(node: UCallExpression): Boolean {
        var psi: PsiElement? = node.sourcePsi ?: node.javaPsi ?: return false
        while (psi != null) {
            if (psi.firstChild?.text == "if") {
                val condition = extractCondition(psi.text)
                if (condition != null && isApi28OrHigherGuard(condition)) {
                    return true
                }
            }
            psi = psi.parent
        }
        return false
    }

    private fun extractCondition(ifText: String): String? {
        val start = ifText.indexOf('(')
        if (start == -1) return null
        var depth = 0
        for (i in start until ifText.length) {
            when (ifText[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        return ifText.substring(start + 1, i)
                    }
                }
            }
        }
        return null
    }

    private fun isApi28OrHigherGuard(condition: String): Boolean {
        val compact = condition.replace("\\s+".toRegex(), "")
        return API28_GUARD_REGEX.containsMatchIn(compact)
    }

    companion object {
        private const val MIN_API = 28
        private const val REQUEST_TYPE = "androidx.credentials.CreatePublicKeyCredentialRequest"

        private val SDK_INT_PATTERN = "(?:(?:Build\\.)?VERSION\\.)?SDK_INT"
        private val API28_PATTERN = "(?:(?:Build\\.)?VERSION_CODES\\.)?P|28"
        private val API28_GUARD_REGEX = Regex(
            "$SDK_INT_PATTERN(?:>=|>)(?:$API28_PATTERN|[3-9]\\d|[2][8-9])|" +
                    "(?:$API28_PATTERN|[3-9]\\d|[2][8-9])(?:<=|<)$SDK_INT_PATTERN",
            RegexOption.IGNORE_CASE
        )

        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Public key credential creation requires Android 9 or higher",
            explanation = """
                The Credential Manager API supports creating public key credentials (passkeys) only on
                Android 9 (API 28) and higher. You must check `Build.VERSION.SDK_INT` before calling
                `CredentialManager.createCredential(...)` with a `CreatePublicKeyCredentialRequest`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}