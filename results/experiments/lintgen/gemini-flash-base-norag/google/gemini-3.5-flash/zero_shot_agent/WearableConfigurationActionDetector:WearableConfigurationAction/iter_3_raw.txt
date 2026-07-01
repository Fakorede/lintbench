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
import java.util.EnumSet

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableFiles(): EnumSet<Scope> = Scope.MANIFEST_SCOPE

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        val root = document.documentElement ?: return
        val rootTag = root.localName ?: root.tagName
        if (rootTag != "manifest") return

        val minSdk = context.project.minSdkVersion.apiLevel

        val services = mutableListOf<Element>()
        val activities = mutableListOf<Element>()

        root.accept { element ->
            val localName = element.localName ?: element.tagName
            if (localName == "service") {
                services.add(element)
            } else if (localName == "activity") {
                activities.add(element)
            }
        }

        val matchingMetadataElements = mutableListOf<Element>()

        for (service in services) {
            val metaDatas = mutableListOf<Element>()
            service.accept { element ->
                val localName = element.localName ?: element.tagName
                if (localName == "meta-data") {
                    metaDatas.add(element)
                }
            }
            for (metaData in metaDatas) {
                val name = metaData.getAndroidAttribute("name").trim()
                val value = metaData.getAndroidAttribute("value").trim()
                if (isMetadataName(name) && isMetadataValue(value)) {
                    matchingMetadataElements.add(metaData)
                }
            }
        }

        val matchingActivities = mutableListOf<Element>()
        val activitiesWithActionButNoCategory = mutableListOf<Element>()
        val actionElements = mutableListOf<Element>()

        for (activity in activities) {
            val intentFilters = mutableListOf<Element>()
            activity.accept { element ->
                val localName = element.localName ?: element.tagName
                if (localName == "intent-filter") {
                    intentFilters.add(element)
                }
            }
            for (filter in intentFilters) {
                val actions = mutableListOf<Element>()
                val categories = mutableListOf<Element>()
                filter.accept { element ->
                    val localName = element.localName ?: element.tagName
                    if (localName == "action") {
                        actions.add(element)
                    } else if (localName == "category") {
                        categories.add(element)
                    }
                }

                val matchingActions = actions.filter { isActionName(it.getAndroidAttribute("name").trim()) }
                if (matchingActions.isNotEmpty()) {
                    actionElements.addAll(matchingActions)

                    val hasCategory = categories.any { isCategoryName(it.getAndroidAttribute("name").trim()) }
                    if (minSdk >= 30 || hasCategory) {
                        matchingActivities.add(activity)
                    } else {
                        activitiesWithActionButNoCategory.add(activity)
                    }
                }
            }
        }

        // Case 1: Metadata is present, but no matching activity is found
        if (matchingMetadataElements.isNotEmpty() && matchingActivities.isEmpty()) {
            for (metaData in matchingMetadataElements) {
                val message = if (minSdk < 30) {
                    "To support wearable configuration, there must be an activity with an intent filter for action `com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR` and category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`."
                } else {
                    "To support wearable configuration, there must be an activity with an intent filter for action `com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR`."
                }
                context.report(
                    ISSUE,
                    metaData,
                    context.getLocation(metaData),
                    message
                )
            }
        }

        // Case 2: Activity with action is present, but metadata is missing
        if (matchingMetadataElements.isEmpty() && (matchingActivities.isNotEmpty() || activitiesWithActionButNoCategory.isNotEmpty())) {
            val targets = if (actionElements.isNotEmpty()) actionElements else (matchingActivities + activitiesWithActionButNoCategory)
            for (target in targets) {
                val message = "An activity is configured as a watch face editor, but the watch face service does not define the `com.google.android.wearable.watchface.wearableConfigurationAction` metadata."
                context.report(
                    ISSUE,
                    target,
                    context.getLocation(target),
                    message
                )
            }
        }
    }

    private fun Element.accept(visitor: (Element) -> Unit) {
        visitor(this)
        var child = this.firstChild
        while (child != null) {
            if (child is Element) {
                child.accept(visitor)
            }
            child = child.nextSibling
        }
    }

    private fun Element.getAndroidAttribute(localName: String): String {
        val attr = this.getAttributeNS(SdkConstants.ANDROID_URI, localName)
        if (attr.isNotEmpty()) return attr
        val attr2 = this.getAttribute("android:$localName")
        if (attr2.isNotEmpty()) return attr2
        return this.getAttribute(localName)
    }

    private fun isMetadataName(name: String): Boolean {
        return name == "com.google.android.wearable.watchface.wearableConfigurationAction" ||
               name == "wearableConfigurationAction"
    }

    private fun isMetadataValue(value: String): Boolean {
        return value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" ||
               value == "WATCH_FACE_EDITOR"
    }

    private fun isActionName(action: String): Boolean {
        return action == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" ||
               action == "WATCH_FACE_EDITOR"
    }

    private fun isCategoryName(category: String): Boolean {
        return category == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" ||
               category == "WEARABLE_CONFIGURATION"
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines `wearableConfigurationAction` metadata with the value \
                `WATCH_FACE_EDITOR`, there must be an activity in the same package that has an intent filter \
                for `WATCH_FACE_EDITOR` (and if `minSdkVersion` is less than 30, it must also include the \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category).
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