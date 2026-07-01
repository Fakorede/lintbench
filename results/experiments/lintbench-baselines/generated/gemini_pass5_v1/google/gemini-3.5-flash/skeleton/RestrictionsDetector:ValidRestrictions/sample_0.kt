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
                Ensures that an application's restrictions XML file is properly formed.
                
                Restrictions XML files must have `<restrictions>` as the root element, which can only contain `<restriction>` elements.
                Each `<restriction>` element must have `android:key`, `android:title`, and a valid `android:restrictionType`.
                
                - `choice` and `multi-select` types require `android:entries` and `android:entryValues`.
                - `bundle` and `bundle_array` types cannot have a default value, but can contain nested `<restriction>` elements.
                - Other types cannot contain nested `<restriction>` elements.
                - Keys must be unique.
            """.trimIndent(),
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

        val keys = mutableSetOf<String>()
        checkRestrictions(context, root, keys)
    }

    private fun checkRestrictions(context: XmlContext, element: Element, keys: MutableSet<String>) {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val child = node as Element
                if (child.tagName != "restriction") {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "Only `<restriction>` elements are allowed under `<restrictions>` or nested restriction bundles"
                    )
                    continue
                }
                checkRestrictionElement(context, child, keys)
            }
        }
    }

    private fun checkRestrictionElement(context: XmlContext, element: Element, keys: MutableSet<String>) {
        val namespace = "http://schemas.android.com/apk/res/android"
        
        val hasKey = element.hasAttributeNS(namespace, "key")
        val key = element.getAttributeNS(namespace, "key")
        
        val hasTitle = element.hasAttributeNS(namespace, "title")
        val hasType = element.hasAttributeNS(namespace, "restrictionType")
        val type = element.getAttributeNS(namespace, "restrictionType")

        if (!hasKey) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:key` attribute"
            )
        } else {
            if (!keys.add(key)) {
                context.report(
                    ISSUE,
                    element,
                    context.getAttributeLocation(element, namespace, "key"),
                    "Duplicate key `$key`"
                )
            }
        }

        if (!hasTitle) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:title` attribute"
            )
        }

        if (!hasType) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:restrictionType` attribute"
            )
            checkRestrictions(context, element, keys)
            return
        }

        val validTypes = setOf(
            "boolean", "integer", "string", "choice", "multi-select", "hidden", "bundle", "bundle_array"
        )
        if (type !in validTypes) {
            context.report(
                ISSUE,
                element,
                context.getAttributeLocation(element, namespace, "restrictionType"),
                "Invalid `android:restrictionType` `$type`"
            )
            return
        }

        val hasDefaultValue = element.hasAttributeNS(namespace, "defaultValue")
        val hasEntries = element.hasAttributeNS(namespace, "entries")
        val hasEntryValues = element.hasAttributeNS(namespace, "entryValues")

        if (type == "bundle" || type == "bundle_array") {
            if (hasDefaultValue) {
                context.report(
                    ISSUE,
                    element,
                    context.getAttributeLocation(element, namespace, "defaultValue"),
                    "Restriction type `$type` cannot have a default value"
                )
            }
            checkRestrictions(context, element, keys)
        } else {
            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                val node = childNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val child = node as Element
                    if (child.tagName == "restriction") {
                        context.report(
                            ISSUE,
                            child,
                            context.getLocation(child),
                            "Nested restrictions are only allowed for `bundle` or `bundle_array` types"
                        )
                    }
                }
            }
        }

        if (type == "choice" || type == "multi-select") {
            if (!hasEntries) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Restriction type `$type` requires an `android:entries` attribute"
                )
            }
            if (!hasEntryValues) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Restriction type `$type` requires an `android:entryValues` attribute"
                )
            }
        } else {
            if (hasEntries) {
                context.report(
                    ISSUE,
                    element,
                    context.getAttributeLocation(element, namespace, "entries"),
                    "Restriction type `$type` does not support `android:entries`"
                )
            }
            if (hasEntryValues) {
                context.report(
                    ISSUE,
                    element,
                    context.getAttributeLocation(element, namespace, "entryValues"),
                    "Restriction type `$type` does not support `android:entryValues`"
                )
            }
        }

        if (hasDefaultValue) {
            val defaultValue = element.getAttributeNS(namespace, "defaultValue")
            if (type == "integer") {
                if (defaultValue.toIntOrNull() == null) {
                    context.report(
                        ISSUE,
                        element,
                        context.getAttributeLocation(element, namespace, "defaultValue"),
                        "Invalid default value `$defaultValue` for integer restriction"
                    )
                }
            } else if (type == "boolean") {
                if (defaultValue != "true" && defaultValue != "false") {
                    context.report(
                        ISSUE,
                        element,
                        context.getAttributeLocation(element, namespace, "defaultValue"),
                        "Invalid default value `$defaultValue` for boolean restriction"
                    )
                }
            }
        }
    }
}