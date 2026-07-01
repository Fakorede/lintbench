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

    companion object {
        private val IMPLEMENTATION = Implementation(
            PublicKeyCredentialDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = "Creating public key credential (Passkeys) is supported starting Android 9 or higher. Please check for the Android version before calling the method.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf("androidx.credentials.CreatePublicKeyCredentialRequest")
    }

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        val minSdk = context.project.minSdkVersion.featureLevel
        if (minSdk >= 28) {
            return
        }

        if (VersionChecks.isWithinVersionCheckConditional(context, node, 28)) {
            return
        }

        if (isAnnotatedWithRequiresApi(node)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Creating public key credential requires Android 9 (API 28) or higher"
        )
    }

    private fun isAnnotatedWithRequiresApi(element: UElement?): Boolean {
        var curr = element
        while (curr != null) {
            if (curr is UAnnotated) {
                for (annotation in curr.uAnnotations) {
                    val fqName = annotation.qualifiedName
                    if (fqName == "androidx.annotation.RequiresApi" || fqName == "android.annotation.TargetApi") {
                        val value = annotation.findAttributeValue("value") ?: annotation.findAttributeValue("api")
                        if (value != null) {
                            val intValue = getIntValue(value)
                            if (intValue >= 28) {
                                return true
                            }
                        }
                    }
                }
            }
            curr = curr.uastParent
        }
        return false
    }

    private fun getIntValue(expression: UExpression?): Int {
        if (expression == null) return 0
        val evaluated = expression.evaluate()
        if (evaluated is Int) {
            return evaluated
        }
        if (evaluated is Number) {
            return evaluated.toInt()
        }
        val text = expression.asSourceString()
        if (text.contains("VERSION_CODES.P") || text.endsWith(".P") || text == "P") {
            return 28
        }
        if (text.contains("VERSION_CODES.Q") || text.endsWith(".Q") || text == "Q") {
            return 29
        }
        if (text.contains("VERSION_CODES.R") || text.endsWith(".R") || text == "R") {
            return 30
        }
        if (text.contains("VERSION_CODES.S") || text.endsWith(".S") || text == "S") {
            return 31
        }
        if (text.contains("VERSION_CODES.TIRAMISU") || text.contains("VERSION_CODES.T") || text.endsWith(".TIRAMISU") || text == "TIRAMISU") {
            return 33
        }
        try {
            return text.toInt()
        } catch (e: NumberFormatException) {
            // ignore
        }
        return 0
    }
}