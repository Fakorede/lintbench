package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines the `wearableConfigurationAction` metadata \
                with the value `WATCH_FACE_EDITOR`, there must be an activity in the same \
                package that has an intent filter for `WATCH_FACE_EDITOR`. If the `minSdkVersion` \
                is less than 30, this intent filter must also include the category \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private const val META_DATA_NAME = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val META_DATA_VALUE = "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR"
        private const val ACTION_NAME = "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR"
        private const val CATEGORY_NAME = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != SdkConstants.TAG_MANIFEST) return

        val minSdk = context.project.minSdkVersion.apiLevel
        val needsCategory = minSdk < 30

        // Find all services with the wearableConfigurationAction metadata
        val serviceMetadataList = mutableListOf<Element>()
        val services = document.getElementsByTagName(SdkConstants.TAG_SERVICE)
        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            val childNodes = service.childNodes
            for (j in 0 until childNodes.length) {
                val child = childNodes.item(j)
                if (child is Element && child.tagName == SdkConstants.TAG_META_DATA) {
                    val name = getAndroidAttribute(child, SdkConstants.ATTR_NAME)
                    val value = getAndroidAttribute(child, SdkConstants.ATTR_VALUE)
                    if (name == META_DATA_NAME && (value == META_DATA_VALUE || value == "WATCH_FACE_EDITOR")) {
                        serviceMetadataList.add(child)
                    }
                }
            }
        }

        // Find all activities with the WATCH_FACE_EDITOR action
        class ActivityInfo(
            val activity: Element,
            val intentFilter: Element,
            val hasAction: Boolean,
            val hasCategory: Boolean
        )

        val activityInfos = mutableListOf<ActivityInfo>()
        val activities = document.getElementsByTagName(SdkConstants.TAG_ACTIVITY)
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            val childNodes = activity.childNodes
            for (j in 0 until childNodes.length) {
                val intentFilter = childNodes.item(j)
                if (intentFilter is Element && intentFilter.tagName == SdkConstants.TAG_INTENT_FILTER) {
                    var hasAction = false
                    var hasCategory = false
                    val filterChildren = intentFilter.childNodes
                    for (k in 0 until filterChildren.length) {
                        val filterChild = filterChildren.item(k)
                        if (filterChild is Element) {
                            if (filterChild.tagName == SdkConstants.TAG_ACTION) {
                                val name = getAndroidAttribute(filterChild, SdkConstants.ATTR_NAME)
                                if (name == ACTION_NAME || name == "WATCH_FACE_EDITOR") {
                                    hasAction = true
                                }
                            } else if (filterChild.tagName == SdkConstants.TAG_CATEGORY) {
                                val name = getAndroidAttribute(filterChild, SdkConstants.ATTR_NAME)
                                if (name == CATEGORY_NAME) {
                                    hasCategory = true
                                }
                            }
                        }
                    }
                    if (hasAction) {
                        activityInfos.add(ActivityInfo(activity, intentFilter, hasAction, hasCategory))
                    }
                }
            }
        }

        val hasValidActivity = activityInfos.any { it.hasAction && (!needsCategory || it.hasCategory) }

        if (serviceMetadataList.isNotEmpty() && !hasValidActivity) {
            for (metaData in serviceMetadataList) {
                val message = if (needsCategory) {
                    "To use WATCH_FACE_EDITOR configuration, there must be an activity with an intent-filter for action `$ACTION_NAME` and category `$CATEGORY_NAME` (required for minSdkVersion < 30)."
                } else {
                    "To use WATCH_FACE_EDITOR configuration, there must be an activity with an intent-filter for action `$ACTION_NAME`."
                }
                context.report(
                    ISSUE,
                    metaData,
                    context.getLocation(metaData),
                    message
                )
            }
        }

        if (serviceMetadataList.isEmpty() && activityInfos.isNotEmpty()) {
            for (info in activityInfos) {
                val message = "An activity with WATCH_FACE_EDITOR intent-filter requires the watch face service to define the wearableConfigurationAction metadata."
                context.report(
                    ISSUE,
                    info.activity,
                    context.getLocation(info.activity),
                    message
                )
            }
        }
    }

    private fun getAndroidAttribute(element: Element, localName: String): String {
        val attr = element.getAttributeNS(SdkConstants.ANDROID_URI, localName)
        if (attr.isNotEmpty()) {
            return attr
        }
        return element.getAttribute("android:$localName")
    }
}