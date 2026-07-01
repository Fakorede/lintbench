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

    private val metadataElements = mutableListOf<Element>()
    private var hasActivityWithAction = false
    private var hasActivityWithCategory = false

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE, TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> checkService(element)
            TAG_ACTIVITY -> checkActivity(element)
        }
    }

    private fun checkService(service: Element) {
        var metaData = getFirstSubTagByName(service, TAG_META_DATA)
        while (metaData != null) {
            val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
            val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
            if (name == "wearableConfigurationAction" && value == "WATCH_FACE_EDITOR") {
                metadataElements.add(metaData)
                break
            }
            metaData = getNextTagByName(metaData, TAG_META_DATA)
        }
    }

    private fun checkActivity(activity: Element) {
        var intentFilter = getFirstSubTagByName(activity, TAG_INTENT_FILTER)
        while (intentFilter != null) {
            var hasAction = false
            var hasCategory = false
            var action = getFirstSubTagByName(intentFilter, TAG_ACTION)
            while (action != null) {
                if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == "WATCH_FACE_EDITOR") {
                    hasAction = true
                }
                action = getNextTagByName(action, TAG_ACTION)
            }
            if (hasAction) {
                hasActivityWithAction = true
                var category = getFirstSubTagByName(intentFilter, TAG_CATEGORY)
                while (category != null) {
                    if (category.getAttributeNS(ANDROID_URI, ATTR_NAME) == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                        hasCategory = true
                    }
                    category = getNextTagByName(category, TAG_CATEGORY)
                }
                if (hasCategory) {
                    hasActivityWithCategory = true
                }
            }
            intentFilter = getNextTagByName(intentFilter, TAG_INTENT_FILTER)
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (metadataElements.isEmpty()) return
        val xmlContext = context as? XmlContext ?: return

        val minSdk = context.mainProject.minSdkVersion ?: 1

        if (!hasActivityWithAction) {
            for (metaData in metadataElements) {
                xmlContext.report(
                    ISSUE,
                    metaData,
                    xmlContext.getLocation(metaData),
                    "Watch face service defines wearableConfigurationAction metadata but no activity handles the WATCH_FACE_EDITOR intent"
                )
            }
        } else if (minSdk < 30 && !hasActivityWithCategory) {
            for (metaData in metadataElements) {
                xmlContext.report(
                    ISSUE,
                    metaData,
                    xmlContext.getLocation(metaData),
                    "Watch face service defines wearableConfigurationAction metadata for minSdk < 30, but the WATCH_FACE_EDITOR activity is missing the required WEARABLE_CONFIGURATION category"
                )
            }
        }

        metadataElements.clear()
        hasActivityWithAction = false
        hasActivityWithCategory = false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action metadata must match an activity",
            explanation = "When a watch face service defines wearableConfigurationAction metadata with the value WATCH_FACE_EDITOR, there should be an activity in the same package which has an intent filter for WATCH_FACE_EDITOR. If minSdkVersion is less than 30, the intent filter must also include the com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION category.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(WearableConfigurationActionDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }
}