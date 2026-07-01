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
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.w3c.dom.Attr
import org.w3c.dom.Element
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class SecretDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private val SECRET_NAME_PATTERN = Regex(
            "(api[_-]?key|apikey|secret|password|passwd|pwd|token|auth|credential|maps_key|access_key|private_key)",
            RegexOption.IGNORE_CASE
        )

        // Google Maps API keys start with "AIza".
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
                if (value.isBlank()) return
                if (isResourceReference(value)) return

                if (isSuspiciousValue(value) || isAssignedToSecretName(node)) {
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
        return GOOGLE_MAPS_KEY_PATTERN.matches(value) || value.contains(SECRET_NAME_PATTERN)
    }

    private fun isResourceReference(value: String): Boolean {
        return value.startsWith("@") || value.startsWith("?")
    }

    private fun isAssignedToSecretName(node: ULiteralExpression): Boolean {
        val parent = node.uastParent ?: return false
        return when (parent) {
            is UVariable -> parent.name?.contains(SECRET_NAME_PATTERN) == true
            is UBinaryExpression -> {
                if (parent.operator != UastBinaryOperator.ASSIGN) return false
                val left = parent.leftOperand as? UReferenceExpression ?: return false
                left.asSourceString().contains(SECRET_NAME_PATTERN)
            }
            else -> false
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return true
    }

    override fun getApplicableElements(): Collection<String>? {
        return XmlScanner.ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val text = element.textContent ?: return
        if (text.isBlank()) return
        if (isResourceReference(text)) return

        if (isSuspiciousValue(text) || hasSecretContext(element)) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                MESSAGE
            )
        }
    }

    private fun hasSecretContext(element: Element): Boolean {
        val tagName = element.tagName ?: ""
        val name = element.getAttribute("name")
        return tagName.contains(SECRET_NAME_PATTERN) || name.contains(SECRET_NAME_PATTERN)
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return XmlScanner.ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val attrLocalName = attribute.localName()
        if (attrLocalName == "name" || attrLocalName == "id") return

        val value = attribute.value ?: return
        if (value.isBlank()) return
        if (isResourceReference(value)) return

        if (isSuspiciousValue(value) || hasSecretContext(attribute)) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                MESSAGE
            )
        }
    }

    private fun hasSecretContext(attribute: Attr): Boolean {
        val attrName = attribute.name ?: ""
        val ownerElement = attribute.ownerElement ?: return false
        val tagName = ownerElement.tagName ?: ""
        val name = ownerElement.getAttribute("name")
        return attrName.contains(SECRET_NAME_PATTERN) ||
                tagName.contains(SECRET_NAME_PATTERN) ||
                name.contains(SECRET_NAME_PATTERN)
    }

    private fun Attr.localName(): String {
        val attrName = name ?: return ""
        val index = attrName.indexOf(":")
        return if (index >= 0) attrName.substring(index + 1) else attrName
    }
}