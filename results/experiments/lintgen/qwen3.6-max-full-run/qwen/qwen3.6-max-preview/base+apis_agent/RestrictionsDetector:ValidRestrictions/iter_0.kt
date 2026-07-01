package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class RestrictionsDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_RESTRICTIONS)

    override fun visitElement(context: XmlContext, element: Element) {
        val keys = mutableSetOf<String>()
        for (child in element.tagChildren()) {
            if (child.tagName == TAG_RESTRICTION) {
                checkRestriction(context, child, keys)
            } else {
                context.report(
                    ISSUE,
                    context.getLocation(child),
                    "Invalid tag `${child.tagName}`; expected `<restriction>`"
                )
            }
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element, keys: MutableSet<String>) {
        val key = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_KEY)
        val title = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_TITLE)
        val type = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_RESTRICTION_TYPE)

        if (key.isNullOrEmpty()) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:key`")
        } else if (!keys.add(key)) {
            context.report(ISSUE, context.getLocation(element), "Duplicate key `$key`")
        }

        if (title.isNullOrEmpty()) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:title`")
        }

        if (type.isNullOrEmpty()) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:restrictionType`")
        } else {
            checkType(context, element, type)
        }

        if (type == TYPE_BUNDLE || type == TYPE_BUNDLE_ARRAY) {
            val nestedKeys = mutableSetOf<String>()
            for (child in element.tagChildren()) {
                if (child.tagName == TAG_RESTRICTION) {
                    checkRestriction(context, child, nestedKeys)
                } else {
                    context.report(
                        ISSUE,
                        context.getLocation(child),
                        "Invalid tag `${child.tagName}` inside bundle; expected `<restriction>`"
                    )
                }
            }
        }
    }

    private fun checkType(context: XmlContext, element: Element, type: String) {
        if (type !in VALID_TYPES) {
            val attrNode = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, ATTR_RESTRICTION_TYPE)
            context.report(
                ISSUE,
                context.getLocation(attrNode ?: element),
                "Invalid restriction type `$type`; must be one of ${VALID_TYPES.joinToString()}"
            )
        }

        if (type == TYPE_CHOICE || type == TYPE_MULTI_SELECT) {
            val entries = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_ENTRIES)
            val entryValues = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_ENTRY_VALUES)
            if (entries.isNullOrEmpty()) {
                context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:entries` for type `$type`")
            }
            if (entryValues.isNullOrEmpty()) {
                context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:entryValues` for type `$type`")
            }
        }
    }

    private fun Element.tagChildren(): Sequence<Element> =
        generateSequence(firstChild) { it.nextSibling }.filterIsInstance<Element>()

    companion object {
        private const val TAG_RESTRICTIONS = "restrictions"
        private const val TAG_RESTRICTION = "restriction"
        private const val ATTR_KEY = "key"
        private const val ATTR_TITLE = "title"
        private const val ATTR_RESTRICTION_TYPE = "restrictionType"
        private const val ATTR_ENTRIES = "entries"
        private const val ATTR_ENTRY_VALUES = "entryValues"

        private const val TYPE_BUNDLE = "bundle"
        private const val TYPE_BUNDLE_ARRAY = "bundle_array"
        private const val TYPE_CHOICE = "choice"
        private const val TYPE_MULTI_SELECT = "multi-select"

        private val VALID_TYPES = setOf(
            "bool", "string", "integer", TYPE_CHOICE, TYPE_MULTI_SELECT, TYPE_BUNDLE, TYPE_BUNDLE_ARRAY
        )

        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed. \
                The file must have a root `<restrictions>` tag, and each `<restriction>` \
                child must define `android:key`, `android:title`, and `android:restrictionType`. \
                The `restrictionType` must be one of the valid types defined by `RestrictionsManager`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}