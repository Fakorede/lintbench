package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class DataBindingDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            checkAttribute(context, attr)
        }
    }

    private fun checkAttribute(context: XmlContext, attr: Attr) {
        val location = context.getValueLocation(attr)
        val raw = rawText(context, location) ?: return
        val unescaped = findUnescapedSpecialChars(raw)
        if (unescaped.isNotEmpty()) {
            context.report(
                ISSUE,
                attr,
                location,
                "Missing XML escape for character(s): ${unescaped.joinToString(", ")}. " +
                        "Escape <, >, and & as &lt;, &gt;, and &amp;."
            )
        }
    }

    private fun rawText(context: XmlContext, location: Location): String? {
        val contents = context.getContents() ?: return null
        val start = location.start?.offset ?: return null
        val end = location.end?.offset ?: return null
        if (start < 0 || end > contents.size || end <= start) return null
        return String(contents, start, end - start)
    }

    private fun findUnescapedSpecialChars(raw: String): List<String> {
        val found = LinkedHashSet<String>()
        var i = 0
        while (i < raw.length) {
            when (raw[i]) {
                '<' -> found.add("<")
                '>' -> found.add(">")
                '&' -> {
                    val semi = raw.indexOf(';', i + 1)
                    if (semi == -1 || semi - i < 2) {
                        found.add("&")
                    } else {
                        val body = raw.substring(i + 1, semi)
                        if (!isXmlEntity(body)) {
                            found.add("&")
                        }
                        i = semi
                    }
                }
            }
            i++
        }
        return found.toList()
    }

    private fun isXmlEntity(body: String): Boolean {
        if (body in PREDEFINED_ENTITIES) return true
        if (!body.startsWith("#")) return false
        val reference = body.substring(1)
        if (reference.isEmpty()) return false
        return reference.matches(Regex("\\d+")) ||
                (reference.length > 1 && reference[0] in setOf('x', 'X') &&
                        reference.substring(1).matches(Regex("[0-9a-fA-F]+")))
    }

    companion object {
        private val PREDEFINED_ENTITIES = setOf("lt", "gt", "amp", "quot", "apos")

        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML,
                you must escape the characters. For example, use &lt; for <,
                &gt; for >, and &amp; for &.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}