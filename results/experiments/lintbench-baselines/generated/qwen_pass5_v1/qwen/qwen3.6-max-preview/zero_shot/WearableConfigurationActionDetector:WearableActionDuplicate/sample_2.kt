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
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    private val services = mutableListOf<Element>()
    private val activities = mutableListOf<Element>()

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_SERVICE, TAG_ACTIVITY)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> services.add(element)
            TAG_ACTIVITY -> activities.add(element)
        }
    }

    override fun afterCheckFile(context: Context) {
        val xmlContext = context as? XmlContext ?: return
        val minSdk = xmlContext.mainProject.minSdkVersion ?: 1
        val requiresCategory = minSdk < 30

        for (service in services) {
            val metadataList = getDirectChildren(service, TAG_META_DATA)
            var needsConfig = false
            for (meta in metadataList) {
                val name = meta.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = meta.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name.endsWith("wearableConfigurationAction") && value == "WATCH_FACE_EDITOR") {
                    needsConfig = true
                    break
                }
            }
            if (!needsConfig) continue

            val matchingActivities = activities.filter { activity ->
                val filters = getDirectChildren(activity, TAG_INTENT_FILTER)
                filters.any { filter ->
                    val actions = getDirectChildren(filter, TAG_ACTION)
                    val hasAction = actions.any {
                        it.getAttributeNS(ANDROID_URI, ATTR_NAME) == "WATCH_FACE_EDITOR"
                    }
                    if (!hasAction) return@any false

                    if (requiresCategory) {
                        val categories = getDirectChildren(filter, TAG_CATEGORY)
                        categories.any {
                            it.getAttributeNS(ANDROID_URI, ATTR_NAME) == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
                        }
                    } else {
                        true
                    }
                }
            }

            if (matchingActivities.isEmpty()) {
                xmlContext.report(
                    ISSUE,
                    service,
                    xmlContext.getLocation(service),
                    "Missing watch face configuration activity for WATCH_FACE_EDITOR"
                )
            } else if (matchingActivities.size > 1) {
                xmlContext.report(
                    ISSUE,
                    matchingActivities[1],
                    xmlContext.getLocation(matchingActivities[1]),
                    "Duplicate watch face configuration activities found"
                )
            }
        }

        services.clear()
        activities.clear()
    }

    private fun getDirectChildren(parent: Element, tagName: String): List<Element> {
        val result = mutableListOf<Element>()
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == tagName) {
                result.add(child as Element)
            }
            child = child.nextSibling
        }
        return result
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
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}