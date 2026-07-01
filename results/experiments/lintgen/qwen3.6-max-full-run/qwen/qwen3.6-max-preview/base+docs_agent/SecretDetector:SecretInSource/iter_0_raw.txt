package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiType
import org.jetbrains.uast.*

class SecretDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(ULiteralExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                if (node.type != PsiType.STRING) return
                val value = node.value as? String ?: return
                if (value.isBlank() || value.length < 8) return
                if (PLACEHOLDER_PATTERN.matches(value)) return

                val parent = node.uastParent
                val targetName = when (parent) {
                    is UVariable -> parent.name
                    is UAssignmentExpression -> (parent.leftOperand as? UReference)?.resolvedName
                    else -> null
                } ?: return

                if (SECRET_NAME_PATTERN.matches(targetName)) {
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