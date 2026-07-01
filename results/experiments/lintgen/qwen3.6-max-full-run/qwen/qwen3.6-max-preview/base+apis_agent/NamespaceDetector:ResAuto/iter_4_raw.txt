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

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val name = attr.name
            if (name.startsWith("xmlns:") || name == "xmlns") {
                val value = attr.value?.trim() ?: continue
                if (value.startsWith(PREFIX) && value != ANDROID_NS && value != AUTO_NS) {
                    context.report(
                        ISSUE,
                        context.getLocation(attr),
                        "Hardcoded package in namespace; use `$AUTO_NS` instead"
                    )
                }
            }
        }
    }

    companion object {
        private const val PREFIX = "http://schemas.android.com/apk/res/"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val AUTO_NS = "http://schemas.android.com/apk/res-auto"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = "In Gradle projects, the actual package used in the final APK can vary; " +
                "for example, you can add a `.debug` package suffix in one version and not the other. " +
                "Therefore, you should **not** hardcode the application package in the resource; " +
                "instead, use the special namespace `http://schemas.android.com/apk/res-auto` " +
                "which will cause the tools to figure out the right namespace for the resource " +
                "regardless of the actual package used during the build.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}