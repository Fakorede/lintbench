package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import com.android.xml.AndroidManifest
import org.w3c.dom.Element
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR = "com.google.android.wearable.watchface.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val META_DATA_NAME = "com.google.android.wearable.watchface.wearableConfigurationAction"

        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                Only when a watch face service defines `wearableConfigurationAction` metadata, \
                with the value `WATCH_FACE_EDITOR`, there should be an activity in the same \
                package, which has an intent filter for `WATCH_FACE_EDITOR` \
                (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` \
                if `minSdkVersion` is less than 30).
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            ),
            moreInfo = "https://developer.android.com/training/wearables/watch-faces/configuration"
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(SdkConstants.TAG_APPLICATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != SdkConstants.TAG_APPLICATION) return

        // Find all services with wearableConfigurationAction metadata = WATCH_FACE_EDITOR
        val services = mutableListOf<Element>()
        val activities = mutableListOf<Element>()

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val childElement = child as Element
            when (childElement.tagName) {
                SdkConstants.TAG_SERVICE -> services.add(childElement)
                SdkConstants.TAG_ACTIVITY -> activities.add(childElement)
            }
        }

        // Check each service for the metadata
        for (service in services) {
            if (!hasWatchFaceEditorMetadata(service)) continue

            // This service declares wearableConfigurationAction = WATCH_FACE_EDITOR
            // Check if there's an activity with the right intent filter
            val minSdk = context.mainProject.minSdkVersion.apiLevel
            val requiresCategory = minSdk < 30

            val hasMatchingActivity = activities.any { activity ->
                hasWatchFaceEditorIntentFilter(activity, requiresCategory)
            }

            if (!hasMatchingActivity) {
                val metaDataElement = getMetaDataElement(service)
                val locationElement = metaDataElement ?: service
                if (requiresCategory) {
                    context.report(
                        ISSUE,
                        locationElement,
                        context.getLocation(locationElement),
                        "Watch face configuration activity must have an intent filter for " +
                                "`$WATCH_FACE_EDITOR` action and " +
                                "`$WEARABLE_CONFIGURATION_CATEGORY` category"
                    )
                } else {
                    context.report(
                        ISSUE,
                        locationElement,
                        context.getLocation(locationElement),
                        "Watch face configuration activity must have an intent filter for " +
                                "`$WATCH_FACE_EDITOR` action"
                    )
                }
            }
        }

        // Also check: if an activity has WATCH_FACE_EDITOR intent filter but no service
        // has the metadata, that might be an issue too - but per spec, we only check
        // when service defines the metadata. Let's also check the reverse:
        // activities with WATCH_FACE_EDITOR but no service with metadata
        for (activity in activities) {
            val minSdk = context.mainProject.minSdkVersion.apiLevel
            val requiresCategory = minSdk < 30
            if (!hasWatchFaceEditorIntentFilter(activity, false)) continue

            // Activity has WATCH_FACE_EDITOR intent filter
            // Check if any service has the metadata
            val hasMatchingService = services.any { service ->
                hasWatchFaceEditorMetadata(service)
            }

            if (!hasMatchingService) {
                context.report(
                    ISSUE,
                    activity,
                    context.getLocation(activity),
                    "Watch face configuration activity must be paired with a watch face service " +
                            "that has `wearableConfigurationAction` metadata set to `WATCH_FACE_EDITOR`"
                )
            }
        }
    }

    private fun hasWatchFaceEditorMetadata(service: Element): Boolean {
        val children = service.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val childElement = child as Element
            if (childElement.tagName == SdkConstants.TAG_META_DATA) {
                val name = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                val value = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
                if (name == META_DATA_NAME && value == WATCH_FACE_EDITOR) {
                    return true
                }
            }
        }
        return false
    }

    private fun getMetaDataElement(service: Element): Element? {
        val children = service.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val childElement = child as Element
            if (childElement.tagName == SdkConstants.TAG_META_DATA) {
                val name = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                val value = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
                if (name == META_DATA_NAME && value == WATCH_FACE_EDITOR) {
                    return childElement
                }
            }
        }
        return null
    }

    private fun hasWatchFaceEditorIntentFilter(activity: Element, requiresCategory: Boolean): Boolean {
        val children = activity.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val childElement = child as Element
            if (childElement.tagName == AndroidManifest.NODE_INTENT) {
                if (intentFilterHasAction(childElement, WATCH_FACE_EDITOR)) {
                    if (!requiresCategory) return true
                    if (intentFilterHasCategory(childElement, WEARABLE_CONFIGURATION_CATEGORY)) return true
                }
            }
        }
        return false
    }

    private fun intentFilterHasAction(intentFilter: Element, action: String): Boolean {
        val children = intentFilter.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val childElement = child as Element
            if (childElement.tagName == SdkConstants.TAG_ACTION) {
                val name = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (name == action) return true
            }
        }
        return false
    }

    private fun intentFilterHasCategory(intentFilter: Element, category: String): Boolean {
        val children = intentFilter.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val childElement = child as Element
            if (childElement.tagName == SdkConstants.TAG_CATEGORY) {
                val name = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (name == category) return true
            }
        }
        return false
    }
}