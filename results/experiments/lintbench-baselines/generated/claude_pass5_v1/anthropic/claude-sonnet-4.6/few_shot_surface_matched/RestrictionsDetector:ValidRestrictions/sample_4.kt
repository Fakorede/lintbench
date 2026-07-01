package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
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

        // Check if this looks like a restrictions file
        if (root.tagName != TAG_RESTRICTIONS) {
            return
        }

        checkRestrictions(context, root, true)
    }

    private fun checkRestrictions(context: XmlContext, element: Element, isRoot: Boolean) {
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val tag = childElement.tagName
                if (tag == TAG_RESTRICTION) {
                    checkRestriction(context, childElement)
                } else if (!isRoot) {
                    context.report(
                        ISSUE,
                        childElement,
                        context.getElementLocation(childElement),
                        "Unexpected tag `<$tag>`, expected `<restriction>`"
                    )
                }
            }
            child = child.nextSibling
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element) {
        // Check required android:key attribute
        val key = element.getAttributeNS(ANDROID_URI, ATTR_KEY)
        if (key.isNullOrEmpty()) {
            val nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
            val location = if (nameAttr != null) {
                context.getValueLocation(nameAttr)
            } else {
                context.getElementLocation(element)
            }
            context.report(
                ISSUE,
                element,
                location,
                "Missing required attribute `android:key`"
            )
        }

        // Check required android:restrictionType attribute
        val restrictionType = element.getAttributeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
        if (restrictionType.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "Missing required attribute `android:restrictionType`"
            )
            return
        }

        when (restrictionType) {
            TYPE_BOOL,
            TYPE_STRING,
            TYPE_INTEGER,
            TYPE_CHOICE,
            TYPE_MULTI_SELECT,
            TYPE_HIDDEN,
            TYPE_BUNDLE,
            TYPE_BUNDLE_ARRAY -> {
                // Valid types
            }
            else -> {
                val typeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
                val location = if (typeAttr != null) {
                    context.getValueLocation(typeAttr)
                } else {
                    context.getElementLocation(element)
                }
                context.report(
                    ISSUE,
                    element,
                    location,
                    "Invalid `android:restrictionType` value `$restrictionType`. " +
                        "Must be one of `bool`, `string`, `integer`, `choice`, " +
                        "`multi-select`, `hidden`, `bundle`, or `bundle_array`"
                )
                return
            }
        }

        // For choice and multi-select, check that entries and entryValues are provided
        if (restrictionType == TYPE_CHOICE || restrictionType == TYPE_MULTI_SELECT) {
            val entries = element.getAttributeNS(ANDROID_URI, ATTR_ENTRIES)
            if (entries.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Missing required attribute `android:entries` for " +
                        "`android:restrictionType=\"$restrictionType\"`"
                )
            }
            val entryValues = element.getAttributeNS(ANDROID_URI, ATTR_ENTRY_VALUES)
            if (entryValues.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Missing required attribute `android:entryValues` for " +
                        "`android:restrictionType=\"$restrictionType\"`"
                )
            }
        }

        // For bundle and bundle_array, recursively check nested restrictions
        if (restrictionType == TYPE_BUNDLE || restrictionType == TYPE_BUNDLE_ARRAY) {
            checkRestrictions(context, element, false)
        } else {
            // Non-bundle types should not have child restriction elements
            var child = element.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val childElement = child as Element
                    context.report(
                        ISSUE,
                        childElement,
                        context.getElementLocation(childElement),
                        "Nested `<restriction>` elements are only allowed for " +
                            "`restrictionType=\"bundle\"` or `restrictionType=\"bundle_array\"`"
                    )
                }
                child = child.nextSibling
            }
        }
    }

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"

        private const val ATTR_KEY = "key"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"

        private const val TYPE_BOOL = "bool"
        private const val TYPE_STRING = "string"
        private const val TYPE_INTEGER = "integer"
        private const val TYPE_CHOICE = "choice"
        private const val TYPE_MULTI_SELECT = "multi-select"
        private const val TYPE_HIDDEN = "hidden"
        private const val TYPE_BUNDLE = "bundle"
        private const val TYPE_BUNDLE_ARRAY = "bundle_array"

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation =
                "Ensures that an application's restrictions XML file is properly formed.\n\n" +
                    "Each `<restriction>` element must have an `android:key` attribute and a " +
                    "valid `android:restrictionType` attribute. The `choice` and `multi-select` " +
                    "types additionally require `android:entries` and `android:entryValues`. " +
                    "Nested `<restriction>` elements are only allowed inside `bundle` or " +
                    "`bundle_array` typed restrictions.\n\n" +
                    "See https://developer.android.com/reference/android/content/" +
                    "RestrictionsManager.html for more details.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}