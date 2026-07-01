/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr

class DataBindingDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val XML_ESCAPE_NEEDED = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, \
                you must escape the characters.
                
                For example, if your string contains an ampersand (`&`), you must escape it \
                as `&amp;`. If your string contains a less-than sign (`<`), you must escape \
                it as `&lt;`. If your string contains a greater-than sign (`>`), you must \
                escape it as `&gt;`. If your string contains a double quote (`"`), you must \
                escape it as `&quot;`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val SPECIAL_XML_CHARS = listOf('&', '<', '>', '"')

        private fun containsUnescapedXmlChars(value: String): Boolean {
            var i = 0
            while (i < value.length) {
                val c = value[i]
                when (c) {
                    '&' -> {
                        // Check if it's already an escape sequence like &amp;, &lt;, &gt;, &quot;, &apos;, &#...
                        val semicolonIndex = value.indexOf(';', i + 1)
                        if (semicolonIndex != -1) {
                            val entity = value.substring(i + 1, semicolonIndex)
                            if (entity == "amp" || entity == "lt" || entity == "gt" ||
                                entity == "quot" || entity == "apos" ||
                                (entity.startsWith("#") && entity.drop(1).all { it.isDigit() }) ||
                                (entity.startsWith("#x") && entity.drop(2).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' })
                            ) {
                                // Valid escape sequence, skip past it
                                i = semicolonIndex + 1
                                continue
                            }
                        }
                        return true
                    }
                    '<', '>' -> return true
                    '"' -> {
                        // In attribute values delimited by double quotes this is problematic,
                        // but in attribute values delimited by single quotes it's fine.
                        // We'll flag it conservatively.
                        return true
                    }
                }
                i++
            }
            return false
        }

        private fun getUnescapedChars(value: String): List<Char> {
            val found = mutableListOf<Char>()
            var i = 0
            while (i < value.length) {
                val c = value[i]
                when (c) {
                    '&' -> {
                        val semicolonIndex = value.indexOf(';', i + 1)
                        if (semicolonIndex != -1) {
                            val entity = value.substring(i + 1, semicolonIndex)
                            if (entity == "amp" || entity == "lt" || entity == "gt" ||
                                entity == "quot" || entity == "apos" ||
                                (entity.startsWith("#") && entity.drop(1).all { it.isDigit() }) ||
                                (entity.startsWith("#x") && entity.drop(2).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' })
                            ) {
                                i = semicolonIndex + 1
                                continue
                            }
                        }
                        if ('&' !in found) found.add('&')
                    }
                    '<' -> if ('<' !in found) found.add('<')
                    '>' -> if ('>' !in found) found.add('>')
                    '"' -> if ('"' !in found) found.add('"')
                }
                i++
            }
            return found
        }
    }

    override fun getApplicableAttributes(): Collection<String>? = ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return

        // Only check data binding expressions (those starting with @{ or @={)
        // or plain string attribute values
        val unescapedChars = getUnescapedChars(value)
        if (unescapedChars.isEmpty()) return

        val charDescriptions = unescapedChars.joinToString(", ") { char ->
            when (char) {
                '&' -> "'&' (escape as &amp;)"
                '<' -> "'<' (escape as &lt;)"
                '>' -> "'>' (escape as &gt;)"
                '"' -> "'\"' (escape as &quot;)"
                else -> "'$char'"
            }
        }

        context.report(
            issue = XML_ESCAPE_NEEDED,
            location = context.getValueLocation(attribute),
            message = "Attribute value contains unescaped XML special character(s): $charDescriptions"
        )
    }
}