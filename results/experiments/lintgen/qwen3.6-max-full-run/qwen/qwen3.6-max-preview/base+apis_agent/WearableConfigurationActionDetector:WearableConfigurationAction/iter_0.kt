package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import com.android.utils.DomUtils
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = listOf(SdkConstants.TAG_MANIFEST)

    override fun visitElement(context: XmlContext, element: Element) {
        val minSdk = context.mainProject.minSdkVersion ?: 1
        val metadataElements = mutableListOf<Element>()
        var hasValidActivity = false

        val application = DomUtils.getChildren(element).find { it.tagName == SdkConstants.TAG_APPLICATION } ?: return

        for (child in DomUtils.getChildren(application)) {
            when (child.tagName) {
                SdkConstants.TAG_SERVICE -> {
                    for (meta in DomUtils.getChildren(child)) {
                        if (meta.tagName == SdkConstants.TAG_META_DATA) {
                            val name = meta.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                            val value = meta.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
                            if (name?.endsWith("wearableConfigurationAction") == true && value == "WATCH_FACE_EDITOR") {
                                metadataElements.add(meta)
                            }
                        }
                    }
                }
                SdkConstants.TAG_ACTIVITY -> {
                    if (hasValidActivity) continue
                    for (filter in DomUtils.getChildren(child)) {
                        if (filter.tagName == SdkConstants.TAG_INTENT_FILTER) {
                            var hasAction = false
                            var hasCategory = false
                            for (item in DomUtils.getChildren(filter)) {
                                when (item.tagName) {
                                    SdkConstants.TAG_ACTION -> {
                                        val actionName = item.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                                        if (actionName?.endsWith("WATCH_FACE_EDITOR") == true) hasAction = true
                                    }
                                    SdkConstants.TAG_CATEGORY -> {
                                        val catName = item.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                                        if (catName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") hasCategory = true
                                    }
                                }
                            }
                            if (hasAction && (minSdk >= 30 || hasCategory)) {
                                hasValidActivity = true
                                break
                            }
                        }
                    }
                }
            }
        }

        if (metadataElements.isNotEmpty() && !hasValidActivity) {
            val message = if (minSdk < 30) {
                "Missing activity with intent filter for WATCH_FACE_EDITOR and category com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
            } else {
                "Missing activity with intent filter for WATCH_FACE_EDITOR"
            }
            for (meta in metadataElements) {
                context.report(ISSUE, meta, context.getLocation(meta), message)
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
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