package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
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
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_SERVICE, TAG_ACTIVITY)

    private val watchFaceServices = mutableListOf<Element>()
    private val configurationActivities = mutableListOf<Element>()

    override fun beforeCheckFile(context: Context) {
        watchFaceServices.clear()
        configurationActivities.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> {
                if (hasWearableConfigurationAction(element)) {
                    watchFaceServices.add(element)
                }
            }
            TAG_ACTIVITY -> {
                if (isConfigurationActivity(context, element)) {
                    configurationActivities.add(element)
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (watchFaceServices.isEmpty() || configurationActivities.size <= 1) {
            return
        }

        for (activity in configurationActivities.subList(1, configurationActivities.size)) {
            context.report(
                ISSUE,
                context.getLocation(activity),
                "Duplicate watch face configuration activity found for action $ACTION",
                null
            )
        }

        watchFaceServices.clear()
        configurationActivities.clear()
    }

    private fun hasWearableConfigurationAction(service: Element): Boolean {
        for (i in 0 until service.childNodes.length) {
            val node = service.childNodes.item(i) as? Element ?: continue
            if (node.tagName != TAG_METADATA) continue
            if (node.getAttributeNS(ANDROID_URI, ATTR_NAME) != META_DATA_NAME) continue
            val value = node.getAttributeNS(ANDROID_URI, ATTR_VALUE)
            if (value == ACTION || value == ACTION_SHORT) return true
        }
        return false
    }

    private fun isConfigurationActivity(context: XmlContext, activity: Element): Boolean {
        val requireCategory = context.project.minSdk < 30
        for (i in 0 until activity.childNodes.length) {
            val node = activity.childNodes.item(i) as? Element ?: continue
            if (node.tagName != TAG_INTENT_FILTER) continue
            if (!node.containsAction(ACTION)) continue
            if (requireCategory && !node.containsCategory(CATEGORY)) continue
            return true
        }
        return false
    }

    private fun Element.containsAction(action: String): Boolean {
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i) as? Element ?: continue
            if (node.tagName == TAG_ACTION &&
                node.getAttributeNS(ANDROID_URI, ATTR_NAME) == action
            ) {
                return true
            }
        }
        return false
    }

    private fun Element.containsCategory(category: String): Boolean {
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i) as? Element ?: continue
            if (node.tagName == TAG_CATEGORY &&
                node.getAttributeNS(ANDROID_URI, ATTR_NAME) == category
            ) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val META_DATA_NAME =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val ACTION =
            "com.google.android.wearable.watchface.WATCH_FACE_EDITOR"
        private const val ACTION_SHORT = "WATCH_FACE_EDITOR"
        private const val CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val TAG_METADATA = "meta-data"

        @JvmField
        val ISSUE: Issue = Issue.create(
            "WearableActionDuplicate",
            "Duplicate watch face configuration activities found",
            "When a watch face service declares the `wearableConfigurationAction` metadata " +
                "with the `WATCH_FACE_EDITOR` action, there must be exactly one companion " +
                "activity in the same package that handles that action. Declaring more than " +
                "one such activity is an error.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}