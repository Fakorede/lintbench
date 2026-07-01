package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.URI_PREFIX
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node

class NamespaceDetector : ResourceXmlDetector() {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: Element) {
        val attributes: NamedNodeMap = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val value = attr.value ?: continue
            if (value.startsWith(URI_PREFIX) &&
                value != ANDROID_URI &&
                value != AUTO_URI
            ) {
                val location = context.getValueLocation(attr)
                context.report(
                    ISSUE,
                    attr,
                    location,
                    "In Gradle projects, always use `$AUTO_URI` for custom attributes; " +
                        "replace `$value` with `$AUTO_URI`"
                )
            }
        }

        var child: Node? = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkElement(context, child as Element)
            }
            child = child.nextSibling
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation =
                "In Gradle projects, the actual package used in the final APK can vary; " +
                    "for example, you can add a `.debug` package suffix in one version and " +
                    "not the other. Therefore, you should **not** hardcode the application " +
                    "package in the resource; instead, use the special namespace " +
                    "`http://schemas.android.com/apk/res-auto` which will cause the tools " +
                    "to figure out the right namespace for the resource regardless of the " +
                    "actual package used during the build.",
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