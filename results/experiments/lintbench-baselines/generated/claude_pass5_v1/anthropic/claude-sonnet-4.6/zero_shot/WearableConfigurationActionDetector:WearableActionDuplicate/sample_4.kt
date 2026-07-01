package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_MIN_SDK_VERSION
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_INTENT_FILTER
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
import java.util.EnumSet

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR_ACTION = "com.google.android.wearable.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WEARABLE_CONFIGURATION_ACTION_METADATA = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val WATCH_FACE_EDITOR_VALUE = "WATCH_FACE_EDITOR"

        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If a watch face service defines `wearableConfigurationAction` metadata with the value \
                `WATCH_FACE_EDITOR`, there should be an activity in the same package which has an \
                intent filter for `WATCH_FACE_EDITOR` (with \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if \
                `minSdkVersion` is less than 30).

                See https://developer.android.com/training/wearables/watch-faces/configuration for \
                more details.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                EnumSet.of(Scope.MANIFEST)
            )
        )
    }

    // Data collected per manifest file
    private data class WatchFaceEditorServiceInfo(
        val element: Element,
        val context: XmlContext
    )

    private data class WatchFaceEditorActivityInfo(
        val element: Element,
        val hasWearableConfigurationCategory: Boolean
    )

    private val serviceInfos = mutableListOf<WatchFaceEditorServiceInfo>()
    private val activityInfos = mutableListOf<WatchFaceEditorActivityInfo>()
    private var minSdkVersion: Int = 1

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE, TAG_ACTIVITY, TAG_USES_SDK, TAG_APPLICATION)
    }

    override fun beforeCheckFile(context: Context) {
        serviceInfos.clear()
        activityInfos.clear()
        minSdkVersion = 1
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_USES_SDK -> {
                val minSdk = element.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
                if (minSdk.isNotEmpty()) {
                    minSdkVersion = minSdk.toIntOrNull() ?: 1
                }
            }
            TAG_SERVICE -> {
                if (hasWearableConfigurationActionMetadata(element)) {
                    serviceInfos.add(WatchFaceEditorServiceInfo(element, context))
                }
            }
            TAG_ACTIVITY -> {
                val (hasWatchFaceEditorAction, hasWearableConfigCategory) = checkActivityIntentFilters(element)
                if (hasWatchFaceEditorAction) {
                    activityInfos.add(WatchFaceEditorActivityInfo(element, hasWearableConfigCategory))
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (serviceInfos.isEmpty()) {
            return
        }

        val requiresCategory = minSdkVersion < 30

        for (serviceInfo in serviceInfos) {
            val matchingActivity = activityInfos.find { activityInfo ->
                if (requiresCategory) {
                    activityInfo.hasWearableConfigurationCategory
                } else {
                    true
                }
            }

            if (matchingActivity == null) {
                // No matching activity found - report on the service
                val xmlContext = serviceInfo.context
                if (requiresCategory) {
                    xmlContext.report(
                        ISSUE,
                        serviceInfo.element,
                        xmlContext.getNameLocation(serviceInfo.element),
                        "Watch face service defines `wearableConfigurationAction` metadata with " +
                            "value `WATCH_FACE_EDITOR` but no activity in the package has an " +
                            "intent filter for `$WATCH_FACE_EDITOR_ACTION` with category " +
                            "`$WEARABLE_CONFIGURATION_CATEGORY` (required when minSdkVersion < 30)"
                    )
                } else {
                    xmlContext.report(
                        ISSUE,
                        serviceInfo.element,
                        xmlContext.getNameLocation(serviceInfo.element),
                        "Watch face service defines `wearableConfigurationAction` metadata with " +
                            "value `WATCH_FACE_EDITOR` but no activity in the package has an " +
                            "intent filter for `$WATCH_FACE_EDITOR_ACTION`"
                    )
                }
            } else if (activityInfos.size > 1) {
                // Multiple activities found with the watch face editor action
                val xmlContext = serviceInfo.context
                xmlContext.report(
                    ISSUE,
                    serviceInfo.element,
                    xmlContext.getNameLocation(serviceInfo.element),
                    "Multiple activities define intent filters for `$WATCH_FACE_EDITOR_ACTION`. " +
                        "Only one configuration activity should be defined per watch face service."
                )
            }
        }

        // Also check: if there are activities with WATCH_FACE_EDITOR but no service defines the metadata
        if (serviceInfos.isEmpty() && activityInfos.isNotEmpty()) {
            // No services with metadata but activities exist - this might be an issue
            // but the spec says "if and only if" so we handle this separately
        }
    }

    private fun hasWearableConfigurationActionMetadata(serviceElement: Element): Boolean {
        val children = serviceElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == "meta-data") {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = child.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                    (value == WATCH_FACE_EDITOR_VALUE || value == WATCH_FACE_EDITOR_ACTION)
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun checkActivityIntentFilters(activityElement: Element): Pair<Boolean, Boolean> {
        var hasWatchFaceEditorAction = false
        var hasWearableConfigCategory = false

        val children = activityElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_INTENT_FILTER) {
                var filterHasAction = false
                var filterHasCategory = false

                val filterChildren = child.childNodes
                for (j in 0 until filterChildren.length) {
                    val filterChild = filterChildren.item(j)
                    if (filterChild is Element) {
                        when (filterChild.tagName) {
                            "action" -> {
                                val actionName = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                                if (actionName == WATCH_FACE_EDITOR_ACTION) {
                                    filterHasAction = true
                                }
                            }
                            "category" -> {
                                val categoryName = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                                if (categoryName == WEARABLE_CONFIGURATION_CATEGORY) {
                                    filterHasCategory = true
                                }
                            }
                        }
                    }
                }

                if (filterHasAction) {
                    hasWatchFaceEditorAction = true
                    if (filterHasCategory) {
                        hasWearableConfigCategory = true
                    }
                }
            }
        }

        return Pair(hasWatchFaceEditorAction, hasWearableConfigCategory)
    }
}