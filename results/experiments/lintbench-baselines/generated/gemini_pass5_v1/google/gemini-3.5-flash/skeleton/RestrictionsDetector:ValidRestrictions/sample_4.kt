package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
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
                Applications that support enterprise restrictions must provide a restrictions \
                descriptor XML file. This file must be properly formed, with a root `<restrictions>` \
                element containing valid `<restriction>` elements with correct attributes and types.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != "restrictions") {
            return
        }

        val keys = HashSet<String>()
        validateElement(context, root, keys)
    }

    private fun validateElement(context: XmlContext, element: Element, keys: HashSet<String>) {
        val tagName = element.tagName
        if (tagName == "restrictions") {
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val childElement = child as Element
                    if (childElement.tagName != "restriction") {
                        context.report(
                            ISSUE,
                            childElement,
                            context.getNameLocation(childElement),
                            "Only `<restriction>` elements are allowed under `<restrictions>`"
                        )
                    } else {
                        validateRestriction(context, childElement, keys, isRoot = true)
                    }
                }
            }
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element, keys: HashSet<String>, isRoot: Boolean) {
        val androidUri = "http://schemas.android.com/apk/res/android"

        // 1. Check android:key
        val keyAttr = element.getAttributeNodeNS(androidUri, "key")
        if (keyAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:key` attribute"
            )
        } else {
            val key = keyAttr.value
            if (key.isEmpty()) {
                context.report(
                    ISSUE,
                    keyAttr,
                    context.getLocation(keyAttr),
                    "Key cannot be empty"
                )
            } else if (keys.contains(key)) {
                context.report(
                    ISSUE,
                    keyAttr,
                    context.getLocation(keyAttr),
                    "Duplicate key `$key`"
                )
            } else {
                keys.add(key)
            }
        }

        // 2. Check android:title (required if at root)
        if (isRoot) {
            val titleAttr = element.getAttributeNodeNS(androidUri, "title")
            if (titleAttr == null || titleAttr.value.isEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `android:title` attribute"
                )
            }
        }

        // 3. Check android:restrictionType
        val typeAttr = element.getAttributeNodeNS(androidUri, "restrictionType")
        if (typeAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:restrictionType` attribute"
            )
        } else {
            val type = typeAttr.value
            when (type) {
                "bool", "integer", "string" -> {
                    checkForNoNestedRestrictions(context, element)
                }
                "choice", "multi-select" -> {
                    val entriesAttr = element.getAttributeNodeNS(androidUri, "entries")
                    if (entriesAttr == null || entriesAttr.value.isEmpty()) {
                        context.report(
                            ISSUE,
                            element,
                            context.getNameLocation(element),
                            "Missing `android:entries` attribute for choice/multi-select restriction"
                        )
                    }
                    val entryValuesAttr = element.getAttributeNodeNS(androidUri, "entryValues")
                    if (entryValuesAttr == null || entryValuesAttr.value.isEmpty()) {
                        context.report(
                            ISSUE,
                            element,
                            context.getNameLocation(element),
                            "Missing `android:entryValues` attribute for choice/multi-select restriction"
                        )
                    }
                    checkForNoNestedRestrictions(context, element)
                }
                "bundle", "bundle_array" -> {
                    if (element.hasAttributeNS(androidUri, "defaultValue")) {
                        context.report(
                            ISSUE,
                            element,
                            context.getNameLocation(element),
                            "Attributes `android:defaultValue` cannot be used on bundle or bundle_array restrictions"
                        )
                    }

                    var hasRestrictionChild = false
                    val children = element.childNodes
                    for (i in 0 until children.length) {
                        val child = children.item(i)
                        if (child.nodeType == Node.ELEMENT_NODE) {
                            val childElement = child as Element
                            if (childElement.tagName == "restriction") {
                                hasRestrictionChild = true
                                validateRestriction(context, childElement, keys, isRoot = false)
                            } else {
                                context.report(
                                    ISSUE,
                                    childElement,
                                    context.getNameLocation(childElement),
                                    "Only `<restriction>` elements are allowed under `<restriction>`"
                                )
                            }
                        }
                    }
                    if (!hasRestrictionChild) {
                        context.report(
                            ISSUE,
                            element,
                            context.getNameLocation(element),
                            "Bundle restrictions must have at least one nested restriction"
                        )
                    }
                }
                else -> {
                    context.report(
                        ISSUE,
                        typeAttr,
                        context.getLocation(typeAttr),
                        "Invalid restriction type `$type`"
                    )
                }
            }
        }
    }

    private fun checkForNoNestedRestrictions(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (childElement.tagName == "restriction") {
                    context.report(
                        ISSUE,
                        childElement,
                        context.getNameLocation(childElement),
                        "Nested restrictions are only allowed for bundle and bundle_array types"
                    )
                } else {
                    context.report(
                        ISSUE,
                        childElement,
                        context.getNameLocation(childElement),
                        "Only `<restriction>` elements are allowed under `<restriction>`"
                    )
                }
            }
        }
    }
}