package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_ITEM)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.parentNode?.nodeName != SdkConstants.TAG_STYLE) return

        val nameAttr = element.getAttributeNode(SdkConstants.ATTR_NAME) ?: return
        if (nameAttr.value != "android:id") return

        val text = element.textContent?.trim() ?: return
        if (text.startsWith("@+id/")) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Potential AAPT crash: defining a style that sets `android:id` to a dynamically generated id can cause `aapt` to crash. Declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation = """
                Defining a style which sets `android:id` to a dynamically generated id can
                cause many versions of `aapt`, the resource packaging tool, to crash.
                To work around this, declare the id explicitly with
                `<item type="id" name="..." />` instead.
            """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}