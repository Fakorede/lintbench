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
import org.w3c.dom.Document
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun getApplicableFiles() = Scope.MANIFEST_SCOPE

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val application = root.getElementsByTagName(SdkConstants.TAG_APPLICATION).item(0) as? Element ?: return
        
        val services = application.getElementsByTagName(SdkConstants.TAG_SERVICE)
        val activities = application.getElementsByTagName(SdkConstants.TAG_ACTIVITY)
        
        val minSdkVersion = context.project.minSdkVersion.apiLevel
        
        val configMetadatas = mutableListOf<Element>()
        for (i in 0 until services.length) {
            val service = services.item(i) as? Element ?: continue
            val metaDatas = service.getElementsByTagName(SdkConstants.TAG_META_DATA)
            for (j in 0 until metaDatas.length) {
                val metaData = metaDatas.item(j) as? Element ?: continue
                if (isWearableConfigMetadata(metaData)) {
                    configMetadatas.add(metaData)
                }
            }
        }
        
        val configActivities = mutableListOf<Element>()
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as? Element ?: continue
            if (isConfigActivity(activity)) {
                configActivities.add(activity)
            }
        }
        
        val hasMatchingActivity = configActivities.any { matchesActivity(it, minSdkVersion) }
        
        if (configMetadatas.isNotEmpty() && !hasMatchingActivity) {
            for (metaData in configMetadatas) {
                val message = if (minSdkVersion < 30) {
                    "To support wearable configuration, there must be an activity with an intent filter for " +
                            "action 'com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR' " +
                            "and category 'com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION'."
                } else {
                    "To support wearable configuration, there must be an activity with an intent filter for " +
                            "action 'com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR'."
                }
                context.report(
                    ISSUE,
                    metaData,
                    context.getLocation(metaData),
                    message
                )
            }
        }
        
        if (configActivities.isNotEmpty() && configMetadatas.isEmpty()) {
            for (activity in configActivities) {
                val message = "An activity is configured as a watch face editor, but no watch face service defines the " +
                        "corresponding 'com.google.android.wearable.watchface.wearableConfigurationAction' metadata."
                context.report(
                    ISSUE,
                    activity,
                    context.getLocation(activity),
                    message
                )
            }
        }
    }

    private fun isWearableConfigMetadata(element: Element): Boolean {
        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        val value = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
        
        val nameMatch = name == "com.google.android.wearable.watchface.wearableConfigurationAction" || 
                        name == "wearableConfigurationAction"
        val valueMatch = value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" || 
                         value == "WATCH_FACE_EDITOR"
        
        return nameMatch && valueMatch
    }

    private fun isConfigActivity(activity: Element): Boolean {
        val intentFilters = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER)
        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as? Element ?: continue
            val actions = filter.getElementsByTagName(SdkConstants.TAG_ACTION)
            for (j in 0 until actions.length) {
                val action = actions.item(j) as? Element ?: continue
                val actionName = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (actionName == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" ||
                    actionName == "WATCH_FACE_EDITOR") {
                    return true
                }
            }
        }
        return false
    }

    private fun matchesActivity(activity: Element, minSdkVersion: Int): Boolean {
        val intentFilters = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER)
        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as? Element ?: continue
            if (matchesIntentFilter(filter, minSdkVersion)) {
                return true
            }
        }
        return false
    }

    private fun matchesIntentFilter(filter: Element, minSdkVersion: Int): Boolean {
        val actions = filter.getElementsByTagName(SdkConstants.TAG_ACTION)
        var hasAction = false
        for (i in 0 until actions.length) {
            val action = actions.item(i) as? Element ?: continue
            val actionName = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (actionName == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" ||
                actionName == "WATCH_FACE_EDITOR") {
                hasAction = true
                break
            }
        }
        if (!hasAction) return false

        if (minSdkVersion < 30) {
            val categories = filter.getElementsByTagName(SdkConstants.TAG_CATEGORY)
            var hasCategory = false
            for (i in 0 until categories.length) {
                val category = categories.item(i) as? Element ?: continue
                val catName = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (catName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" ||
                    catName == "WEARABLE_CONFIGURATION") {
                    hasCategory = true
                    break
                }
            }
            return hasCategory
        }

        return true
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines the `wearableConfigurationAction` metadata \
                with the value `WATCH_FACE_EDITOR`, there must be an activity in the same \
                package that has an intent filter for `WATCH_FACE_EDITOR`. If the `minSdkVersion` \
                is less than 30, this intent filter must also include the \
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
}