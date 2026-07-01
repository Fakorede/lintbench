package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        val root = document.documentElement ?: return
        val attributes = root.attributes ?: return
        for (i in 0 until attributes.length) {
            val attribute = attributes.item(i) as? org.w3c.dom.Attr ?: continue
            val value = attribute.nodeValue ?: continue
            if (value.startsWith("http://schemas.android.com/apk/res/") && value != "http://schemas.android.com/apk/res/android") {
                val location = context.getValueLocation(attribute)
                val fix = fix()
                    .replace()
                    .text(value)
                    .with("http://schemas.android.com/apk/res-auto")
                    .build()

                context.report(
                    ISSUE,
                    attribute,
                    location,
                    "In Gradle projects, packaging is not fixed; use `http://schemas.android.com/apk/res-auto` instead of hardcoding the package name",
                    fix
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = "In Gradle projects, the actual package used in the final APK can vary; for example, you can add a `.debug` package suffix in one version and not the other. Therefore, you should not hardcode the application package in the resource; instead, use the special namespace `http://schemas.android.com/apk/res-auto` which will cause the tools to figure out the right namespace for the resource regardless of the actual package used during the build.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.FATAL,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}