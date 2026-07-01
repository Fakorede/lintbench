package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_MANIFEST)

    override fun visitElement(context: XmlContext, element: Element) {
        val manifestPackage = element.getAttribute(PACKAGE) ?: return
        val minSdk = context.project.minSdkVersion

        val servicesNeedingEditor = mutableListOf<Element>()
        val packagesWithEditorActivity = mutableSetOf<String>()

        for (child in element.getChildren()) {
            when (child.tagName) {
                TAG_SERVICE -> {
                    if (hasWearableConfigMetadata(child)) {
                        servicesNeedingEditor.add(child)
                    }
                }
                TAG_ACTIVITY, TAG_ACTIVITY_ALIAS -> {
                    if (hasEditorIntentFilter(child, minSdk)) {
                        val activityPackage = getPackageFromClassName(child.getAttribute(ATTR_NAME), manifestPackage)
                        if (activityPackage != null) {
                            packagesWithEditorActivity.add(activityPackage)
                        }
                    }
                }
            }
        }

        for (service in servicesNeedingEditor) {
            val servicePackage = getPackageFromClassName(service.getAttribute(ATTR_NAME), manifestPackage)
            if (servicePackage != null && servicePackage !in packagesWithEditorActivity) {
                context.report(
                    ISSUE,
                    context.getLocation(service),
                    "Watch face service defines `wearableConfigurationAction` metadata but no matching activity with `WATCH_FACE_EDITOR` intent filter exists in the same package."
                )
            }
        }
    }

    private fun hasWearableConfigMetadata(service: Element): Boolean {
        for (child in service.getChildren()) {
            if (child.tagName == TAG_META_DATA) {
                val name = child.getAttribute(ATTR_NAME)
                val value = child.getAttribute(ATTR_VALUE)
                if (name.endsWith("wearableConfigurationAction") && value == "WATCH_FACE_EDITOR") {
                    return true
                }
            }
        }
        return false
    }

    private fun hasEditorIntentFilter(activity: Element, minSdk: Int): Boolean {
        for (child in activity.getChildren()) {
            if (child.tagName == TAG_INTENT_FILTER) {
                var hasAction = false
                var hasCategory = false
                for (filterChild in child.getChildren()) {
                    when (filterChild.tagName) {
                        TAG_ACTION -> {
                            if (filterChild.getAttribute(ATTR_NAME).endsWith("WATCH_FACE_EDITOR")) {
                                hasAction = true
                            }
                        }
                        TAG_CATEGORY -> {
                            if (filterChild.getAttribute(ATTR_NAME).endsWith("WEARABLE_CONFIGURATION")) {
                                hasCategory = true
                            }
                        }
                    }
                }
                if (hasAction && (minSdk >= 30 || hasCategory)) {
                    return true
                }
            }
        }
        return false
    }

    private fun getPackageFromClassName(className: String?, manifestPackage: String): String? {
        if (className.isNullOrEmpty()) return null
        val fqcn = when {
            className.startsWith(".") -> "$manifestPackage$className"
            "." !in className -> "$manifestPackage.$className"
            else -> className
        }
        val lastDot = fqcn.lastIndexOf('.')
        return if (lastDot > 0) fqcn.substring(0, lastDot) else manifestPackage
    }

    private fun Element.getChildren(): Sequence<Element> {
        return sequence {
            val nodes = childNodes
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    yield(node as Element)
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
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