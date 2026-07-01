package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
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

                A restrictions file must have a `<restrictions>` root element. Each \
                `<restriction>` element must have a `android:key` attribute and a \
                `android:restrictionType` attribute with a valid type. The `android:defaultValue` \
                attribute must be consistent with the restriction type. Nested `<restriction>` \
                elements are only allowed inside a `bundle` or `bundle_array` type restriction.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )

        // Root element
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"

        // Attributes
        private const val ATTR_ANDROID_KEY = "android:key"
        private const val ATTR_ANDROID_RESTRICTION_TYPE = "android:restrictionType"
        private const val ATTR_ANDROID_DEFAULT_VALUE = "android:defaultValue"
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
                "Expected `<restrictions>` as the root element",
            )
            return
        }

        // Validate children of <restrictions>
        visitRestrictionChildren(context, root, isNested = false)
    }

    private fun visitRestrictionChildren(context: XmlContext, parent: Element, isNested: Boolean) {
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                if (element.tagName == TAG_RESTRICTION) {
                    validateRestriction(context, element, isNested)
                } else {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Unexpected element `<${element.tagName}>`, expected `<restriction>`",
                    )
                }
            }
            child = child.nextSibling
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element, isNested: Boolean) {
        // Must have android:key
        val key = element.getAttribute(ATTR_ANDROID_KEY)
        if (key.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing required attribute `android:key`",
            )
        }

        // Must have android:restrictionType
        val restrictionType = element.getAttribute(ATTR_ANDROID_RESTRICTION_TYPE)
        if (restrictionType.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing required attribute `android:restrictionType`",
            )
            // Still validate children if possible
            visitRestrictionChildren(context, element, isNested = true)
            return
        }

        // Validate that restrictionType is one of the known types
        if (restrictionType !in VALID_RESTRICTION_TYPES) {
            val attrNode = element.getAttributeNode(ATTR_ANDROID_RESTRICTION_TYPE)
            context.report(
                ISSUE,
                element,
                if (attrNode != null) context.getValueLocation(attrNode) else context.getLocation(element),
                "Invalid `android:restrictionType` value `$restrictionType`. " +
                    "Must be one of: ${VALID_RESTRICTION_TYPES.joinToString(", ")}",
            )
        }

        // Validate defaultValue consistency
        val defaultValue = element.getAttribute(ATTR_ANDROID_DEFAULT_VALUE)
        if (!defaultValue.isNullOrEmpty()) {
            validateDefaultValue(context, element, restrictionType, defaultValue)
        }

        // choice and multi-select require android:entries and android:entryValues
        if (restrictionType == TYPE_CHOICE || restrictionType == TYPE_MULTI_SELECT) {
            val entries = element.getAttribute(ATTR_ANDROID_ENTRIES)
            if (entries.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing required attribute `android:entries` for restriction type `$restrictionType`",
                )
            }
            val entryValues = element.getAttribute(ATTR_ANDROID_ENTRY_VALUES)
            if (entryValues.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing required attribute `android:entryValues` for restriction type `$restrictionType`",
                )
            }
        }

        // bundle and bundle_array can have nested <restriction> children
        // other types should NOT have nested <restriction> children
        val hasChildRestrictions = hasChildRestrictions(element)
        if (restrictionType == TYPE_BUNDLE || restrictionType == TYPE_BUNDLE_ARRAY) {
            // Recurse into children
            visitRestrictionChildren(context, element, isNested = true)
        } else if (hasChildRestrictions) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Nested `<restriction>` elements are only allowed inside " +
                    "`$TYPE_BUNDLE` or `$TYPE_BUNDLE_ARRAY` type restrictions",
            )
        }
    }

    private fun validateDefaultValue(
        context: XmlContext,
        element: Element,
        restrictionType: String,
        defaultValue: String,
    ) {
        when (restrictionType) {
            TYPE_BOOL -> {
                if (defaultValue != "true" && defaultValue != "false") {
                    val attrNode = element.getAttributeNode(ATTR_ANDROID_DEFAULT_VALUE)
                    context.report(
                        ISSUE,
                        element,
                        if (attrNode != null) context.getValueLocation(attrNode) else context.getLocation(element),
                        "Invalid `android:defaultValue` for type `bool`: " +
                            "expected `true` or `false`, got `$defaultValue`",
                    )
                }
            }
            TYPE_INTEGER -> {
                // defaultValue must be parseable as an integer
                val intValue = defaultValue.trim()
                val isValid = try {
                    intValue.toInt()
                    true
                } catch (e: NumberFormatException) {
                    // Also allow hex and resource references
                    intValue.startsWith("@") || intValue.startsWith("0x") || intValue.startsWith("0X")
                }
                if (!isValid) {
                    val attrNode = element.getAttributeNode(ATTR_ANDROID_DEFAULT_VALUE)
                    context.report(
                        ISSUE,
                        element,
                        if (attrNode != null) context.getValueLocation(attrNode) else context.getLocation(element),
                        "Invalid `android:defaultValue` for type `integer`: " +
                            "`$defaultValue` is not a valid integer",
                    )
                }
            }
            TYPE_BUNDLE, TYPE_BUNDLE_ARRAY -> {
                // defaultValue is not applicable for bundle/bundle_array
                val attrNode = element.getAttributeNode(ATTR_ANDROID_DEFAULT_VALUE)
                context.report(
                    ISSUE,
                    element,
                    if (attrNode != null) context.getValueLocation(attrNode) else context.getLocation(element),
                    "`android:defaultValue` is not applicable for restriction type `$restrictionType`",
                )
            }
            else -> {
                // string, choice, multi-select, hidden: any string value is acceptable
            }
        }
    }

    private fun hasChildRestrictions(element: Element): Boolean {
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && (child as Element).tagName == TAG_RESTRICTION) {
                return true
            }
            child = child.nextSibling
        }
        return false
    }
}