package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        if (!context.project.isLibrary) {
            return
        }
        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: org.w3c.dom.Element) {
        val attributes = element.attributes
        if (attributes != null) {
            for (i in 0 until attributes.length) {
                val attribute = attributes.item(i) as? org.w3c.dom.Attr ?: continue
                val name = attribute.nodeName
                if (name != null && name.startsWith("xmlns:")) {
                    val uri = attribute.nodeValue
                    if (uri != null && 
                        uri.startsWith("http://schemas.android.com/apk/res/") && 
                        uri != "http://schemas.android.com/apk/res/android") {
                        context.report(
                            ISSUE,
                            attribute,
                            context.getValueLocation(attribute),
                            "When using a custom view with custom attributes in a library project, " +
                                    "the layout must use the special namespace http://schemas.android.com/apk/res-auto " +
                                    "instead of a URI which includes the library project's own package"
                        )
                    }
                }
            }
        }
        var child = element.firstChild
        while (child != null) {
            if (child is org.w3c.dom.Element) {
                checkElement(context, child)
            }
            child = child.nextSibling
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Using custom-namespace attributes in a library project",
            explanation = "When using a custom view with custom attributes in a library project, " +
                    "the layout must use the special namespace http://schemas.android.com/apk/res-auto " +
                    "instead of a URI which includes the library project's own package. This will be " +
                    "used to automatically adjust the namespace of the attributes when the library " +
                    "resources are merged into the application project.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}