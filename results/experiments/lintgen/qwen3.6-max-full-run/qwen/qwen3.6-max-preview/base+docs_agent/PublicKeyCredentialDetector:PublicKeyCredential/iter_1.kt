package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf(
            "androidx.credentials.CreatePublicKeyCredentialRequest",
            "android.credentials.CreatePublicKeyCredentialRequest"
        )
    }

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        checkApiLevel(context, node)
    }

    private fun checkApiLevel(context: JavaContext, node: UCallExpression) {
        val method = node.getContainingUMethod()
        if (method != null) {
            if (hasApiAnnotation(method, 28)) return
            val cls = method.getContainingUClass()
            if (cls != null && hasApiAnnotation(cls, 28)) return
        }

        if (isGuardedBySdkCheck(node, 28)) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Creating public key credential requires Android 9 (API 28) or higher. " +
                "Please check for the Android version before calling this method."
        )
    }

    private fun hasApiAnnotation(element: UAnnotated, minApi: Int): Boolean {
        val annotations = listOf(
            "android.annotation.RequiresApi",
            "androidx.annotation.RequiresApi",
            "android.annotation.TargetApi",
            "androidx.annotation.TargetApi"
        )
        for (annName in annotations) {
            val ann = element.findAnnotation(annName)
            if (ann != null) {
                val value = ann.findAttributeValue("value")?.evaluate() as? Int
                val api = ann.findAttributeValue("api")?.evaluate() as? Int
                val level = value ?: api ?: 0
                if (level >= minApi) return true
            }
        }
        return false
    }

    private fun isGuardedBySdkCheck(node: UCallExpression, minApi: Int): Boolean {
        var current: UElement? = node.uastParent
        while (current != null) {
            if (current is UIfExpression) {
                val condition = current.condition
                if (condition != null && isSdkCheck(condition, minApi)) {
                    return true
                }
            }
            current = current.uastParent
        }
        return false
    }

    private fun isSdkCheck(condition: UExpression, minApi: Int): Boolean {
        val text = condition.asSourceString()
        return text.contains("Build.VERSION.SDK_INT") &&
            (text.contains(">=$minApi") || text.contains(">= $minApi") ||
             text.contains(">27") || text.contains("> 27"))
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = """
                Credential Manager API supports creating public key credential (Passkeys) \
                starting Android 9 or higher. Please check for the Android version before \
                calling the method.
            """.trimIndent(),
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