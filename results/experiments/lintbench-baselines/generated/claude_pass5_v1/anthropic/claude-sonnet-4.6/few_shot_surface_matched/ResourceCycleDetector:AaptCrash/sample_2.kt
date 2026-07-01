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

    // Track declared id names (via <item type="id" name="..." />) across files
    private val declaredIds = mutableSetOf<String>()

    // Track (context, attr) pairs where android:id is set to @+id/... inside a <style>
    // We collect them and then after the root project check we report any that aren't declared
    private data class PendingReport(val context: XmlContext, val attr: Attr, val idName: String)

    private val pendingReports = mutableListOf<PendingReport>()

    override fun beforeCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        declaredIds.clear()
        pendingReports.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ITEM)
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_NAME)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Collect explicitly declared ids: <item type="id" name="..." />
        val type = element.getAttribute(ATTR_TYPE)
        if (type == "id") {
            val name = element.getAttribute(ATTR_NAME)
            if (name.isNotEmpty()) {
                declaredIds.add(name)
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // We only care about android:id attributes set inside <style> items
        val namespaceURI = attribute.namespaceURI ?: return
        if (namespaceURI != ANDROID_URI) return
        if (attribute.localName != "id") return

        // The attribute must be on a <item> element whose parent is a <style>
        val itemElement = attribute.ownerElement ?: return
        if (itemElement.tagName != TAG_ITEM) return
        val parent = itemElement.parentNode as? Element ?: return
        if (parent.tagName != TAG_STYLE) return

        // Check if the value is a dynamically generated id (@+id/...)
        val value = attribute.value ?: return
        if (!value.startsWith("@+id/")) return

        val idName = value.removePrefix("@+id/")
        if (idName.isEmpty()) return

        pendingReports.add(PendingReport(context, attribute, idName))
    }

    override fun afterCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        for (pending in pendingReports) {
            if (!declaredIds.contains(pending.idName)) {
                val attr = pending.attr
                pending.context.report(
                    ISSUE,
                    attr,
                    pending.context.getValueLocation(attr),
                    "This construct can potentially crash `aapt` during a build. " +
                        "Change `@+id/${pending.idName}` to `@id/${pending.idName}` and declare " +
                        "the id explicitly using `<item type=\"id\" name=\"${pending.idName}\" />` " +
                        "instead."
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