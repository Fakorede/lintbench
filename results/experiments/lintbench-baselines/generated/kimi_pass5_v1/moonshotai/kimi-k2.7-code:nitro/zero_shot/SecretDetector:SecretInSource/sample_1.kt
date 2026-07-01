package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UElementHandler
import org.w3c.dom.Element
import java.util.Locale

class SecretDetector : Detector(), SourceCodeScanner, XmlScanner {

    override fun getApplicableUastTypes() = listOf(ULiteralExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitLiteralExpression(node: ULiteralExpression) {
                val value = node.value as? String ?: return
                if (isSuspicious(value)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Possible secret found in source code"
                    )
                }
            }
        }
    }

    override fun getApplicableElements() = listOf("meta-data", "string")

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "meta-data" -> visitMetaData(context, element)
            "string" -> visitStringResource(context, element)
        }
    }

    private fun visitMetaData(context: XmlContext, element: Element) {
        val name = element.getAndroidAttribute("name") ?: element.getAttribute("name")
        val value = element.getAndroidAttribute("value") ?: element.getAttribute("value")
        if (value.isBlank()) return

        if (isSuspicious(value) || (isKeyLikeName(name) && looksLikeSecretValue(value))) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Possible secret found in AndroidManifest.xml"
            )
        }
    }

    private fun visitStringResource(context: XmlContext, element: Element) {
        val name = element.getAttribute("name")
        if (!isKeyLikeName(name)) return

        val value = element.textContent ?: return
        if (looksLikeSecretValue(value)) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Possible secret found in string resource"
            )
        }
    }

    private fun Element.getAndroidAttribute(localName: String): String? {
        return if (hasAttributeNS(ANDROID_URI, localName)) {
            getAttributeNS(ANDROID_URI, localName)
        } else {
            null
        }
    }

    private fun isKeyLikeName(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.contains("key") ||
                lower.contains("secret") ||
                lower.contains("token") ||
                lower.contains("password") ||
                lower.contains("auth")
    }

    private fun looksLikeSecretValue(value: String): Boolean {
        return value.length >= 16 &&
                !value.contains(" ") &&
                !isPlaceholder(value)
    }

    private fun isSuspicious(value: String): Boolean {
        if (value.isBlank() || isPlaceholder(value)) return false
        if (SECRET_PATTERNS.any { it.containsMatchIn(value) }) return true
        return false
    }

    private fun isPlaceholder(value: String): Boolean {
        val lower = value.lowercase(Locale.ROOT)
        return PLACEHOLDER_VALUES.any { lower.contains(it) } ||
                value.all { it == '0' || it == 'x' || it == 'X' }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SecretInSource",
            briefDescription = "Secret in source code",
            explanation = """
                Including secrets, such as API keys, in source code is a security risk.
                It is generally best practice to not include API keys in source code,
                and instead use something like the Secrets Gradle Plugin for Android.
            """.trimIndent(),
            moreInfo = "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin",
            category = Category.SECURITY,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecretDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
                Scope.RESOURCE_FILE_SCOPE,
                Scope.MANIFEST_SCOPE
            )
        )

        private val SECRET_PATTERNS = listOf(
            Regex("""AIza[0-9A-Za-z_-]{35}"""),
            Regex("""AKIA[0-9A-Z]{16}"""),
            Regex("""ghp_[A-Za-z0-9_]{35,}""", RegexOption.IGNORE_CASE),
            Regex("""glpat-[A-Za-z0-9_-]{20,}""", RegexOption.IGNORE_CASE),
            Regex("""sk-[a-zA-Z0-9]{48}""", RegexOption.IGNORE_CASE),
            Regex("""sk_live_[a-zA-Z0-9]{24,}""", RegexOption.IGNORE_CASE),
            Regex("""SG\.[A-Za-z0-9_-]{22,}\.[A-Za-z0-9_-]{43}""", RegexOption.IGNORE_CASE),
            Regex("""\b(key|apikey|api_key|token|secret|password)=([^&\s]{8,})""", RegexOption.IGNORE_CASE)
        )

        private val PLACEHOLDER_VALUES = listOf(
            "your",
            "example",
            "placeholder",
            "test",
            "demo",
            "0123456789"
        )
    }
}