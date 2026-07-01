package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

    // Track all explicitly declared IDs: name -> true
    private val declaredIds = mutableSetOf<String>()

    // Track style items that set android:id to a dynamic @+id/... reference
    // Each entry is a pair of (XmlContext, Attr) to report after scanning
    private val pendingReports = mutableListOf<Pair<XmlContext, Attr>>()

    override fun beforeCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        declaredIds.clear()
        pendingReports.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ITEM, TAG_STYLE)
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_NAME)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName ?: return
        if (tagName == TAG_ITEM) {
            // Check for <item type="id" name="..." />
            val typeAttr = element.getAttribute(ATTR_TYPE)
            if (typeAttr == "id") {
                val nameAttr = element.getAttribute(ATTR_NAME)
                if (nameAttr.isNotEmpty()) {
                    declaredIds.add(nameAttr)
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // We're looking for android:name attributes on <item> elements inside <style>
        // that set android:id to a @+id/... value
        val element = attribute.ownerElement ?: return
        if (element.tagName != TAG_ITEM) return

        val parent = element.parentNode as? Element ?: return
        if (parent.tagName != TAG_STYLE) return

        // Check if this item's android:name is "android:id"
        val nameValue = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (nameValue != "android:id") {
            // Also check plain name attribute
            val plainName = element.getAttribute(ATTR_NAME)
            if (plainName != "android:id") return
        }

        // Get the text content (the value being set)
        val value = element.textContent?.trim() ?: return
        if (value.startsWith("@+id/")) {
            // This is a dynamic id reference inside a style - potential AAPT crash
            pendingReports.add(Pair(context, attribute))
        }
    }

    override fun afterCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        for ((xmlContext, attribute) in pendingReports) {
            val element = attribute.ownerElement ?: continue
            val value = element.textContent?.trim() ?: continue
            // Extract the id name from @+id/name
            val idName = value.removePrefix("@+id/")
            if (!declaredIds.contains(idName)) {
                xmlContext.report(
                    ISSUE,
                    element,
                    xmlContext.getLocation(element),
                    "Defining a style which sets `android:id` to a dynamically generated id " +
                        "(`$value`) can cause many versions of `aapt` to crash. To work around " +
                        "this, declare the id explicitly with " +
                        "`<item type=\"id\" name=\"$idName\" />` instead."
                )
            }
        }
        pendingReports.clear()
        declaredIds.clear()
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation =
                "Defining a style which sets `android:id` to a dynamically generated id can " +
                    "cause many versions of `aapt`, the resource packaging tool, to crash. " +
                    "To work around this, declare the id explicitly with " +
                    "`<item type=\"id\" name=\"...\" />` instead.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}