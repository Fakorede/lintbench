package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an application's restrictions XML file is properly formed according to the RestrictionsManager specification. " +
                    "The root element must be <restrictions>, containing <restriction> children with required attributes like android:key, " +
                    "android:title, and android:restrictionType.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_TYPE = "restrictionType"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"

        private val VALID_TYPES = setOf(
            "bool", "string", "integer", "choice", "multi-select", "hidden", "bundle", "bundle_array"
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_RESTRICTIONS, TAG_RESTRICTION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_RESTRICTIONS -> validateRoot(context, element)
            TAG_RESTRICTION -> validateRestriction(context, element)
        }
    }

    private fun validateRoot(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName != TAG_RESTRICTION) {
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "Invalid child element `<${child.tagName}>`. Only `<$TAG_RESTRICTION>` is allowed inside `<$TAG_RESTRICTIONS>`."
                )
            }
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element) {
        if (!element.hasAttributeNS(ANDROID_URI, ATTR_KEY)) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `android:$ATTR_KEY`"
            )
        }

        if (!element.hasAttributeNS(ANDROID_URI, ATTR_TITLE)) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `android:$ATTR_TITLE`"
            )
        }

        val type = element.getAttributeNS(ANDROID_URI, ATTR_TYPE)
        if (type.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `android:$ATTR_TYPE`"
            )
        } else if (type !in VALID_TYPES) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Invalid `android:$ATTR_TYPE` value: `$type`. Must be one of: ${VALID_TYPES.joinToString(", ")}"
            )
        } else if (type == "choice" || type == "multi-select") {
            if (!element.hasAttributeNS(ANDROID_URI, ATTR_ENTRIES)) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:$ATTR_ENTRIES` for restriction type `$type`"
                )
            }
            if (!element.hasAttributeNS(ANDROID_URI, ATTR_ENTRY_VALUES)) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:$ATTR_ENTRY_VALUES` for restriction type `$type`"
                )
            }
        }
    }
}