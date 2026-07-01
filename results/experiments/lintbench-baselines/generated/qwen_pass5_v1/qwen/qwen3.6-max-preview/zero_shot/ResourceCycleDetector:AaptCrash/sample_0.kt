package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation = "Defining a style which sets `android:id` to a dynamically generated id can " +
                    "cause many versions of `aapt`, the resource packaging tool, to crash. " +
                    "To work around this, declare the id explicitly with " +
                    "`<item type=\"id\" name=\"...\" />` instead.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_ITEM)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode
        if (parent == null || parent.nodeName != TAG_STYLE) {
            return
        }

        if (element.getAttribute(ATTR_NAME) != "android:id") {
            return
        }

        val text = element.textContent?.trim() ?: return
        if (text.startsWith("@+id/")) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Defining `android:id` with a dynamically generated id in a style can cause AAPT to crash. " +
                        "Declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead."
            )
        }
    }
}