package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import com.android.tools.lint.client.api.UElementHandler
import org.jetbrains.uast.*
import com.android.SdkConstants
import org.w3c.dom.Attr
import org.w3c.dom.Element

class DataBindingDetector : ResourceXmlDetector() {

    companion object {
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, \
                you must escape the characters. For example, `&` must be escaped as `&amp;`, \
                `<` must be escaped as `&lt;`, `>` must be escaped as `&gt;`, \
                `"` must be escaped as `&quot;`, and `'` must be escaped as `&apos;`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val SPECIAL_CHARS = mapOf(
            '&' to "&amp;",
            '<' to "&lt;",
            '>' to "&gt;"
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.TAG_STRING)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val text = element.textContent ?: return
        checkForUnescapedChars(context, element, text)
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return XmlScannerConstants.ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        // Skip namespace declarations and tool attributes
        if (attribute.name.startsWith("xmlns:") || 
            attribute.namespaceURI == SdkConstants.TOOLS_URI) {
            return
        }
        checkForUnescapedCharsInAttribute(context, attribute, value)
    }

    private fun checkForUnescapedChars(context: XmlContext, element: Element, text: String) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '&' -> {
                    // Check if it's already escaped
                    if (!isValidEscape(text, i)) {
                        context.report(
                            ISSUE,
                            element,
                            context.getElementLocation(element),
                            "String contains unescaped `&` character; use `&amp;` instead"
                        )
                        return
                    } else {
                        // Skip past the entity reference
                        val semicolonIdx = text.indexOf(';', i)
                        if (semicolonIdx > i) {
                            i = semicolonIdx
                        }
                    }
                }
                c == '<' -> {
                    // In element text content, < should be escaped unless in CDATA
                    if (!isInCData(text, i)) {
                        context.report(
                            ISSUE,
                            element,
                            context.getElementLocation(element),
                            "String contains unescaped `<` character; use `&lt;` instead"
                        )
                        return
                    }
                }
            }
            i++
        }
    }

    private fun checkForUnescapedCharsInAttribute(context: XmlContext, attribute: Attr, value: String) {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when (c) {
                '&' -> {
                    if (!isValidEscape(value, i)) {
                        context.report(
                            ISSUE,
                            attribute,
                            context.getValueLocation(attribute),
                            "Attribute value contains unescaped `&` character; use `&amp;` instead"
                        )
                        return
                    } else {
                        val semicolonIdx = value.indexOf(';', i)
                        if (semicolonIdx > i) {
                            i = semicolonIdx
                        }
                    }
                }
                '<' -> {
                    context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "Attribute value contains unescaped `<` character; use `&lt;` instead"
                    )
                    return
                }
            }
            i++
        }
    }

    private fun isValidEscape(text: String, ampIndex: Int): Boolean {
        // Check if this & starts a valid entity reference like &amp; &lt; &gt; &quot; &apos; &#...;
        val remaining = text.substring(ampIndex + 1)
        
        // Named entities
        val namedEntities = listOf("amp;", "lt;", "gt;", "quot;", "apos;")
        for (entity in namedEntities) {
            if (remaining.startsWith(entity)) {
                return true
            }
        }
        
        // Numeric character references &#...; or &#x...;
        if (remaining.startsWith("#")) {
            val semicolonIdx = remaining.indexOf(';')
            if (semicolonIdx > 0) {
                val ref = remaining.substring(1, semicolonIdx)
                if (ref.startsWith("x") || ref.startsWith("X")) {
                    // Hex reference
                    val hex = ref.substring(1)
                    if (hex.isNotEmpty() && hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                        return true
                    }
                } else {
                    // Decimal reference
                    if (ref.isNotEmpty() && ref.all { it.isDigit() }) {
                        return true
                    }
                }
            }
        }
        
        return false
    }

    private fun isInCData(text: String, index: Int): Boolean {
        // Simple check - in real XML parsing this would be handled by the parser
        // This is a simplified heuristic
        val beforeIndex = text.substring(0, index)
        val cdataStart = beforeIndex.lastIndexOf("<![CDATA[")
        if (cdataStart >= 0) {
            val cdataEnd = beforeIndex.indexOf("]]>", cdataStart)
            if (cdataEnd < 0) {
                return true // We're inside CDATA
            }
        }
        return false
    }
}