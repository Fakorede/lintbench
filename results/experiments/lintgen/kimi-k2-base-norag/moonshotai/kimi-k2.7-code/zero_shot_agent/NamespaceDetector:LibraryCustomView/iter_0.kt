package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String> = Detector.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.project.isLibrary) {
            return
        }

        if ('.' !in element.tagName) {
            return
        }

        val projectPackage = context.project.`package`
        val packageUri = SdkConstants.NS_RESOURCES + projectPackage

        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i) as? Attr ?: continue
            val uri = attr.namespaceURI ?: continue

            if (uri == packageUri) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "When using a custom view with custom attributes in a library project, " +
                        "the layout must use the special namespace ${SdkConstants.AUTO_URI} " +
                        "instead of a URI which includes the library project's own package"
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, the layout
                must use the special namespace ${SdkConstants.AUTO_URI} instead of a URI which
                includes the library project's own package. This will be used to automatically
                adjust the namespace of the attributes when the library resources are merged into
                the application project.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}