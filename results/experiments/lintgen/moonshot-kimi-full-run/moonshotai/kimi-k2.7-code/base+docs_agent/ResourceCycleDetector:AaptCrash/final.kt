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

    override fun getApplicableElements(): Collection<String>? =
        listOf(SdkConstants.TAG_ITEM)

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        if (parent.tagName != SdkConstants.TAG_STYLE) {
            return
        }

        val name = element.getAttribute(SdkConstants.ATTR_NAME)
        if (name != SdkConstants.ANDROID_NS_NAME + ":" + SdkConstants.ATTR_ID) {
            return
        }

        val value = element.textContent.trim()
        if (value.startsWith("@+id/")) {
            context.report(
                AAPT_CRASH,
                element,
                context.getLocation(element),
                "Defining a style which sets `android:id` to a dynamically generated id can " +
                    "cause many versions of `aapt`, the resource packaging tool, to crash. To work " +
                    "around this, declare the id explicitly with `<item type=\\\"id\\\" " +
                    "name=\\\"...\\\" />` instead."
            )
        }
    }

    companion object {
        @JvmField
        val AAPT_CRASH: Issue = Issue.create(
            "AaptCrash",
            "Potential AAPT crash",
            "Defining a style which sets `android:id` to a dynamically generated id can " +
                "cause many versions of `aapt`, the resource packaging tool, to crash. To work " +
                "around this, declare the id explicitly with `<item type=\\\"id\\\" " +
                "name=\\\"...\\\" />` instead.",
            Category.CORRECTNESS,
            9,
            Severity.ERROR,
            Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}