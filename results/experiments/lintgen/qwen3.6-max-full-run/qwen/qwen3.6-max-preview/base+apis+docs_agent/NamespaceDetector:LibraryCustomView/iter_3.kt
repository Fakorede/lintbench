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
import org.w3c.dom.Document
import org.w3c.dom.Element

class NamespaceDetector : Detector(), XmlScanner {

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val RES_URI_PREFIX = "http://schemas.android.com/apk/res/"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val name = attr.nodeName ?: continue
            if (name.startsWith("xmlns")) {
                val value = attr.value ?: continue
                if (value.startsWith(RES_URI_PREFIX) && value != AUTO_URI && value != ANDROID_URI) {
                    val fix = fix()
                        .replace()
                        .text(value)
                        .with(AUTO_URI)
                        .build()

                    context.report(
                        ISSUE,
                        context.getLocation(attr),
                        "Custom views in libraries should use `res-auto` namespace instead of `$value`",
                        fix
                    )
                }
            }
        }
    }
}