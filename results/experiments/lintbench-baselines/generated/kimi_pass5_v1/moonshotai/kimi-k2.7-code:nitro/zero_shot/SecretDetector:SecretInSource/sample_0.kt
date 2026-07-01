package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UElementHandler
import org.w3c.dom.Element

class SecretDetector : Detector(), Detector.SourceCodeScanner, Detector.XmlScanner {

    companion object {
        private const val MESSAGE = "Hardcoded secret detected; avoid storing secrets in source code."

        private val SECRET_NAME_REGEX =
            "(?i)(api[-_\\s]?key|secret|private[-_\\s]?key|auth[-_\\s]?token|access[-_\\s]?token|password|passwd)".toRegex()

        private const val MORE_INFO_URL =
            "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk. It is generally best practice to not include API keys in source code, and instead use something like the Secrets Gradle Plugin for Android.
                """.trimIndent(),
            moreInfo = MORE_INFO_URL,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
                Scope.MANIFEST_SCOPE,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes() = listOf(ULiteralExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (value.isBlank()) return

                if (isAssignedToSecretName(node)) {
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

    private fun isAssignedToSecretName(node: ULiteralExpression): Boolean {
        var parent: UElement? = node.uastParent
        while (parent != null) {
            when (parent) {
                is UVariable -> {
                    if (parent.name?.let { isSecretName(it) } == true) {
                        return true
                    }
                }
                is UBinaryExpression -> {
                    if (parent.operator == UastBinaryOperator.ASSIGN) {
                        val left = parent.leftOperand
                        if (isSecretReference(left)) {
                            return true
                        }
                    }
                }
            }
            parent = parent.uastParent
        }
        return false
    }

    private fun isSecretReference(expression: UElement): Boolean {
        return when (expression) {
            is USimpleNameReferenceExpression -> isSecretName(expression.identifier)
            is UQualifiedReferenceExpression -> {
                val selector = expression.selector
                if (selector is USimpleNameReferenceExpression) {
                    isSecretName(selector.identifier)
                } else {
                    false
                }
            }
            else -> false
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> = listOf("meta-data", "string")

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "meta-data" -> {
                val name = element.getAttributeNS(ANDROID_URI, "name")
                if (isSecretName(name)) {
                    val valueAttr = element.getAttributeNodeNS(ANDROID_URI, "value")
                    val value = valueAttr?.value ?: ""
                    if (value.isNotBlank() && !value.startsWith("@") && !value.startsWith("\${")) {
                        context.report(
                            ISSUE,
                            element,
                            valueAttr?.let { context.getLocation(it) } ?: context.getLocation(element),
                            MESSAGE
                        )
                    }
                }
            }
            "string" -> {
                val name = element.getAttribute("name")
                if (isSecretName(name)) {
                    val value = element.textContent?.trim()
                    if (!value.isNullOrBlank() && !value.startsWith("@")) {
                        context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            MESSAGE
                        )
                    }
                }
            }
        }
    }

    private fun isSecretName(name: String): Boolean {
        return name.isNotBlank() && SECRET_NAME_REGEX.containsMatchIn(name)
    }
}