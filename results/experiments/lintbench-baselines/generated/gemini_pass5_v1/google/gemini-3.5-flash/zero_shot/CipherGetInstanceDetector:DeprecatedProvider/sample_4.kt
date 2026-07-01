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
import org.jetbrains.uast.UExpression

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstance", "getProvider")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val name = method.name
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (name == "getProvider") {
            if (qualifiedName == "java.security.Security") {
                val firstArg = node.valueArguments.firstOrNull() ?: return
                if (isBcProvider(firstArg)) {
                    report(context, node)
                }
            }
        } else if (name == "getInstance") {
            if (qualifiedName.startsWith("java.security.") ||
                qualifiedName.startsWith("javax.crypto.")
            ) {
                for (arg in node.valueArguments) {
                    if (isBcProvider(arg)) {
                        report(context, node)
                        break
                    }
                }
            }
        }
    }

    private fun isBcProvider(expression: UExpression): Boolean {
        val value = expression.evaluate()
        return value is String && "BC".equals(value, ignoreCase = true)
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "The `BC` provider is deprecated and should not be used"
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = "The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        ).addMoreInfo("https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html")
         .addMoreInfo("https://goo.gle/DeprecatedProvider")
    }
}