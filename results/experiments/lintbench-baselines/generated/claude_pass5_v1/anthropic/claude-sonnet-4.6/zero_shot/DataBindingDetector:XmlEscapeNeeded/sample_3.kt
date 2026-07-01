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
import org.w3c.dom.Text

class DataBindingDetector : Detector(), Detector.XmlScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, \
                you must escape the characters.
                
                For example, `&` must be escaped as `&amp;`, `<` as `&lt;`, \
                `>` as `&gt;`, `"` as `&quot;` when used in attribute values, \
                and `'` as `&apos;` when used in single-quoted attribute values.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        // Characters that must be escaped in XML text content
        private val UNESCAPED_AMPERSAND = Regex("&(?!(?:#\\d+|#x[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);)")
        private val UNESCAPED_LT = Regex("<(?!\\!\\[CDATA\\[)")

        // Characters that must be escaped in XML attribute values
        private val ATTR_UNESCAPED_AMPERSAND = Regex("&(?!(?:#\\d+|#x[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);)")
        private val ATTR_UNESCAPED_LT = Regex("<")
        private val ATTR_UNESCAPED_DOUBLE_QUOTE = Regex("\"")
        private val ATTR_UNESCAPED_SINGLE_QUOTE = Regex("'")
    }

    override fun getApplicableElements(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        // Check text content of elements
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Text) {
                val text = node.nodeValue ?: continue
                checkTextContent(context, node, text, element)
            }
        }

        // Check attributes
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val value = attr.value ?: continue
            checkAttributeValue(context, attr, value)
        }
    }

    private fun checkTextContent(context: XmlContext, node: Text, text: String, element: Element) {
        // Check for unescaped ampersand in text content
        val ampersandMatch = UNESCAPED_AMPERSAND.find(text)
        if (ampersandMatch != null) {
            val location = context.getLocation(node)
            context.report(
                ISSUE,
                node,
                location,
                "The character `&` must be escaped as `&amp;` in XML text content"
            )
            return
        }

        // Check for unescaped less-than in text content
        val ltMatch = UNESCAPED_LT.find(text)
        if (ltMatch != null) {
            val location = context.getLocation(node)
            context.report(
                ISSUE,
                node,
                location,
                "The character `<` must be escaped as `&lt;` in XML text content"
            )
        }
    }

    private fun checkAttributeValue(context: XmlContext, attr: Attr, value: String) {
        // Check for unescaped ampersand in attribute value
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

        // Check for unescaped less-than in attribute value
        val ltMatch = ATTR_UNESCAPED_LT.find(value)
        if (ltMatch != null) {
            val location = context.getLocation(attr)
            context.report(
                ISSUE,
                attr,
                location,
                "The character `<` must be escaped as `&lt;` in XML attribute values"
            )
            return
        }

        // Check for unescaped double-quote in double-quoted attribute values
        // Note: The XML parser typically handles this, but we check the raw attribute value
        if (value.contains("\"")) {
            val location = context.getLocation(attr)
            context.report(
                ISSUE,
                attr,
                location,
                "The character `\"` must be escaped as `&quot;` in XML attribute values"
            )
            return
        }

        // Check for unescaped single-quote in single-quoted attribute values
        if (value.contains("'")) {
            val location = context.getLocation(attr)
            context.report(
                ISSUE,
                attr,
                location,
                "The character `'` must be escaped as `&apos;` in XML attribute values"
            )
        }
    }
}