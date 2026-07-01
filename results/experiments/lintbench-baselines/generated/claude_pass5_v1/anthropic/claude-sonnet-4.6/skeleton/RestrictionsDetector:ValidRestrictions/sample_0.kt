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
        private val IMPLEMENTATION = Implementation(
            RestrictionsDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed.

                The restrictions XML file must have a root element of `<restrictions>` and \
                each child must be a `<restriction>` element with the required attributes \
                `android:key` and `android:restrictionType`. Certain restriction types \
                (`choice` and `multi-select`) must also have an `android:entries` and \
                `android:entryValues` attribute. The `bundle` and `bundle_array` types \
                may have nested `<restriction>` elements.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )

        // Root element
        private const val TAG_RESTRICTIONS = "restrictions"

        // Child elements
        private const val TAG_RESTRICTION = "restriction"

        // Attributes
        private const val ATTR_ANDROID_KEY = "android:key"
        private const val ATTR_ANDROID_RESTRICTION_TYPE = "android:restrictionType"
        private const val ATTR_ANDROID_TITLE = "android:title"
        private const val ATTR_ANDROID_ENTRIES = "android:entries"
        private const val ATTR_ANDROID_ENTRY_VALUES = "android:entryValues"

        // Restriction types
        private const val TYPE_BOOL = "bool"
        private const val TYPE_STRING = "string"
        private const val TYPE_INTEGER = "integer"
        private const val TYPE_CHOICE = "choice"
        private const val TYPE_MULTI_SELECT = "multi-select"
        private const val TYPE_HIDDEN = "hidden"
        private const val TYPE_BUNDLE = "bundle"
        private const val TYPE_BUNDLE_ARRAY = "bundle_array"

        private val VALID_RESTRICTION_TYPES = setOf(
            TYPE_BOOL,
            TYPE_STRING,
            TYPE_INTEGER,
            TYPE_CHOICE,
            TYPE_MULTI_SELECT,
            TYPE_HIDDEN,
            TYPE_BUNDLE,
            TYPE_BUNDLE_ARRAY,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        // The root element must be <restrictions>
        if (root.tagName != TAG_RESTRICTIONS) {
            context.report(
                ISSUE,
                root,
                context.getLocation(root),
                "Expected root element `<restrictions>`, but found `<${root.tagName}>`",
            )
            return
        }

        // Validate each child element of the root
        visitRestrictionChildren(context, root, isTopLevel = true)
    }

    private fun visitRestrictionChildren(
        context: XmlContext,
        parent: Element,
        isTopLevel: Boolean,
    ) {
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                if (element.tagName != TAG_RESTRICTION) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Expected a `<restriction>` element here, but found `<${element.tagName}>`",
                    )
                } else {
                    validateRestriction(context, element)
                }
            }
            child = child.nextSibling
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element) {
        // Must have android:key
        val key = getAndroidAttribute(element, "key")
        if (key == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `android:key`",
            )
        }

        // Must have android:restrictionType
        val restrictionType = getAndroidAttribute(element, "restrictionType")
        if (restrictionType == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `android:restrictionType`",
            )
            return
        }

        // android:restrictionType must be a valid value
        if (restrictionType !in VALID_RESTRICTION_TYPES) {
            val attr = element.getAttributeNode(ATTR_ANDROID_RESTRICTION_TYPE)
                ?: element.getAttributeNodeNS(
                    "http://schemas.android.com/apk/res/android",
                    "restrictionType",
                )
            val location = if (attr != null) context.getLocation(attr) else context.getLocation(element)
            context.report(
                ISSUE,
                element,
                location,
                "Invalid `android:restrictionType` value `$restrictionType`. " +
                    "Must be one of: ${VALID_RESTRICTION_TYPES.joinToString(", ")}",
            )
            return
        }

        // choice and multi-select require android:entries and android:entryValues
        if (restrictionType == TYPE_CHOICE || restrictionType == TYPE_MULTI_SELECT) {
            val entries = getAndroidAttribute(element, "entries")
            if (entries == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Restriction type `$restrictionType` requires attribute `android:entries`",
                )
            }
            val entryValues = getAndroidAttribute(element, "entryValues")
            if (entryValues == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Restriction type `$restrictionType` requires attribute `android:entryValues`",
                )
            }
        }

        // bundle and bundle_array may have nested <restriction> children
        if (restrictionType == TYPE_BUNDLE || restrictionType == TYPE_BUNDLE_ARRAY) {
            visitRestrictionChildren(context, element, isTopLevel = false)
        } else {
            // Non-bundle types should not have nested <restriction> children
            var child = element.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val childElement = child as Element
                    context.report(
                        ISSUE,
                        childElement,
                        context.getLocation(childElement),
                        "Nested `<restriction>` elements are only allowed inside " +
                            "`bundle` or `bundle_array` restrictions",
                    )
                }
                child = child.nextSibling
            }
        }
    }

    /**
     * Returns the value of an attribute in the Android namespace, or null if not present.
     * Checks both prefixed form (android:attrName) and namespace URI form.
     */
    private fun getAndroidAttribute(element: Element, localName: String): String? {
        // Try the prefixed attribute name first
        val prefixedValue = element.getAttribute("android:$localName")
        if (prefixedValue.isNotEmpty()) return prefixedValue

        // Try namespace URI
        val nsValue = element.getAttributeNS(
            "http://schemas.android.com/apk/res/android",
            localName,
        )
        if (nsValue.isNotEmpty()) return nsValue

        return null
    }
}