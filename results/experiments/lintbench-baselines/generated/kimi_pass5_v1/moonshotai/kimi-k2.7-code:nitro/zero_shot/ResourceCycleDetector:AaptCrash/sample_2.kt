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

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean =
        folderType == com.android.resources.ResourceFolderType.VALUES

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_ITEM)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != SdkConstants.TAG_ITEM) {
            return
        }

        val parent = element.parentNode as? Element ?: return
        if (parent.tagName != SdkConstants.TAG_STYLE) {
            return
        }

        val name = element.getAttributeNS(null, SdkConstants.ATTR_NAME)
        if (name != "android:id") {
            return
        }

        val value = element.textContent?.trim() ?: return
        if (value.startsWith("@+id/")) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Potential AAPT crash: defining a style item that sets android:id to a dynamically generated id can cause aapt to crash. Declare the id explicitly with <item type=\"id\" name=\"...\" /> instead."
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation = """
                Defining a style which sets <code>android:id</code> to a dynamically generated id \
                can cause many versions of <code>aapt</code>, the resource packaging tool, to crash. \
                To work around this, declare the id explicitly with \
                <code>&lt;item type="id" name="..." /&gt;</code> instead.
            """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}