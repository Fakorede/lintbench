package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class ResourceCycleDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation = """
                Defining a style which sets `android:id` to a dynamically generated id can \
                cause many versions of `aapt`, the resource packaging tool, to crash. \
                To work around this, declare the id explicitly with \
                `<item type="id" name="..." />` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("item")

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.parentNode?.nodeName != "style") return

        val name = element.getAttribute("name")
        if (name != "android:id" && name != "id") return

        val value = element.textContent?.trim() ?: return
        if (value.startsWith("@+id/")) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Defining `android:id` in a style with a dynamically generated ID (`@+id/...`) can cause AAPT to crash. " +
                    "Declare the ID explicitly with `<item type=\"id\" name=\"...\" />` instead."
            )
        }
    }
}