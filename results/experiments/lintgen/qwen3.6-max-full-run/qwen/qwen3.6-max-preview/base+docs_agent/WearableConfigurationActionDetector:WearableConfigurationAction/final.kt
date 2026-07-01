package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTIVITY_ALIAS
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_MANIFEST
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_ACTION
import com.android.tools.lint.detector.api.Category
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

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_MANIFEST)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != TAG_MANIFEST) return

        val minSdk = context.project.minSdkVersion?.apiLevel ?: 1
        val requireCategory = minSdk < 30

        val applicationNodes = element.getElementsByTagName(TAG_APPLICATION)
        if (applicationNodes.length == 0) return
        val application = applicationNodes.item(0) as? Element ?: return

        val servicesNeedingEditor = mutableListOf<Element>()
        var hasMatchingActivity = false

        val children = application.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val tag = child.nodeName
            when (tag) {
                TAG_SERVICE -> {
                    if (hasWearableConfigActionMetadata(child as Element)) {
                        servicesNeedingEditor.add(child)
                    }
                }
                TAG_ACTIVITY, TAG_ACTIVITY_ALIAS -> {
                    if (hasWatchFaceEditorIntentFilter(child as Element, requireCategory)) {
                        hasMatchingActivity = true
                    }
                }
            }
        }

        if (servicesNeedingEditor.isNotEmpty() && !hasMatchingActivity) {
            val message = if (requireCategory) {
                "Watch face service defines `wearableConfigurationAction` metadata but no matching activity with " +
                    "`WATCH_FACE_EDITOR` intent filter and `WEARABLE_CONFIGURATION` category exists in the manifest."
            } else {
                "Watch face service defines `wearableConfigurationAction` metadata but no matching activity with " +
                    "`WATCH_FACE_EDITOR` intent filter exists in the manifest."
            }
            for (service in servicesNeedingEditor) {
                context.report(ISSUE, service, context.getLocation(service), message)
            }
        }
    }

    private fun getAttr(element: Element, name: String): String {
        val nsVal = element.getAttributeNS(ANDROID_URI, name)
        return if (nsVal.isNotEmpty()) nsVal else element.getAttribute(name) ?: ""
    }

    private fun hasWearableConfigActionMetadata(service: Element): Boolean {
        val children = service.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == TAG_META_DATA) {
                val meta = child as Element
                val name = getAttr(meta, ATTR_NAME)
                val value = getAttr(meta, ATTR_VALUE)
                if (name.isNotEmpty() && name.endsWith("wearableConfigurationAction") &&
                    value.isNotEmpty() && value.endsWith("WATCH_FACE_EDITOR")) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasWatchFaceEditorIntentFilter(activity: Element, requireCategory: Boolean): Boolean {
        val children = activity.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == TAG_INTENT_FILTER) {
                val filter = child as Element
                var hasAction = false
                var hasCategory = !requireCategory
                val filterChildren = filter.childNodes
                for (j in 0 until filterChildren.length) {
                    val sub = filterChildren.item(j)
                    if (sub.nodeType == Node.ELEMENT_NODE) {
                        when (sub.nodeName) {
                            TAG_ACTION -> {
                                val actionName = getAttr(sub as Element, ATTR_NAME)
                                if (actionName.isNotEmpty() && actionName.endsWith("WATCH_FACE_EDITOR")) {
                                    hasAction = true
                                }
                            }
                            TAG_CATEGORY -> {
                                val catName = getAttr(sub as Element, ATTR_NAME)
                                if (catName.isNotEmpty() && catName.endsWith("WEARABLE_CONFIGURATION")) {
                                    hasCategory = true
                                }
                            }
                        }
                    }
                }
                if (hasAction && hasCategory) return true
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = "When a watch face service defines `wearableConfigurationAction` metadata with the value " +
                "`WATCH_FACE_EDITOR`, there must be an activity in the same package with an intent filter for " +
                "`WATCH_FACE_EDITOR`. If minSdkVersion < 30, the intent filter must also include the category " +
                "`com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(WearableConfigurationActionDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }
}