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
        val declaredNamespaces = mutableMapOf<String, Attr>() // prefix -> attr node

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
                // Default namespace - not tracked by prefix
            }
        }

        if (declaredNamespaces.isEmpty()) {
            return
        }

        // Collect all used namespace prefixes in the document
        val usedPrefixes = mutableSetOf<String>()
        collectUsedNamespaces(root, usedPrefixes, true)

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

    /**
     * Recursively collects all namespace prefixes used in element names and attribute names.
     *
     * @param element the element to inspect
     * @param usedPrefixes the set to add used prefixes to
     * @param isRoot whether this is the root element (to skip xmlns declarations themselves)
     */
    private fun collectUsedNamespaces(
        element: Element,
        usedPrefixes: MutableSet<String>,
        isRoot: Boolean
    ) {
        // Check element prefix
        val elementPrefix = element.prefix
        if (!elementPrefix.isNullOrEmpty()) {
            usedPrefixes.add(elementPrefix)
        }

        // Check attributes
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val attrName = attr.name

            // Skip xmlns declarations themselves when checking usage
            if (attrName.startsWith(XMLNS_PREFIX) || attrName == XMLNS) {
                continue
            }

            // Check attribute prefix
            val attrPrefix = attr.prefix
            if (!attrPrefix.isNullOrEmpty()) {
                usedPrefixes.add(attrPrefix)
            } else {
                // Also handle cases like "android:something" style (non-namespace-aware parsing)
                val colonIndex = attrName.indexOf(':')
                if (colonIndex > 0) {
                    val prefix = attrName.substring(0, colonIndex)
                    usedPrefixes.add(prefix)
                }
            }
        }

        // Recurse into children
        var child: Node? = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                collectUsedNamespaces(child as Element, usedPrefixes, false)
            }
            child = child.nextSibling
        }
    }
}