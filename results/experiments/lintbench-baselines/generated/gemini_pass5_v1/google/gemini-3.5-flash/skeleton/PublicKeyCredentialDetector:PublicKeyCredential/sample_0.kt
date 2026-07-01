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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UAnnotated

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
            explanation = "Credential Manager API supports creating public key credential (Passkeys) starting Android 9 (API level 28) or higher. Please check for the Android version before calling the method.",
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
        if (context.project.minSdkVersion.apiLevel >= 28) {
            return
        }

        if (isVersionChecked(node)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Creating public key credential requires Android 9 (API level 28) or higher"
        )
    }

    private fun isVersionChecked(node: UCallExpression): Boolean {
        var current: UElement? = node
        while (current != null) {
            if (current is UIfExpression) {
                val conditionStr = current.condition.asSourceString()
                if (conditionStr.contains("SDK_INT")) {
                    if (conditionStr.contains("28") || 
                        conditionStr.contains("29") || 
                        conditionStr.contains("30") || 
                        conditionStr.contains("31") || 
                        conditionStr.contains("32") || 
                        conditionStr.contains("33") || 
                        conditionStr.contains("34") || 
                        conditionStr.contains("P") || 
                        conditionStr.contains("Q") || 
                        conditionStr.contains("R") || 
                        conditionStr.contains("S") || 
                        conditionStr.contains("T") || 
                        conditionStr.contains("U")
                    ) {
                        return true
                    }
                }
            }

            val annotated = current as? UAnnotated
            if (annotated != null) {
                for (annotation in annotated.uAnnotations) {
                    val qualifiedName = annotation.qualifiedName
                    if (qualifiedName == "androidx.annotation.RequiresApi" || 
                        qualifiedName == "android.annotation.TargetApi"
                    ) {
                        val valueStr = annotation.findAttributeValue("value")?.asSourceString() ?: ""
                        if (valueStr.contains("28") || 
                            valueStr.contains("29") || 
                            valueStr.contains("30") || 
                            valueStr.contains("P") || 
                            valueStr.contains("Q") || 
                            valueStr.contains("R") || 
                            valueStr.contains("S") || 
                            valueStr.contains("T") || 
                            valueStr.contains("U")
                        ) {
                            return true
                        }
                    }
                }
            }

            current = current.uastParent
        }
        return false
    }
}