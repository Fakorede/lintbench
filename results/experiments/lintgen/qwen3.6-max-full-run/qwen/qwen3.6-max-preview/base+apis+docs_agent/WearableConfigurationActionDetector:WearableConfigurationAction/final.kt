package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTIVITY_ALIAS
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_MANIFEST
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_MANIFEST)

    override fun visitElement(context: XmlContext, element: Element) {
        val minSdk = runCatching { context.mainProject.minSdkVersion.apiLevel }.getOrDefault(1)

        val services = element.getElementsByTagName(TAG_SERVICE)
        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            if (!hasWearableConfigMetadata(service)) continue

            val activities = element.getElementsByTagName(TAG_ACTIVITY)
            val aliases = element.getElementsByTagName(TAG_ACTIVITY_ALIAS)

            var hasMatchingActivity = false

            for (j in 0 until activities.length) {
                if (hasEditorIntentFilter(activities.item(j) as Element, minSdk)) {
                    hasMatchingActivity = true
                    break
                }
            }
            if (!hasMatchingActivity) {
                for (j in 0 until aliases.length) {
                    if (hasEditorIntentFilter(aliases.item(j) as Element, minSdk)) {
                        hasMatchingActivity = true
                        break
                    }
                }
            }

            if (!hasMatchingActivity) {
                context.report(
                    ISSUE,
                    context.getLocation(service),
                    "Watch face service defines `wearableConfigurationAction` metadata but no matching activity with `WATCH_FACE_EDITOR` intent filter exists in the same package."
                )
            }
        }
    }

    private fun hasWearableConfigMetadata(service: Element): Boolean {
        val children = service.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == TAG_META_DATA) {
                val meta = node as Element
                val name = meta.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = meta.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name.endsWith("wearableConfigurationAction") && value.endsWith("WATCH_FACE_EDITOR")) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasEditorIntentFilter(activity: Element, minSdk: Int): Boolean {
        val children = activity.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == TAG_INTENT_FILTER) {
                val filter = node as Element
                var hasAction = false
                var hasCategory = false
                val filterChildren = filter.childNodes
                for (j in 0 until filterChildren.length) {
                    val fc = filterChildren.item(j)
                    if (fc.nodeType == Node.ELEMENT_NODE) {
                        val fcElement = fc as Element
                        when (fcElement.tagName) {
                            TAG_ACTION -> {
                                if (fcElement.getAttributeNS(ANDROID_URI, ATTR_NAME).endsWith("WATCH_FACE_EDITOR")) {
                                    hasAction = true
                                }
                            }
                            TAG_CATEGORY -> {
                                if (fcElement.getAttributeNS(ANDROID_URI, ATTR_NAME).endsWith("WEARABLE_CONFIGURATION")) {
                                    hasCategory = true
                                }
                            }
                        }
                    }
                }
                if (hasAction && (minSdk >= 30 || hasCategory)) {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = "Only when a watch face service defines `wearableConfigurationAction` metadata, with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package, which has an intent filter for `WATCH_FACE_EDITOR` (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if `minSdkVersion` is less than 30).",
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