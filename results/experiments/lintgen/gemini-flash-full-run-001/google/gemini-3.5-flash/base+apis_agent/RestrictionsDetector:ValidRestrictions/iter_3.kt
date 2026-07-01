package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class RestrictionsDetector : Detector(), XmlScanner {

    private var nestedCount = 0

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("restrictions")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element == element.ownerDocument.documentElement) {
            nestedCount = 0
            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child is Element) {
                    if (child.tagName == "restriction") {
                        validateRestriction(context, child, 1)
                    } else {
                        context.report(
                            ISSUE,
                            child,
                            context.getNameLocation(child),
                            "Only `<restriction>` elements are allowed under `<restrictions>`"
                        )
                    }
                }
            }
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element, depth: Int) {
        if (depth > MAX_NESTING_DEPTH) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Restrictions cannot be nested more than $MAX_NESTING_DEPTH levels deep"
            )
            return
        }
        if (depth > 1) {
            nestedCount++
            if (nestedCount > MAX_NUMBER_OF_NESTED_RESTRICTIONS) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Too many nested restrictions (max $MAX_NUMBER_OF_NESTED_RESTRICTIONS)"
                )
                return
            }
        }

        val keyAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "key")
        if (keyAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:key` attribute"
            )
        }

        val typeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "restrictionType")
        val type = typeAttr?.value
        if (type == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:restrictionType` attribute"
            )
            return
        }

        val validTypes = setOf(
            "string", "bool", "integer", "choice", "multi-select",
            "hidden", "bundle", "bundle_array"
        )
        if (type !in validTypes) {
            context.report(
                ISSUE,
                typeAttr,
                context.getLocation(typeAttr),
                "Invalid restriction type `$type`"
            )
            return
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

        if (type == "choice" || type == "multi-select") {
            val entriesAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "entries")
            if (entriesAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `android:entries` attribute"
                )
            }
            val entryValuesAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "entryValues")
            if (entryValuesAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `android:entryValues` attribute"
                )
            }
        }

        val defaultValueAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "defaultValue")
        if (defaultValueAttr != null) {
            if (type == "bundle" || type == "bundle_array") {
                context.report(
                    ISSUE,
                    defaultValueAttr,
                    context.getLocation(defaultValueAttr),
                    "Restriction type `$type` cannot have a default value"
                )
            } else {
                val value = defaultValueAttr.value
                if (type == "bool") {
                    if (value != "true" && value != "false") {
                        context.report(
                            ISSUE,
                            defaultValueAttr,
                            context.getLocation(defaultValueAttr),
                            "Invalid default value `$value` for boolean restriction"
                        )
                    }
                } else if (type == "integer") {
                    try {
                        value.toInt()
                    } catch (e: NumberFormatException) {
                        context.report(
                            ISSUE,
                            defaultValueAttr,
                            context.getLocation(defaultValueAttr),
                            "Invalid default value `$value` for integer restriction"
                        )
                    }
                }
            }
        } else {
            if (type == "hidden") {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Restriction type `hidden` must have a default value"
                )
            }
        }

        var hasChildren = false
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val childName = child.tagName
                if (childName == "restriction") {
                    hasChildren = true
                    if (type != "bundle" && type != "bundle_array") {
                        context.report(
                            ISSUE,
                            child,
                            context.getNameLocation(child),
                            "Restriction type `$type` cannot have nested restrictions"
                        )
                    } else if (type == "bundle_array") {
                        val childType = child.getAttributeNS(SdkConstants.ANDROID_URI, "restrictionType")
                        if (childType != "bundle") {
                            context.report(
                                ISSUE,
                                child,
                                context.getNameLocation(child),
                                "Restriction type `bundle_array` can only have nested restrictions of type `bundle`"
                            )
                        }
                    }
                    validateRestriction(context, child, depth + 1)
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

        if ((type == "bundle" || type == "bundle_array") && !hasChildren) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Restriction type `$type` must have at least one nested restriction"
            )
        }
    }

    companion object {
        const val MAX_NESTING_DEPTH = 20
        const val MAX_NUMBER_OF_NESTED_RESTRICTIONS = 1000

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