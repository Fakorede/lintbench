package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed according to the spec.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != "restrictions") {
            return
        }
        validateRestrictions(context, root)
    }

    private fun validateRestrictions(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (childElement.tagName != "restriction") {
                    context.report(
                        ISSUE,
                        childElement,
                        context.getNameLocation(childElement),
                        "Only `<restriction>` elements are allowed inside `<restrictions>`"
                    )
                } else {
                    validateRestrictionElement(context, childElement)
                }
            }
        }
    }

    private fun validateRestrictionElement(context: XmlContext, element: Element) {
        // Check for key
        val hasKey = element.hasAttributeNS(ANDROID_URI, "key")
        if (!hasKey) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing required attribute `android:key`"
            )
        }

        // Check for restrictionType
        val typeAttr = element.getAttributeNodeNS(ANDROID_URI, "restrictionType")
        val restrictionType = typeAttr?.value
        if (restrictionType == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing required attribute `android:restrictionType`"
            )
        } else {
            val validTypes = setOf(
                "boolean", "integer", "string", "choice", "multi-select", "hidden", "bundle", "bundle_array"
            )
            if (restrictionType !in validTypes) {
                context.report(
                    ISSUE,
                    typeAttr,
                    context.getValueLocation(typeAttr),
                    "Invalid restriction type `$restrictionType`"
                )
            }
        }

        // Check for title (required unless type is "hidden")
        if (restrictionType != "hidden") {
            val hasTitle = element.hasAttributeNS(ANDROID_URI, "title")
            if (!hasTitle) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing required attribute `android:title`"
                )
            }
        }

        // Check choice/multi-select requirement for entries and entryValues
        if (restrictionType == "choice" || restrictionType == "multi-select") {
            val hasEntries = element.hasAttributeNS(ANDROID_URI, "entries")
            val hasEntryValues = element.hasAttributeNS(ANDROID_URI, "entryValues")
            if (!hasEntries) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing required attribute `android:entries` for type `$restrictionType`"
                )
            }
            if (!hasEntryValues) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing required attribute `android:entryValues` for type `$restrictionType`"
                )
            }
        }

        val isBundle = restrictionType == "bundle" || restrictionType == "bundle_array"

        // Bundle types cannot have a default value
        if (isBundle && element.hasAttributeNS(ANDROID_URI, "defaultValue")) {
            val defValueAttr = element.getAttributeNodeNS(ANDROID_URI, "defaultValue")
            context.report(
                ISSUE,
                defValueAttr,
                context.getValueLocation(defValueAttr),
                "Attribute `android:defaultValue` is not supported for `bundle` or `bundle_array` types"
            )
        }

        // Check children
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (!isBundle) {
                    context.report(
                        ISSUE,
                        childElement,
                        context.getNameLocation(childElement),
                        "Nested `<restriction>` elements are only allowed for `bundle` or `bundle_array` types"
                    )
                } else if (childElement.tagName != "restriction") {
                    context.report(
                        ISSUE,
                        childElement,
                        context.getNameLocation(childElement),
                        "Only `<restriction>` elements are allowed inside bundle-type `<restriction>`"
                    )
                } else {
                    validateRestrictionElement(context, childElement)
                }
            }
        }
    }
}