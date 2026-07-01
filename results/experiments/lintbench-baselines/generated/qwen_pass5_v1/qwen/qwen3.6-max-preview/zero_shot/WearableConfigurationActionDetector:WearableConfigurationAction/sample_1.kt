package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private var currentPackage: String? = null
    private val servicesNeedingEditor = mutableListOf<Pair<XmlElement, Location>>()
    private val validPackages = mutableSetOf<String>()

    override fun getApplicableElements(): Collection<String>? = listOf("manifest", "service", "activity")

    override fun visitElement(context: XmlContext, element: XmlElement) {
        when (element.name) {
            "manifest" -> {
                currentPackage = element.getAttribute("package")
            }
            "service" -> {
                if (hasWearableConfigAction(element)) {
                    servicesNeedingEditor.add(element to context.getLocation(element))
                }
            }
            "activity" -> {
                if (hasValidEditorIntentFilter(context, element)) {
                    currentPackage?.let { validPackages.add(it) }
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        val pkg = currentPackage ?: return
        for ((_, location) in servicesNeedingEditor) {
            if (pkg !in validPackages) {
                context.report(
                    ISSUE,
                    location,
                    "Missing activity with WATCH_FACE_EDITOR intent filter in package $pkg"
                )
            }
        }
        currentPackage = null
        servicesNeedingEditor.clear()
        validPackages.clear()
    }

    private fun hasWearableConfigAction(service: XmlElement): Boolean {
        for (child in service.children()) {
            if (child.name == "meta-data") {
                val name = child.getAttribute("android:name")
                val value = child.getAttribute("android:value")
                if (name == "wearableConfigurationAction" && value == "WATCH_FACE_EDITOR") {
                    return true
                }
            }
        }
        return false
    }

    private fun hasValidEditorIntentFilter(context: XmlContext, activity: XmlElement): Boolean {
        val minSdk = context.minSdkVersion
        for (child in activity.children()) {
            if (child.name == "intent-filter") {
                var hasAction = false
                var hasCategory = false
                for (filterChild in child.children()) {
                    val name = filterChild.getAttribute("android:name")
                    when (filterChild.name) {
                        "action" -> if (name == "WATCH_FACE_EDITOR") hasAction = true
                        "category" -> if (name == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") hasCategory = true
                    }
                }
                if (hasAction && (minSdk >= 30 || hasCategory)) {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = "When a watch face service defines `wearableConfigurationAction` metadata with the value `WATCH_FACE_EDITOR`, there must be an activity in the same package with an intent filter for `WATCH_FACE_EDITOR`. If minSdkVersion is less than 30, the intent filter must also include the category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`.",
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