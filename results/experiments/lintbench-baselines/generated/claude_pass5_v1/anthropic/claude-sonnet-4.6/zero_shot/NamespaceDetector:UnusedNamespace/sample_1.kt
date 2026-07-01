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
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val UNUSED = Issue.create(
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
        val declaredNamespaces = mutableMapOf<String, Attr>() // prefix -> attr

        val attributes: NamedNodeMap = root.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val name = attr.name
            if (name.startsWith(XMLNS_PREFIX)) {
                val prefix = name.substring(XMLNS_PREFIX.length)
                if (prefix.isNotEmpty()) {
                    declaredNamespaces[prefix] = attr
                }
            } else if (name == XMLNS) {
                // default namespace declaration - skip, not a prefixed namespace
            }
        }

        if (declaredNamespaces.isEmpty()) {
            return
        }

        // Collect all used namespace prefixes in the document
        val usedPrefixes = mutableSetOf<String>()
        collectUsedNamespacePrefixes(root, usedPrefixes, true)

        // Report unused namespaces
        for ((prefix, attr) in declaredNamespaces) {
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

    private fun collectUsedNamespacePrefixes(
        node: Node,
        usedPrefixes: MutableSet<String>,
        isRoot: Boolean
    ) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element

            // Check the element's own prefix
            val elementPrefix = element.prefix
            if (!elementPrefix.isNullOrEmpty()) {
                usedPrefixes.add(elementPrefix)
            }

            // Check attributes
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as Attr
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

            // Recurse into children
            val children = element.childNodes
            for (i in 0 until children.length) {
                collectUsedNamespacePrefixes(children.item(i), usedPrefixes, false)
            }
        }
    }
}