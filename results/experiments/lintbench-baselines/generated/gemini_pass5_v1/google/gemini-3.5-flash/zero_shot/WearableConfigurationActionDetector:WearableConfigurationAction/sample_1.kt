package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf("manifest")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val document = element.ownerDocument ?: return
        val services = document.getElementsByTagName("service")
        val metadataElements = mutableListOf<Element>()

        for (i in 0 until services.length) {
            val service = services.item(i) as? Element ?: continue
            val childNodes = service.childNodes
            for (j in 0 until childNodes.length) {
                val child = childNodes.item(j) as? Element ?: continue
                if (child.tagName == "meta-data") {
                    val name = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                    val value = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
                    if (name == "com.google.android.wearable.watchface.wearableConfigurationAction" &&
                        value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR") {
                        metadataElements.add(child)
                    }
                }
            }
        }

        if (metadataElements.isEmpty()) {
            return
        }

        val activities = document.getElementsByTagName("activity")
        var activityFound = false
        var categoryMissingForFoundAction = false
        val minSdkVersion = context.project.minSdkVersion.apiLevel

        for (i in 0 until activities.length) {
            val activity = activities.item(i) as? Element ?: continue
            val intentFilters = activity.getElementsByTagName("intent-filter")
            for (j in 0 until intentFilters.length) {
                val filter = intentFilters.item(j) as? Element ?: continue
                
                val actions = filter.getElementsByTagName("action")
                var hasAction = false
                for (k in 0 until actions.length) {
                    val action = actions.item(k) as? Element ?: continue
                    val actionName = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                    if (actionName == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR") {
                        hasAction = true
                        break
                    }
                }

                if (hasAction) {
                    if (minSdkVersion < 30) {
                        val categories = filter.getElementsByTagName("category")
                        var hasCategory = false
                        for (k in 0 until categories.length) {
                            val category = categories.item(k) as? Element ?: continue
                            val catName = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                            if (catName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                                htmlCategory = true
                                hasCategory = true
                                break
                            }
                        }
                        if (hasCategory) {
                            activityFound = true
                        } else {
                            categoryMissingForFoundAction = true
                        }
                    } else {
                        activityFound = true
                    }
                }
            }
        }

        if (!activityFound) {
            for (metaData in metadataElements) {
                val message = if (categoryMissingForFoundAction) {
                    "Activity with `WATCH_FACE_EDITOR` action is missing the required category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` (required when minSdkVersion < 30)"
                } else {
                    "An activity with intent-filter action `com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR` must be configured in the manifest"
                }
                context.report(
                    ISSUE,
                    metaData,
                    context.getLocation(metaData),
                    message
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                Only when a watch face service defines `wearableConfigurationAction` metadata, with the value `WATCH_FACE_EDITOR`, \
                there should be an activity in the same package, which has an intent filter for `WATCH_FACE_EDITOR` \
                (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if `minSdkVersion` is less than 30).
                """.trimIndent(),
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