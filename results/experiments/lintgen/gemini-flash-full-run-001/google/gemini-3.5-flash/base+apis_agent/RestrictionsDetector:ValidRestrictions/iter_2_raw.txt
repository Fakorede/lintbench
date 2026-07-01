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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("restrictions")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element == element.ownerDocument.documentElement) {
            validateRestrictions(context, element)
        }
    }

    private class ValidationState {
        var nestedCount = 0
    }

    private fun validateRestrictions(context: XmlContext, root: Element) {
        val state = ValidationState()
        val childNodes = root.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                if (child.tagName == "restriction") {
                    validate(context, child, 1, state)
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

    private fun validate(context: XmlContext, element: Element, depth: Int, state: ValidationState) {
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
            state.nestedCount++
            if (state.nestedCount > MAX_NUMBER_OF_NESTED_RESTRICTIONS) {
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
        } else {
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

        if (type == "choice" || type == "multi-select") {
            val entriesAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "entries")
            val entryValuesAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "entryValues")
            if (entriesAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `android:entries` attribute for choice/multi-select restriction"
                )
            }
            if (entryValuesAttr == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `android:entryValues` attribute for choice/multi-select restriction"
                )
            }
        }

        val defaultValueAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "defaultValue")
        if (defaultValueAttr != null && type != null) {
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

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                if (child.tagName == "restriction") {
                    if (type != "bundle" && type != "bundle_array") {
                        context.report(
                            ISSUE,
                            child,
                            context.getNameLocation(child),
                            "Nested restrictions are only supported for `bundle` and `bundle_array` types"
                        )
                    } else {
                        validate(context, child, depth + 1, state)
                    }
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