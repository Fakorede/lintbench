package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UElementHandler
import org.jetbrains.uast.UAssignmentExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReference
import org.jetbrains.uast.UVariable

class SecretDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(ULiteralExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (value.isBlank() || value.length < 8) return
                if (PLACEHOLDER_PATTERN.matches(value)) return

                val parent = node.uastParent
                val targetName: String? = when (parent) {
                    is UVariable -> parent.name
                    is UAssignmentExpression -> (parent.leftOperand as? UReference)?.resolvedName
                    else -> null
                }

                if (targetName != null && SECRET_NAME_PATTERN.matches(targetName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Hardcoded secret found in source code. Use the Secrets Gradle Plugin instead."
                    )
                }
            }
        }
    }

    companion object {
        private val SECRET_NAME_PATTERN = Regex("(?i).*(api[_\\-]?key|secret|token|password|credential|auth).*$")
        private val PLACEHOLDER_PATTERN = Regex("(?i)^(your|insert|replace|todo|xxx|placeholder|sample|test|dummy|fake|none|null|empty|changeme|\\{\\{.*\\}\\}).*$")

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
            severity = Severity.WARNING,
            implementation = Implementation(SecretDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}