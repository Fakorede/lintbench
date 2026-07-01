package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import java.util.regex.Pattern

class SecretDetector : Detector(), SourceCodeScanner {

    companion object {
        private val SECRET_NAME_PATTERN = Pattern.compile(
            "(?i).*(secret|key|password|token|credential|api|auth|access|private).*"
        )
        private val GOOGLE_API_KEY_PATTERN = Pattern.compile(
            "AIza[0-9A-Za-z\\-_]{35}"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk. \
                It is generally best practice to not include API keys in source code, \
                and instead use something like the Secrets Gradle Plugin for Android.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(ULiteralExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (value.isBlank() || value.length < 6) return

                val isGoogleKey = GOOGLE_API_KEY_PATTERN.matcher(value).matches()
                val targetName = resolveTargetName(node)
                val isSecretName = targetName != null && SECRET_NAME_PATTERN.matcher(targetName).matches()

                if (isGoogleKey) {
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Hardcoded Google API key detected"
                    )
                } else if (isSecretName) {
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Potential hardcoded secret assigned to '$targetName'"
                    )
                }
            }

            private fun resolveTargetName(node: ULiteralExpression): String? {
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
        }
}