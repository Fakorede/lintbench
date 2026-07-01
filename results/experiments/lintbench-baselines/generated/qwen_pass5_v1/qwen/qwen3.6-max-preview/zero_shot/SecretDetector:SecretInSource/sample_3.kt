package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiType
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.UElementHandler

class SecretDetector : Detector(), UastScanner {

    companion object {
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

        private val SECRET_KEYWORD_PATTERN = Regex("(?i).*(api[_\\-]?key|secret|password|token|auth|credential|private[_\\-]?key).*")
        private val KNOWN_SECRET_PATTERN = Regex("^(AIza[0-9A-Za-z\\-_]{35}|sk-[0-9a-zA-Z]{20,}|ghp_[0-9a-zA-Z]{36}|xox[baprs]-[0-9a-zA-Z\\-]+|glpat-[0-9a-zA-Z\\-]{20,}|AKIA[0-9A-Z]{16})$")
        private val PLACEHOLDER_PATTERN = Regex("(?i)^(your[_\\-]?api[_\\-]?key|insert[_\\-]?here|x{3,}|todo|placeholder|change[_\\-]?me|none|null|empty|test|example|dummy|sample)$")
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val psi = node.sourcePsi ?: return
                if (context.isGenerated(psi)) return
                if (node.type != PsiType.STRING) return

                val value = node.value as? String ?: return
                if (value.isBlank()) return

                if (KNOWN_SECRET_PATTERN.matches(value)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Hardcoded secret detected (known pattern)"
                    )
                    return
                }

                val varName = findVariableName(node)
                if (varName != null && SECRET_KEYWORD_PATTERN.matches(varName)) {
                    if (!PLACEHOLDER_PATTERN.matches(value) && value.length >= 8) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Possible hardcoded secret assigned to '$varName'"
                        )
                    }
                }
            }
        }
    }

    private fun findVariableName(node: ULiteralExpression): String? {
        var current: UElement? = node.uastParent
        while (current != null) {
            when (current) {
                is UField -> return current.name
                is ULocalVariable -> return current.name
                is UParameter -> return current.name
                is UBinaryExpression -> {
                    if (current.operator == UastBinaryOperator.ASSIGN) {
                        val left = current.leftOperand
                        return when (left) {
                            is UReferenceExpression -> left.asRenderString().substringAfterLast('.')
                            else -> null
                        }
                    }
                    return null
                }
                is UCallExpression, is UReturnExpression, is UIfExpression -> return null
                else -> current = current.uastParent
            }
        }
        return null
    }
}