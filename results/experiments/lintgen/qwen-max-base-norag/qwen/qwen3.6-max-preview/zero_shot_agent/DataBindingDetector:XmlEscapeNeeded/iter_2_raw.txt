package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes ?: return
        val contents = context.contents

        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val loc = context.getLocation(attr)
            val start = loc.start?.offset ?: continue
            val end = loc.end?.offset ?: continue
            if (start < 0 || end > contents.length || start >= end) continue

            val rawAttr = contents.substring(start, end)
            val eq = rawAttr.indexOf('=')
            if (eq == -1) continue

            val valueRaw = rawAttr.substring(eq + 1).trim()
            if (valueRaw.length < 2) continue

            val quote = valueRaw[0]
            if ((quote == '"' || quote == '\'') && valueRaw.last() == quote) {
                val inner = valueRaw.substring(1, valueRaw.length - 1)
                if (inner.contains("@{") || inner.contains("@={")) {
                    val cleaned = inner
                        .replace("&lt;", "")
                        .replace("&gt;", "")
                        .replace("&amp;", "")
                        .replace("&quot;", "")
                        .replace("&apos;", "")
                        .replace(Regex("&#\\d+;"), "")
                        .replace(Regex("&#x[0-9a-fA-F]+;"), "")

                    if ('<' in cleaned || '&' in cleaned) {
                        context.report(
                            ISSUE,
                            loc,
                            "Missing XML Escape: Data binding expression contains unescaped '<' or '&'"
                        )
                    }
                }
            }
        }
    }
}