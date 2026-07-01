package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val RES_PREFIX = "http://schemas.android.com/apk/res/"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val XMLNS_URI = "http://www.w3.org/2000/xmlns/"

        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = "When using a custom view with custom attributes in a library project, the layout must use the special namespace $AUTO_URI instead of a URI which includes the library project's own package. This will be used to automatically adjust the namespace of the attributes when the library resources are merged into the application project.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (!context.project.isLibrary) return

        val namespaceUri = attribute.namespaceURI
        val isXmlns = namespaceUri == XMLNS_URI || attribute.name.startsWith("xmlns:")
        if (!isXmlns) return

        val value = attribute.value
        if (value.startsWith(RES_PREFIX) && value != AUTO_URI && value != ANDROID_URI) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "Use `$AUTO_URI` instead of `$value` in library projects",
                fix()
                    .replace()
                    .text(value)
                    .with(AUTO_URI)
                    .build()
            )
        }
    }
}