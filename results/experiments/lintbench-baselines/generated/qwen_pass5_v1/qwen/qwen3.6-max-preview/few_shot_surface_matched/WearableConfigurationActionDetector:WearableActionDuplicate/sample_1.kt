package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private val serviceLocations = mutableListOf<Location>()
    private val activityLocations = mutableListOf<Location>()

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE, TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tag = element.tagName
        if (tag == TAG_SERVICE) {
            var meta = getFirstSubTagByName(element, TAG_META_DATA)
            while (meta != null) {
                val name = meta.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = meta.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == "wearableConfigurationAction" && value == "WATCH_FACE_EDITOR") {
                    serviceLocations.add(context.getLocation(element))
                    break
                }
                meta = getNextTagByName(meta, TAG_META_DATA)
            }
        } else if (tag == TAG_ACTIVITY) {
            val minSdk = context.mainProject.minSdk
            var hasAction = false
            var hasCategory = false

            var intentFilter = getFirstSubTagByName(element, TAG_INTENT_FILTER)
            while (intentFilter != null) {
                var action = getFirstSubTagByName(intentFilter, TAG_ACTION)
                while (action != null) {
                    if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == "WATCH_FACE_EDITOR") {
                        hasAction = true
                    }
                    action = getNextTagByName(action, TAG_ACTION)
                }

                if (minSdk < 30) {
                    var category = getFirstSubTagByName(intentFilter, TAG_CATEGORY)
                    while (category != null) {
                        if (category.getAttributeNS(ANDROID_URI, ATTR_NAME) ==
                            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                            hasCategory = true
                        }
                        category = getNextTagByName(category, TAG_CATEGORY)
                    }
                }
                intentFilter = getNextTagByName(intentFilter, TAG_INTENT_FILTER)
            }

            if (hasAction && (minSdk >= 30 || hasCategory)) {
                activityLocations.add(context.getLocation(element))
            }
        }
    }

    override fun checkMergedProject(context: Context) {
        if (serviceLocations.isEmpty()) {
            return
        }

        if (activityLocations.isEmpty()) {
            for (location in serviceLocations) {
                context.report(
                    ISSUE,
                    location,
                    "Missing watch face configuration activity for WATCH_FACE_EDITOR"
                )
            }
        } else if (activityLocations.size > 1) {
            for (location in activityLocations) {
                context.report(
                    ISSUE,
                    location,
                    "Duplicate watch face configuration activities found"
                )
            }
        }

        serviceLocations.clear()
        activityLocations.clear()
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If and only if a watch face service defines wearableConfigurationAction metadata, " +
                "with the value WATCH_FACE_EDITOR, there should be an activity in the same package, " +
                "which has an intent filter for WATCH_FACE_EDITOR (with " +
                "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION if minSdkVersion is less than 30).",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}