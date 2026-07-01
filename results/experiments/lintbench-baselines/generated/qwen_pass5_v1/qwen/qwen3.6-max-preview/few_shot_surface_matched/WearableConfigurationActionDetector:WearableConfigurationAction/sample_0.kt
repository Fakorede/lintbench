package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
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
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private val servicesNeedingConfig = mutableListOf<Element>()
    private var hasValidConfigActivity = false

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE, TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> checkService(element)
            TAG_ACTIVITY -> checkActivity(context, element)
        }
    }

    private fun checkService(element: Element) {
        var metaData = getFirstSubTagByName(element, TAG_META_DATA)
        while (metaData != null) {
            val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
            if (name == "com.google.android.wearable.watchface.wearableConfigurationAction" &&
                value == "WATCH_FACE_EDITOR") {
                servicesNeedingConfig.add(element)
                break
            }
            metaData = getNextTagByName(metaData, TAG_META_DATA)
        }
    }

    private fun checkActivity(context: XmlContext, element: Element) {
        if (hasValidConfigActivity) return

        var intentFilter = getFirstSubTagByName(element, TAG_INTENT_FILTER)
        while (intentFilter != null) {
            var action = getFirstSubTagByName(intentFilter, TAG_ACTION)
            var hasAction = false
            while (action != null) {
                val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (actionName == "WATCH_FACE_EDITOR" ||
                    actionName == "com.google.android.wearable.watchface.action.WATCH_FACE_EDITOR") {
                    hasAction = true
                    break
                }
                action = getNextTagByName(action, TAG_ACTION)
            }

            if (hasAction) {
                val minSdk = context.mainProject.minSdkVersion ?: 1
                if (minSdk >= 30) {
                    hasValidConfigActivity = true
                    break
                } else {
                    var category = getFirstSubTagByName(intentFilter, TAG_CATEGORY)
                    while (category != null) {
                        val catName = category.getAttributeNS(ANDROID_URI, ATTR_NAME)
                        if (catName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                            hasValidConfigActivity = true
                            break
                        }
                        category = getNextTagByName(category, TAG_CATEGORY)
                    }
                }
            }

            if (hasValidConfigActivity) break
            intentFilter = getNextTagByName(intentFilter, TAG_INTENT_FILTER)
        }
    }

    override fun beforeCheckRootProject(context: Context) {
        servicesNeedingConfig.clear()
        hasValidConfigActivity = false
    }

    override fun afterCheckRootProject(context: Context) {
        if (servicesNeedingConfig.isNotEmpty() && !hasValidConfigActivity) {
            for (service in servicesNeedingConfig) {
                val attr = service.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: continue
                context.report(
                    ISSUE,
                    attr,
                    context.getValueLocation(attr),
                    "Wearable configuration action metadata must match an activity"
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action metadata must match an activity",
            explanation = "Only when a watch face service defines wearableConfigurationAction metadata, " +
                "with the value WATCH_FACE_EDITOR, there should be an activity in the same package, " +
                "which has an intent filter for WATCH_FACE_EDITOR (with " +
                "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION if minSdkVersion is less than 30).",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(WearableConfigurationActionDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }
}