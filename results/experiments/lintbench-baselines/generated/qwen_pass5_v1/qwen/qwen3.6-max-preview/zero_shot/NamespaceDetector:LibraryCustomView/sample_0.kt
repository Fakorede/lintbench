package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector() {
    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, the layout must use the special namespace ${SdkConstants.AUTO_URI} instead of a URI which includes the library project's own package. This will be used to automatically adjust the namespace of the attributes when the library resources are merged into the application project.
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

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.project.isLibrary) return

        val packageName = context.project.packageName ?: return
        val packageSpecificUri = "${SdkConstants.ANDROID_RES_URI_PREFIX}$packageName"

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            if (attr.nodeName.startsWith("xmlns:")) {
                if (attr.nodeValue == packageSpecificUri) {
                    context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        "Use `${SdkConstants.AUTO_URI}` instead of package-specific namespace in library projects"
                    )
                }
            }
        }
    }
}