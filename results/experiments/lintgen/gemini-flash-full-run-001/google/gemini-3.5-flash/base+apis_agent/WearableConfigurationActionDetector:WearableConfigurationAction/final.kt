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

    override fun appliesTo(folderType: ResourceFolderType): Boolean = false

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.TAG_MANIFEST)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != SdkConstants.TAG_MANIFEST) return

        val matchingMetadata = mutableListOf<Element>()
        val metaDataNodes = element.getElementsByTagName(SdkConstants.TAG_META_DATA)
        for (i in 0 until metaDataNodes.length) {
            val metaData = metaDataNodes.item(i) as? Element ?: continue
            val parent = metaData.parentNode as? Element ?: continue
            if (parent.tagName == SdkConstants.TAG_SERVICE) {
                val name = metaData.getAndroidAttribute(SdkConstants.ATTR_NAME)
                val value = metaData.getAndroidAttribute(SdkConstants.ATTR_VALUE)
                if (name != null && (name == "com.google.android.wearable.watchface.wearableConfigurationAction" || name == "wearableConfigurationAction") &&
                    value != null && (value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" || value == "WATCH_FACE_EDITOR")
                ) {
                    matchingMetadata.add(metaData)
                }
            }
        }

        val matchingFilters = mutableListOf<IntentFilterInfo>()
        val activityElements = mutableListOf<Element>()
        val activityNodes = element.getElementsByTagName(SdkConstants.TAG_ACTIVITY)
        for (i in 0 until activityNodes.length) {
            (activityNodes.item(i) as? Element)?.let { activityElements.add(it) }
        }
        val aliasNodes = element.getElementsByTagName("activity-alias")
        for (i in 0 until aliasNodes.length) {
            (aliasNodes.item(i) as? Element)?.let { activityElements.add(it) }
        }

        for (activity in activityElements) {
            val intentFilterNodes = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER)
            for (j in 0 until intentFilterNodes.length) {
                val intentFilter = intentFilterNodes.item(j) as? Element ?: continue
                
                // Find actions
                val actionNodes = intentFilter.getElementsByTagName(SdkConstants.TAG_ACTION)
                var hasEditorAction = false
                for (k in 0 until actionNodes.length) {
                    val action = actionNodes.item(k) as? Element ?: continue
                    val actionName = action.getAndroidAttribute(SdkConstants.ATTR_NAME)
                    if (actionName != null && (actionName == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" || actionName == "WATCH_FACE_EDITOR")) {
                        hasEditorAction = true
                        break
                    }
                }
                
                if (hasEditorAction) {
                    // Find categories
                    val categoryNodes = intentFilter.getElementsByTagName(SdkConstants.TAG_CATEGORY)
                    var hasCategory = false
                    for (k in 0 until categoryNodes.length) {
                        val category = categoryNodes.item(k) as? Element ?: continue
                        val categoryName = category.getAndroidAttribute(SdkConstants.ATTR_NAME)
                        if (categoryName != null && (categoryName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" || categoryName == "WEARABLE_CONFIGURATION")) {
                            hasCategory = true
                            break
                        }
                    }
                    matchingFilters.add(IntentFilterInfo(activity, intentFilter, hasCategory))
                }
            }
        }

        val minSdk = context.project.minSdkVersion.apiLevel
        val needsCategory = minSdk < 30
        val hasMetadata = matchingMetadata.isNotEmpty()

        if (hasMetadata) {
            if (matchingFilters.isEmpty()) {
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
            } else if (needsCategory) {
                for (filterInfo in matchingFilters) {
                    if (!filterInfo.hasCategory) {
                        context.report(
                            ISSUE,
                            filterInfo.intentFilter,
                            context.getLocation(filterInfo.intentFilter),
                            "To support wearable configuration, the intent filter must also include the `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category (required for minSdkVersion < 30)."
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

    private fun Element.getAndroidAttribute(localName: String): String? {
        val value = getAttributeNS(SdkConstants.ANDROID_URI, localName)
        if (value.isNotEmpty()) return value
        val value2 = getAttribute("android:$localName")
        if (value2.isNotEmpty()) return value2
        return null
    }

    private class IntentFilterInfo(
        val activity: Element,
        val intentFilter: Element,
        val hasCategory: Boolean
    )
}