package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = listOf(SdkConstants.TAG_MANIFEST)

    override fun visitElement(context: XmlContext, element: Element) {
        val minSdk = context.mainProject.minSdkVersion
        val metadataElements = mutableListOf<Element>()
        var hasValidActivity = false

        val applicationNodes = element.getElementsByTagName(SdkConstants.TAG_APPLICATION)
        if (applicationNodes.length == 0) return
        val application = applicationNodes.item(0) as Element

        val appChildren = application.childNodes
        for (i in 0 until appChildren.length) {
            val child = appChildren.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val childElement = child as Element

            when (childElement.tagName) {
                SdkConstants.TAG_SERVICE -> {
                    val serviceChildren = childElement.childNodes
                    for (j in 0 until serviceChildren.length) {
                        val meta = serviceChildren.item(j)
                        if (meta.nodeType == Node.ELEMENT_NODE && meta.nodeName == SdkConstants.TAG_META_DATA) {
                            val metaElement = meta as Element
                            val name = metaElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                            val value = metaElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
                            if (name?.endsWith("wearableConfigurationAction") == true && value == "WATCH_FACE_EDITOR") {
                                metadataElements.add(metaElement)
                            }
                        }
                    }
                }
                SdkConstants.TAG_ACTIVITY -> {
                    if (hasValidActivity) continue
                    val intentFilters = childElement.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER)
                    for (k in 0 until intentFilters.length) {
                        val filter = intentFilters.item(k) as Element
                        var hasAction = false
                        var hasCategory = false
                        val filterChildren = filter.childNodes
                        for (l in 0 until filterChildren.length) {
                            val item = filterChildren.item(l)
                            if (item.nodeType != Node.ELEMENT_NODE) continue
                            val itemElement = item as Element
                            when (itemElement.tagName) {
                                SdkConstants.TAG_ACTION -> {
                                    val actionName = itemElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                                    if (actionName?.endsWith("WATCH_FACE_EDITOR") == true) hasAction = true
                                }
                                SdkConstants.TAG_CATEGORY -> {
                                    val catName = itemElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
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
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}