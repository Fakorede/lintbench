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
import org.w3c.dom.NodeList

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun getApplicableFiles() = Scope.MANIFEST_SCOPE

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val application = root.getElementsByTagName(SdkConstants.TAG_APPLICATION).item(0) as? Element ?: return
        val services = application.getElementsByTagName(SdkConstants.TAG_SERVICE)
        val activities = application.getElementsByTagName(SdkConstants.TAG_ACTIVITY)

        for (i in 0 until services.length) {
            val service = services.item(i) as? Element ?: continue
            val metaDatas = service.getElementsByTagName(SdkConstants.TAG_META_DATA)
            for (j in 0 until metaDatas.length) {
                val metaData = metaDatas.item(j) as? Element ?: continue
                val name = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                val value = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
                if (name == "com.google.android.wearable.watchface.wearableConfigurationAction" &&
                    value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR") {
                    
                    val minSdkVersion = context.project.minSdkVersion.apiLevel
                    if (!hasMatchingActivity(activities, minSdkVersion)) {
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
            }
        }
    }

    private fun hasMatchingActivity(activities: NodeList, minSdkVersion: Int): Boolean {
        val needsCategory = minSdkVersion < 30
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as? Element ?: continue
            val intentFilters = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER)
            for (j in 0 until intentFilters.length) {
                val filter = intentFilters.item(j) as? Element ?: continue
                if (matchesFilter(filter, needsCategory)) {
                    return true
                }
            }
        }
        return false
    }

    private fun matchesFilter(filter: Element, needsCategory: Boolean): Boolean {
        val actions = filter.getElementsByTagName(SdkConstants.TAG_ACTION)
        var hasAction = false
        for (k in 0 until actions.length) {
            val action = actions.item(k) as? Element ?: continue
            val actionName = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (actionName == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR") {
                hasAction = true
                break
            }
        }
        if (!hasAction) return false

        if (needsCategory) {
            val categories = filter.getElementsByTagName(SdkConstants.TAG_CATEGORY)
            var hasCategory = false
            for (k in 0 until categories.length) {
                val category = categories.item(k) as? Element ?: continue
                val catName = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (catName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
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