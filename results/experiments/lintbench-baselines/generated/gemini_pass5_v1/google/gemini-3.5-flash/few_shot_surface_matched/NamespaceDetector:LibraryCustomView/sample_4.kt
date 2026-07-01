package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.URI_PREFIX
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        if (!context.project.isLibrary) {
            return
        }
        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attribute = attributes.item(i) as Attr
            val name = attribute.nodeName
            if (name.startsWith("xmlns:")) {
                val value = attribute.nodeValue
                if (value.startsWith(URI_PREFIX) && value != ANDROID_URI && value != AUTO_URI) {
                    context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "In libraries, should use `$AUTO_URI` instead of `$value` to avoid conflicts"
                    )
                }
            }
        }
        var child = element.firstChild
        while (child != null) {
            if (child is Element) {
                checkElement(context, child)
            }
            child = child.nextSibling
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Using Custom Server Namespace in Library Project",
            explanation = """
                When using a custom view with custom attributes in a library project, the layout \
                must use the special namespace $AUTO_URI instead of a URI which includes the \
                library project's own package. This will be used to automatically adjust the \
                namespace of the attributes when the library resources are merged into the \
                application project.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}