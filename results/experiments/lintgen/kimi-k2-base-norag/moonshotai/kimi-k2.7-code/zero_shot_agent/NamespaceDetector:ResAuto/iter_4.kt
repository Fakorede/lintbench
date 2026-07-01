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
                In Gradle projects, the actual package used in the final APK can vary; \
                for example, you can add a `.debug` package suffix in one version and not \
                the other. Therefore, you should not hardcode the application package in \
                the resource; instead, use the special namespace \
                `http://schemas.android.com/apk/res-auto` which will cause the tools to \
                figure out the right namespace for the resource regardless of the actual \
                package used during the build.
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

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val PACKAGE_URI_PREFIX = "http://schemas.android.com/apk/res/"
    }

    override fun getApplicableElements(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val name = attr.name
            if (name != "xmlns" && !name.startsWith("xmlns:")) {
                continue
            }
            val value = attr.value
            if (value.startsWith(ANDROID_URI)) {
                continue
            }
            if (value.startsWith(PACKAGE_URI_PREFIX) ||
                (value.length > AUTO_URI.length && value.startsWith(AUTO_URI))) {
                context.report(
                    ISSUE,
                    attr,
                    context.getValueLocation(attr),
                    "Should use `$AUTO_URI` instead of `$value`",
                    fix().replace().text(value).with(AUTO_URI).build()
                )
            }
        }
    }
}