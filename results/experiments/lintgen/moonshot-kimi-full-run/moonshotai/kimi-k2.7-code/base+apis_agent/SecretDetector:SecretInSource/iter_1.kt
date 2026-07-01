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
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.w3c.dom.Attr
import org.w3c.dom.Element
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class SecretDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private val SECRET_PATTERN = Regex(
            "(api[_-]?key|secret|password|passwd|pwd|token|auth|credential)",
            RegexOption.IGNORE_CASE
        )

        private const val MESSAGE =
            "Possible secret found in source code. Avoid including secrets such as API keys in source code."

        @JvmField
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk. It is generally best practice to not include API keys in source code, and instead use something like the Secrets Gradle Plugin for Android.

                Reference documentation:
                - https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecretDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE)
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (looksLikeSecret(value)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE
                    )
                }
            }
        }
    }

    override fun getApplicableElements(): Collection<String>? {
        return null
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val text = element.textContent ?: return
        if (text.isBlank()) return
        if (looksLikeSecret(text)) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                MESSAGE
            )
        }
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return null
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        if (value.isBlank()) return
        if (looksLikeSecret(value)) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                MESSAGE
            )
        }
    }

    private fun looksLikeSecret(value: String): Boolean {
        return SECRET_PATTERN.containsMatchIn(value)
    }
}