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
     * Set of explicitly declared IDs found in resource files (via `<item type="id" name="..."/>`).
     */
    private val declaredIds = mutableSetOf<String>()

    /**
     * List of (XmlContext, Attr, idValue) for style items that set android:id to a @+id/... ref.
     * We collect these during the scan and report after we've seen all declared IDs.
     */
    private data class PendingReport(
        val context: XmlContext,
        val attribute: Attr,
        val idValue: String
    )

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
        // Collect explicitly declared IDs: <item type="id" name="foo"/>
        val typeAttr = element.getAttribute(ATTR_TYPE)
        if (typeAttr == "id") {
            val nameAttr = element.getAttribute(ATTR_NAME)
            if (nameAttr.isNotEmpty()) {
                declaredIds.add(nameAttr)
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // We're looking for android:name on <item> elements inside <style> that set android:id
        // Actually, we want to intercept style items like:
        //   <style name="...">
        //     <item name="android:id">@+id/foo</item>
        //   </style>
        // The attribute here is "name" on any element. We filter to <item> inside <style>
        // where name="android:id" and the text content is @+id/...

        val element = attribute.ownerElement ?: return

        // We only care about <item> elements
        if (element.tagName != TAG_ITEM) return

        // The parent must be a <style>
        val parent = element.parentNode as? Element ?: return
        if (parent.tagName != TAG_STYLE) return

        // The attribute must be "name" (without namespace, i.e. the plain name attribute)
        if (attribute.namespaceURI != null) return
        if (attribute.localName != ATTR_NAME && attribute.name != ATTR_NAME) return

        // The value of name must be "android:id"
        val nameValue = attribute.value
        if (nameValue != "android:id") return

        // Get the text content of this <item> element
        val textContent = element.textContent?.trim() ?: return

        // Check if it's a dynamically generated id reference (@+id/...)
        if (textContent.startsWith("@+id/")) {
            val idName = textContent.removePrefix("@+id/")
            pendingReports.add(PendingReport(context, attribute, idName))
        }
    }

    override fun afterCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        for (pending in pendingReports) {
            val idName = pending.idValue
            // If the id is NOT explicitly declared, report the crash risk
            if (!declaredIds.contains(idName)) {
                pending.context.report(
                    ISSUE,
                    pending.attribute,
                    pending.context.getLocation(pending.attribute),
                    "Defining a style which sets `android:id` to a dynamically generated id " +
                        "(`@+id/$idName`) can cause many versions of `aapt` to crash. " +
                        "To work around this, declare the id explicitly with " +
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