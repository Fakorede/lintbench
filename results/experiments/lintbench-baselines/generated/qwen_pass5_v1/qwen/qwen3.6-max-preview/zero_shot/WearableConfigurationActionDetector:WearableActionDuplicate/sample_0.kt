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
import org.w3c.dom.Element
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private val configServices = mutableListOf<Element>()
    private val configActivities = mutableListOf<Element>()

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_SERVICE, TAG_ACTIVITY)

    override fun beforeCheckFile(context: Context) {
        configServices.clear()
        configActivities.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> {
                val children = element.childNodes
                for (i in 0 until children.length) {
                    val node = children.item(i)
                    if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == TAG_META_DATA) {
                        val meta = node as Element
                        val name = meta.getAttributeNS(ANDROID_URI, ATTR_NAME)
                        val value = meta.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                        if (name.endsWith("wearableConfigurationAction") && value == "WATCH_FACE_EDITOR") {
                            configServices.add(element)
                            break
                        }
                    }
                }
            }
            TAG_ACTIVITY -> {
                val children = element.childNodes
                for (i in 0 until children.length) {
                    val node = children.item(i)
                    if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == TAG_INTENT_FILTER) {
                        val filter = node as Element
                        if (hasAction(filter, "WATCH_FACE_EDITOR")) {
                            configActivities.add(element)
                            break
                        }
                    }
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (configServices.isEmpty()) return

        val minSdk = context.project.minSdkVersion
        val requiresCategory = minSdk < 30

        val validActivities = configActivities.filter { activity ->
            val children = activity.childNodes
            for (i in 0 until children.length) {
                val node = children.item(i)
                if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == TAG_INTENT_FILTER) {
                    val filter = node as Element
                    if (hasAction(filter, "WATCH_FACE_EDITOR")) {
                        if (requiresCategory) {
                            if (hasCategory(filter, "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION")) {
                                return@filter true
                            }
                        } else {
                            return@filter true
                        }
                    }
                }
            }
            false
        }

        if (validActivities.size > 1) {
            val message = "Duplicate watch face configuration activities found"
            for (i in 1 until validActivities.size) {
                context.report(
                    ISSUE,
                    validActivities[i],
                    context.getLocation(validActivities[i]),
                    message
                )
            }
        } else if (validActivities.isEmpty()) {
            val message = "Missing watch face configuration activity for WATCH_FACE_EDITOR"
            for (service in configServices) {
                context.report(
                    ISSUE,
                    service,
                    context.getLocation(service),
                    message
                )
            }
        }
    }

    private fun hasAction(filter: Element, actionName: String): Boolean {
        val children = filter.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == TAG_ACTION) {
                if ((node as Element).getAttributeNS(ANDROID_URI, ATTR_NAME) == actionName) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasCategory(filter: Element, categoryName: String): Boolean {
        val children = filter.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == TAG_CATEGORY) {
                if ((node as Element).getAttributeNS(ANDROID_URI, ATTR_NAME) == categoryName) {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If and only if a watch face service defines `wearableConfigurationAction` metadata, with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package, which has an intent filter for `WATCH_FACE_EDITOR` (with com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION if minSdkVersion is less than 30).",
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