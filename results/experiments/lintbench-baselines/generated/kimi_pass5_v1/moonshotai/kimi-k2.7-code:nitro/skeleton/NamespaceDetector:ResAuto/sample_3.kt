package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceFolderType
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private const val AUTO_NAMESPACE = "http://schemas.android.com/apk/res-auto"
        private const val RESOURCE_SCHEMA_PREFIX = "http://schemas.android.com/apk/res/"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = "In Gradle projects, the actual package used in the final APK can vary; " +
                "for example, you can add a `.debug` package suffix in one version and not the other. " +
                "Therefore, you should not hardcode the application package in the resource; " +
                "instead, use the special namespace `$AUTO_NAMESPACE` which will cause the tools " +
                "to figure out the right namespace for the resource regardless of the actual package " +
                "used during the build.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun visitDocument(context: XmlContext, document: Document) {
        val elements = document.getElementsByTagName("*")
        for (i in 0 until elements.length) {
            val element = elements.item(i) as? Element ?: continue
            val attributes = element.attributes
            for (j in 0 until attributes.length) {
                val attr = attributes.item(j) as? Attr ?: continue
                if (attr.name == "xmlns" || attr.name.startsWith("xmlns:")) {
                    val value = attr.value
                    if (value.startsWith(RESOURCE_SCHEMA_PREFIX) && value != ANDROID_URI) {
                        val message = "Hardcoded package namespace `$value` found; use `$AUTO_NAMESPACE` instead"
                        context.report(ISSUE, context.getLocation(attr), message)
                    }
                }
            }
        }
    }
}