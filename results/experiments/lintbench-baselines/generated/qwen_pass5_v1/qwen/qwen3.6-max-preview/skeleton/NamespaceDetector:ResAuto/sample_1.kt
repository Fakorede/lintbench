package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        private const val RES_PREFIX = "http://schemas.android.com/apk/res/"
        private const val RES_AUTO = "http://schemas.android.com/apk/res-auto"
        private const val RES_ANDROID = "http://schemas.android.com/apk/res/android"

        @JvmField
        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = "In Gradle projects, the actual package used in the final APK can vary; for example, you can add a `.debug` package suffix in one version and not the other. Therefore, you should **not** hardcode the application package in the resource; instead, use the special namespace `http://schemas.android.com/apk/res-auto` which will cause the tools to figure out the right namespace for the resource regardless of the actual package used during the build.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val elements = document.getElementsByTagName("*")
        for (i in 0 until elements.length) {
            val element = elements.item(i) as? Element ?: continue
            checkElement(context, element)
        }
    }

    private fun checkElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val value = attr.nodeValue ?: continue
            if (value.startsWith(RES_PREFIX) && value != RES_AUTO && value != RES_ANDROID) {
                val fix = fix().replace().text(value).with(RES_AUTO).build()
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Hardcoded package in namespace; use res-auto instead",
                    fix
                )
            }
        }
    }
}