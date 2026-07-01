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
        @JvmField
        val RES_AUTO = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = "In Gradle projects, the actual package used in the final APK can vary; for  example,you can add a `.debug` package suffix in one version and not the other.  Therefore, you should **not** hardcode the application package in the resource;  instead, use the special namespace `http://schemas.android.com/apk/res-auto`  which will cause the tools to figure out the right namespace for the resource  regardless of the actual package used during the build.",
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val RES_AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val RES_PREFIX = "http://schemas.android.com/apk/res/"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }

    override fun getApplicableElements(): Collection<String>? = ALL_ELEMENTS

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val name = attr.name
            if (name.startsWith("xmlns:")) {
                val value = attr.value
                if (value.startsWith(RES_PREFIX) && value != ANDROID_NS && value != RES_AUTO_URI) {
                    context.report(
                        RES_AUTO,
                        context.getLocation(attr),
                        "Hardcoded package in namespace; use `$RES_AUTO_URI` instead",
                        fix()
                            .replace()
                            .name("Replace with res-auto")
                            .text(value)
                            .with(RES_AUTO_URI)
                            .autoFix()
                            .build()
                    )
                }
            }
        }
    }
}