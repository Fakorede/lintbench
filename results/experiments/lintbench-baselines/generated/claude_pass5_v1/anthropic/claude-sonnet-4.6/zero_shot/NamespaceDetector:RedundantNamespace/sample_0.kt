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

/**
 * Checks for redundant namespace declarations in XML files.
 *
 * Namespace declarations should only appear on the root element of an
 * Android XML document. Declarations on non-root elements are typically
 * accidental leftovers from copy/pasting XML from other files.
 */
class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

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
            implementation = IMPLEMENTATION
        )

        private const val XMLNS_PREFIX = "xmlns"
        private const val XMLNS_PREFIX_WITH_COLON = "xmlns:"
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        // Check all descendant elements (non-root elements) for namespace declarations
        checkElement(context, root, isRoot = true)
    }

    private fun checkElement(context: XmlContext, element: Element, isRoot: Boolean) {
        if (!isRoot) {
            // Check attributes for namespace declarations
            val attributes = element.attributes
            if (attributes != null) {
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as? Attr ?: continue
                    val name = attr.name ?: continue
                    if (name == XMLNS_PREFIX || name.startsWith(XMLNS_PREFIX_WITH_COLON)) {
                        val fix = fix()
                            .name("Remove redundant namespace declaration")
                            .set()
                            .attribute(name)
                            .remove()
                            .build()
                        context.report(
                            issue = REDUNDANT_NAMESPACE,
                            location = context.getLocation(attr),
                            message = "Redundant namespace declaration; already declared on the root element",
                            quickfixData = fix
                        )
                    }
                }
            }
        }

        // Recurse into child elements
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                checkElement(context, child, isRoot = false)
            }
        }
    }
}