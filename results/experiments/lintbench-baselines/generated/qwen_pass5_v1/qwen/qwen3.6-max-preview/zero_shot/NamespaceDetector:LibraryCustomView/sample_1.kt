package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class NamespaceDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.project.isLibrary) {
            return
        }

        val attributes = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.nodeName
            if (name.startsWith("xmlns:") || name == "xmlns") {
                val uri = attr.nodeValue ?: continue
                if (uri.startsWith(PREFIX_RESOURCE_REF) &&
                    uri != AUTO_URI &&
                    uri != ANDROID_URI) {
                    context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        "Custom views in libraries should use res-auto-namespace",
                        fix().replace()
                            .name("Replace with res-auto")
                            .text(uri)
                            .with(AUTO_URI)
                            .build()
                    )
                }
            }
        }
    }

    companion object {
        private const val PREFIX_RESOURCE_REF = "http://schemas.android.com/apk/res/"
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        @JvmField
        val ISSUE: Issue = Issue.create(
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
}