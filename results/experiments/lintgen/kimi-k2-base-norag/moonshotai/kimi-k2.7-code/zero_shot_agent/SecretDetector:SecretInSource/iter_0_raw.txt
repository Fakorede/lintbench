package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import org.w3c.dom.Attr
import org.w3c.dom.Element
import java.util.EnumSet

class SecretDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk.
                It is generally best practice to not include API keys in source code,
                and instead use something like the Secrets Gradle Plugin for Android.
            """.trimIndent(),
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecretDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE, Scope.MANIFEST)
            )
        )

        private const val MIN_SECRET_LENGTH = 8

        private val SECRET_NAME_REGEX = Regex(
            "(api[_\\s-]?key|secret|token|password|auth|private|credential)",
            RegexOption.IGNORE_CASE
        )

        private val SUSPICIOUS_VALUE_REGEX = Regex(
            "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z0-9_\\-]{16,}$"
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(ULiteralExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (shouldSkipValue(value)) return

                val name = getSecretName(node) ?: ""
                if (SECRET_NAME_REGEX.containsMatchIn(name) || SUSPICIOUS_VALUE_REGEX.matches(value)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Possible secret hardcoded in source code"
                    )
                }
            }
        }
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(XmlScannerConstants.ALL)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            checkXmlValue(context, attr, attr.name, attr.value ?: continue)
        }

        if (element.tagName == "string") {
            val name = element.getAttribute("name") ?: ""
            val text = element.textContent?.trim() ?: ""
            checkXmlValue(context, element, name, text)
        }
    }

    private fun getSecretName(node: UElement): String? {
        var current: UElement? = node.uastParent
        while (current != null) {
            when (current) {
                is UVariable -> return current.name
                is UBinaryExpression -> {
                    val left = current.leftOperand
                    return when (left) {
                        is USimpleNameReferenceExpression -> left.identifier
                        is UReferenceExpression -> left.resolvedName
                        else -> null
                    }
                }
                is UCallExpression, is UMethod -> return null
            }
            current = current.uastParent
        }
        return null
    }

    private fun shouldSkipValue(value: String): Boolean {
        if (value.length < MIN_SECRET_LENGTH) return true
        if (value.contains(" ") || value.contains("://")) return true
        if (value.startsWith("@") || value.startsWith("\${")) return true
        if (value.contains("BuildConfig")) return true
        return false
    }

    private fun checkXmlValue(
        context: XmlContext,
        scope: org.w3c.dom.Node,
        name: String,
        value: String
    ) {
        if (value.length < MIN_SECRET_LENGTH) return
        if (value.contains(" ") || value.contains("://")) return
        if (value.startsWith("@") || value.startsWith("\$")) return

        if (SECRET_NAME_REGEX.containsMatchIn(name) || SUSPICIOUS_VALUE_REGEX.matches(value)) {
            context.report(
                ISSUE,
                scope,
                context.getLocation(scope),
                "Possible secret hardcoded in XML"
            )
        }
    }
}