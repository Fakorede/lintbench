package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.Text
import java.util.EnumSet

class DataBindingDetector : Detector(), Detector.XmlScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, \
                you must escape the characters.
                
                For example, `&` must be escaped as `&amp;`, `<` must be escaped as `&lt;`, \
                `>` must be escaped as `&gt;`, `"` must be escaped as `&quot;` when used \
                inside attribute values delimited by double quotes, and `'` must be escaped \
                as `&apos;` when used inside attribute values delimited by single quotes.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                EnumSet.of(Scope.RESOURCE_FILE)
            )
        )

        // Characters that need escaping in XML text content
        private val UNESCAPED_AMPERSAND = Regex("&(?!(?:#\\d+|#x[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);)")
        private val UNESCAPED_LT = Regex("<(?![!?/a-zA-Z])")

        // Characters that need escaping in attribute values
        private val ATTR_UNESCAPED_AMPERSAND = Regex("&(?!(?:#\\d+|#x[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);)")
    }

    override fun getApplicableElements(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        // Check text content of elements
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.TEXT_NODE) {
                val textNode = child as Text
                checkTextContent(context, textNode)
            }
        }

        // Check attributes
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            checkAttributeValue(context, attr)
        }
    }

    private fun checkTextContent(context: XmlContext, textNode: Text) {
        val text = textNode.nodeValue ?: return
        if (text.isBlank()) return

        // Check for unescaped & in text content
        val ampersandMatch = UNESCAPED_AMPERSAND.find(text)
        if (ampersandMatch != null) {
            val location = context.getLocation(textNode)
            context.report(
                ISSUE,
                textNode,
                location,
                "The character `&` must be escaped as `&amp;` in XML text content"
            )
            return
        }

        // Check for unescaped < in text content
        val ltMatch = UNESCAPED_LT.find(text)
        if (ltMatch != null) {
            val location = context.getLocation(textNode)
            context.report(
                ISSUE,
                textNode,
                location,
                "The character `<` must be escaped as `&lt;` in XML text content"
            )
        }
    }

    private fun checkAttributeValue(context: XmlContext, attr: Attr) {
        val value = attr.value ?: return
        if (value.isEmpty()) return

        // Check for unescaped & in attribute values
        val ampersandMatch = ATTR_UNESCAPED_AMPERSAND.find(value)
        if (ampersandMatch != null) {
            val location = context.getLocation(attr)
            context.report(
                ISSUE,
                attr,
                location,
                "The character `&` must be escaped as `&amp;` in XML attribute values"
            )
            return
        }

        // Check for unescaped < in attribute values
        if (value.contains('<')) {
            val location = context.getLocation(attr)
            context.report(
                ISSUE,
                attr,
                location,
                "The character `<` must be escaped as `&lt;` in XML attribute values"
            )
            return
        }

        // Check for unescaped " in double-quoted attribute values
        // Note: The XML parser typically handles quote detection, but we check the raw value
        // for cases where the value contains a double quote that should be escaped
        if (value.contains('"')) {
            val location = context.getLocation(attr)
            context.report(
                ISSUE,
                attr,
                location,
                "The character `\"` must be escaped as `&quot;` in XML attribute values"
            )
        }
    }
}