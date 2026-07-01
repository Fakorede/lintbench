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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            PublicKeyCredentialDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = """
                Credential Manager API supports creating public key credential (Passkeys) \
                starting Android 9 or higher. Please check for the Android version before \
                calling the method.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.CreatePublicKeyCredentialRequest"

        // Android 9 is API level 28
        private const val MIN_REQUIRED_API = 28
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(PUBLIC_KEY_CREDENTIAL_CLASS)
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        if (!isInsideApiVersionCheck(node)) {
            context.report(
                issue = ISSUE,
                location = context.getLocation(node),
                message = "Credential Manager API supports creating public key credential " +
                    "(Passkeys) starting Android 9 (API level 28) or higher. Please check " +
                    "for the Android version before calling this method.",
            )
        }
    }

    private fun isInsideApiVersionCheck(node: UCallExpression): Boolean {
        // Walk up the UAST tree looking for an SDK_INT version check
        var parent = node.uastParent
        while (parent != null) {
            if (parent is UIfExpression) {
                val condition = parent.condition.asSourceString()
                if (containsSdkIntCheck(condition)) {
                    return true
                }
            }
            if (parent is UMethod) {
                // Stop at method boundary
                break
            }
            parent = parent.uastParent
        }

        // Also check via getParentOfType for if expressions
        val enclosingIf = node.getParentOfType<UIfExpression>(strict = true)
        if (enclosingIf != null) {
            val condition = enclosingIf.condition.asSourceString()
            if (containsSdkIntCheck(condition)) {
                return true
            }
        }

        return false
    }

    private fun containsSdkIntCheck(condition: String): Boolean {
        // Check if the condition references Build.VERSION.SDK_INT with a value >= 28
        if (!condition.contains("SDK_INT")) {
            return false
        }

        // Look for patterns like SDK_INT >= 28, SDK_INT > 27, etc.
        val sdkIntPattern = Regex("""SDK_INT\s*>=\s*(\d+)""")
        val sdkIntGtPattern = Regex("""SDK_INT\s*>\s*(\d+)""")
        val sdkIntConstantPattern = Regex("""SDK_INT\s*>=\s*[A-Z_]+""")
        val sdkIntConstantGtPattern = Regex("""SDK_INT\s*>\s*[A-Z_]+""")

        // Check >= pattern
        val geMatch = sdkIntPattern.find(condition)
        if (geMatch != null) {
            val apiLevel = geMatch.groupValues[1].toIntOrNull()
            if (apiLevel != null && apiLevel >= MIN_REQUIRED_API) {
                return true
            }
        }

        // Check > pattern
        val gtMatch = sdkIntGtPattern.find(condition)
        if (gtMatch != null) {
            val apiLevel = gtMatch.groupValues[1].toIntOrNull()
            if (apiLevel != null && apiLevel >= MIN_REQUIRED_API - 1) {
                return true
            }
        }

        // Check for named constants (e.g., Build.VERSION_CODES.P)
        if (sdkIntConstantPattern.containsMatchIn(condition) ||
            sdkIntConstantGtPattern.containsMatchIn(condition)) {
            return true
        }

        return false
    }
}