package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_MIN_SDK_VERSION
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_SDK
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
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR_ACTION =
            "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WEARABLE_CONFIGURATION_ACTION_METADATA =
            "com.google.android.wearable.watchface.wearableConfigurationAction"

        val ISSUE: Issue = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines `wearableConfigurationAction` metadata with the \
                value `WATCH_FACE_EDITOR`, there should be an activity in the same package that has \
                an intent filter for `WATCH_FACE_EDITOR`. If `minSdkVersion` is less than 30, the \
                activity's intent filter should also include the \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.
                
                See https://developer.android.com/training/wearables/watch-faces/configuration
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    // Per-manifest state
    private var minSdkVersion: Int = 1
    private var hasWatchFaceEditorMetadata: Boolean = false
    private var watchFaceEditorMetadataElement: Element? = null
    private var hasMatchingActivity: Boolean = false

    override fun beforeCheckFile(context: Context) {
        minSdkVersion = 1
        hasWatchFaceEditorMetadata = false
        watchFaceEditorMetadataElement = null
        hasMatchingActivity = false
    }

    override fun getApplicableElements(): Collection<String> = listOf(
        TAG_USES_SDK,
        TAG_META_DATA,
        TAG_ACTIVITY
    )

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_USES_SDK -> {
                val minSdk = element.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
                if (minSdk.isNotEmpty()) {
                    minSdkVersion = minSdk.toIntOrNull() ?: 1
                }
            }
            TAG_META_DATA -> {
                // Check if this meta-data is inside a service element
                val parent = element.parentNode
                if (parent is Element && parent.tagName == TAG_SERVICE) {
                    val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    val value = element.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                    if (name == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                        value == WATCH_FACE_EDITOR_ACTION
                    ) {
                        hasWatchFaceEditorMetadata = true
                        watchFaceEditorMetadataElement = element
                    }
                }
            }
            TAG_ACTIVITY -> {
                // Check if this activity has an intent filter with WATCH_FACE_EDITOR action
                if (activityHasWatchFaceEditorIntentFilter(element)) {
                    hasMatchingActivity = true
                }
            }
        }
    }

    private fun activityHasWatchFaceEditorIntentFilter(activityElement: Element): Boolean {
        val children = activityElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_INTENT_FILTER) {
                if (intentFilterHasWatchFaceEditorAction(child)) {
                    return true
                }
            }
        }
        return false
    }

    private fun intentFilterHasWatchFaceEditorAction(intentFilter: Element): Boolean {
        var hasAction = false
        var hasCategory = false

        val children = intentFilter.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                when (child.tagName) {
                    "action" -> {
                        val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                        if (name == WATCH_FACE_EDITOR_ACTION) {
                            hasAction = true
                        }
                    }
                    "category" -> {
                        val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                        if (name == WEARABLE_CONFIGURATION_CATEGORY) {
                            hasCategory = true
                        }
                    }
                }
            }
        }

        return if (minSdkVersion < 30) {
            hasAction && hasCategory
        } else {
            hasAction
        }
    }

    override fun afterCheckFile(context: Context) {
        if (hasWatchFaceEditorMetadata && !hasMatchingActivity) {
            val element = watchFaceEditorMetadataElement ?: return
            val xmlContext = context as? XmlContext ?: return

            val message = if (minSdkVersion < 30) {
                "Missing activity with intent filter for action `$WATCH_FACE_EDITOR_ACTION` " +
                    "and category `$WEARABLE_CONFIGURATION_CATEGORY` (required when " +
                    "`minSdkVersion` < 30)"
            } else {
                "Missing activity with intent filter for action `$WATCH_FACE_EDITOR_ACTION`"
            }

            xmlContext.report(
                issue = ISSUE,
                scope = element,
                location = xmlContext.getLocation(element),
                message = message
            )
        }
    }
}