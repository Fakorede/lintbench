package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class SecretDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private val SECRET_NAME_PATTERN = Regex(
            "(api[_-]?key|apikey|secret|password|passwd|pwd|token|auth|credential|access[_-]?key|private[_-]?key|maps_key)",
            RegexOption.IGNORE_CASE
        )

        private val GOOGLE_MAPS_KEY_PATTERN = Regex(
            "^AIza[0-9A-Za-z_-]{20,}$",
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
                EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST, Scope.RESOURCE_FILE)
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
                if (value.isBlank() || isResourceReference(value)) return

                if (isSuspiciousValue(value) || isAssignedToSecretName(node, value)) {
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

    private fun isSuspiciousValue(value: String): Boolean {
        return GOOGLE_MAPS_KEY_PATTERN.matches(value) || isGenericSecretValue(value)
    }

    private fun isGenericSecretValue(value: String): Boolean {
        if (value.length < 20 || value.containsWhitespace() || value.startsWith("http", ignoreCase = true)) {
            return false
        }
        return value.any { it.isDigit() } && value.any { it.isLetter() }
    }

    private fun isSecretValueCandidate(value: String): Boolean {
        if (value.length < 5 || value.containsWhitespace() || isResourceReference(value)) {
            return false
        }
        return true
    }

    private fun String.containsWhitespace(): Boolean = any { it.isWhitespace() }

    private fun isResourceReference(value: String): Boolean {
        return value.startsWith("@") || value.startsWith("?")
    }

    private fun isSecretName(name: String?): Boolean {
        return name != null && SECRET_NAME_PATTERN.containsMatchIn(name)
    }

    private fun isAssignedToSecretName(node: ULiteralExpression, value: String): Boolean {
        if (!isSecretValueCandidate(value)) return false
        val parent = node.uastParent ?: return false
        return when (parent) {
            is UVariable -> isSecretName(parent.name)
            is UBinaryExpression -> {
                if (parent.operator != UastBinaryOperator.ASSIGN) return false
                val left = parent.leftOperand as? UReferenceExpression ?: return false
                isSecretName(left.asSourceString())
            }
            else -> false
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return true
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        visitElement(context, root)
    }

    private fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName ?: ""
        val nameAttribute = element.getAttribute("name")

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val attrLocalName = attr.localName ?: attr.name
            if (attrLocalName == "name" || attrLocalName == "id") continue

            val value = attr.value ?: continue
            if (value.isBlank() || isResourceReference(value)) continue

            if (isSuspiciousValue(value)) {
                context.report(
                    ISSUE,
                    attr,
                    context.getValueLocation(attr),
                    MESSAGE
                )
            }
        }

        if (tagName == "string" || tagName == "item") {
            val text = element.textContent?.trim() ?: ""
            if (text.isNotBlank() && !isResourceReference(text)) {
                if (isSuspiciousValue(text) ||
                    (isSecretName(nameAttribute) && isSecretValueCandidate(text))
                ) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        MESSAGE
                    )
                }
            }
        }

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                visitElement(context, child as Element)
            }
            child = child.nextSibling
        }
    }
}