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
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private val servicesRequiringConfig = mutableListOf<Element>()
    private val configActivities = mutableListOf<Element>()

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_SERVICE, TAG_ACTIVITY, "manifest")

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> {
                if (hasWearableConfigMetadata(element)) {
                    servicesRequiringConfig.add(element)
                }
            }
            TAG_ACTIVITY -> {
                if (hasConfigIntentFilter(context, element)) {
                    configActivities.add(element)
                }
            }
        }
    }

    override fun visitElementAfter(context: XmlContext, element: Element) {
        if (element.tagName == "manifest") {
            val count = configActivities.size
            if (count > 1) {
                for (activity in configActivities) {
                    context.report(
                        ISSUE,
                        activity,
                        context.getLocation(activity),
                        "Duplicate watch face configuration activities found. Expected exactly one activity with WATCH_FACE_EDITOR intent filter."
                    )
                }
            } else if (count == 0 && servicesRequiringConfig.isNotEmpty()) {
                for (service in servicesRequiringConfig) {
                    context.report(
                        ISSUE,
                        service,
                        context.getLocation(service),
                        "Missing watch face configuration activity. A service with wearableConfigurationAction metadata requires exactly one activity with WATCH_FACE_EDITOR intent filter."
                    )
                }
            }
            servicesRequiringConfig.clear()
            configActivities.clear()
        }
    }

    private fun hasWearableConfigMetadata(service: Element): Boolean {
        val children = service.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_META_DATA) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = child.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name.endsWith("wearableConfigurationAction") && value == "WATCH_FACE_EDITOR") {
                    return true
                }
            }
        }
        return false
    }

    private fun hasConfigIntentFilter(context: XmlContext, activity: Element): Boolean {
        val minSdk = context.mainProject.minSdkVersion?.apiLevel ?: 1
        val children = activity.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_INTENT_FILTER) {
                var hasAction = false
                var hasCategory = false
                val filterChildren = child.childNodes
                for (j in 0 until filterChildren.length) {
                    val fc = filterChildren.item(j)
                    if (fc is Element) {
                        when (fc.tagName) {
                            TAG_ACTION -> {
                                if (fc.getAttributeNS(ANDROID_URI, ATTR_NAME) == "WATCH_FACE_EDITOR") {
                                    hasAction = true
                                }
                            }
                            TAG_CATEGORY -> {
                                if (fc.getAttributeNS(ANDROID_URI, ATTR_NAME) == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                                    hasCategory = true
                                }
                            }
                        }
                    }
                }
                if (hasAction) {
                    return if (minSdk < 30) hasCategory else true
                }
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If a watch face service defines wearableConfigurationAction metadata with the value WATCH_FACE_EDITOR, there should be exactly one activity in the same package with an intent filter for WATCH_FACE_EDITOR (and the WEARABLE_CONFIGURATION category if minSdkVersion < 30).",
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