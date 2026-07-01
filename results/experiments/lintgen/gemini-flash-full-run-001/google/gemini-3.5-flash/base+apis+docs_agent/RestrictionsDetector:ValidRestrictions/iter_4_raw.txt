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
        val keys = HashSet<String>()
        val count = intArrayOf(0)
        for (child in children) {
            validateRestriction(context, child, keys, 1, count)
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
        keys: MutableSet<String>,
        depth: Int,
        count: IntArray
    ) {
        if (depth > MAX_NESTING_DEPTH) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Nesting depth is more than $MAX_NESTING_DEPTH"
            )
            return
        }
        count[0]++
        if (count[0] > MAX_NUMBER_OF_NESTED_RESTRICTIONS) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Total number of nested restrictions is more than $MAX_NUMBER_OF_NESTED_RESTRICTIONS"
            )
            return
        }

        val tagName = element.tagName
        if (tagName != TAG_RESTRICTION) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Expected `<restriction>` tag, found `<$tagName>`"
            )
            return
        }

        val key = element.getAttributeNS(ANDROID_URI, ATTR_KEY)
        if (key.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `android:key` attribute"
            )
        } else {
            if (key.startsWith("@")) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The `android:key` attribute cannot be a resource reference"
                )
            }
            if (!keys.add(key)) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Duplicate key `$key`"
                )
            }
        }

        val title = element.getAttributeNS(ANDROID_URI, ATTR_TITLE)
        if (title.isNotEmpty() && !title.startsWith("@")) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "The `android:title` attribute must be a resource reference"
            )
        }

        val description = element.getAttributeNS(ANDROID_URI, ATTR_DESCRIPTION)
        if (description.isNotEmpty() && !description.startsWith("@")) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "The `android:description` attribute must be a resource reference"
            )
        }

        val restrictionType = element.getAttributeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
        if (restrictionType.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `android:restrictionType` attribute"
            )
            return
        }

        val entries = element.getAttributeNS(ANDROID_URI, ATTR_ENTRIES)
        if (entries.isNotEmpty() && !entries.startsWith("@")) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "The `android:entries` attribute must be a resource reference"
            )
        }

        val entryValues = element.getAttributeNS(ANDROID_URI, ATTR_ENTRY_VALUES)
        if (entryValues.isNotEmpty() && !entryValues.startsWith("@")) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "The `android:entryValues` attribute must be a resource reference"
            )
        }

        val defaultValue = element.getAttributeNS(ANDROID_URI, ATTR_DEFAULT_VALUE)

        when (restrictionType) {
            "bool" -> {
                if (defaultValue.isNotEmpty() && !defaultValue.startsWith("@") &&
                    defaultValue != "true" && defaultValue != "false"
                ) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "The `android:defaultValue` attribute must be a boolean"
                    )
                }
            }
            "integer" -> {
                if (defaultValue.isNotEmpty() && !defaultValue.startsWith("@")) {
                    try {
                        defaultValue.toInt()
                    } catch (e: NumberFormatException) {
                        context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "The `android:defaultValue` attribute must be an integer"
                        )
                    }
                }
            }
            "choice", "multi-select" -> {
                if (entries.isEmpty()) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "The `android:entries` attribute must be specified for choice or multi-select types"
                    )
                }
                if (entryValues.isEmpty()) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "The `android:entryValues` attribute must be specified for choice or multi-select types"
                    )
                }
            }
            "hidden" -> {
                if (defaultValue.isEmpty()) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "The `android:defaultValue` attribute must be specified for hidden type"
                    )
                }
            }
            "bundle" -> {
                if (defaultValue.isNotEmpty()) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "The `android:defaultValue` attribute cannot be specified for bundle type"
                    )
                }
                val children = getChildElements(element)
                if (children.isEmpty()) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "The bundle type must have at least one nested restriction"
                    )
                } else {
                    for (child in children) {
                        validateRestriction(context, child, keys, depth + 1, count)
                    }
                }
            }
            "bundle_array" -> {
                if (defaultValue.isNotEmpty()) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "The `android:defaultValue` attribute cannot be specified for bundle_array type"
                    )
                }
                val children = getChildElements(element)
                if (children.isEmpty()) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "The bundle_array type must have at least one nested restriction"
                    )
                } else {
                    for (child in children) {
                        val childType = child.getAttributeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
                        if (childType != "bundle") {
                            context.report(
                                ISSUE,
                                child,
                                context.getLocation(child),
                                "The bundle_array type can only contain bundle type restrictions"
                            )
                        }
                        validateRestriction(context, child, keys, depth + 1, count)
                    }
                }
            }
            "string" -> {
                // No special validation
            }
            else -> {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Invalid restriction type `$restrictionType`"
                )
            }
        }

        if (restrictionType != "bundle" && restrictionType != "bundle_array") {
            val children = getChildElements(element)
            if (children.isNotEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The restriction type `$restrictionType` cannot have nested restrictions"
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

        const val MAX_NUMBER_OF_NESTED_RESTRICTIONS = 1000
        const val MAX_NESTING_DEPTH = 20

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