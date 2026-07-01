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
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    private val servicesNeedingConfig = mutableListOf<ServiceInfo>()
    private val activitiesWithConfig = mutableListOf<ActivityConfigInfo>()

    private data class ServiceInfo(val element: Element, val context: XmlContext)
    private data class ActivityConfigInfo(val element: Element, val context: XmlContext, val hasAction: Boolean, val hasCategory: Boolean)

    override fun getApplicableElements(): Collection<String>? =
        listOf(TAG_SERVICE, TAG_ACTIVITY)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> {
                if (hasWearableConfigMetadata(element)) {
                    servicesNeedingConfig.add(ServiceInfo(element, context))
                }
            }
            TAG_ACTIVITY -> {
                val info = getActivityConfigInfo(element, context)
                if (info.hasAction) {
                    activitiesWithConfig.add(info)
                }
            }
        }
    }

    private fun hasWearableConfigMetadata(service: Element): Boolean {
        val metaDataList = service.getElementsByTagName(TAG_META_DATA)
        for (i in 0 until metaDataList.length) {
            val meta = metaDataList.item(i) as? Element ?: continue
            val name = meta.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val value = meta.getAttributeNS(ANDROID_URI, ATTR_VALUE)
            if (value == "WATCH_FACE_EDITOR" &&
                (name == "wearableConfigurationAction" || name == "com.google.android.wearable.watchface.wearableConfigurationAction")
            ) {
                return true
            }
        }
        return false
    }

    private fun getActivityConfigInfo(activity: Element, context: XmlContext): ActivityConfigInfo {
        var hasAction = false
        var hasCategory = false
        val intentFilters = activity.getElementsByTagName(TAG_INTENT_FILTER)
        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as? Element ?: continue
            var localHasAction = false
            var localHasCategory = false

            val actions = filter.getElementsByTagName(TAG_ACTION)
            for (j in 0 until actions.length) {
                val action = actions.item(j) as? Element ?: continue
                val name = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == "WATCH_FACE_EDITOR" || name == "com.google.android.wearable.watchface.action.WATCH_FACE_EDITOR") {
                    localHasAction = true
                }
            }

            val categories = filter.getElementsByTagName(TAG_CATEGORY)
            for (j in 0 until categories.length) {
                val cat = categories.item(j) as? Element ?: continue
                val name = cat.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                    localHasCategory = true
                }
            }

            if (localHasAction) {
                hasAction = true
                if (localHasCategory) hasCategory = true
            }
        }
        return ActivityConfigInfo(activity, context, hasAction, hasCategory)
    }

    override fun afterCheckEachProject(context: Context) {
        if (servicesNeedingConfig.isEmpty()) {
            servicesNeedingConfig.clear()
            activitiesWithConfig.clear()
            return
        }

        val minSdk = context.project.minSdkVersion ?: 1
        val requiresCategory = minSdk < 30

        for (serviceInfo in servicesNeedingConfig) {
            val matchingActivity = activitiesWithConfig.find { it.hasAction }
            if (matchingActivity == null) {
                serviceInfo.context.report(
                    ISSUE,
                    serviceInfo.element,
                    serviceInfo.context.getLocation(serviceInfo.element),
                    "No activity found with intent filter action `WATCH_FACE_EDITOR` for this watch face service"
                )
            } else if (requiresCategory && !matchingActivity.hasCategory) {
                serviceInfo.context.report(
                    ISSUE,
                    serviceInfo.element,
                    serviceInfo.context.getLocation(serviceInfo.element),
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
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}