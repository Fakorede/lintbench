package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : Detector(), XmlScanner {

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val RES_URI_PREFIX = "http://schemas.android.com/apk/res/"

        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = "When using a custom view with custom attributes in a library project, the layout must use the special namespace `http://schemas.android.com/apk/res-auto` instead of a URI which includes the library project's own package. This will be used to automatically adjust the namespace of the attributes when the library resources are merged into the application project.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.project.isLibrary) return

        val attributes = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val ns = attr.namespaceURI ?: continue

            if (ns.startsWith(RES_URI_PREFIX) && ns != AUTO_URI) {
                val fix = fix()
                    .replace()
                    .text(ns)
                    .with(AUTO_URI)
                    .all()
                    .build()

                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Custom views in libraries should use `res-auto` namespace instead of `$ns`",
                    fix
                )
            }
        }
    }
}