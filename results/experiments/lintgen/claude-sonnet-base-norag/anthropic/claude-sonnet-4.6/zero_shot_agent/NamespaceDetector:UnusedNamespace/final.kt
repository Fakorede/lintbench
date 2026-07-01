/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node

/**
 * Checks for unused namespace declarations in XML documents.
 */
class NamespaceDetector : LayoutDetector() {

    companion object {
        /** Unused namespace declarations */
        @JvmField
        val UNUSED: Issue = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = """
                Unused namespace declarations take up space and require processing that is \
                not necessary
                """,
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val XMLNS_PREFIX = "xmlns:"
        private const val XMLNS = "xmlns"
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        // Collect all namespace declarations on the root element
        val namespaceDeclarations = mutableMapOf<String, Attr>()
        val attributes: NamedNodeMap = root.attributes ?: return

        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.name
            if (name.startsWith(XMLNS_PREFIX)) {
                val prefix = name.substring(XMLNS_PREFIX.length)
                if (prefix.isNotEmpty()) {
                    namespaceDeclarations[prefix] = attr
                }
            } else if (name == XMLNS) {
                // Default namespace declaration - skip, not prefix-based
            }
        }

        if (namespaceDeclarations.isEmpty()) {
            return
        }

        // Collect all used prefixes in the document
        val usedPrefixes = mutableSetOf<String>()
        collectUsedPrefixes(root, usedPrefixes)

        // Report unused namespaces
        for ((prefix, attr) in namespaceDeclarations) {
            if (prefix !in usedPrefixes) {
                context.report(
                    UNUSED,
                    attr,
                    context.getLocation(attr),
                    "Unused namespace `${attr.name}`"
                )
            }
        }
    }

    private fun collectUsedPrefixes(node: Node, usedPrefixes: MutableSet<String>) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element

            // Check element prefix
            val elementPrefix = element.prefix
            if (!elementPrefix.isNullOrEmpty()) {
                usedPrefixes.add(elementPrefix)
            }

            // Check attribute prefixes
            val attributes = element.attributes
            if (attributes != null) {
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as? Attr ?: continue
                    val attrName = attr.name

                    // Skip namespace declarations themselves
                    if (attrName.startsWith(XMLNS_PREFIX) || attrName == XMLNS) {
                        continue
                    }

                    val attrPrefix = attr.prefix
                    if (!attrPrefix.isNullOrEmpty()) {
                        usedPrefixes.add(attrPrefix)
                    }
                }
            }

            // Recurse into children
            val children = element.childNodes
            if (children != null) {
                for (i in 0 until children.length) {
                    collectUsedPrefixes(children.item(i), usedPrefixes)
                }
            }
        }
    }
}