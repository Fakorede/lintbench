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

    override fun getApplicableElements(): Collection<String> {
        return listOf(SdkConstants.TAG_MANIFEST)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != SdkConstants.TAG_MANIFEST) return

        val matchingMetadataElements = mutableListOf<Element>()
        val servicesNodeList = element.getElementsByTagName(SdkConstants.TAG_SERVICE)
        for (i in 0 until servicesNodeList.length) {
            val service = servicesNodeList.item(i) as Element
            val metaDataNodeList = service.getElementsByTagName(SdkConstants.TAG_META_DATA)
            for (j in 0 until metaDataNodeList.length) {
                val metaData = metaDataNodeList.item(j) as Element
                if (metaData.parentNode == service && isConfigurationMetadata(metaData)) {
                    matchingMetadataElements.add(metaData)
                }
            }
        }

        class ActivityFilterInfo(
            val activity: Element,
            val intentFilter: Element,
            val hasAction: Boolean,
            val hasCategory: Boolean
        )

        val activityFilters = mutableListOf<ActivityFilterInfo>()
        val activitiesNodeList = element.getElementsByTagName(SdkConstants.TAG_ACTIVITY)
        for (i in 0 until activitiesNodeList.length) {
            val activity = activitiesNodeList.item(i) as Element
            val filterNodeList = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER)
            for (j in 0 until filterNodeList.length) {
                val filter = filterNodeList.item(j) as Element
                if (filter.parentNode != activity) continue

                var hasAction = false
                val actionNodeList = filter.getElementsByTagName(SdkConstants.TAG_ACTION)
                for (k in 0 until actionNodeList.length) {
                    val action = actionNodeList.item(k) as Element
                    if (action.parentNode == filter && isEditorAction(action)) {
                        hasAction = true
                        break
                    }
                }

                var hasCategory = false
                val categoryNodeList = filter.getElementsByTagName(SdkConstants.TAG_CATEGORY)
                for (k in 0 until categoryNodeList.length) {
                    val category = categoryNodeList.item(k) as Element
                    if (category.parentNode == filter && isWearableConfigCategory(category)) {
                        hasCategory = true
                        break
                    }
                }

                if (hasAction) {
                    activityFilters.add(ActivityFilterInfo(activity, filter, hasAction, hasCategory))
                }
            }
        }

        val minSdk = context.project.minSdkVersion.apiLevel
        val hasMetadata = matchingMetadataElements.isNotEmpty()

        val hasFullyMatchingFilter = activityFilters.any { filterInfo ->
            if (minSdk < 30) {
                filterInfo.hasCategory
            } else {
                true
            }
        }

        if (hasMetadata) {
            if (!hasFullyMatchingFilter) {
                if (activityFilters.isNotEmpty()) {
                    for (filterInfo in activityFilters) {
                        val message = "To support wearable configuration, the intent filter must also include the `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category (required for minSdkVersion < 30)."
                        context.report(
                            ISSUE,
                            filterInfo.intentFilter,
                            context.getLocation(filterInfo.intentFilter),
                            message
                        )
                    }
                } else {
                    for (metaData in matchingMetadataElements) {
                        val message = if (minSdk < 30) {
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
            for (filterInfo in activityFilters) {
                val message = "An activity with intent filter `com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR` requires a watch face service with `com.google.android.wearable.watchface.wearableConfigurationAction` metadata."
                context.report(
                    ISSUE,
                    filterInfo.intentFilter,
                    context.getLocation(filterInfo.intentFilter),
                    message
                )
            }
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return false
    }

    private fun getAndroidAttribute(element: Element, localName: String): String {
        return element.getAttributeNS(SdkConstants.ANDROID_URI, localName).takeIf { it.isNotEmpty() }
            ?: element.getAttribute("android:$localName").takeIf { it.isNotEmpty() }
            ?: element.getAttribute(localName)
    }

    private fun isConfigurationMetadata(metaData: Element): Boolean {
        val name = getAndroidAttribute(metaData, SdkConstants.ATTR_NAME)
        val value = getAndroidAttribute(metaData, SdkConstants.ATTR_VALUE)

        val nameMatch = name == "com.google.android.wearable.watchface.wearableConfigurationAction" ||
                name == "wearableConfigurationAction"
        val valueMatch = value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" ||
                value == "WATCH_FACE_EDITOR"

        return nameMatch && valueMatch
    }

    private fun isEditorAction(action: Element): Boolean {
        val name = getAndroidAttribute(action, SdkConstants.ATTR_NAME)
        return name == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" ||
                name == "WATCH_FACE_EDITOR"
    }

    private fun isWearableConfigCategory(category: Element): Boolean {
        val name = getAndroidAttribute(category, SdkConstants.ATTR_NAME)
        return name == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" ||
                name == "WEARABLE_CONFIGURATION"
    }
}