/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Node
import org.w3c.dom.NodeList

class ExtraTextDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ExtraText",
            briefDescription = "Extraneous text in resource files",
            explanation = """
                Non-value resource files should only contain elements and attributes. \
                Any XML text content found in the file is likely accidental (and potentially \
                dangerous if the text resembles XML and the developer believes the text to be \
                functional).
                """,
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                ExtraTextDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        // Apply to all resource folder types except values (which legitimately contain text)
        return folderType != ResourceFolderType.VALUES
    }

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        checkNodeForExtraText(context, document)
    }

    private fun checkNodeForExtraText(context: XmlContext, node: Node) {
        val children: NodeList = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            when (child.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    val text = child.nodeValue ?: continue
                    if (text.isNotBlank()) {
                        context.report(
                            ISSUE,
                            child,
                            context.getLocation(child),
                            "Unexpected text found in layout file: \"${text.trim()}\""
                        )
                    }
                }
                Node.ELEMENT_NODE -> {
                    checkNodeForExtraText(context, child)
                }
            }
        }
    }
}