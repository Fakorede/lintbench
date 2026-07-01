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

        val keys = mutableSetOf<String>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                validateRestriction(context, child as org.w3c.dom.Element, keys)
            }
        }
    }

    private fun validateRestriction(context: XmlContext, element: org.w3c.dom.Element, keys: MutableSet<String>) {
        val tagName = element.tagName
        if (tagName != "restriction") {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Expected `<restriction>` tag, found `<$tagName>`"
            )
            return
        }

        val androidUri = "http://schemas.android.com/apk/res/android"
        val keyAttr = element.getAttributeNodeNS(androidUri, "key")
        val key = keyAttr?.value
        if (key.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Every restriction must have a key"
            )
        } else {
            if (!keys.add(key)) {
                context.report(
                    ISSUE,
                    keyAttr,
                    context.getValueLocation(keyAttr),
                    "Duplicate restriction key: $key"
                )
            }
        }

        val typeAttr = element.getAttributeNodeNS(androidUri, "restrictionType")
        val type = typeAttr?.value
        if (type.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Every restriction must have a restrictionType"
            )
        } else {
            when (type) {
                "bool", "string", "integer", "choice", "multi-select", "hidden", "bundle", "bundle_array" -> {
                    // Valid type
                }
                else -> {
                    context.report(
                        ISSUE,
                        typeAttr,
                        context.getValueLocation(typeAttr),
                        "Invalid restrictionType: $type"
                    )
                }
            }
        }

        val titleAttr = element.getAttributeNodeNS(androidUri, "title")
        val title = titleAttr?.value
        if (title.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Every restriction must have a title"
            )
        }

        val entriesAttr = element.getAttributeNodeNS(androidUri, "entries")
        val entryValuesAttr = element.getAttributeNodeNS(androidUri, "entryValues")

        if (type == "choice" || type == "multi-select") {
            if (entriesAttr == null || entryValuesAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Restrictions of type \"choice\"/\"multi-select\" must specify entries and entryValues"
                )
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                val childElement = child as org.w3c.dom.Element
                if (type != "bundle" && type != "bundle_array") {
                    context.report(
                        ISSUE,
                        childElement,
                        context.getNameLocation(childElement),
                        "Only restrictions of type \"bundle\" or \"bundle_array\" can have nested restrictions"
                    )
                } else {
                    validateRestriction(context, childElement, keys)
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an application's restrictions XML file is properly formed.",
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