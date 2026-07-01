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
import org.w3c.dom.Document
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines the `wearableConfigurationAction` metadata with \
                the value `WATCH_FACE_EDITOR`, there must be an activity in the same package \
                that has an intent filter for `WATCH_FACE_EDITOR`. If the `minSdkVersion` is \
                less than 30, this intent filter must also include the \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = false

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != SdkConstants.TAG_MANIFEST) return

        val applications = root.childElements().filter { it.tagName == SdkConstants.TAG_APPLICATION }
        val matchingMetadata = mutableListOf<Element>()
        val matchingFilters = mutableListOf<ActivityFilterInfo>()

        for (application in applications) {
            for (service in application.childElements().filter { it.tagName == SdkConstants.TAG_SERVICE }) {
                for (metaData in service.childElements().filter { it.tagName == SdkConstants.TAG_META_DATA }) {
                    val name = metaData.getAndroidAttribute(SdkConstants.ATTR_NAME)
                    val value = metaData.getAndroidAttribute(SdkConstants.ATTR_VALUE)
                    if (name == "com.google.android.wearable.watchface.wearableConfigurationAction" &&
                        (value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" || value == "WATCH_FACE_EDITOR")
                    ) {
                        matchingMetadata.add(metaData)
                    }
                }
            }

            for (activity in application.childElements().filter { it.tagName == SdkConstants.TAG_ACTIVITY }) {
                for (intentFilter in activity.childElements().filter { it.tagName == SdkConstants.TAG_INTENT_FILTER }) {
                    val actions = intentFilter.childElements().filter { it.tagName == SdkConstants.TAG_ACTION }
                    val hasEditorAction = actions.any { action ->
                        val actionName = action.getAndroidAttribute(SdkConstants.ATTR_NAME)
                        actionName == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" || actionName == "WATCH_FACE_EDITOR"
                    }
                    if (hasEditorAction) {
                        val categories = intentFilter.childElements().filter { it.tagName == SdkConstants.TAG_CATEGORY }
                        val hasCategory = categories.any { category ->
                            val categoryName = category.getAndroidAttribute(SdkConstants.ATTR_NAME)
                            categoryName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" || categoryName == "WEARABLE_CONFIGURATION"
                        }
                        matchingFilters.add(ActivityFilterInfo(activity, intentFilter, hasCategory))
                    }
                }
            }
        }

        val minSdk = context.project.minSdkVersion.apiLevel
        val needsCategory = minSdk < 30
        val hasMetadata = matchingMetadata.isNotEmpty()
        val hasFullyMatchingFilter = matchingFilters.any { !needsCategory || it.hasCategory }

        if (hasMetadata) {
            if (!hasFullyMatchingFilter) {
                if (matchingFilters.isNotEmpty()) {
                    for (filterInfo in matchingFilters) {
                        if (needsCategory && !filterInfo.hasCategory) {
                            context.report(
                                ISSUE,
                                filterInfo.intentFilter,
                                context.getLocation(filterInfo.intentFilter),
                                "To support wearable configuration, the intent filter must also include the `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category (required for minSdkVersion < 30)."
                            )
                        }
                    }
                } else {
                    for (metaData in matchingMetadata) {
                        val message = if (needsCategory) {
                            "To support wearable configuration, there must be an activity with an intent filter for action `com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR` and category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` (required for minSdkVersion < 30)."
                        } else {
                            "To support wearable configuration, there must be an activity with an intent filter for action `com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR`."
                        }
                        context.report(
                            ISSUE,
                            metaData,
                            context.getLocation(metaData),
                            message
                        )
                    }
                }
            }
        } else {
            for (filterInfo in matchingFilters) {
                context.report(
                    ISSUE,
                    filterInfo.intentFilter,
                    context.getLocation(filterInfo.intentFilter),
                    "An activity with intent filter `com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR` requires a watch face service with `com.google.android.wearable.watchface.wearableConfigurationAction` metadata."
                )
            }
        }
    }

    private fun Element.childElements(): List<Element> {
        val list = mutableListOf<Element>()
        var child = firstChild
        while (child != null) {
            if (child is Element) {
                list.add(child)
            }
            child = child.nextSibling
        }
        return list
    }

    private fun Element.getAndroidAttribute(localName: String): String? {
        val value = getAttributeNS(SdkConstants.ANDROID_URI, localName)
        if (value.isNotEmpty()) return value
        val value2 = getAttribute("android:$localName")
        if (value2.isNotEmpty()) return value2
        return null
    }

    private class ActivityFilterInfo(
        val activity: Element,
        val intentFilter: Element,
        val hasCategory: Boolean
    )
}