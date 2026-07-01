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

    /**
     * Map from style name to the XmlContext + Element pairs where
     * android:id is set to a dynamically generated id (@+id/...).
     */
    private data class PendingIssue(val context: XmlContext, val attr: Attr)

    private val pendingIssues = mutableListOf<PendingIssue>()

    // Set of explicitly declared id names (via <item type="id" name="..."/>)
    private val declaredIds = mutableSetOf<String>()

    // Tracks whether we are currently inside a <style> element
    private var inStyle = false

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun beforeCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        pendingIssues.clear()
        declaredIds.clear()
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_STYLE, TAG_ITEM)
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf("name")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName ?: return
        if (tagName == TAG_STYLE) {
            inStyle = true
        } else if (tagName == TAG_ITEM) {
            // Check if this is a top-level <item type="id" name="..."/> declaration
            val type = element.getAttribute(ATTR_TYPE)
            if (type == "id") {
                val name = element.getAttribute(ATTR_NAME)
                if (name.isNotEmpty()) {
                    declaredIds.add(name)
                }
            }

            // Check if this <item> is inside a style and sets android:id to @+id/...
            if (inStyle) {
                val nameAttr = element.getAttributeNS(ANDROID_URI, "id")
                if (nameAttr != null && nameAttr.startsWith("@+id/")) {
                    val idName = nameAttr.substring("@+id/".length)
                    if (idName.isNotEmpty()) {
                        // We need the attribute node for location; try to get it
                        val attrNode = element.getAttributeNodeNS(ANDROID_URI, "id")
                        if (attrNode != null) {
                            pendingIssues.add(PendingIssue(context, attrNode))
                        }
                    }
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // We use visitElement for the main logic; this handles the android:id attribute
        // on <item> elements inside <style> blocks
        val element = attribute.ownerElement ?: return
        val tagName = element.tagName ?: return

        if (tagName != TAG_ITEM) return

        // Check that the attribute is android:name on a top-level item type="id"
        // (handled in visitElement) or android:id inside a style item
        val localName = attribute.localName ?: attribute.name ?: return
        val ns = attribute.namespaceURI

        // Handle the case where the item inside a style sets android:id = @+id/foo
        if (localName == "name" && (ns == null || ns.isEmpty())) {
            // This is the name attribute on a top-level item
            val type = element.getAttribute(ATTR_TYPE)
            if (type == "id") {
                val name = attribute.value
                if (name.isNotEmpty()) {
                    declaredIds.add(name)
                }
            }
        }
    }

    override fun afterCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        for (pending in pendingIssues) {
            val attr = pending.attr
            val value = attr.value ?: continue
            if (!value.startsWith("@+id/")) continue
            val idName = value.substring("@+id/".length)
            if (idName !in declaredIds) {
                val xmlContext = pending.context
                xmlContext.report(
                    ISSUE,
                    attr,
                    xmlContext.getValueLocation(attr),
                    "Defining a style which sets `android:id` to a dynamically generated " +
                        "id can cause many versions of `aapt` to crash. To work around " +
                        "this, declare the id explicitly with " +
                        "`<item type=\"id\" name=\"$idName\" />` instead."
                )
            }
        }
        pendingIssues.clear()
        declaredIds.clear()
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AaptCrash",
            briefDescription = "Potential AAPT crash",
            explanation =
                "Defining a style which sets `android:id` to a dynamically generated id " +
                    "can cause many versions of `aapt`, the resource packaging tool, to crash. " +
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