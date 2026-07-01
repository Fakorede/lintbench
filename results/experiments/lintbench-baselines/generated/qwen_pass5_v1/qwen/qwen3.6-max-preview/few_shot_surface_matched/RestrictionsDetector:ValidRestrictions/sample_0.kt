package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != "restrictions") return

        validateRestrictionChildren(context, root)
    }

    private fun validateRestrictionChildren(context: XmlContext, parent: Element) {
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                if (element.tagName != "restriction") {
                    context.report(
                        ISSUE, element, context.getLocation(element),
                        "Expected <restriction> element"
                    )
                } else {
                    validateRestriction(context, element)
                }
            }
            child = child.nextSibling
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element) {
        val keyAttr = element.getAttributeNodeNS(ANDROID_URI, "key")
        val titleAttr = element.getAttributeNodeNS(ANDROID_URI, "title")
        val typeAttr = element.getAttributeNodeNS(ANDROID_URI, "restrictionType")

        if (keyAttr == null || keyAttr.value.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element), "Missing required attribute android:key")
        }
        if (titleAttr == null || titleAttr.value.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element), "Missing required attribute android:title")
        }

        val type = typeAttr?.value ?: ""
        if (type.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element), "Missing required attribute android:restrictionType")
            return
        }

        val validTypes = setOf("bool", "choice", "multi-select", "string", "integer", "hidden", "bundle", "bundle_array")
        if (type !in validTypes) {
            context.report(
                ISSUE, typeAttr, context.getLocation(typeAttr),
                "Invalid android:restrictionType \"$type\". Must be one of: ${validTypes.joinToString(", ")}"
            )
        }

        val allowsNested = type == "bundle" || type == "bundle_array"
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                if (!allowsNested) {
                    context.report(
                        ISSUE, child as Element, context.getLocation(child),
                        "Elements of type \"$type\" cannot contain child <restriction> elements"
                    )
                } else {
                    validateRestrictionChildren(context, child as Element)
                }
            }
            child = child.nextSibling
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an applications restrictions XML file is properly formed. " +
                "The root element must be <restrictions>, containing <restriction> elements with " +
                "required attributes android:key, android:title, and android:restrictionType. " +
                "Only bundle and bundle_array types may contain nested restrictions.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(RestrictionsDetector::class.java, Scope.ALL_RESOURCE_FILES)
        )
    }
}