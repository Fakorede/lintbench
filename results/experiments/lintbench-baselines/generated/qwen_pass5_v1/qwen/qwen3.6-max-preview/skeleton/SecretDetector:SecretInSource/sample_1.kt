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
import org.jetbrains.uast.ULiteralExpression

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SecretDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        private val SECRET_PATTERN = Regex(
            "^(AIza[0-9A-Za-z\\-_]{35}|" +
            "AKIA[0-9A-Z]{16}|" +
            "gh[po]_[A-Za-z0-9_]{36,}|" +
            "xox[baprs]-[0-9a-zA-Z]{10,48}|" +
            "sk_live_[0-9a-zA-Z]{24,}|" +
            "rk_live_[0-9a-zA-Z]{24,})$"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = "Including secrets, such as API keys, in source code is a security risk. " +
                "It is generally best practice to not include API keys in source code, " +
                "and instead use something like the Secrets Gradle Plugin for Android.",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? = listOf("*")

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        for (argument in node.valueArguments) {
            val literal = argument as? ULiteralExpression ?: continue
            val value = literal.value as? String ?: continue
            if (SECRET_PATTERN.matches(value)) {
                context.report(
                    ISSUE,
                    argument,
                    context.getLocation(argument),
                    "Potential secret or API key found in source code. Use the Secrets Gradle Plugin instead."
                )
            }
        }
    }
}