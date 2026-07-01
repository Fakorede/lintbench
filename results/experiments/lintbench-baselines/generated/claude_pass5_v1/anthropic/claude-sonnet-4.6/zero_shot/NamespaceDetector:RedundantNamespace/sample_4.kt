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
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node

/**
 * Detector that finds redundant namespace declarations on non-root elements in Android XML
 * documents.
 */
class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

        @JvmField
        val REDUNDANT_NAMESPACE = Issue.create(
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
            implementation = IMPLEMENTATION
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        // Check all non-root elements for namespace declarations
        checkElement(context, root, isRoot = true)
    }

    private fun checkElement(context: XmlContext, element: Element, isRoot: Boolean) {
        if (!isRoot) {
            // Check attributes for namespace declarations (xmlns:* or xmlns)
            val attributes: NamedNodeMap = element.attributes ?: return
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as? Attr ?: continue
                val name = attr.name ?: continue
                if (name == "xmlns" || name.startsWith("xmlns:")) {
                    context.report(
                        REDUNDANT_NAMESPACE,
                        attr,
                        context.getLocation(attr),
                        "Redundant namespace declaration; already declared on the root element"
                    )
                }
            }
        }

        // Recurse into child elements
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkElement(context, child as Element, isRoot = false)
            }
        }
    }
}