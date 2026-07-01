package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document

class NamespaceDetector : ResourceXmlDetector() {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val attributes = root.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val value = attr.nodeValue ?: continue
            if (attr.nodeName.startsWith("xmlns:") &&
                value.startsWith("http://schemas.android.com/apk/res/") &&
                value != "http://schemas.android.com/apk/res/android") {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "Hardcoded package in namespace; use `http://schemas.android.com/apk/res-auto` instead"
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
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
            severity = Severity.FATAL,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}