package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : Detector(), XmlScanner {

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val RES_PREFIX = "http://schemas.android.com/apk/res/"

        val ISSUE = Issue.create(
            "LibraryCustomView",
            "Custom views in libraries should use res-auto-namespace",
            "When using a custom view with custom attributes in a library project, the " +
            "layout must use the special namespace `$AUTO_URI` instead of a URI which includes " +
            "the library project's own package. This will be used to automatically adjust " +
            "the namespace of the attributes when the library resources are merged into " +
            "the application project.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.project.isLibrary) return

        val packageName = context.project.getPackage() ?: return
        val badNamespace = "$RES_PREFIX$packageName"

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            if (attr.namespaceURI == badNamespace) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "Use `$AUTO_URI` instead of `$badNamespace`"
                )
            }
        }
    }
}