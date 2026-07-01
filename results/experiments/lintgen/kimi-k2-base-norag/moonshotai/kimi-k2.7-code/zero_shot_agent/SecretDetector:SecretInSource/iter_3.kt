package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

class SecretDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        @JvmField
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
            "(api[_\\s-]?key|apikey|secret|token|password|passwd|auth|private|credential|access[_\\s-]?key|key)",
            RegexOption.IGNORE_CASE
        )

        private val SUSPICIOUS_VALUE_REGEX = Regex(
            "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z0-9_\\-]{16,}$"
        )

        private val PLACEHOLDER_REGEX = Regex(
            "\\b(your|my|placeholder|todo|example|sample|test|testing|dummy|fake|changeme|insert|here|xxx)\\b",
            RegexOption.IGNORE_CASE
        )

        private val SENSITIVE_VALUE_ATTRIBUTES = setOf(
            "value", "key", "secret", "token", "password", "passwd", "auth",
            "apikey", "api_key", "accesskey", "privatekey", "credential"
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
                if (name.isNotBlank() && SECRET_NAME_REGEX.containsMatchIn(name)) {
                    reportSource(context, node)
                    return
                }

                if (SUSPICIOUS_VALUE_REGEX.matches(value)) {
                    reportSource(context, node)
                }
            }
        }
    }

    override fun getApplicableElements(): Collection<String>? {
        return XmlScannerConstants.ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == "meta-data") {
            val metaName = element.getAttribute("android:name").ifBlank {
                element.getAttribute("name")
            }
            if (metaName.isNotBlank() && SECRET_NAME_REGEX.containsMatchIn(metaName)) {
                findAttributeByLocalName(element, "value")?.let { attr ->
                    val attrValue = attr.value ?: ""
                    if (!shouldSkipValue(attrValue)) {
                        reportXml(context, attr, "Possible secret hardcoded in manifest")
                    }
                }
            }
        }

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val localName = attr.localName ?: continue
            val attrValue = attr.value ?: continue

            if (shouldSkipValue(attrValue)) continue
            if (localName == "name" || attr.prefix == "xmlns") continue
            if (element.tagName == "meta-data" && localName == "value") continue

            val nameSuspicious = SECRET_NAME_REGEX.containsMatchIn(localName)
            val valueSuspicious = SUSPICIOUS_VALUE_REGEX.matches(attrValue) &&
                    localName in SENSITIVE_VALUE_ATTRIBUTES

            if (nameSuspicious || valueSuspicious) {
                reportXml(context, attr, "Possible secret hardcoded in XML")
            }
        }

        if (element.tagName == "string") {
            val name = element.getAttribute("name")
            val text = element.textContent?.trim() ?: ""
            if (shouldSkipValue(text)) return

            if (SECRET_NAME_REGEX.containsMatchIn(name) || SUSPICIOUS_VALUE_REGEX.matches(text)) {
                reportXml(context, element, "Possible secret hardcoded in XML")
            }
        }
    }

    private fun getSecretName(node: UElement): String? {
        var current: UElement? = node.uastParent
        while (current != null) {
            when (current) {
                is UVariable -> return current.name
                is UNamedExpression -> return current.name
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
        if (value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true)) return true
        if (PLACEHOLDER_REGEX.containsMatchIn(value)) return true
        return false
    }

    private fun findAttributeByLocalName(element: Element, localName: String): Attr? {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            if (attr.localName == localName) return attr
        }
        return null
    }

    private fun reportSource(context: JavaContext, node: UElement) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Possible secret hardcoded in source code"
        )
    }

    private fun reportXml(context: XmlContext, scope: Node, message: String) {
        context.report(ISSUE, scope, context.getLocation(scope), message)
    }
}