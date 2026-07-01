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

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"

        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_TITLE = "title"
        private const val ATTR_DESCRIPTION = "description"
        private const val ATTR_KEY = "key"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"
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

        private val VALID_TYPES = setOf(
            TYPE_BOOL,
            TYPE_STRING,
            TYPE_INTEGER,
            TYPE_CHOICE,
            TYPE_MULTI_SELECT,
            TYPE_HIDDEN,
            TYPE_BUNDLE,
            TYPE_BUNDLE_ARRAY
        )

        const val MAX_NESTING_DEPTH = 20
        const val MAX_NUMBER_OF_NESTED_RESTRICTIONS = 1000

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed.

                The restrictions XML file must follow the format described in the \
                `RestrictionsManager` documentation. Each `<restriction>` element must have \
                a valid `android:key`, `android:restrictionType`, and `android:title` \
                (except for `bundle` and `bundle_array` types which may have nested restrictions).

                For `choice` and `multi-select` types, `android:entries` and \
                `android:entryValues` must be specified.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            ),
            moreInfo = "https://developer.android.com/reference/android/content/RestrictionsManager.html"
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        // Check if this looks like a restrictions file
        if (root.tagName != TAG_RESTRICTIONS) {
            return
        }

        // Validate child restriction elements
        validateRestrictions(context, root, true, 0, Counter())
    }

    private class Counter {
        var count = 0
    }

    private fun validateRestrictions(
        context: XmlContext,
        parent: Element,
        isRoot: Boolean,
        depth: Int,
        counter: Counter
    ) {
        if (depth > MAX_NESTING_DEPTH) {
            context.report(
                ISSUE,
                parent,
                context.getLocation(parent),
                "Restrictions nesting depth exceeds $MAX_NESTING_DEPTH"
            )
            return
        }

        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                if (element.tagName == TAG_RESTRICTION) {
                    counter.count++
                    if (counter.count > MAX_NUMBER_OF_NESTED_RESTRICTIONS) {
                        context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Number of restrictions exceeds $MAX_NUMBER_OF_NESTED_RESTRICTIONS"
                        )
                        return
                    }
                    validateRestriction(context, element, depth + 1, counter)
                } else if (!isRoot) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Unexpected tag `<${element.tagName}>`, expected `<$TAG_RESTRICTION>`"
                    )
                }
            }
            child = child.nextSibling
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element, depth: Int, counter: Counter) {
        // Validate android:key
        val key = element.getAttributeNS(ANDROID_URI, ATTR_KEY)
        if (key.isNullOrEmpty()) {
            val keyAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_KEY)
            if (keyAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:key`"
                )
            } else {
                context.report(
                    ISSUE,
                    keyAttr,
                    context.getLocation(keyAttr),
                    "The `android:key` attribute cannot be empty"
                )
            }
        }

        // Validate android:restrictionType
        val restrictionTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
        if (restrictionTypeAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `android:restrictionType`"
            )
            return
        }

        val restrictionType = restrictionTypeAttr.value
        if (restrictionType.isNullOrEmpty()) {
            context.report(
                ISSUE,
                restrictionTypeAttr,
                context.getLocation(restrictionTypeAttr),
                "The `android:restrictionType` attribute cannot be empty"
            )
            return
        }

        if (!VALID_TYPES.contains(restrictionType)) {
            context.report(
                ISSUE,
                restrictionTypeAttr,
                context.getLocation(restrictionTypeAttr),
                "Invalid restriction type `$restrictionType`. Must be one of: ${VALID_TYPES.joinToString(", ")}"
            )
            return
        }

        // Validate android:title (required for most types)
        if (restrictionType != TYPE_BUNDLE && restrictionType != TYPE_BUNDLE_ARRAY) {
            val title = element.getAttributeNS(ANDROID_URI, ATTR_TITLE)
            if (title.isNullOrEmpty()) {
                val titleAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_TITLE)
                if (titleAttr == null) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Missing required attribute `android:title`"
                    )
                } else {
                    context.report(
                        ISSUE,
                        titleAttr,
                        context.getLocation(titleAttr),
                        "The `android:title` attribute cannot be empty"
                    )
                }
            }
        }

        // Validate choice and multi-select types require entries and entryValues
        if (restrictionType == TYPE_CHOICE || restrictionType == TYPE_MULTI_SELECT) {
            val entriesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ENTRIES)
            if (entriesAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:entries` for restriction type `$restrictionType`"
                )
            } else if (entriesAttr.value.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    entriesAttr,
                    context.getLocation(entriesAttr),
                    "The `android:entries` attribute cannot be empty for restriction type `$restrictionType`"
                )
            }

            val entryValuesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ENTRY_VALUES)
            if (entryValuesAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:entryValues` for restriction type `$restrictionType`"
                )
            } else if (entryValuesAttr.value.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    entryValuesAttr,
                    context.getLocation(entryValuesAttr),
                    "The `android:entryValues` attribute cannot be empty for restriction type `$restrictionType`"
                )
            }
        }

        // Validate bundle and bundle_array types have nested restrictions
        if (restrictionType == TYPE_BUNDLE || restrictionType == TYPE_BUNDLE_ARRAY) {
            if (depth > MAX_NESTING_DEPTH) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Restrictions nesting depth exceeds $MAX_NESTING_DEPTH"
                )
                return
            }

            var hasChildRestrictions = false
            var child = element.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val childElement = child as Element
                    if (childElement.tagName == TAG_RESTRICTION) {
                        hasChildRestrictions = true
                        counter.count++
                        if (counter.count > MAX_NUMBER_OF_NESTED_RESTRICTIONS) {
                            context.report(
                                ISSUE,
                                childElement,
                                context.getLocation(childElement),
                                "Number of restrictions exceeds $MAX_NUMBER_OF_NESTED_RESTRICTIONS"
                            )
                            return
                        }
                        validateRestriction(context, childElement, depth + 1, counter)
                    } else {
                        context.report(
                            ISSUE,
                            childElement,
                            context.getLocation(childElement),
                            "Unexpected tag `<${childElement.tagName}>` inside `$restrictionType` restriction, expected `<$TAG_RESTRICTION>`"
                        )
                    }
                }
                child = child.nextSibling
            }

            if (!hasChildRestrictions) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "A `$restrictionType` restriction should have nested `<$TAG_RESTRICTION>` elements"
                )
            }
        } else {
            // Non-bundle types should not have child restriction elements
            var child = element.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val childElement = child as Element
                    if (childElement.tagName == TAG_RESTRICTION) {
                        context.report(
                            ISSUE,
                            childElement,
                            context.getLocation(childElement),
                            "Nested `<$TAG_RESTRICTION>` elements are only allowed inside `$TYPE_BUNDLE` or `$TYPE_BUNDLE_ARRAY` restrictions"
                        )
                    }
                }
                child = child.nextSibling
            }
        }

        // Validate defaultValue type consistency
        val defaultValueAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_DEFAULT_VALUE)
        if (defaultValueAttr != null && !defaultValueAttr.value.isNullOrEmpty()) {
            val defaultValue = defaultValueAttr.value
            when (restrictionType) {
                TYPE_BOOL -> {
                    if (defaultValue != "true" && defaultValue != "false" &&
                        !defaultValue.startsWith("@")) {
                        context.report(
                            ISSUE,
                            defaultValueAttr,
                            context.getLocation(defaultValueAttr),
                            "The `android:defaultValue` for a `bool` restriction must be `true` or `false`"
                        )
                    }
                }
                TYPE_INTEGER -> {
                    if (!defaultValue.startsWith("@")) {
                        try {
                            defaultValue.toInt()
                        } catch (e: NumberFormatException) {
                            context.report(
                                ISSUE,
                                defaultValueAttr,
                                context.getLocation(defaultValueAttr),
                                "The `android:defaultValue` for an `integer` restriction must be an integer"
                            )
                        }
                    }
                }
            }
        }
    }
}