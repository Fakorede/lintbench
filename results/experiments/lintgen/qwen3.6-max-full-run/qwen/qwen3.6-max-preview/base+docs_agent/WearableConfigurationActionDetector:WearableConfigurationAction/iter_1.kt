package com.android.tools.lint.checks

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

        val minSdk = context.project.minSdkVersion
        val requireCategory = minSdk < 30

        val application = element.getElementsByTagName(TAG_APPLICATION).item(0) as? Element ?: return

        val servicesNeedingEditor = mutableListOf<Element>()
        var hasMatchingActivity = false

        application.forEachChild { child ->
            when (child.tagName) {
                TAG_SERVICE -> {
                    if (hasWearableConfigActionMetadata(child)) {
                        servicesNeedingEditor.add(child)
                    }
                }
                TAG_ACTIVITY, TAG_ACTIVITY_ALIAS -> {
                    if (hasWatchFaceEditorIntentFilter(child, requireCategory)) {
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

    private fun hasWearableConfigActionMetadata(service: Element): Boolean {
        var found = false
        service.forEachChild { child ->
            if (child.tagName == TAG_META_DATA) {
                val name = child.getAttribute(ATTR_NAME)
                val value = child.getAttribute(ATTR_VALUE)
                if (name.endsWith("wearableConfigurationAction") && value == "WATCH_FACE_EDITOR") {
                    found = true
                }
            }
        }
        return found
    }

    private fun hasWatchFaceEditorIntentFilter(activity: Element, requireCategory: Boolean): Boolean {
        var found = false
        activity.forEachChild { child ->
            if (child.tagName == TAG_INTENT_FILTER) {
                var hasAction = false
                var hasCategory = !requireCategory
                child.forEachChild { sub ->
                    when (sub.tagName) {
                        TAG_ACTION -> {
                            val actionName = sub.getAttribute(ATTR_NAME)
                            if (actionName.endsWith("WATCH_FACE_EDITOR")) {
                                hasAction = true
                            }
                        }
                        TAG_CATEGORY -> {
                            val catName = sub.getAttribute(ATTR_NAME)
                            if (catName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                                hasCategory = true
                            }
                        }
                    }
                }
                if (hasAction && hasCategory) {
                    found = true
                }
            }
        }
        return found
    }

    private fun Element.forEachChild(action: (Element) -> Unit) {
        var child: Node? = firstChild
        while (child != null) {
            if (child is Element) action(child)
            child = child.nextSibling
        }
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