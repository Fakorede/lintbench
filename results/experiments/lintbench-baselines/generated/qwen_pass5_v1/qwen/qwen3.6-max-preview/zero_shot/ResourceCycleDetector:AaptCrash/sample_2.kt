package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

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

    override fun getIssues(): List<Issue> = listOf(ISSUE)

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.TAG_ITEM)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode
        if (parent == null || parent.nodeName != SdkConstants.TAG_STYLE) {
            return
        }

        val nameAttr = element.getAttribute(SdkConstants.ATTR_NAME)
        if (nameAttr != "android:id") {
            return
        }

        val text = element.textContent?.trim()
        if (text != null && text.startsWith("@+id/")) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Defining `android:id` with a dynamic resource reference (`@+id/...`) in a style can crash AAPT. Declare the ID explicitly in a values file instead."
            )
        }
    }
}