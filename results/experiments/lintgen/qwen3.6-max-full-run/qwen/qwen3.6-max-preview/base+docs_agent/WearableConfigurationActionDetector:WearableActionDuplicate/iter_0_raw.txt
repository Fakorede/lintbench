package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_MANIFEST
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
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

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If and only if a watch face service defines wearableConfigurationAction metadata, with the value WATCH_FACE_EDITOR, there should be an activity in the same package, which has an intent filter for WATCH_FACE_EDITOR (with com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION if minSdkVersion is less than 30).",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_MANIFEST)

    override fun visitElement(context: XmlContext, element: Element) {
        val application = getChildrenByTag(element, TAG_APPLICATION).firstOrNull() ?: return
        val minSdk = context.mainProject.minSdkVersion ?: 1

        val servicesNeedingConfig = mutableListOf<Element>()
        for (service in getChildrenByTag(application, TAG_SERVICE)) {
            for (meta in getChildrenByTag(service, TAG_META_DATA)) {
                val name = meta.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = meta.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == "wearableConfigurationAction" && value == "WATCH_FACE_EDITOR") {
                    servicesNeedingConfig.add(service)
                    break
                }
            }
        }

        if (servicesNeedingConfig.isEmpty()) return

        val activitiesWithEditor = mutableListOf<Element>()
        for (activity in getChildrenByTag(application, TAG_ACTIVITY)) {
            for (filter in getChildrenByTag(activity, TAG_INTENT_FILTER)) {
                var hasAction = false
                var hasCategory = minSdk >= 30

                for (child in getChildrenByTag(filter, null)) {
                    val tagName = child.tagName
                    val attrName = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (tagName == TAG_ACTION && attrName == "WATCH_FACE_EDITOR") {
                        hasAction = true
                    } else if (tagName == TAG_CATEGORY &&
                        attrName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
                    ) {
                        hasCategory = true
                    }
                }

                if (hasAction && hasCategory) {
                    activitiesWithEditor.add(activity)
                    break
                }
            }
        }

        if (activitiesWithEditor.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing watch face configuration activity for WATCH_FACE_EDITOR"
            )
        } else if (activitiesWithEditor.size > 1) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Duplicate watch face configuration activities found"
            )
        }
    }

    private fun getChildrenByTag(parent: Element, tagName: String?): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                if (tagName == null || node.nodeName == tagName) {
                    result.add(node as Element)
                }
            }
        }
        return result
    }
}