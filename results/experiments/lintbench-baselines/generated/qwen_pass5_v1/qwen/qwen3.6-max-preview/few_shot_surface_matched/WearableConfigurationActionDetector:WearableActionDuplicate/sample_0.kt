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
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private data class ServiceInfo(
        val xmlContext: XmlContext,
        val element: Element,
        val packageName: String
    )

    private data class ActivityInfo(
        val packageName: String,
        val hasAction: Boolean,
        val hasCategory: Boolean
    )

    private val services = mutableListOf<ServiceInfo>()
    private val activities = mutableListOf<ActivityInfo>()

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_SERVICE, TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val packageName = context.manifestPackage ?: return

        when (element.tagName) {
            TAG_SERVICE -> {
                var metaData = XmlUtils.getFirstSubTagByName(element, TAG_META_DATA)
                while (metaData != null) {
                    val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                    if (name == "wearableConfigurationAction" && value == "WATCH_FACE_EDITOR") {
                        services.add(ServiceInfo(context, element, packageName))
                        break
                    }
                    metaData = XmlUtils.getNextTagByName(metaData, TAG_META_DATA)
                }
            }
            TAG_ACTIVITY -> {
                var intentFilter = XmlUtils.getFirstSubTagByName(element, TAG_INTENT_FILTER)
                while (intentFilter != null) {
                    var hasAction = false
                    var hasCategory = false
                    var child = intentFilter.firstChild
                    while (child != null) {
                        if (child is Element) {
                            when (child.tagName) {
                                TAG_ACTION -> {
                                    if (child.getAttributeNS(ANDROID_URI, ATTR_NAME) == "WATCH_FACE_EDITOR") {
                                        hasAction = true
                                    }
                                }
                                TAG_CATEGORY -> {
                                    if (child.getAttributeNS(ANDROID_URI, ATTR_NAME) == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                                        hasCategory = true
                                    }
                                }
                            }
                        }
                        child = child.nextSibling
                    }
                    if (hasAction) {
                        activities.add(ActivityInfo(packageName, true, hasCategory))
                    }
                    intentFilter = XmlUtils.getNextTagByName(intentFilter, TAG_INTENT_FILTER)
                }
            }
        }
    }

    override fun checkMergedProject(context: Context) {
        val minSdk = context.project.minSdkVersion
        val activityMap = activities.groupBy { it.packageName }

        for (service in services) {
            val pkgActivities = activityMap[service.packageName]
            if (pkgActivities.isNullOrEmpty()) {
                service.xmlContext.report(
                    ISSUE,
                    service.xmlContext.getLocation(service.element),
                    "Missing configuration activity with action WATCH_FACE_EDITOR for this watch face service"
                )
            } else {
                val hasValidCategory = pkgActivities.any { it.hasCategory }
                if (minSdk < 30 && !hasValidCategory) {
                    service.xmlContext.report(
                        ISSUE,
                        service.xmlContext.getLocation(service.element),
                        "Configuration activity for WATCH_FACE_EDITOR must include category " +
                            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION " +
                            "for minSdkVersion < 30"
                    )
                }
            }
        }
        services.clear()
        activities.clear()
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If a watch face service defines wearableConfigurationAction metadata with the value " +
                "WATCH_FACE_EDITOR, there should be a corresponding activity in the same package with an intent " +
                "filter for WATCH_FACE_EDITOR. For minSdkVersion < 30, the category " +
                "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION is also required.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(WearableConfigurationActionDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }
}