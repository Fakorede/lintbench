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

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        if (!context.project.isLibrary) {
            return
        }

        val root = document.documentElement ?: return
        val attributes = root.attributes
        for (i in 0 until attributes.length) {
            val attribute = attributes.item(i) as? Attr ?: continue
            val name = attribute.nodeName
            if (name != null && name.startsWith("xmlns:")) {
                val value = attribute.nodeValue
                if (value != null && value.startsWith(URI_PREFIX) && value != AND_URI) {
                    context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "In library projects, use the XML namespace class `$AUTO_URI` instead of `$value` to prevent problems with merging resources in applications"
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Using Custom Library Namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, \
                the layout must use the special namespace http://schemas.android.com/apk/res-auto \
                instead of a URI which includes the library project's own package. This will be used \
                to automatically adjust the namespace of the attributes when the library resources \
                are merged into the application project.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}