package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr

class DataBindingDetector : LayoutDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            DataBindingDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, \
                you must escape the characters.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val XML_SPECIAL_CHARS = setOf('<', '>', '&', '"', '\'')

        /**
         * Returns true if the given value contains unescaped XML special characters.
         */
        private fun containsUnescapedXmlChars(value: String): Boolean {
            var i = 0
            while (i < value.length) {
                val c = value[i]
                when (c) {
                    '&' -> {
                        // Check if this is already an XML entity reference like &amp; &lt; &gt; &quot; &apos; &#..;
                        val semicolonIndex = value.indexOf(';', i + 1)
                        if (semicolonIndex != -1) {
                            val entity = value.substring(i + 1, semicolonIndex)
                            if (isValidXmlEntity(entity)) {
                                // Skip past the entity
                                i = semicolonIndex + 1
                                continue
                            }
                        }
                        // Unescaped ampersand
                        return true
                    }
                    '<' -> return true
                    '>' -> return true
                    else -> { /* not a special char */ }
                }
                i++
            }
            return false
        }

        /**
         * Returns true if the entity name (without & and ;) is a valid XML entity.
         */
        private fun isValidXmlEntity(entity: String): Boolean {
            if (entity.isEmpty()) return false
            return when (entity) {
                "amp", "lt", "gt", "quot", "apos" -> true
                else -> {
                    // Numeric character references: &#decimal; or &#xhex;
                    if (entity.startsWith("#")) {
                        val numPart = entity.substring(1)
                        if (numPart.startsWith("x") || numPart.startsWith("X")) {
                            numPart.substring(1).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                        } else {
                            numPart.all { it.isDigit() }
                        }
                    } else {
                        false
                    }
                }
            }
        }
    }

    override fun getApplicableAttributes(): Collection<String>? {
        // Return null to visit all attributes
        return ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return

        // Only check attributes that look like data binding expressions or plain string values
        // that might contain unescaped XML special characters.
        // Data binding expressions are wrapped in @{...} or @={...}
        val trimmed = value.trim()

        if (trimmed.startsWith("@{") || trimmed.startsWith("@={")) {
            // Data binding expression — check for unescaped characters inside the expression
            // The expression itself may contain < > & which need escaping in XML attributes
            if (containsUnescapedXmlChars(value)) {
                context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Attribute value contains unescaped XML special characters; " +
                        "use `&amp;`, `&lt;`, `&gt;` etc. to escape them"
                )
            }
        } else {
            // Plain attribute value — check for unescaped XML special characters
            if (containsUnescapedXmlChars(value)) {
                context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Attribute value contains unescaped XML special characters; " +
                        "use `&amp;`, `&lt;`, `&gt;` etc. to escape them"
                )
            }
        }
    }
}