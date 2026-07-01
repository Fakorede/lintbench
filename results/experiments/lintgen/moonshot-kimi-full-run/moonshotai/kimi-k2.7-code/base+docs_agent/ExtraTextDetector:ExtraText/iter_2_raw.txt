package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.Text
import java.util.EnumSet

class ExtraTextDetector : Detector(), Detector.XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        if (context.resourceFolderType == ResourceFolderType.VALUES) {
            return
        }

        visitNode(context, document)
    }

    private fun visitNode(context: XmlContext, node: Node) {
        if (node is Text) {
            val value = node.nodeValue ?: return
            if (value.isBlank()) {
                return
            }

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Extraneous text in resource file"
            )
        } else {
            val children = node.childNodes
            for (i in 0 until children.length) {
                visitNode(context, children.item(i))
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ExtraText",
            briefDescription = "Extraneous text in resource files",
            explanation = """
                Non-value resource files should only contain elements and attributes. Any XML text
                content found in the file is likely accidental (and potentially dangerous if the
                text resembles XML and the developer believes the text to be functional).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            Implementation(
                ExtraTextDetector::class.java,
                EnumSet.copyOf(Scope.RESOURCE_FILE_SCOPE).apply {
                    addAll(Scope.MANIFEST_SCOPE)
                }
            )
        )
    }
}