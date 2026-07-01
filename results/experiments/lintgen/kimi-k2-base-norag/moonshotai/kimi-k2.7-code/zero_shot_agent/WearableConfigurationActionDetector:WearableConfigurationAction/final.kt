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
import com.android.tools.lint.detector.api.Detector.XmlScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val META_DATA_WEARABLE_CONFIGURATION_ACTION = "wearableConfigurationAction"
private const val META_DATA_WEARABLE_CONFIGURATION_ACTION_LEGACY =
    "com.google.android.wearable.watchface.WEARABLE_CONFIGURATION_ACTION"
private const val VALUE_WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
private const val CATEGORY_WEARABLE_CONFIGURATION =
    "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

private data class ServiceInfo(
    val context: XmlContext,
    val metadata: Element,
    val requireCategory: Boolean
)

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private val pendingServices = mutableListOf<ServiceInfo>()
    private val activities = mutableListOf<Element>()

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_SERVICE, TAG_ACTIVITY)

    override fun beforeCheckRootProject(context: Context) {
        pendingServices.clear()
        activities.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> {
                val metaData = findWearableConfigurationActionMetadata(element) ?: return
                val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (value == VALUE_WATCH_FACE_EDITOR) {
                    pendingServices.add(
                        ServiceInfo(
                            context,
                            metaData,
                            context.project.minSdk < 30
                        )
                    )
                }
            }
            TAG_ACTIVITY -> {
                activities.add(element)
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (pendingServices.isEmpty()) {
            return
        }

        for (serviceInfo in pendingServices) {
            val actionName = serviceInfo.metadata.getAttributeNS(ANDROID_URI, ATTR_VALUE)
            if (actionName.isBlank()) {
                continue
            }

            val found = activities.any { activity ->
                hasMatchingIntentFilter(
                    activity,
                    actionName,
                    serviceInfo.requireCategory
                )
            }

            if (!found) {
                val message = buildString {
                    append(
                        "The watch face service declares a `$META_DATA_WEARABLE_CONFIGURATION_ACTION` metadata with value `$VALUE_WATCH_FACE_EDITOR`, "
                    )
                    append("but no activity in the same package has a matching intent filter with action `$actionName`")
                    if (serviceInfo.requireCategory) {
                        append(" and category `$CATEGORY_WEARABLE_CONFIGURATION`")
                    }
                    append(".")
                }

                serviceInfo.context.report(
                    ISSUE,
                    serviceInfo.metadata,
                    serviceInfo.context.getLocation(serviceInfo.metadata),
                    message
                )
            }
        }
    }

    private fun findWearableConfigurationActionMetadata(service: Element): Element? {
        val children = service.childNodes ?: return null
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) {
                continue
            }
            val child = node as Element
            if (child.tagName != TAG_META_DATA) {
                continue
            }
            val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name == META_DATA_WEARABLE_CONFIGURATION_ACTION ||
                name == META_DATA_WEARABLE_CONFIGURATION_ACTION_LEGACY
            ) {
                return child
            }
        }
        return null
    }

    private fun hasMatchingIntentFilter(
        activity: Element,
        actionName: String,
        requireCategory: Boolean
    ): Boolean {
        val children = activity.childNodes ?: return false
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) {
                continue
            }
            val child = node as Element
            if (child.tagName != TAG_INTENT_FILTER) {
                continue
            }
            if (hasAction(child, actionName) &&
                (!requireCategory || hasCategory(child, CATEGORY_WEARABLE_CONFIGURATION))
            ) {
                return true
            }
        }
        return false
    }

    private fun hasAction(intentFilter: Element, actionName: String): Boolean {
        val children = intentFilter.childNodes ?: return false
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) {
                continue
            }
            val child = node as Element
            if (child.tagName == TAG_ACTION &&
                child.getAttributeNS(ANDROID_URI, ATTR_NAME) == actionName
            ) {
                return true
            }
        }
        return false
    }

    private fun hasCategory(intentFilter: Element, categoryName: String): Boolean {
        val children = intentFilter.childNodes ?: return false
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) {
                continue
            }
            val child = node as Element
            if (child.tagName == TAG_CATEGORY &&
                child.getAttributeNS(ANDROID_URI, ATTR_NAME) == categoryName
            ) {
                return true
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action must match an activity",
            explanation = """
                When a watch face service defines a `wearableConfigurationAction` metadata with value `WATCH_FACE_EDITOR`, there must be a corresponding activity in the same package with an intent filter for `WATCH_FACE_EDITOR`. When `minSdkVersion` is less than 30, that intent filter must also include the category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`.
            """,
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