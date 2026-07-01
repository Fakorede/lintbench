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

                A restrictions file must have a `<restrictions>` root element, and each \
                child `<restriction>` element must have a valid `android:restrictionType` \
                attribute (one of `bool`, `string`, `integer`, `choice`, `multi-select`, \
                `hidden`, `bundle`, or `bundle_array`), as well as a required \
                `android:key` attribute.

                `choice` and `multi-select` restriction types must have an \
                `android:entries` and `android:entryValues` attribute.

                `bundle` and `bundle_array` restriction types can have nested \
                `<restriction>` children.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )

        // Root element
        private const val TAG_RESTRICTIONS = "restrictions"

        // Child element
        private const val TAG_RESTRICTION = "restriction"

        // Attributes
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_DESCRIPTION = "description"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"

        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        // Valid restriction types
        private val VALID_RESTRICTION_TYPES = setOf(
            "bool",
            "string",
            "integer",
            "choice",
            "multi-select",
            "hidden",
            "bundle",
            "bundle_array",
        )

        // Types that require entries/entryValues
        private val CHOICE_TYPES = setOf("choice", "multi-select")

        // Types that can have nested restrictions
        private val BUNDLE_TYPES = setOf("bundle", "bundle_array")
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        // Check that the root element is <restrictions>
        if (root.tagName != TAG_RESTRICTIONS) {
            // Not a restrictions file — skip silently
            return
        }

        // Visit all child <restriction> elements of the root
        visitRestrictionChildren(context, root, isBundle = false)
    }

    private fun visitRestrictionChildren(
        context: XmlContext,
        parent: Element,
        isBundle: Boolean,
    ) {
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                if (element.tagName == TAG_RESTRICTION) {
                    validateRestriction(context, element)
                } else {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Unexpected element `<${element.tagName}>`, expected `<$TAG_RESTRICTION>`",
                    )
                }
            }
            child = child.nextSibling
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element) {
        // Validate android:key
        val key = element.getAttributeNS(ANDROID_NS, ATTR_KEY)
        if (key.isNullOrEmpty()) {
            val keyAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_KEY)
            if (keyAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:key`",
                )
            } else {
                context.report(
                    ISSUE,
                    keyAttr,
                    context.getLocation(keyAttr),
                    "`android:key` must not be empty",
                )
            }
        }

        // Validate android:restrictionType
        val restrictionTypeAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_RESTRICTION_TYPE)
        if (restrictionTypeAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `android:restrictionType`",
            )
            return
        }

        val restrictionType = restrictionTypeAttr.value
        if (restrictionType !in VALID_RESTRICTION_TYPES) {
            context.report(
                ISSUE,
                restrictionTypeAttr,
                context.getLocation(restrictionTypeAttr),
                "Invalid `android:restrictionType` value `$restrictionType`. " +
                    "Must be one of: ${VALID_RESTRICTION_TYPES.joinToString(", ")}",
            )
            return
        }

        // Validate android:title (required for non-hidden types)
        if (restrictionType != "hidden") {
            val title = element.getAttributeNS(ANDROID_NS, ATTR_TITLE)
            if (title.isNullOrEmpty()) {
                val titleAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_TITLE)
                if (titleAttr == null) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Missing required attribute `android:title`",
                    )
                } else {
                    context.report(
                        ISSUE,
                        titleAttr,
                        context.getLocation(titleAttr),
                        "`android:title` must not be empty",
                    )
                }
            }
        }

        // Validate choice / multi-select require entries and entryValues
        if (restrictionType in CHOICE_TYPES) {
            val entries = element.getAttributeNS(ANDROID_NS, ATTR_ENTRIES)
            if (entries.isNullOrEmpty()) {
                val entriesAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_ENTRIES)
                if (entriesAttr == null) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Restriction type `$restrictionType` requires an `android:entries` attribute",
                    )
                } else {
                    context.report(
                        ISSUE,
                        entriesAttr,
                        context.getLocation(entriesAttr),
                        "`android:entries` must not be empty for restriction type `$restrictionType`",
                    )
                }
            }

            val entryValues = element.getAttributeNS(ANDROID_NS, ATTR_ENTRY_VALUES)
            if (entryValues.isNullOrEmpty()) {
                val entryValuesAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_ENTRY_VALUES)
                if (entryValuesAttr == null) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Restriction type `$restrictionType` requires an `android:entryValues` attribute",
                    )
                } else {
                    context.report(
                        ISSUE,
                        entryValuesAttr,
                        context.getLocation(entryValuesAttr),
                        "`android:entryValues` must not be empty for restriction type `$restrictionType`",
                    )
                }
            }
        }

        // Validate bundle / bundle_array: recurse into nested <restriction> children
        if (restrictionType in BUNDLE_TYPES) {
            visitRestrictionChildren(context, element, isBundle = true)
        } else {
            // Non-bundle types should not have child <restriction> elements
            var child = element.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val childElement = child as Element
                    context.report(
                        ISSUE,
                        childElement,
                        context.getLocation(childElement),
                        "Restriction type `$restrictionType` should not have nested `<${childElement.tagName}>` elements; " +
                            "only `bundle` and `bundle_array` types may have children",
                    )
                }
                child = child.nextSibling
            }
        }
    }
}