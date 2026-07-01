package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
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
import java.util.EnumSet

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR = "com.google.android.wearable.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val META_DATA_WEARABLE_CONFIGURATION_ACTION = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val WATCH_FACE_SERVICE = "android.service.wallpaper.WallpaperService"

        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action metadata must match an activity",
            explanation = """
                Only when a watch face service defines `wearableConfigurationAction` metadata with \
                the value `WATCH_FACE_EDITOR`, there should be an activity in the same package \
                which has an intent filter for `WATCH_FACE_EDITOR` \
                (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` \
                if `minSdkVersion` is less than 30).
                
                See https://developer.android.com/training/wearables/watch-faces/configuration
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                EnumSet.of(Scope.MANIFEST)
            )
        )

        private const val TAG_META_DATA = "meta-data"
        private const val TAG_ACTION = "action"
        private const val TAG_CATEGORY = "category"
    }

    // Data collected during manifest scanning
    private data class ServiceInfo(
        val element: Element,
        val context: XmlContext
    )

    private data class ActivityInfo(
        val hasWatchFaceEditorAction: Boolean,
        val hasWearableConfigurationCategory: Boolean
    )

    private var watchFaceServices = mutableListOf<ServiceInfo>()
    private var activitiesWithWatchFaceEditor = mutableListOf<ActivityInfo>()

    override fun beforeCheckFile(context: Context) {
        watchFaceServices = mutableListOf()
        activitiesWithWatchFaceEditor = mutableListOf()
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE, TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> handleService(context, element)
            TAG_ACTIVITY -> handleActivity(context, element)
        }
    }

    private fun handleService(context: XmlContext, element: Element) {
        // Check if this service has wearableConfigurationAction metadata with WATCH_FACE_EDITOR value
        val metaDataElements = element.childNodes
        for (i in 0 until metaDataElements.length) {
            val node = metaDataElements.item(i)
            if (node is Element && node.tagName == TAG_META_DATA) {
                val name = node.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = node.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == META_DATA_WEARABLE_CONFIGURATION_ACTION && value == WATCH_FACE_EDITOR) {
                    watchFaceServices.add(ServiceInfo(element, context))
                    break
                }
            }
        }
    }

    private fun handleActivity(context: XmlContext, element: Element) {
        // Check if this activity has an intent filter with WATCH_FACE_EDITOR action
        var hasWatchFaceEditorAction = false
        var hasWearableConfigurationCategory = false

        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == TAG_INTENT_FILTER) {
                val filterChildren = node.childNodes
                var filterHasAction = false
                var filterHasCategory = false
                for (j in 0 until filterChildren.length) {
                    val filterChild = filterChildren.item(j)
                    if (filterChild is Element) {
                        when (filterChild.tagName) {
                            TAG_ACTION -> {
                                val actionName = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                                if (actionName == WATCH_FACE_EDITOR) {
                                    filterHasAction = true
                                }
                            }
                            TAG_CATEGORY -> {
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
                        hasWearableConfigurationCategory = true
                    }
                }
            }
        }

        if (hasWatchFaceEditorAction) {
            activitiesWithWatchFaceEditor.add(
                ActivityInfo(
                    hasWatchFaceEditorAction = true,
                    hasWearableConfigurationCategory = hasWearableConfigurationCategory
                )
            )
        }
    }

    override fun afterCheckFile(context: Context) {
        if (context !is XmlContext) return

        val minSdkVersion = context.project.minSdk

        for (serviceInfo in watchFaceServices) {
            val xmlContext = serviceInfo.context
            val serviceElement = serviceInfo.element

            // Check if there's a matching activity
            if (activitiesWithWatchFaceEditor.isEmpty()) {
                // No activity with WATCH_FACE_EDITOR action found
                xmlContext.report(
                    ISSUE,
                    serviceElement,
                    xmlContext.getLocation(serviceElement),
                    "Watch face service defines `wearableConfigurationAction` metadata but no " +
                        "activity in the package has an intent filter with action `$WATCH_FACE_EDITOR`"
                )
            } else if (minSdkVersion < 30) {
                // Need to check that at least one activity also has the WEARABLE_CONFIGURATION category
                val hasActivityWithCategory = activitiesWithWatchFaceEditor.any {
                    it.hasWearableConfigurationCategory
                }
                if (!hasActivityWithCategory) {
                    xmlContext.report(
                        ISSUE,
                        serviceElement,
                        xmlContext.getLocation(serviceElement),
                        "Watch face service defines `wearableConfigurationAction` metadata but " +
                            "the activity with `$WATCH_FACE_EDITOR` action intent filter is missing " +
                            "the `$WEARABLE_CONFIGURATION_CATEGORY` category " +
                            "(required when `minSdkVersion` < 30)"
                    )
                }
            }
        }
    }
}