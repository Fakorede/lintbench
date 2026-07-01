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

    companion object {
        private const val WEARABLE_CONFIGURATION_ACTION = "wearableConfigurationAction"
        private const val WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val MIN_SDK_NO_CATEGORY = 30

        val ISSUE: Issue = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action must be matched by an activity",
            explanation = """
                When a watch face service declares a `wearableConfigurationAction` metadata
                element with the value `WATCH_FACE_EDITOR`, an activity must be present that
                handles the `WATCH_FACE_EDITOR` action. For apps with a `minSdkVersion` below 30,
                the activity's intent filter must also include the category
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    private val servicesWithConfig = mutableListOf<Element>()
    private val activities = mutableListOf<Element>()

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_SERVICE, TAG_ACTIVITY)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> {
                if (findMetadata(element, WEARABLE_CONFIGURATION_ACTION, WATCH_FACE_EDITOR) != null) {
                    servicesWithConfig.add(element)
                }
            }
            TAG_ACTIVITY -> {
                activities.add(element)
            }
        }
    }

    override fun afterCheckFile(context: XmlContext) {
        if (servicesWithConfig.isEmpty()) {
            servicesWithConfig.clear()
            activities.clear()
            return
        }

        val minSdk = context.mainProject.minSdkVersion.featureLevel
        val requireCategory = minSdk < MIN_SDK_NO_CATEGORY

        for (service in servicesWithConfig) {
            val metadata = findMetadata(service, WEARABLE_CONFIGURATION_ACTION, WATCH_FACE_EDITOR)
                ?: continue
            val matched = activities.any { activity ->
                activityHandlesWatchFaceEditor(activity, requireCategory)
            }
            if (!matched) {
                val location = context.getLocation(metadata)
                val categoryRequirement = if (requireCategory) {
                    " and category `$WEARABLE_CONFIGURATION_CATEGORY`"
                } else {
                    ""
                }
                context.report(
                    ISSUE,
                    metadata,
                    location,
                    "The `wearableConfigurationAction` value `WATCH_FACE_EDITOR` must be matched by " +
                        "an activity with an intent filter for `WATCH_FACE_EDITOR`$categoryRequirement."
                )
            }
        }

        servicesWithConfig.clear()
        activities.clear()
    }

    private fun findMetadata(service: Element, name: String, value: String): Element? {
        val children = service.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName != TAG_META_DATA) continue
            val attrName = child.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: continue
            if (attrName != name) continue
            val attrValue = child.getAttributeNS(ANDROID_URI, ATTR_VALUE) ?: continue
            if (attrValue == value) return child
        }
        return null
    }

    private fun activityHandlesWatchFaceEditor(activity: Element, requireCategory: Boolean): Boolean {
        val children = activity.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName != TAG_INTENT_FILTER) continue

            var hasAction = false
            var hasCategory = false
            val filterChildren = child.childNodes
            for (j in 0 until filterChildren.length) {
                val item = filterChildren.item(j) as? Element ?: continue
                when (item.tagName) {
                    TAG_ACTION -> {
                        if (item.getAttributeNS(ANDROID_URI, ATTR_NAME) == WATCH_FACE_EDITOR) {
                            hasAction = true
                        }
                    }
                    TAG_CATEGORY -> {
                        if (item.getAttributeNS(ANDROID_URI, ATTR_NAME) == WEARABLE_CONFIGURATION_CATEGORY) {
                            hasCategory = true
                        }
                    }
                }
            }

            if (hasAction && (!requireCategory || hasCategory)) {
                return true
            }
        }
        return false
    }
}