package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.DomUtil
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    private val servicesNeedingConfig = mutableListOf<Element>()
    private val activitiesWithConfig = mutableListOf<ActivityConfigInfo>()

    private data class ActivityConfigInfo(
        val element: Element,
        val hasAction: Boolean,
        val hasCategory: Boolean
    )

    override fun getApplicableElements(): Collection<String>? =
        listOf(TAG_SERVICE, TAG_ACTIVITY)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> {
                if (hasWearableConfigMetadata(element)) {
                    servicesNeedingConfig.add(element)
                }
            }
            TAG_ACTIVITY -> {
                val info = getActivityConfigInfo(element)
                if (info.hasAction) {
                    activitiesWithConfig.add(info)
                }
            }
        }
    }

    private fun hasWearableConfigMetadata(service: Element): Boolean {
        var child = DomUtil.getFirstChildTag(service)
        while (child != null) {
            if (child.tagName == TAG_META_DATA) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = child.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (value == "WATCH_FACE_EDITOR" &&
                    (name == "wearableConfigurationAction" || name == "com.google.android.wearable.watchface.wearableConfigurationAction")
                ) {
                    return true
                }
            }
            child = DomUtil.getNextTag(child)
        }
        return false
    }

    private fun getActivityConfigInfo(activity: Element): ActivityConfigInfo {
        var hasAction = false
        var hasCategory = false
        var child = DomUtil.getFirstChildTag(activity)
        while (child != null) {
            if (child.tagName == TAG_INTENT_FILTER) {
                var filterChild = DomUtil.getFirstChildTag(child)
                var localHasAction = false
                var localHasCategory = false
                while (filterChild != null) {
                    val name = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    when (filterChild.tagName) {
                        TAG_ACTION -> {
                            if (name == "WATCH_FACE_EDITOR" || name == "com.google.android.wearable.watchface.action.WATCH_FACE_EDITOR") {
                                localHasAction = true
                            }
                        }
                        TAG_CATEGORY -> {
                            if (name == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                                localHasCategory = true
                            }
                        }
                    }
                    filterChild = DomUtil.getNextTag(filterChild)
                }
                if (localHasAction) {
                    hasAction = true
                    if (localHasCategory) hasCategory = true
                }
            }
            child = DomUtil.getNextTag(child)
        }
        return ActivityConfigInfo(activity, hasAction, hasCategory)
    }

    override fun afterCheckFile(context: XmlContext) {
        if (servicesNeedingConfig.isEmpty()) {
            servicesNeedingConfig.clear()
            activitiesWithConfig.clear()
            return
        }

        val minSdk = context.project.minSdkVersion?.apiLevel ?: 1
        val requiresCategory = minSdk < 30

        for (service in servicesNeedingConfig) {
            val matchingActivity = activitiesWithConfig.find { it.hasAction }
            if (matchingActivity == null) {
                context.report(
                    ISSUE,
                    service,
                    context.getLocation(service),
                    "No activity found with intent filter action `WATCH_FACE_EDITOR` for this watch face service"
                )
            } else if (requiresCategory && !matchingActivity.hasCategory) {
                context.report(
                    ISSUE,
                    service,
                    context.getLocation(service),
                    "For minSdkVersion < 30, the configuration activity must include the category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`"
                )
            }
        }

        servicesNeedingConfig.clear()
        activitiesWithConfig.clear()
    }

    companion object {
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = "When a watch face service defines `wearableConfigurationAction` metadata with the value `WATCH_FACE_EDITOR`, there must be a corresponding activity in the same package with an intent filter for `WATCH_FACE_EDITOR`. If `minSdkVersion` is less than 30, the intent filter must also include the category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`.",
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