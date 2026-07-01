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
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node

class NamespaceDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val REDUNDANT_NAMESPACE: Issue = Issue.create(
            id = "RedundantNamespace",
            briefDescription = "Redundant namespace",
            explanation = """
                In Android XML documents, only specify the namespace on the root/document \
                element. Namespace declarations elsewhere in the document are typically \
                accidental leftovers from copy/pasting XML from other files or documentation.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val XMLNS_PREFIX = "xmlns"
        private const val XMLNS_COLON = "xmlns:"
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        // Collect all namespace prefixes declared on the root element
        val rootNamespaces = mutableSetOf<String>()
        val rootAttributes: NamedNodeMap = root.attributes ?: return
        for (i in 0 until rootAttributes.length) {
            val attr = rootAttributes.item(i) as? Attr ?: continue
            val name = attr.name
            if (name == XMLNS_PREFIX || name.startsWith(XMLNS_COLON)) {
                rootNamespaces.add(name)
            }
        }

        // Now walk all non-root elements and check for namespace declarations
        checkChildren(context, root, rootNamespaces)
    }

    private fun checkChildren(context: XmlContext, element: Element, rootNamespaces: Set<String>) {
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                checkElement(context, childElement, rootNamespaces)
                checkChildren(context, childElement, rootNamespaces)
            }
            child = child.nextSibling
        }
    }

    private fun checkElement(context: XmlContext, element: Element, rootNamespaces: Set<String>) {
        val attributes: NamedNodeMap = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.name
            if (name == XMLNS_PREFIX || name.startsWith(XMLNS_COLON)) {
                val message = if (rootNamespaces.contains(name)) {
                    "Redundant namespace declaration `$name`; already declared on the root element"
                } else {
                    "Redundant namespace declaration `$name`; namespace declarations should only appear on the root element"
                }
                context.report(
                    issue = REDUNDANT_NAMESPACE,
                    location = context.getLocation(attr),
                    message = message
                )
            }
        }
    }
}