package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector() {
    companion object {
        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            "LibraryCustomView",
            "Custom views in libraries should use res-auto-namespace",
            "When using a custom view with custom attributes in a library project, " +
            "the layout must use the special namespace `http://schemas.android.com/apk/res-auto` " +
            "instead of a URI which includes the library project's own package. This will be used " +
            "to automatically adjust the namespace of the attributes when the library resources " +
            "are merged into the application project.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION
        )
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.project.isLibrary) return
        if (context.resourceFolderType != ResourceFolderType.LAYOUT) return

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val namespaceUri = attr.namespaceURI ?: continue

            if (namespaceUri.startsWith(SdkConstants.URI_PREFIX) &&
                namespaceUri != SdkConstants.ANDROID_URI &&
                namespaceUri != SdkConstants.AUTO_URI) {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Use `res-auto` namespace for custom attributes in libraries"
                )
            }
        }
    }
}