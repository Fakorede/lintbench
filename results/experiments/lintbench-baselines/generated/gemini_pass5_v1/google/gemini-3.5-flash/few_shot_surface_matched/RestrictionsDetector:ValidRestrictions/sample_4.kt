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
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != "restrictions") {
            return
        }

        var child = root.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (childElement.tagName != "restriction") {
                    context.report(
                        ISSUE,
                        childElement,
                        context.getNameLocation(childElement),
                        "Only `<restriction>` elements are allowed as direct children of `<restrictions>`"
                    )
                } else {
                    validateRestriction(context, childElement)
                }
            }
            child = child.nextSibling
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element) {
        val hasKey = element.hasAttributeNS(ANDROID_URI, "key")
        if (!hasKey) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing required attribute `android:key`"
            )
        }

        val typeAttr = element.getAttributeNodeNS(ANDROID_URI, "restrictionType")
        if (typeAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing required attribute `android:restrictionType`"
            )
            return
        }

        val type = typeAttr.value
        val validTypes = setOf("bool", "integer", "string", "choice", "multi-select", "hidden", "bundle", "bundle_array")
        if (type !in validTypes) {
            context.report(
                ISSUE,
                typeAttr,
                context.getValueLocation(typeAttr),
                "Invalid restriction type `$type`"
            )
            return
        }

        if (type != "hidden") {
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

        if (type == "choice" || type == "multi-select") {
            val hasEntries = element.hasAttributeNS(ANDROID_URI, "entries")
            val hasEntryValues = element.hasAttributeNS(ANDROID_URI, "entryValues")
            if (!hasEntries || !hasEntryValues) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Choice and multi-select restrictions require both `android:entries` and `android:entryValues` attributes"
                )
            }
        }

        val isBundle = type == "bundle" || type == "bundle_array"

        if (isBundle) {
            val defaultValueAttr = element.getAttributeNodeNS(ANDROID_URI, "defaultValue")
            if (defaultValueAttr != null) {
                context.report(
                    ISSUE,
                    defaultValueAttr,
                    context.getLocation(defaultValueAttr),
                    "Bundles and bundle arrays cannot have a default value"
                )
            }
        }

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (childElement.tagName == "restriction") {
                    if (!isBundle) {
                        context.report(
                            ISSUE,
                            childElement,
                            context.getNameLocation(childElement),
                            "Nested restrictions are only allowed for type `bundle` or `bundle_array`"
                        )
                    } else {
                        validateRestriction(context, childElement)
                    }
                } else {
                    context.report(
                        ISSUE,
                        childElement,
                        context.getNameLocation(childElement),
                        "Only `<restriction>` elements are allowed as children"
                    )
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
            explanation = "Ensures that an application's restrictions XML file is properly formed",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = Implementation(RestrictionsDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}