package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParentOfType
import org.w3c.dom.Node

class SecretDetector : Detector(), SourceCodeScanner {
    companion object {
        private val SECRET_NAME_PATTERN = Regex("(?i).*(api[_\\-]?key|secret|token|password|credential|auth|private[_\\-]?key).*$")
        private val SECRET_VALUE_PATTERN = Regex("^(AIza[0-9A-Za-z_\\-]{35}|sk_(live|test)_[0-9a-zA-Z]{24,}|pk_(live|test)_[0-9a-zA-Z]{24,}|ghp_[0-9a-zA-Z]{36}|xox[baprs]-[0-9a-zA-Z\\-]{10,}|[0-9a-fA-F]{32,}|[A-Za-z0-9+/]{40,}={0,2})$")

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
            implementation = Implementation(SecretDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (value.isBlank() || value.length < 8) return

                val matchesValuePattern = SECRET_VALUE_PATTERN.matches(value)
                val variable = node.getParentOfType(UVariable::class.java, true)
                val matchesNamePattern = variable?.name?.let { SECRET_NAME_PATTERN.matches(it) } == true

                if (matchesValuePattern || matchesNamePattern) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Potential secret hardcoded in source code. Use the Secrets Gradle Plugin or environment variables instead."
                    )
                }
            }
        }
    }
}