package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String>? =
        XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.project.isAndroidLibrary) {
            return
        }

        if ('.' !in element.tagName) {
            return
        }

        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i) as? Attr ?: continue
            val prefix = attr.prefix
            if (prefix.isNullOrEmpty()) {
                continue
            }

            val namespaceUri = attr.namespaceURI ?: continue
            if (namespaceUri == AUTO_URI ||
                namespaceUri == ANDROID_URI ||
                namespaceUri == TOOLS_URI
            ) {
                continue
            }

            context.report(
                ISSUE,
                attr,
                context.getLocation(attr),
                "Custom views in libraries must use `$AUTO_URI` " +
                    "for custom attributes, not `$namespaceUri`."
            )
        }
    }

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val TOOLS_URI = "http://schemas.android.com/tools"

        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, the
                layout must use the special `$AUTO_URI` namespace instead of a URI which
                includes the library project's own package. This allows the build system
                to automatically adjust the namespace of the attributes when the library
                resources are merged into the application project.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}