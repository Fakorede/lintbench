package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
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
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private val servicesNeedingEditor = mutableListOf<Pair<XmlContext, Element>>()
    private val editorActivities = mutableListOf<Pair<XmlContext, Element>>()

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE, TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> {
                if (hasMetaData(element, "wearableConfigurationAction", "WATCH_FACE_EDITOR")) {
                    servicesNeedingEditor.add(context to element)
                }
            }
            TAG_ACTIVITY -> {
                if (hasAction(element, "WATCH_FACE_EDITOR")) {
                    editorActivities.add(context to element)
                }
            }
        }
    }

    override fun checkMergedProject(context: Context) {
        if (servicesNeedingEditor.isEmpty()) {
            editorActivities.clear()
            return
        }

        val minSdk = context.mainProject.minSdkVersion ?: 1
        val hasValidEditorActivity = editorActivities.any { (_, activity) ->
            minSdk >= 30 || hasCategory(activity, "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION")
        }

        if (!hasValidEditorActivity) {
            for ((ctx, service) in servicesNeedingEditor) {
                ctx.report(
                    ISSUE,
                    service,
                    ctx.getLocation(service),
                    "Missing watch face configuration activity for WATCH_FACE_EDITOR"
                )
            }
        }

        servicesNeedingEditor.clear()
        editorActivities.clear()
    }

    private fun hasMetaData(element: Element, name: String, value: String): Boolean {
        var meta = getFirstSubTagByName(element, TAG_META_DATA)
        while (meta != null) {
            if (meta.getAttributeNS(ANDROID_URI, ATTR_NAME) == name &&
                meta.getAttributeNS(ANDROID_URI, ATTR_VALUE) == value) {
                return true
            }
            meta = getNextTagByName(meta, TAG_META_DATA)
        }
        return false
    }

    private fun hasAction(element: Element, actionName: String): Boolean {
        var filter = getFirstSubTagByName(element, TAG_INTENT_FILTER)
        while (filter != null) {
            var action = getFirstSubTagByName(filter, TAG_ACTION)
            while (action != null) {
                if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == actionName) {
                    return true
                }
                action = getNextTagByName(action, TAG_ACTION)
            }
            filter = getNextTagByName(filter, TAG_INTENT_FILTER)
        }
        return false
    }

    private fun hasCategory(element: Element, categoryName: String): Boolean {
        var filter = getFirstSubTagByName(element, TAG_INTENT_FILTER)
        while (filter != null) {
            var cat = getFirstSubTagByName(filter, TAG_CATEGORY)
            while (cat != null) {
                if (cat.getAttributeNS(ANDROID_URI, ATTR_NAME) == categoryName) {
                    return true
                }
                cat = getNextTagByName(cat, TAG_CATEGORY)
            }
            filter = getNextTagByName(filter, TAG_INTENT_FILTER)
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If and only if a watch face service defines wearableConfigurationAction metadata, with the value WATCH_FACE_EDITOR, there should be an activity in the same package, which has an intent filter for WATCH_FACE_EDITOR (with com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION if minSdkVersion is less than 30).",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(WearableConfigurationActionDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }
}