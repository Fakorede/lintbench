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

        private val SECRET_REGEX = Regex(
            "^(AIza[0-9A-Za-z\\-_]{35}|sk-[0-9a-zA-Z]{20,}|ghp_[0-9a-zA-Z]{36}|xoxb-[0-9a-zA-Z\\-]{10,})$"
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
            if (argument is ULiteralExpression && argument.isStringLiteral()) {
                val value = argument.value as? String ?: continue
                if (SECRET_REGEX.matches(value)) {
                    context.report(
                        ISSUE,
                        argument,
                        context.getLocation(argument),
                        "Hardcoded secret/API key detected. Use the Secrets Gradle Plugin instead."
                    )
                }
            }
        }
    }
}