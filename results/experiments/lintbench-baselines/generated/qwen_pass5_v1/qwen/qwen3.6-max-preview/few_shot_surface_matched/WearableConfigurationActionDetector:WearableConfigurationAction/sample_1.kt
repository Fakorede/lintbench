package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
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
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private data class ServiceInfo(val name: String, val location: Location)
    private data class ActivityInfo(val name: String, val hasCategory: Boolean)

    private val servicesNeedingEditor = mutableListOf<ServiceInfo>()
    private val activitiesWithEditor = mutableListOf<ActivityInfo>()

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE, TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
        val componentName = nameAttr.value

        when (element.tagName) {
            TAG_SERVICE -> {
                var metaData = getFirstSubTagByName(element, TAG_META_DATA)
                while (metaData != null) {
                    val metaName = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    val metaValue = metaData.getAttributeNS(ANDROID_URI, "value")
                    if (metaName?.endsWith("wearableConfigurationAction") == true && metaValue == "WATCH_FACE_EDITOR") {
                        servicesNeedingEditor.add(ServiceInfo(componentName, context.getLocation(nameAttr)))
                        break
                    }
                    metaData = getNextTagByName(metaData, TAG_META_DATA)
                }
            }
            TAG_ACTIVITY -> {
                var intentFilter = getFirstSubTagByName(element, TAG_INTENT_FILTER)
                var hasAction = false
                var hasCategory = false
                while (intentFilter != null) {
                    var action = getFirstSubTagByName(intentFilter, TAG_ACTION)
                    while (action != null) {
                        val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
                        if (actionName?.endsWith("WATCH_FACE_EDITOR") == true) {
                            hasAction = true
                        }
                        action = getNextTagByName(action, TAG_ACTION)
                    }
                    var category = getFirstSubTagByName(intentFilter, TAG_CATEGORY)
                    while (category != null) {
                        val catName = category.getAttributeNS(ANDROID_URI, ATTR_NAME)
                        if (catName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                            hasCategory = true
                        }
                        category = getNextTagByName(category, TAG_CATEGORY)
                    }
                    intentFilter = getNextTagByName(intentFilter, TAG_INTENT_FILTER)
                }
                if (hasAction) {
                    activitiesWithEditor.add(ActivityInfo(componentName, hasCategory))
                }
            }
        }
    }

    override fun checkMergedProject(context: Context) {
        if (servicesNeedingEditor.isEmpty()) {
            servicesNeedingEditor.clear()
            activitiesWithEditor.clear()
            return
        }

        val minSdk = context.project.minSdkVersion?.apiLevel ?: 1
        val requiresCategory = minSdk < 30

        for (service in servicesNeedingEditor) {
            val validActivity = activitiesWithEditor.find { activity ->
                if (requiresCategory) activity.hasCategory else true
            }

            if (validActivity == null) {
                val message = if (requiresCategory) {
                    "Watch face service defines wearableConfigurationAction metadata but no activity with " +
                        "WATCH_FACE_EDITOR action and WEARABLE_CONFIGURATION category found in the manifest."
                } else {
                    "Watch face service defines wearableConfigurationAction metadata but no activity with " +
                        "WATCH_FACE_EDITOR action found in the manifest."
                }
                context.report(ISSUE, service.location, message)
            }
        }

        servicesNeedingEditor.clear()
        activitiesWithEditor.clear()
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