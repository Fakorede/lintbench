package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector() {
    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; ...
            """,
            moreInfo = "https://developer.android.com/studio/write/lint#ResAuto",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val PACKAGE_URI_PREFIX = "http://schemas.android.com/apk/res/"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    }

    override fun getApplicableElements(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            if (attr.name.startsWith("xmlns")) {
                val value = attr.value
                if (value != ANDROID_URI && value != AUTO_URI &&
                    (value.startsWith(PACKAGE_URI_PREFIX) || value.startsWith(AUTO_URI))) {
                    context.report(ISSUE, attr, context.getValueLocation(attr),
                        "Should use `$AUTO_URI` instead of `$value`",
                        fix().replace().text(value).with(AUTO_URI).build())
                }
            }
        }
    }
}