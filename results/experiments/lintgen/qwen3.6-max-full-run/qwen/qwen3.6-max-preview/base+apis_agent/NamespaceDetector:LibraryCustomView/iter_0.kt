package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.project.isLibrary) return

        val packageName = context.project.packageName ?: return
        val explicitUri = "http://schemas.android.com/apk/res/$packageName"

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            if (attr.value == explicitUri && attr.name.startsWith("xmlns:")) {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Use `$AUTO_URI` instead of explicit package namespace in library projects"
                )
            }
        }
    }

    companion object {
        const val AUTO_URI = "http://schemas.android.com/apk/res-auto"

        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = "When using a custom view with custom attributes in a library project, the layout must use the special namespace `$AUTO_URI` instead of a URI which includes the library project's own package. This will be used to automatically adjust the namespace of the attributes when the library resources are merged into the application project.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}