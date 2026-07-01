package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector() {
    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val RES_PREFIX = "http://schemas.android.com/apk/res/"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; \
                for example, you can add a `.debug` package suffix in one version and not the other. \
                Therefore, you should **not** hardcode the application package in the resource; \
                instead, use the special namespace `http://schemas.android.com/apk/res-auto` \
                which will cause the tools to figure out the right namespace for the resource \
                regardless of the actual package used during the build.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            if (attr.name.startsWith("xmlns:")) {
                val value = attr.value
                if (value.startsWith(RES_PREFIX) && value != ANDROID_URI && value != AUTO_URI) {
                    context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        "Should use `$AUTO_URI` instead of hardcoded package namespace",
                        fix().replace().text(value).with(AUTO_URI).build()
                    )
                }
            }
        }
    }
}