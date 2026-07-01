package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val folderType = context.resourceFolderType
        if (folderType != ResourceFolderType.LAYOUT && 
            folderType != ResourceFolderType.MENU && 
            folderType != ResourceFolderType.XML) {
            return
        }

        if (!context.project.isLibrary) {
            return
        }

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val name = attr.nodeName
            if (name.startsWith("xmlns:")) {
                val uri = attr.value
                if (uri.startsWith(URI_PREFIX) &&
                    uri != ANDROID_URI &&
                    uri != AUTO_URI) {
                    
                    val fix = fix()
                        .name("Replace with $AUTO_URI")
                        .replace()
                        .text(uri)
                        .with(AUTO_URI)
                        .autoFix()
                        .build()

                    context.report(
                        ISSUE,
                        attr,
                        context.getValueLocation(attr),
                        "When using a custom namespace attribute in a library project, " +
                                "use the namespace \"$AUTO_URI\" instead.",
                        fix
                    )
                }
            }
        }
    }

    companion object {
        private const val URI_PREFIX = "http://schemas.android.com/apk/res/"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"

        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "References to custom resources in library projects should use the res-auto namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, the \
                layout must use the special namespace http://schemas.android.com/apk/res-auto \
                instead of a URI which includes the library project's own package. This will \
                be used to automatically adjust the namespace of the attributes when the \
                library resources are merged into the application project.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.FATAL,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}