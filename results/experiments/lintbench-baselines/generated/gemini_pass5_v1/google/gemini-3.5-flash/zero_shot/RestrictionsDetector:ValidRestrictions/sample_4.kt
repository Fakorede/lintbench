package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_KEY
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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != "restrictions") {
            return
        }
        validateRestrictions(context, root)
    }

    private fun validateRestrictions(context: XmlContext, restrictionsElement: Element) {
        val childNodes = restrictionsElement.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (childElement.tagName != "restriction") {
                    context.report(
                        ISSUE,
                        childElement,
                        context.getNameLocation(childElement),
                        "Only `<restriction>` elements are allowed here"
                    )
                } else {
                    validateRestriction(context, childElement)
                }
            }
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element) {
        val keyAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_KEY)
        if (keyAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:key` attribute"
            )
        }

        val titleAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_TITLE)
        if (titleAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:title` attribute"
            )
        } else {
            val titleValue = titleAttr.value
            if (!titleValue.startsWith("@")) {
                context.report(
                    ISSUE,
                    titleAttr,
                    context.getValueLocation(titleAttr),
                    "`android:title` must be a string resource reference"
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
        val validTypes = setOf("bool", "integer", "string", "choice", "multi-select", "bundle", "bundle_array")
        if (type !in validTypes) {
            context.report(
                ISSUE,
                typeAttr,
                context.getValueLocation(typeAttr),
                "Invalid restriction type `$type`"
            )
            return
        }

        val descAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_DESCRIPTION)
        if (descAttr != null && !descAttr.value.startsWith("@")) {
            context.report(
                ISSUE,
                descAttr,
                context.getValueLocation(descAttr),
                "`android:description` must be a string resource reference"
            )
        }

        val entriesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ENTRIES)
        val entryValuesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ENTRY_VALUES)
        val defaultValueAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_DEFAULT_VALUE)

        when (type) {
            "choice", "multi-select" -> {
                if (entriesAttr == null) {
                    context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "Type `$type` requires `android:entries` attribute"
                    )
                } else if (!entriesAttr.value.startsWith("@")) {
                    context.report(
                        ISSUE,
                        entriesAttr,
                        context.getValueLocation(entriesAttr),
                        "`android:entries` must be an array resource reference"
                    )
                }

                if (entryValuesAttr == null) {
                    context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "Type `$type` requires `android:entryValues` attribute"
                    )
                } else if (!entryValuesAttr.value.startsWith("@")) {
                    context.report(
                        ISSUE,
                        entryValuesAttr,
                        context.getValueLocation(entryValuesAttr),
                        "`android:entryValues` must be an array resource reference"
                    )
                }
            }
            "bundle", "bundle_array" -> {
                if (entriesAttr != null) {
                    context.report(
                        ISSUE,
                        entriesAttr,
                        context.getValueLocation(entriesAttr),
                        "Attributes `android:entries` are not supported for type `$type`"
                    )
                }
                if (entryValuesAttr != null) {
                    context.report(
                        ISSUE,
                        entryValuesAttr,
                        context.getValueLocation(entryValuesAttr),
                        "Attributes `android:entryValues` are not supported for type `$type`"
                    )
                }
                if (defaultValueAttr != null) {
                    context.report(
                        ISSUE,
                        defaultValueAttr,
                        context.getValueLocation(defaultValueAttr),
                        "Attributes `android:defaultValue` are not supported for type `$type`"
                    )
                }

                var hasNestedRestrictions = false
                val childNodes = element.childNodes
                for (i in 0 until childNodes.length) {
                    val child = childNodes.item(i)
                    if (child.nodeType == Node.ELEMENT_NODE) {
                        val childElement = child as Element
                        if (childElement.tagName != "restriction") {
                            context.report(
                                ISSUE,
                                childElement,
                                context.getNameLocation(childElement),
                                "Only `<restriction>` elements are allowed here"
                            )
                        } else {
                            hasNestedRestrictions = true
                            validateRestriction(context, childElement)
                        }
                    }
                }

                if (!hasNestedRestrictions) {
                    context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "Type `$type` requires at least one nested `<restriction>`"
                    )
                }
            }
            "bool" -> {
                if (entriesAttr != null) {
                    context.report(ISSUE, entriesAttr, context.getValueLocation(entriesAttr), "Attributes `android:entries` are not supported for type `$type`")
                }
                if (entryValuesAttr != null) {
                    context.report(ISSUE, entryValuesAttr, context.getValueLocation(entryValuesAttr), "Attributes `android:entryValues` are not supported for type `$type`")
                }
                if (defaultValueAttr != null) {
                    val defaultValue = defaultValueAttr.value
                    if (defaultValue != "true" && defaultValue != "false") {
                        context.report(
                            ISSUE,
                            defaultValueAttr,
                            context.getValueLocation(defaultValueAttr),
                            "Invalid default value `$defaultValue` for type `bool`"
                        )
                    }
                }
                validateNoNestedRestrictions(context, element)
            }
            "integer" -> {
                if (entriesAttr != null) {
                    context.report(ISSUE, entriesAttr, context.getValueLocation(entriesAttr), "Attributes `android:entries` are not supported for type `$type`")
                }
                if (entryValuesAttr != null) {
                    context.report(ISSUE, entryValuesAttr, context.getValueLocation(entryValuesAttr), "Attributes `android:entryValues` are not supported for type `$type`")
                }
                if (defaultValueAttr != null) {
                    val defaultValue = defaultValueAttr.value
                    if (defaultValue.toIntOrNull() == null) {
                        context.report(
                            ISSUE,
                            defaultValueAttr,
                            context.getValueLocation(defaultValueAttr),
                            "Invalid default value `$defaultValue` for type `integer`"
                        )
                    }
                }
                validateNoNestedRestrictions(context, element)
            }
            "string" -> {
                if (entriesAttr != null) {
                    context.report(ISSUE, entriesAttr, context.getValueLocation(entriesAttr), "Attributes `android:entries` are not supported for type `$type`")
                }
                if (entryValuesAttr != null) {
                    context.report(ISSUE, entryValuesAttr, context.getValueLocation(entryValuesAttr), "Attributes `android:entryValues` are not supported for type `$type`")
                }
                validateNoNestedRestrictions(context, element)
            }
        }
    }

    private fun validateNoNestedRestrictions(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                context.report(
                    ISSUE,
                    childElement,
                    context.getNameLocation(childElement),
                    "Nested `<restriction>` elements are not allowed for this type"
                )
            }
        }
    }

    companion object {
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_TITLE = "title"
        private const val ATTR_DESCRIPTION = "description"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"
        private const val ATTR_DEFAULT_VALUE = "defaultValue"

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an applications restrictions XML file is properly formed",
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