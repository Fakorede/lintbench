package com.android.tools.lint.checks

import com.android.SdkConstants
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

    companion object {
        private const val WEARABLE_CONFIGURATION_ACTION = "wearableConfigurationAction"
        private const val WATCH_FACE_EDITOR =
            "com.google.android.wearable.watchface.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If and only if a watch face service defines `wearableConfigurationAction` \
                metadata with the value `WATCH_FACE_EDITOR`, there should be exactly one \
                activity in the same package that has an intent filter for `WATCH_FACE_EDITOR` \
                (with the `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` \
                category if minSdkVersion is less than 30).
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    private val servicesWithConfiguration = mutableListOf<Pair<Element, XmlContext>>()
    private val configurationActivities = mutableListOf<Pair<Element, XmlContext>>()

    override fun getApplicableElements(): Collection<String> = listOf(
        SdkConstants.TAG_SERVICE,
        SdkConstants.TAG_ACTIVITY
    )

    override fun beforeCheckEachProject(context: Context) {
        servicesWithConfiguration.clear()
        configurationActivities.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            SdkConstants.TAG_SERVICE -> {
                val metadata = findMetadata(element, WEARABLE_CONFIGURATION_ACTION) ?: return
                val value = metadata.getAttributeNS(
                    SdkConstants.ANDROID_URI,
                    SdkConstants.ATTR_VALUE
                )
                if (value == WATCH_FACE_EDITOR) {
                    servicesWithConfiguration.add(element to context)
                }
            }
            SdkConstants.TAG_ACTIVITY -> {
                if (isConfigurationActivity(context, element)) {
                    configurationActivities.add(element to context)
                }
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (servicesWithConfiguration.isEmpty()) {
            return
        }

        if (configurationActivities.size > 1) {
            for ((activity, ctx) in configurationActivities) {
                ctx.report(
                    ISSUE,
                    activity,
                    ctx.getLocation(activity),
                    "Duplicate watch face configuration activities found"
                )
            }
        }
    }

    private fun findMetadata(parent: Element, name: String): Element? {
        val children = parent.childNodes ?: return null
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE &&
                child.nodeName == SdkConstants.TAG_METADATA
            ) {
                val childName = (child as Element).getAttributeNS(
                    SdkConstants.ANDROID_URI,
                    SdkConstants.ATTR_NAME
                )
                if (childName == name) {
                    return child
                }
            }
        }
        return null
    }

    private fun isConfigurationActivity(context: XmlContext, activity: Element): Boolean {
        val children = activity.childNodes ?: return false
        val requiresCategory = context.project.minSdkVersion.featureLevel < 30

        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE ||
                child.nodeName != SdkConstants.TAG_INTENT_FILTER
            ) {
                continue
            }

            val filter = child as Element
            var hasAction = false
            var hasCategory = !requiresCategory

            val filterChildren = filter.childNodes ?: continue
            for (j in 0 until filterChildren.length) {
                val node = filterChildren.item(j)
                if (node.nodeType != Node.ELEMENT_NODE) continue

                val name = (node as Element).getAttributeNS(
                    SdkConstants.ANDROID_URI,
                    SdkConstants.ATTR_NAME
                )
                when (node.nodeName) {
                    SdkConstants.TAG_ACTION ->
                        if (name == WATCH_FACE_EDITOR) hasAction = true
                    SdkConstants.TAG_CATEGORY ->
                        if (name == WEARABLE_CONFIGURATION_CATEGORY) hasCategory = true
                }
            }

            if (hasAction && hasCategory) {
                return true
            }
        }
        return false
    }
}