package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("restrictions", "restriction")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        if (tagName == "restrictions") {
            val parent = element.parentNode
            if (parent != null && parent.nodeType == Node.ELEMENT_NODE) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "The `<restrictions>` element must be the root element"
                )
            }
            var child = element.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val childElement = child as Element
                    if (childElement.tagName != "restriction") {
                        context.report(
                            ISSUE,
                            childElement,
                            context.getNameLocation(childElement),
                            "Only `<restriction>` elements are allowed inside `<restrictions>`"
                        )
                    }
                }
                child = child.nextSibling
            }
        } else if (tagName == "restriction") {
            val parent = element.parentNode
            if (parent != null && parent.nodeType == Node.ELEMENT_NODE) {
                val parentElement = parent as Element
                val parentTag = parentElement.tagName
                if (parentTag == "restriction") {
                    val parentType = parentElement.getAttributeNS(SdkConstants.ANDROID_URI, "restrictionType")
                    if (parentType != "bundle" && parentType != "bundle_array") {
                        context.report(
                            ISSUE,
                            element,
                            context.getNameLocation(element),
                            "Nested `<restriction>` elements are only allowed inside `<restriction>` elements of type `bundle` or `bundle_array`"
                        )
                    }
                } else if (parentTag != "restrictions") {
                    context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "The `<restriction>` element must be inside `<restrictions>` or another `<restriction>`"
                    )
                }
            }

            val keyAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "key")
            if (keyAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing required attribute `android:key`"
                )
            }

            val typeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "restrictionType")
            val type = typeAttr?.value
            if (type == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing required attribute `android:restrictionType`"
                )
            } else {
                val validTypes = setOf("bool", "integer", "string", "choice", "multi-select", "hidden", "bundle", "bundle_array")
                if (type !in validTypes) {
                    context.report(
                        ISSUE,
                        typeAttr,
                        context.getValueLocation(typeAttr),
                        "Invalid restriction type `$type`"
                    )
                }
            }

            val titleAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "title")
            if (titleAttr == null && type != "hidden" && type != "bundle" && type != "bundle_array") {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing required attribute `android:title`"
                )
            }

            if (type == "choice" || type == "multi-select") {
                val entriesAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "entries")
                val entryValuesAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "entryValues")
                if (entriesAttr == null) {
                    context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "Missing required attribute `android:entries` for restriction type `$type`"
                    )
                }
                if (entryValuesAttr == null) {
                    context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "Missing required attribute `android:entryValues` for restriction type `$type`"
                    )
                }
            }

            val defaultValueAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "defaultValue")
            if (defaultValueAttr != null) {
                val defaultValue = defaultValueAttr.value
                if (type == "bundle" || type == "bundle_array") {
                    context.report(
                        ISSUE,
                        defaultValueAttr,
                        context.getValueLocation(defaultValueAttr),
                        "Restriction type `$type` cannot have a default value"
                    )
                } else if (type == "bool") {
                    if (defaultValue != "true" && defaultValue != "false" && !defaultValue.startsWith("@")) {
                        context.report(
                            ISSUE,
                            defaultValueAttr,
                            context.getValueLocation(defaultValueAttr),
                            "Invalid default value `$defaultValue` for boolean restriction"
                        )
                    }
                } else if (type == "integer") {
                    if (defaultValue.toIntOrNull() == null && !defaultValue.startsWith("@")) {
                        context.report(
                            ISSUE,
                            defaultValueAttr,
                            context.getValueLocation(defaultValueAttr),
                            "Invalid default value `$defaultValue` for integer restriction"
                        )
                    }
                }
            }

            if (type != "bundle" && type != "bundle_array") {
                var child = element.firstChild
                while (child != null) {
                    if (child.nodeType == Node.ELEMENT_NODE) {
                        val childElement = child as Element
                        if (childElement.tagName == "restriction") {
                            context.report(
                                ISSUE,
                                childElement,
                                context.getNameLocation(childElement),
                                "Nested `<restriction>` elements are only allowed inside `<restriction>` elements of type `bundle` or `bundle_array`"
                            )
                        }
                    }
                    child = child.nextSibling
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed.
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