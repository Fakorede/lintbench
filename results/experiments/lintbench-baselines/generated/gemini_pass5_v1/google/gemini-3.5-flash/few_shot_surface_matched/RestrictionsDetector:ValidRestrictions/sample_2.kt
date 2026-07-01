package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class RestrictionsDetector : ResourceXmlDetector(), XmlScanner {

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        val root = document.documentElement ?: return
        if (root.tagName != "restrictions") {
            return
        }
        val children = root.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                val childElement = child as org.w3c.dom.Element
                if (childElement.tagName != "restriction") {
                    context.report(
                        ISSUE,
                        childElement,
                        context.getNameLocation(childElement),
                        "Expected `<restriction>` tag, found `<${childElement.tagName}>` instead"
                    )
                } else {
                    validateRestriction(context, childElement)
                }
            }
        }
    }

    private fun validateRestriction(context: XmlContext, element: org.w3c.dom.Element) {
        val keyAttr = element.getAttributeNodeNS(com.android.SdkConstants.ANDROID_URI, com.android.SdkConstants.ATTR_KEY)
        if (keyAttr == null || keyAttr.value.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Every `<restriction>` must have an `android:key` attribute"
            )
        }

        val typeAttr = element.getAttributeNodeNS(com.android.SdkConstants.ANDROID_URI, "restrictionType")
        val type = typeAttr?.value
        if (type.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Every `<restriction>` must have an `android:restrictionType` attribute"
            )
        } else {
            when (type) {
                "bool", "integer", "string", "choice", "multi-select", "hidden", "bundle", "bundle_array" -> {
                    // valid
                }
                else -> {
                    context.report(
                        ISSUE,
                        typeAttr,
                        context.getValueLocation(typeAttr),
                        "Invalid restrictionType `$type`"
                    )
                }
            }
        }

        val titleAttr = element.getAttributeNodeNS(com.android.SdkConstants.ANDROID_URI, com.android.SdkConstants.ATTR_TITLE)
        if (type != "hidden" && (titleAttr == null || titleAttr.value.isEmpty())) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Every `<restriction>` must have an `android:title` attribute (unless type is `hidden`)"
            )
        }

        val entriesAttr = element.getAttributeNodeNS(com.android.SdkConstants.ANDROID_URI, "entries")
        val entryValuesAttr = element.getAttributeNodeNS(com.android.SdkConstants.ANDROID_URI, "entryValues")

        if (type == "choice" || type == "multi-select") {
            if (entriesAttr == null || entriesAttr.value.isEmpty() || entryValuesAttr == null || entryValuesAttr.value.isEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Restrictions of type `choice` and `multi-select` must specify both `android:entries` and `android:entryValues` attributes"
                )
            }
        }

        if (entriesAttr != null && entriesAttr.value.isNotEmpty()) {
            if (!entriesAttr.value.startsWith("@array/")) {
                context.report(
                    ISSUE,
                    entriesAttr,
                    context.getValueLocation(entriesAttr),
                    "`android:entries` must reference an array resource"
                )
            }
        }

        if (entryValuesAttr != null && entryValuesAttr.value.isNotEmpty()) {
            if (!entryValuesAttr.value.startsWith("@array/")) {
                context.report(
                    ISSUE,
                    entryValuesAttr,
                    context.getValueLocation(entryValuesAttr),
                    "`android:entryValues` must reference an array resource"
                )
            }
        }

        val defaultAttr = element.getAttributeNodeNS(com.android.SdkConstants.ANDROID_URI, "defaultValue")
        if (defaultAttr != null && defaultAttr.value.isNotEmpty()) {
            if (type == "multi-select") {
                if (!defaultAttr.value.startsWith("@array/")) {
                    context.report(
                        ISSUE,
                        defaultAttr,
                        context.getValueLocation(defaultAttr),
                        "Default value for multi-select restriction must be an array resource"
                    )
                }
            } else if (type == "bundle" || type == "bundle_array") {
                context.report(
                    ISSUE,
                    defaultAttr,
                    context.getValueLocation(defaultAttr),
                    "Restrictions of type `bundle` or `bundle_array` cannot have a default value"
                )
            }
        }

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                val childElement = child as org.w3c.dom.Element
                if (type != "bundle" && type != "bundle_array") {
                    context.report(
                        ISSUE,
                        childElement,
                        context.getNameLocation(childElement),
                        "Only restrictions of type `bundle` or `bundle_array` can have nested restrictions"
                    )
                }
                if (childElement.tagName != "restriction") {
                    context.report(
                        ISSUE,
                        childElement,
                        context.getNameLocation(childElement),
                        "Expected `<restriction>` tag, found `<${childElement.tagName}>` instead"
                    )
                } else {
                    validateRestriction(context, childElement)
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an applications restrictions XML file is properly formed",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}