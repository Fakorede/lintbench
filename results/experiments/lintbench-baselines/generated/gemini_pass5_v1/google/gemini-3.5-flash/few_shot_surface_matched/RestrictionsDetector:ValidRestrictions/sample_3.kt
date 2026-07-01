package com.android.tools.lint.checks

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

class RestrictionsDetector : ResourceXmlDetector(), XmlScanner {

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != TAG_RESTRICTIONS) {
            val name = context.file.name
            if (name == "restrictions.xml" || name.startsWith("app_restrictions")) {
                context.report(
                    ISSUE,
                    root,
                    context.getNameLocation(root),
                    "The root element of a restrictions file must be <restrictions>"
                )
            }
            return
        }

        val keys = mutableSetOf<String>()
        val childNodes = root.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                if (child.tagName != TAG_RESTRICTION) {
                    context.report(
                        ISSUE,
                        child,
                        context.getNameLocation(child),
                        "Only <restriction> elements are allowed inside <restrictions>"
                    )
                } else {
                    validateRestriction(context, child, keys)
                }
            }
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element, keys: MutableSet<String>) {
        val keyAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_KEY)
        if (keyAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:key` attribute"
            )
        } else {
            val key = keyAttr.value
            if (!keys.add(key)) {
                context.report(
                    ISSUE,
                    keyAttr,
                    context.getLocation(keyAttr),
                    "Duplicate key `$key`"
                )
            }
        }

        val typeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
        if (typeAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:restrictionType` attribute"
            )
            return
        }

        val type = typeAttr.value
        if (!VALID_TYPES.contains(type)) {
            context.report(
                ISSUE,
                typeAttr,
                context.getValueLocation(typeAttr),
                "Invalid restrictionType `$type`"
            )
            return
        }

        if (type != "hidden") {
            val titleAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_TITLE)
            if (titleAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `android:title` attribute"
                )
            }
        }

        if (type == "choice" || type == "multi-select") {
            val entriesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ENTRIES)
            if (entriesAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `android:entries` attribute for `$type` restrictions"
                )
            }
            val entryValuesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ENTRY_VALUES)
            if (entryValuesAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `android:entryValues` attribute for `$type` restrictions"
                )
            }
        } else {
            val entriesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ENTRIES)
            if (entriesAttr != null) {
                context.report(
                    ISSUE,
                    entriesAttr,
                    context.getLocation(entriesAttr),
                    "Restriction type `$type` cannot have `android:entries`"
                )
            }
            val entryValuesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ENTRY_VALUES)
            if (entryValuesAttr != null) {
                context.report(
                    ISSUE,
                    entryValuesAttr,
                    context.getLocation(entryValuesAttr),
                    "Restriction type `$type` cannot have `android:entryValues`"
                )
            }
        }

        val isBundle = type == "bundle" || type == "bundle-array"
        if (isBundle) {
            val defaultValueAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_DEFAULT_VALUE)
            if (defaultValueAttr != null) {
                context.report(
                    ISSUE,
                    defaultValueAttr,
                    context.getLocation(defaultValueAttr),
                    "Restriction type `$type` cannot have `android:defaultValue`"
                )
            }

            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child is Element) {
                    if (child.tagName != TAG_RESTRICTION) {
                        context.report(
                            ISSUE,
                            child,
                            context.getNameLocation(child),
                            "Only <restriction> elements are allowed inside `<restriction>`"
                        )
                    } else {
                        validateRestriction(context, child, keys)
                    }
                }
            }
        } else {
            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child is Element) {
                    if (child.tagName == TAG_RESTRICTION) {
                        context.report(
                            ISSUE,
                            child,
                            context.getNameLocation(child),
                            "Nested restrictions are only allowed for bundle and bundle-array types"
                        )
                    } else {
                        context.report(
                            ISSUE,
                            child,
                            context.getNameLocation(child),
                            "Only bundle and bundle-array restrictions can have nested elements"
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val ATTR_KEY = "key"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_TITLE = "title"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"

        private val VALID_TYPES = setOf(
            "bool", "integer", "string", "choice", "multi-select",
            "hidden", "integer-array", "bundle", "bundle-array"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an applications restrictions XML file is properly formed",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = Implementation(RestrictionsDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}