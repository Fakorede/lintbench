package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

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
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
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

    override fun getApplicableElements(): Collection<String> {
        return listOf(SdkConstants.TAG_STYLE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val items = element.getElementsByTagName(SdkConstants.TAG_ITEM)
        for (i in 0 until items.length) {
            val item = items.item(i) as? Element ?: continue
            val name = item.getAttribute(SdkConstants.ATTR_NAME)
                .ifEmpty { item.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME) }
            if (name != "${SdkConstants.ANDROID_NS_NAME}:${SdkConstants.ATTR_ID}") {
                continue
            }
            val value = item.textContent?.trim() ?: continue
            if (value.startsWith("@+id/")) {
                context.report(
                    ISSUE,
                    item,
                    context.getLocation(item),
                    "Potential AAPT crash: declare id resources explicitly instead"
                )
            }
        }
    }
}