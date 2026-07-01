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
import org.w3c.dom.Node

/**
 * Checks for redundant namespace declarations in XML documents.
 *
 * Namespace declarations should only appear on the root element of an
 * Android XML document. Declarations on non-root elements are typically
 * accidental leftovers from copy/pasting XML from other files.
 */
class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        /** The main issue reported by this detector. */
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
        private const val XMLNS_ANDROID = "xmlns:android"
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkElement(context, root, isRoot = true)
    }

    private fun checkElement(context: XmlContext, element: Element, isRoot: Boolean) {
        if (!isRoot) {
            // Check for namespace declarations on non-root elements
            val attributes = element.attributes
            if (attributes != null) {
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as? Attr ?: continue
                    val name = attr.name ?: continue
                    if (name == XMLNS_PREFIX || name.startsWith("$XMLNS_PREFIX:")) {
                        val fix = fix()
                            .name("Remove namespace declaration")
                            .unset(attr.namespaceURI, attr.localName ?: name)
                            .build()
                        context.report(
                            issue = REDUNDANT_NAMESPACE,
                            scope = attr,
                            location = context.getLocation(attr),
                            message = "Redundant namespace declaration `$name`; already declared on the root element",
                            quickfixData = fix
                        )
                    }
                }
            }
        }

        // Recurse into children
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkElement(context, child as Element, isRoot = false)
            }
            child = child.nextSibling
        }
    }
}