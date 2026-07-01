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

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

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
                
                The root element must be `<restrictions>`. Only `<restriction>` elements are allowed \
                under `<restrictions>`.
                
                Each `<restriction>` element must have a `key`, `restrictionType`, and `title`. \
                The `restrictionType` must be one of `bool`, `integer`, `string`, `choice`, `multi-select`, \
                `bundle`, or `bundle_array`.
                
                For `choice` and `multi-select` types, `entries` and `entryValues` are required. \
                For `bundle` and `bundle_array` types, nested `<restriction>` elements are allowed, but `defaultValue` is not.
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
        val rootName = root.localName ?: root.tagName
        if (rootName != "restrictions") {
            return
        }
        validateElement(context, root, isRoot = true, siblingKeys = mutableSetOf())
    }

    private fun validateElement(
        context: XmlContext,
        element: org.w3c.dom.Element,
        isRoot: Boolean,
        siblingKeys: MutableSet<String>
    ) {
        if (isRoot) {
            val childNodes = element.childNodes
            val childKeys = mutableSetOf<String>()
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child is org.w3c.dom.Element) {
                    val tagName = child.localName ?: child.tagName
                    if (tagName != "restriction") {
                        context.report(
                            ISSUE,
                            child,
                            context.getNameLocation(child),
                            "Only `<restriction>` elements are allowed under `<restrictions>`"
                        )
                    } else {
                        validateElement(context, child, false, childKeys)
                    }
                }
            }
        } else {
            val keyAttr = getAttributeNode(element, "key")
            val key = keyAttr?.value
            if (key.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `key` attribute on `<restriction>`"
                )
            } else {
                if (!siblingKeys.add(key)) {
                    context.report(
                        ISSUE,
                        keyAttr,
                        context.getLocation(keyAttr),
                        "Duplicate key `$key`"
                    )
                }
            }

            val typeAttr = getAttributeNode(element, "restrictionType")
            val type = typeAttr?.value
            if (type.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `restrictionType` attribute on `<restriction>`"
                )
            } else {
                val validTypes = setOf("bool", "integer", "string", "choice", "multi-select", "bundle", "bundle_array")
                if (type !in validTypes) {
                    context.report(
                        ISSUE,
                        typeAttr,
                        context.getLocation(typeAttr),
                        "Invalid restrictionType `$type`"
                    )
                }
            }

            val titleAttr = getAttributeNode(element, "title")
            if (titleAttr == null || titleAttr.value.isEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `title` attribute on `<restriction>`"
                )
            }

            if (type != null) {
                when (type) {
                    "choice", "multi-select" -> {
                        val entriesAttr = getAttributeNode(element, "entries")
                        if (entriesAttr == null || entriesAttr.value.isEmpty()) {
                            context.report(
                                ISSUE,
                                element,
                                context.getNameLocation(element),
                                "Missing `entries` attribute for choice/multi-select restriction"
                            )
                        }
                        val entryValuesAttr = getAttributeNode(element, "entryValues")
                        if (entryValuesAttr == null || entryValuesAttr.value.isEmpty()) {
                            context.report(
                                ISSUE,
                                element,
                                context.getNameLocation(element),
                                "Missing `entryValues` attribute for choice/multi-select restriction"
                            )
                        }
                    }
                    "bundle", "bundle_array" -> {
                        val defaultValueAttr = getAttributeNode(element, "defaultValue")
                        if (defaultValueAttr != null) {
                            context.report(
                                ISSUE,
                                defaultValueAttr,
                                context.getLocation(defaultValueAttr),
                                "Attributes `defaultValue` and `restrictionType=\"$type\"` are mutually exclusive"
                            )
                        }

                        val childNodes = element.childNodes
                        val nestedKeys = mutableSetOf<String>()
                        for (i in 0 until childNodes.length) {
                            val child = childNodes.item(i)
                            if (child is org.w3c.dom.Element) {
                                val tagName = child.localName ?: child.tagName
                                if (tagName != "restriction") {
                                    context.report(
                                        ISSUE,
                                        child,
                                        context.getNameLocation(child),
                                        "Only `<restriction>` elements are allowed under `<restriction>`"
                                    )
                                } else {
                                    validateElement(context, child, false, nestedKeys)
                                }
                            }
                        }
                    }
                    else -> {
                        val childNodes = element.childNodes
                        for (i in 0 until childNodes.length) {
                            val child = childNodes.item(i)
                            if (child is org.w3c.dom.Element) {
                                context.report(
                                    ISSUE,
                                    child,
                                    context.getNameLocation(child),
                                    "Restriction of type `$type` cannot have nested restrictions"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getAttributeNode(element: org.w3c.dom.Element, name: String): org.w3c.dom.Attr? {
        return element.getAttributeNodeNS(ANDROID_URI, name) ?: element.getAttributeNode(name)
    }
}