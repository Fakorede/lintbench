package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : Detector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != TAG_RESTRICTIONS) {
            return
        }
        val keys = HashSet<String>()
        val children = getChildElements(root)
        if (children.isEmpty()) {
            context.report(
                ISSUE,
                root,
                context.getLocation(root),
                "The `<restrictions>` element must have at least one child `<restriction>`"
            )
            return
        }
        for (child in children) {
            validateRestriction(context, child, keys)
        }
    }

    private fun getChildElements(element: Element): List<Element> {
        val list = mutableListOf<Element>()
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                list.add(child as Element)
            }
        }
        return list
    }

    private fun validateRestriction(
        context: XmlContext,
        element: Element,
        keys: HashSet<String>
    ) {
        if (element.tagName != TAG_RESTRICTION) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Only `<restriction>` elements are allowed here"
            )
            return
        }

        // Check key
        val keyAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_KEY)
        if (keyAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `android:key` attribute"
            )
        } else {
            val keyValue = keyAttr.value
            if (keyValue.startsWith("@")) {
                context.report(
                    ISSUE,
                    keyAttr,
                    context.getLocation(keyAttr),
                    "The `android:key` attribute cannot be a resource reference"
                )
            }
            if (!keys.add(keyValue)) {
                context.report(
                    ISSUE,
                    keyAttr,
                    context.getLocation(keyAttr),
                    "The `android:key` attribute must be unique"
                )
            }
        }

        // Check title and description
        val titleAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_TITLE)
        if (titleAttr != null && !titleAttr.value.startsWith("@")) {
            context.report(
                ISSUE,
                titleAttr,
                context.getLocation(titleAttr),
                "The `android:title` attribute must be a resource reference"
            )
        }

        val descAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_DESCRIPTION)
        if (descAttr != null && !descAttr.value.startsWith("@")) {
            context.report(
                ISSUE,
                descAttr,
                context.getLocation(descAttr),
                "The `android:description` attribute must be a resource reference"
            )
        }

        // Check restrictionType
        val typeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
        if (typeAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `android:restrictionType` attribute"
            )
            return
        }

        val type = typeAttr.value
        val validTypes = setOf("bool", "integer", "string", "choice", "multi-select", "hidden", "bundle", "bundle_array")
        if (type !in validTypes) {
            context.report(
                ISSUE,
                typeAttr,
                context.getLocation(typeAttr),
                "Invalid restriction type `$type`"
            )
            return
        }

        // Check entries and entryValues
        val entriesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ENTRIES)
        val entryValuesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ENTRY_VALUES)

        if (type == "choice" || type == "multi-select") {
            if (entriesAttr == null || entryValuesAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Restriction type `$type` requires `android:entries` and `android:entryValues` attributes"
                )
            }
        }

        if (entriesAttr != null && !entriesAttr.value.startsWith("@")) {
            context.report(
                ISSUE,
                entriesAttr,
                context.getLocation(entriesAttr),
                "The `android:entries` attribute must be a resource reference"
            )
        }
        if (entryValuesAttr != null && !entryValuesAttr.value.startsWith("@")) {
            context.report(
                ISSUE,
                entryValuesAttr,
                context.getLocation(entryValuesAttr),
                "The `android:entryValues` attribute must be a resource reference"
            )
        }

        // Check defaultValue
        val defaultValueAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_DEFAULT_VALUE)
        if (type == "bundle" || type == "bundle_array") {
            if (defaultValueAttr != null) {
                context.report(
                    ISSUE,
                    defaultValueAttr,
                    context.getLocation(defaultValueAttr),
                    "A restriction of type `$type` cannot have a default value"
                )
            }
        } else if (type == "hidden") {
            if (defaultValueAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "A restriction of type `hidden` must have a default value"
                )
            }
        }

        if (defaultValueAttr != null) {
            val defaultValue = defaultValueAttr.value
            if (!defaultValue.startsWith("@")) {
                if (type == "integer") {
                    try {
                        defaultValue.toInt()
                    } catch (e: NumberFormatException) {
                        context.report(
                            ISSUE,
                            defaultValueAttr,
                            context.getLocation(defaultValueAttr),
                            "The `android:defaultValue` attribute must be a valid integer"
                        )
                    }
                } else if (type == "bool") {
                    if (defaultValue != "true" && defaultValue != "false") {
                        context.report(
                            ISSUE,
                            defaultValueAttr,
                            context.getLocation(defaultValueAttr),
                            "The `android:defaultValue` attribute must be a valid boolean"
                        )
                    }
                }
            }
        }

        // Check children
        val children = getChildElements(element)
        if (type == "bundle") {
            if (children.isEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "A restriction of type `bundle` must have at least one child restriction"
                )
            } else {
                for (child in children) {
                    if (child.tagName != TAG_RESTRICTION) {
                        context.report(
                            ISSUE,
                            child,
                            context.getLocation(child),
                            "Only `<restriction>` elements are allowed here"
                        )
                        continue
                    }
                    val childType = child.getAttributeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
                    if (childType == "bundle" || childType == "bundle_array") {
                        context.report(
                            ISSUE,
                            child,
                            context.getLocation(child),
                            "A restriction of type `bundle` cannot have child restrictions of type `bundle` or `bundle_array`"
                        )
                    }
                    validateRestriction(context, child, keys)
                }
            }
        } else if (type == "bundle_array") {
            if (children.isEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "A restriction of type `bundle_array` must have at least one child restriction"
                )
            } else {
                for (child in children) {
                    if (child.tagName != TAG_RESTRICTION) {
                        context.report(
                            ISSUE,
                            child,
                            context.getLocation(child),
                            "Only `<restriction>` elements are allowed here"
                        )
                        continue
                    }
                    val childType = child.getAttributeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
                    if (childType != "bundle") {
                        context.report(
                            ISSUE,
                            child,
                            context.getLocation(child),
                            "A restriction of type `bundle_array` can only have child restrictions of type `bundle`"
                        )
                    }
                    validateRestriction(context, child, keys)
                }
            }
        } else {
            // For other types, no children are allowed
            for (child in children) {
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "Nested restrictions are only supported for `bundle` and `bundle_array` types"
                )
            }
        }
    }

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_DESCRIPTION = "description"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an applications restrictions XML file is properly formed
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
}