package com.android.tools.lint.checks

import com.android.SdkConstants
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
import org.w3c.dom.Element
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action metadata must match an activity",
            explanation = "When a watch face service defines wearableConfigurationAction metadata with the value WATCH_FACE_EDITOR, " +
                "there must be a corresponding activity in the same package with an intent filter for that action. " +
                "If minSdkVersion is less than 30, the intent filter must also include the category " +
                "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    private var manifestPackage: String? = null
    private val servicesNeedingConfig = mutableListOf<Pair<String, Location>>()
    private val packagesWithConfigActivity = mutableSetOf<String>()

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.TAG_MANIFEST, SdkConstants.TAG_SERVICE, SdkConstants.TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            SdkConstants.TAG_MANIFEST -> {
                manifestPackage = element.getAttribute(SdkConstants.ATTR_PACKAGE)
            }
            SdkConstants.TAG_SERVICE -> checkService(context, element)
            SdkConstants.TAG_ACTIVITY -> checkActivity(context, element)
        }
    }

    private fun resolvePackageName(componentName: String?): String? {
        if (componentName.isNullOrEmpty()) return null
        val pkg = manifestPackage ?: return null
        val fullName = when {
            componentName.startsWith(".") -> "$pkg$componentName"
            !componentName.contains(".") -> "$pkg.$componentName"
            else -> componentName
        }
        return fullName.substringBeforeLast(".")
    }

    private fun getChildElements(element: Element): List<Element> {
        val children = mutableListOf<Element>()
        val nodeList = element.childNodes
        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                children.add(node as Element)
            }
        }
        return children
    }

    private fun checkService(context: XmlContext, element: Element) {
        val componentName = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        val pkg = resolvePackageName(componentName) ?: return

        val metadata = getChildElements(element)
            .filter { it.tagName == SdkConstants.TAG_META_DATA }
            .find {
                it.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME) == "wearableConfigurationAction"
            } ?: return

        val value = metadata.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
        if (value == "WATCH_FACE_EDITOR") {
            servicesNeedingConfig.add(pkg to context.getLocation(metadata))
        }
    }

    private fun checkActivity(context: XmlContext, element: Element) {
        val componentName = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        val pkg = resolvePackageName(componentName) ?: return
        val minSdk = context.project.minSdkVersion?.apiLevel ?: 30
        val requiresCategory = minSdk < 30

        val intentFilters = getChildElements(element).filter { it.tagName == SdkConstants.TAG_INTENT_FILTER }
        for (filter in intentFilters) {
            val children = getChildElements(filter)
            val hasAction = children.any {
                it.tagName == SdkConstants.TAG_ACTION &&
                    it.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME) == "WATCH_FACE_EDITOR"
            }
            if (hasAction) {
                val hasCategory = !requiresCategory || children.any {
                    it.tagName == SdkConstants.TAG_CATEGORY &&
                        it.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME) ==
                        "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
                }
                if (hasCategory) {
                    packagesWithConfigActivity.add(pkg)
                    break
                }
            }
        }
    }

    override fun checkMergedProject(context: Context) {
        val minSdk = context.project.minSdkVersion?.apiLevel ?: 30
        val categoryNote = if (minSdk < 30) {
            " and category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`"
        } else {
            ""
        }

        for ((pkg, location) in servicesNeedingConfig) {
            if (!packagesWithConfigActivity.contains(pkg)) {
                context.report(
                    ISSUE,
                    location,
                    "Missing configuration activity for package `$pkg`. " +
                        "Define an activity with an intent filter for action `WATCH_FACE_EDITOR`$categoryNote."
                )
            }
        }

        servicesNeedingConfig.clear()
        packagesWithConfigActivity.clear()
        manifestPackage = null
    }
}