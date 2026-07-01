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
import org.jetbrains.uast.ULiteralExpression

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SecretDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = "Including secrets, such as API keys, in source code is a security risk. It is generally best practice to not include API keys in source code, and instead use something like the Secrets Gradle Plugin for Android.",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val SECRET_REGEX = Regex(
            "(?i)^(AIzaSy[A-Za-z0-9_\\-]{33}|" +
            "sk[_-](?:live|test)_[A-Za-z0-9]{20,}|" +
            "pk[_-](?:live|test)_[A-Za-z0-9]{20,}|" +
            "ghp_[A-Za-z0-9]{36}|" +
            "github_pat_[A-Za-z0-9_]{22,}|" +
            "xoxb-[A-Za-z0-9\\-]{10,}|" +
            "AKIA[A-Z0-9]{16}|" +
            "eyJ[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,})$"
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? = null

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        // Constructor scanning is not required for this detector.
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(ULiteralExpression::class.java)
    }

    override fun visitLiteralExpression(context: JavaContext, node: ULiteralExpression) {
        if (context.isTestSource) return

        val value = node.value as? String ?: return
        if (value.length < 10) return

        if (SECRET_REGEX.matches(value)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Possible hardcoded secret detected. Use the Secrets Gradle Plugin or environment variables instead."
            )
        }
    }
}