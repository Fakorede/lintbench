package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ResourceCycleDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            "AaptCrash",
            "Potential AAPT crash",
            "Defining a style which sets `android:id` to a dynamically generated id can cause many versions of `aapt`, the resource packaging tool, to crash. To work around this, declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead.",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_ITEM)

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME)
        if (name != "android:id") return

        val parent = element.parentNode
        if (parent == null || parent.nodeName != TAG_STYLE) return

        val value = element.textContent?.trim().orEmpty()
        if (value.startsWith("@+id/")) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Defining a style which sets android:id to a dynamically generated id can cause aapt to crash. Declare the id explicitly with <item type=\"id\" name=\"...\" /> instead."
            )
        }
    }
}