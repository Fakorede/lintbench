package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstance")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return
        if (!(qualifiedName.startsWith("java.security.") || qualifiedName.startsWith("javax.crypto."))) {
            return
        }

        val parameters = method.parameterList.parameters
        val arguments = node.valueArguments

        var providerIndex = -1
        for (i in parameters.indices) {
            val param = parameters[i]
            val typeName = param.type.canonicalText
            if (typeName == "java.lang.String" && (param.name == "provider" || i == 1)) {
                providerIndex = i
                break
            }
        }

        if (providerIndex >= 0 && providerIndex < arguments.size) {
            val argument = arguments[providerIndex]
            val value = ConstantEvaluator.evaluate(context, argument)
            if (value is String && "BC".equals(value, ignoreCase = true)) {
                val message = "The `BC` provider is deprecated and as of Android P (API 28) " +
                        "it will no longer be available for use. See " +
                        "https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html " +
                        "for details."
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(argument),
                    message
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.
                
                Reference documentation:
                - https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html
                - https://goo.gle/DeprecatedProvider
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}