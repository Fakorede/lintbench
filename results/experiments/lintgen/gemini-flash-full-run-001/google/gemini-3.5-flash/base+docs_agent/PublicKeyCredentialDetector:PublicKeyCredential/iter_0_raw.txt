package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.VersionChecks
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            "androidx.credentials.CreatePublicKeyCredentialRequest",
            "androidx.credentials.CreatePublicKeyCredentialRequest.Builder"
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        val minSdk = context.project.minSdkVersion.featureLevel
        if (minSdk >= 28) {
            return
        }

        if (isVersionChecked(context, node, 28)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Creating public key credential (Passkeys) requires Android 9 (API level 28) or higher"
        )
    }

    private fun isVersionChecked(context: JavaContext, node: UElement, targetApi: Int): Boolean {
        if (VersionChecks.isPrecededByVersionCheck(context, node, targetApi)) {
            return true
        }

        var current: UElement? = node
        while (current != null) {
            if (current is UAnnotated) {
                for (annotation in current.uAnnotations) {
                    val fqName = annotation.qualifiedName
                    if (fqName == "androidx.annotation.RequiresApi" || fqName == "android.annotation.TargetApi") {
                        val value = annotation.findAttributeValue("value") ?: annotation.findAttributeValue("api")
                        val api = getApiLevel(value)
                        if (api >= targetApi) {
                            return true
                        }
                    }
                }
            }
            current = current.uastParent
        }

        return false
    }

    private fun getApiLevel(value: UExpression?): Int {
        if (value == null) return 0
        val evaluated = value.evaluate()
        if (evaluated is Int) {
            return evaluated
        }
        val text = value.asSourceString()
        if (text.contains("VERSION_CODES.P") || text.contains("Build.VERSION_CODES.P")) {
            return 28
        }
        val digits = text.filter { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = """
                Credential Manager API supports creating public key credential (Passkeys) starting Android 9 or higher. \
                Please check for the Android version before calling the method.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}