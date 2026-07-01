package com.android.tools.lint.checks

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

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val WEARABLE_CONFIGURATION_ACTION_METADATA =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WALLPAPER_SERVICE_ACTION =
            "android.service.wallpaper.WallpaperService"
        private const val WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"

        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                When a watch face service declares a `wearableConfigurationAction`
                metadata value of `WATCH_FACE_EDITOR`, there must be a corresponding
                activity in the same package with an intent filter whose action is
                `WATCH_FACE_EDITOR`. If `minSdkVersion` is less than 30, that intent
                filter must also include the
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`
                category.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    private data class WatchFaceServiceInfo(
        val packageName: String,
        val serviceName: String,
        val location: Location,
        val metadataValue: String
    )

    private data class CandidateActivityInfo(
        val packageName: String,
        val activityName: String,
        val hasWatchFaceEditorAction: Boolean,
        val hasWearableConfigurationCategory: Boolean
    )

    private val watchFaceServices = mutableListOf<WatchFaceServiceInfo>()
    private val candidateActivities = mutableListOf<CandidateActivityInfo>()

    override fun getApplicableElements(): Collection<String>? {
        return listOf("service", "activity")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        when (element.tagName) {
            "service" -> visitService(context, element)
            "activity" -> visitActivity(context, element)
        }
    }

    override fun checkMergedProject(context: Context) {
        val minSdk = context.mainProject.getMinSdk()
        val mainPackage = context.mainProject.getPackage()

        for (service in watchFaceServices) {
            val matchingActivities = candidateActivities.filter {
                isSamePackage(service.packageName, it.packageName, mainPackage) &&
                    it.hasWatchFaceEditorAction
            }

            if (matchingActivities.isEmpty()) {
                context.report(
                    ISSUE,
                    service.location,
                    "No activity in the same package has an intent filter with action `WATCH_FACE_EDITOR` to match this watch face service's `wearableConfigurationAction` metadata."
                )
                continue
            }

            if (minSdk < 30 && matchingActivities.none { it.hasWearableConfigurationCategory }) {
                context.report(
                    ISSUE,
                    service.location,
                    "The activity matching `WATCH_FACE_EDITOR` must include the `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category because `minSdkVersion` is $minSdk."
                )
            }
        }

        watchFaceServices.clear()
        candidateActivities.clear()
    }

    private fun visitService(context: XmlContext, service: org.w3c.dom.Element) {
        if (!isWatchFaceService(service)) return

        val packageName = getPackageName(service) ?: return
        val serviceName = getAndroidAttribute(service, "name") ?: return
        val (metadataValue, metadataElement) = findWearableConfigurationAction(service) ?: return
        if (!isWatchFaceEditorValue(metadataValue)) return

        watchFaceServices.add(
            WatchFaceServiceInfo(
                packageName = packageName,
                serviceName = serviceName,
                location = context.getLocation(metadataElement),
                metadataValue = metadataValue
            )
        )
    }

    private fun visitActivity(context: XmlContext, activity: org.w3c.dom.Element) {
        val packageName = getPackageName(activity) ?: return
        val activityName = getAndroidAttribute(activity, "name") ?: return

        candidateActivities.add(
            CandidateActivityInfo(
                packageName = packageName,
                activityName = activityName,
                hasWatchFaceEditorAction = hasIntentFilterWithActionSuffix(activity, WATCH_FACE_EDITOR),
                hasWearableConfigurationCategory = hasIntentFilterWithExactCategory(
                    activity,
                    WEARABLE_CONFIGURATION_CATEGORY
                )
            )
        )
    }

    private fun isWatchFaceService(service: org.w3c.dom.Element): Boolean {
        return hasIntentFilterWithExactAction(service, WALLPAPER_SERVICE_ACTION)
    }

    private fun findWearableConfigurationAction(
        service: org.w3c.dom.Element
    ): Pair<String, org.w3c.dom.Element>? {
        val children = service.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child !is org.w3c.dom.Element || child.tagName != "meta-data") continue

            val name = getAndroidAttribute(child, "name") ?: continue
            if (name == WEARABLE_CONFIGURATION_ACTION_METADATA ||
                name.endsWith(".wearableConfigurationAction")
            ) {
                val value = getAndroidAttribute(child, "value") ?: continue
                return value to child
            }
        }
        return null
    }

    private fun isWatchFaceEditorValue(value: String): Boolean {
        return value == WATCH_FACE_EDITOR || value.endsWith(".$WATCH_FACE_EDITOR")
    }

    private fun hasIntentFilterWithExactAction(
        parent: org.w3c.dom.Element,
        actionName: String
    ): Boolean {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child !is org.w3c.dom.Element || child.tagName != "intent-filter") continue
            if (hasChildWithExactName(child, "action", actionName)) return true
        }
        return false
    }

    private fun hasIntentFilterWithActionSuffix(
        parent: org.w3c.dom.Element,
        actionSuffix: String
    ): Boolean {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child !is org.w3c.dom.Element || child.tagName != "intent-filter") continue
            if (hasChildWithNameSuffix(child, "action", actionSuffix)) return true
        }
        return false
    }

    private fun hasIntentFilterWithExactCategory(
        parent: org.w3c.dom.Element,
        categoryName: String
    ): Boolean {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child !is org.w3c.dom.Element || child.tagName != "intent-filter") continue
            if (hasChildWithExactName(child, "category", categoryName)) return true
        }
        return false
    }

    private fun hasChildWithExactName(
        parent: org.w3c.dom.Element,
        childTag: String,
        exactName: String
    ): Boolean {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child !is org.w3c.dom.Element || child.tagName != childTag) continue
            val name = getAndroidAttribute(child, "name") ?: continue
            if (name == exactName) return true
        }
        return false
    }

    private fun hasChildWithNameSuffix(
        parent: org.w3c.dom.Element,
        childTag: String,
        nameSuffix: String
    ): Boolean {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child !is org.w3c.dom.Element || child.tagName != childTag) continue
            val name = getAndroidAttribute(child, "name") ?: continue
            if (name == nameSuffix || name.endsWith(".$nameSuffix")) return true
        }
        return false
    }

    private fun isSamePackage(
        packageName1: String,
        packageName2: String,
        mainPackage: String
    ): Boolean {
        return packageName1 == packageName2 ||
            packageName1 == mainPackage ||
            packageName2 == mainPackage
    }

    private fun getPackageName(element: org.w3c.dom.Element): String? {
        var current: org.w3c.dom.Node? = element
        while (current != null) {
            if (current is org.w3c.dom.Element && current.tagName == "manifest") {
                return current.getAttribute("package").takeIf { it.isNotEmpty() }
            }
            current = current.parentNode
        }
        return null
    }

    private fun getAndroidAttribute(
        element: org.w3c.dom.Element,
        localName: String
    ): String? {
        val valueNs = element.getAttributeNS(ANDROID_URI, localName)
        if (valueNs.isNotEmpty()) return valueNs
        val valuePrefix = element.getAttribute("android:$localName")
        if (valuePrefix.isNotEmpty()) return valuePrefix
        return element.getAttribute(localName).takeIf { it.isNotEmpty() }
    }
}