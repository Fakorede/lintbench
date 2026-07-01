package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.SdkConstants.VALUE_ID
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

    // Track whether we are currently inside a <style> element
    private var inStyle = false

    // Collect locations of attributes that are potentially crashing
    private val crashLocations = mutableListOf<Pair<XmlContext, Attr>>()

    // Track declared ids (via <item type="id" name="..." />) across all resource files
    private val declaredIds = mutableSetOf<String>()

    // Track referenced ids from android:id inside styles
    private val referencedIds = mutableListOf<Pair<XmlContext, Attr>>()

    override fun beforeCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        crashLocations.clear()
        declaredIds.clear()
        referencedIds.clear()
        inStyle = false
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_STYLE, TAG_ITEM)
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf("id")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_STYLE -> {
                inStyle = true
            }
            TAG_ITEM -> {
                // Check if this is a top-level <item type="id" name="..." /> declaration
                val typeAttr = element.getAttribute(ATTR_TYPE)
                if (typeAttr == VALUE_ID) {
                    val nameAttr = element.getAttribute(ATTR_NAME)
                    if (nameAttr.isNotEmpty()) {
                        declaredIds.add(nameAttr)
                    }
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // We only care about android:id attributes
        if (attribute.namespaceURI != ANDROID_URI) return
        if (attribute.localName != "id") return

        // Check if we're inside a style element
        val parent = attribute.ownerElement ?: return
        var node: org.w3c.dom.Node? = parent.parentNode
        var insideStyle = false
        while (node != null) {
            if (node is Element && node.tagName == TAG_STYLE) {
                insideStyle = true
                break
            }
            node = node.parentNode
        }

        if (!insideStyle) return

        val value = attribute.value
        // Check if the value is a dynamically generated id (e.g., @+id/...)
        if (value.startsWith("@+id/")) {
            val idName = value.substring("@+id/".length)
            referencedIds.add(Pair(context, attribute))
        }
    }

    override fun afterCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        // Report any android:id references inside styles that use @+id/ (dynamic id generation)
        for ((xmlContext, attribute) in referencedIds) {
            val value = attribute.value
            val idName = if (value.startsWith("@+id/")) value.substring("@+id/".length) else continue

            // If the id is not explicitly declared, report the crash risk
            if (!declaredIds.contains(idName)) {
                xmlContext.report(
                    ISSUE,
                    attribute,
                    xmlContext.getValueLocation(attribute),
                    "This construct can potentially crash `aapt` during a build. " +
                        "Change `@+id/$idName` to `@id/$idName` and declare the id explicitly " +
                        "using `<item type=\"id\" name=\"$idName\"/>` instead."
                )
            }
        }
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