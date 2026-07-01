package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_RES_URI_PREFIX
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val elements = document.getElementsByTagName("*")
        for (i in 0 until elements.length) {
            val element = elements.item(i) as? Element ?: continue
            val attributes = element.attributes
            for (j in 0 until attributes.length) {
                val attr = attributes.item(j) as? Attr ?: continue
                val name = attr.name
                if (name != "xmlns" && !name.startsWith("xmlns:")) continue

                val value = attr.value
                if (value.startsWith(ANDROID_RES_URI_PREFIX) &&
                    value != ANDROID_URI &&
                    value != AUTO_URI) {
                    context.report(
                        ISSUE,
                        attr,
                        context.getValueLocation(attr),
                        "Replace hardcoded package name with `res-auto`"
                    )
                }
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