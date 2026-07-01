package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class RestrictionsDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("restrictions")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == "restrictions") {
            if (element.ownerDocument.documentElement == element) {
                validateRestrictions(context, element)
            }
        }
    }

    private fun validateRestrictions(context: XmlContext, restrictionsElement: Element) {
        val rootChildren = restrictionsElement.childNodes
        val keys = mutableSetOf<String>()
        for (i in 0 until rootChildren.length) {
            val child = rootChildren.item(i)
            if (child is Element) {
                if (child.tagName != "restriction") {
                    context.report(
                        ISSUE,
                        child,
                        context.getNameLocation(child),
                        "Only `<restriction>` elements are allowed under `<restrictions>`"
                    )
                } else {
                    validateRestriction(context, child, keys)
                }
            }
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element, keys: MutableSet<String>) {
        val keyAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "key")
        if (keyAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:key` attribute"
            )
        } else {
            val keyValue = keyAttr.value
            if (!keys.add(keyValue)) {
                context.report(
                    ISSUE,
                    keyAttr,
                    context.getLocation(keyAttr),
                    "Duplicate key `$keyValue`"
                )
            }
        }

        val typeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "restrictionType")
        val type = typeAttr?.value
        if (typeAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:restrictionType` attribute"
            )
        } else {
            val validTypes = setOf(
                "bool", "integer", "string", "select-one", "select-multiple", "hidden", "bundle", "bundle-array"
            )
            if (type !in validTypes) {
                context.report(
                    ISSUE,
                    typeAttr,
                    context.getLocation(typeAttr),
                    "Invalid restriction type `$type`"
                )
            }
        }

        val titleAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "title")
        if (titleAttr == null && type != "hidden") {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:title` attribute"
            )
        }

        val entriesAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "entries")
        val entryValuesAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "entryValues")
        if (type == "select-one" || type == "select-multiple") {
            if (entriesAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `android:entries` attribute"
                )
            }
            if (entryValuesAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `android:entryValues` attribute"
                )
            }
        } else {
            if (entriesAttr != null) {
                context.report(
                    ISSUE,
                    entriesAttr,
                    context.getLocation(entriesAttr),
                    "Attributes `android:entries` and `android:entryValues` are only supported for `select-one` and `select-multiple` types"
                )
            }
            if (entryValuesAttr != null) {
                context.report(
                    ISSUE,
                    entryValuesAttr,
                    context.getLocation(entryValuesAttr),
                    "Attributes `android:entries` and `android:entryValues` are only supported for `select-one` and `select-multiple` types"
                )
            }
        }

        val defaultValueAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "defaultValue")
        if (defaultValueAttr != null) {
            if (type == "bundle" || type == "bundle-array") {
                context.report(
                    ISSUE,
                    defaultValueAttr,
                    context.getLocation(defaultValueAttr),
                    "Default values are not supported for type `$type`"
                )
            } else if (type == "bool") {
                val value = defaultValueAttr.value
                if (value != "true" && value != "false" && !value.startsWith("@")) {
                    context.report(
                        ISSUE,
                        defaultValueAttr,
                        context.getLocation(defaultValueAttr),
                        "Invalid default value `$value` for type `bool`"
                    )
                }
            } else if (type == "integer") {
                val value = defaultValueAttr.value
                if (value.toIntOrNull() == null && !value.startsWith("@")) {
                    context.report(
                        ISSUE,
                        defaultValueAttr,
                        context.getLocation(defaultValueAttr),
                        "Invalid default value `$value` for type `integer`"
                    )
                }
            }
        }

        val childNodes = element.childNodes
        val nestedKeys = mutableSetOf<String>()
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                if (child.tagName == "restriction") {
                    if (type != "bundle" && type != "bundle-array") {
                        context.report(
                            ISSUE,
                            child,
                            context.getNameLocation(child),
                            "Nested restrictions are only supported for `bundle` and `bundle-array` types"
                        )
                    }
                    validateRestriction(context, child, nestedKeys)
                } else {
                    context.report(
                        ISSUE,
                        child,
                        context.getNameLocation(child),
                        "Only `<restriction>` elements are allowed"
                    )
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