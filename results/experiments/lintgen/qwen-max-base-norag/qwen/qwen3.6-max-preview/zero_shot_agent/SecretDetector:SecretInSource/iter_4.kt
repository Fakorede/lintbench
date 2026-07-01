package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UElementHandler
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import java.util.EnumSet

class SecretDetector : Detector(), Detector.UastScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk.
                It is generally best practice to not include API keys in source code,
                and instead use something like the Secrets Gradle Plugin for Android.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                SecretDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
            )
        )

        private val SECRET_KEYWORDS = listOf(
            "API_KEY", "APIKEY", "SECRET", "TOKEN", "PASSWORD", "PWD",
            "CREDENTIAL", "AUTH", "PRIVATE_KEY", "ACCESS_KEY"
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (value.length < 8 || value.contains(' ')) return

                val variableName = getVariableName(node) ?: return
                if (isSecretVariableName(variableName)) {
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Secret hardcoded in source code. Use the Secrets Gradle Plugin instead."
                    )
                }
            }
        }
    }

    private fun getVariableName(node: ULiteralExpression): String? {
        val parent = node.uastParent ?: return null
        return when (parent) {
            is UVariable -> parent.name
            is UBinaryExpression -> {
                if (parent.operator == UastBinaryOperator.ASSIGN) {
                    (parent.leftOperand as? UReferenceExpression)?.resolvedName
                } else null
            }
            else -> null
        }
    }

    private fun isSecretVariableName(name: String): Boolean {
        val upper = name.uppercase()
        return SECRET_KEYWORDS.any { upper.contains(it) }
    }
}