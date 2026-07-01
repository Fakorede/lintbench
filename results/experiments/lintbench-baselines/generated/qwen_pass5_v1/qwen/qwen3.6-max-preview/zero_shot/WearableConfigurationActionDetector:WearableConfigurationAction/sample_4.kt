package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_MANIFEST
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
import com.android.utils.XmlUtils
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    private val serviceElements = mutableListOf<Element>()
    private val activityElements = mutableListOf<Element>()

    override fun getApplicableElements(): Collection<String>? = listOf(
        TAG_MANIFEST, TAG_SERVICE, TAG_ACTIVITY
    )

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> serviceElements.add(element)
            TAG_ACTIVITY -> activityElements.add(element)
        }
    }

    override fun afterCheckFile(context: Context) {
        val xmlContext = context as XmlContext
        val servicesNeedingEditor = serviceElements.filter { hasWearableConfigAction(it) }
        if (servicesNeedingEditor.isEmpty()) {
            reset()
            return
        }

        val minSdk = context.mainProject.minSdkVersion.takeIf { it > 0 } ?: 1
        val hasMatchingActivity = activityElements.any { hasEditorIntentFilter(it, minSdk) }

        if (!hasMatchingActivity) {
            val message = buildString {
                append("Watch face service defines `wearableConfigurationAction` metadata with value `WATCH_FACE_EDITOR`, ")
                append("but no activity in the same package has an intent filter for `WATCH_FACE_EDITOR`")
                if (minSdk < 30) {
                    append(" with the `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category")
                }
                append(".")
            }
            for (service in servicesNeedingEditor) {
                xmlContext.report(ISSUE, xmlContext.getLocation(service), message)
            }
        }
        reset()
    }

    private fun reset() {
        serviceElements.clear()
        activityElements.clear()
    }

    private fun hasWearableConfigAction(service: Element): Boolean {
        return XmlUtils.getSubTagsByName(service, TAG_META_DATA).any { meta ->
            val name = meta.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val value = meta.getAttributeNS(ANDROID_URI, ATTR_VALUE)
            (name == "wearableConfigurationAction" || name == "com.google.android.wearable.watchface.wearableConfigurationAction") &&
                value == "WATCH_FACE_EDITOR"
        }
    }

    private fun hasEditorIntentFilter(activity: Element, minSdkVersion: Int): Boolean {
        return XmlUtils.getSubTagsByName(activity, TAG_INTENT_FILTER).any { filter ->
            var hasAction = false
            var hasCategory = false
            for (child in XmlUtils.getChildren(filter)) {
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
            hasAction && (minSdkVersion >= 30 || hasCategory)
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = "When a watch face service defines `wearableConfigurationAction` metadata with the value `WATCH_FACE_EDITOR`, " +
                "there must be an activity in the same package that declares an intent filter for `WATCH_FACE_EDITOR`. " +
                "If `minSdkVersion` is less than 30, the intent filter must also include the " +
                "`com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.",
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