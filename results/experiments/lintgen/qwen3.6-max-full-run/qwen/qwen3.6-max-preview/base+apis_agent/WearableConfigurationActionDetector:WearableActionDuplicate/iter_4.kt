package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
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

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = listOf("manifest")

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.MANIFEST

    override fun visitElement(context: XmlContext, element: Element) {
        val activities = element.getElementsByTagName(TAG_ACTIVITY)
        val configActivities = mutableListOf<Element>()

        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            if (hasConfigIntentFilter(activity)) {
                configActivities.add(activity)
            }
        }

        if (configActivities.size > 1) {
            for (activity in configActivities) {
                context.report(
                    ISSUE,
                    activity,
                    context.getLocation(activity),
                    "Duplicate watch face configuration activities found"
                )
            }
        }
    }

    private fun hasConfigIntentFilter(activity: Element): Boolean {
        val intentFilters = activity.getElementsByTagName(TAG_INTENT_FILTER)
        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as Element
            var hasAction = false
            val children = filter.childNodes
            for (j in 0 until children.length) {
                val child = children.item(j)
                if (child is Element) {
                    val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (child.tagName == TAG_ACTION && name.endsWith("WATCH_FACE_EDITOR")) {
                        hasAction = true
                    }
                }
            }
            if (hasAction) return true
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If a watch face service defines wearableConfigurationAction metadata with the value WATCH_FACE_EDITOR, there should be exactly one activity in the same package with an intent filter for WATCH_FACE_EDITOR (and the WEARABLE_CONFIGURATION category if minSdkVersion < 30).",
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