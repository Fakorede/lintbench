package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Element

private const val PACKAGE_NS_PREFIX = "http://schemas.android.com/apk/res/"

class NamespaceDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String>? =
        XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.project.isLibrary) {
            return
        }

        if (element.tagName.indexOf('.') == -1) {
            return
        }

        val libraryPackage = context.project.getPackage()?.takeIf { it.isNotEmpty() }
            ?: return

        val badAttributes = (0 until element.attributes.length)
            .map { element.attributes.item(it) as Attr }
            .filter { attr ->
                val uri = attr.namespaceURI ?: return@filter false
                !isReservedUri(uri) && !isAutoUri(uri) && !isXmlns(attr) &&
                    uri == "$PACKAGE_NS_PREFIX$libraryPackage"
            }

        if (badAttributes.isEmpty()) {
            return
        }

        val message = buildString {
            append("When using a custom view with custom attributes in a library project, ")
            append("the layout must use the special ")
            append(SdkConstants.AUTO_URI)
            append(" namespace instead of ")
            append(PACKAGE_NS_PREFIX)
            append(libraryPackage)
            append(" for attributes: ")
            append(badAttributes.joinToString(", ") { it.name })
            append('.')
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            message
        )
    }

    private fun isXmlns(attr: Attr): Boolean =
        attr.name == SdkConstants.XMLNS ||
            attr.name.startsWith("${SdkConstants.XMLNS}:")

    private fun isReservedUri(uri: String): Boolean =
        uri == SdkConstants.ANDROID_URI || uri == SdkConstants.TOOLS_URI

    private fun isAutoUri(uri: String): Boolean =
        uri == SdkConstants.AUTO_URI

    companion object {
        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, the layout must use the special namespace `http://schemas.android.com/apk/res-auto` instead of a URI which includes the library project's own package. This will be used to automatically adjust the namespace of the attributes when the library resources are merged into the application project.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )
    }
}