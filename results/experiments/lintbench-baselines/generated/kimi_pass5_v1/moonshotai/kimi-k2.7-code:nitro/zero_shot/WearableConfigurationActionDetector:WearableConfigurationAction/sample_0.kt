package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private val metadataInfos = mutableListOf<ConfigActionInfo>()
    private val matchedPackages = mutableSetOf<String>()

    override fun getApplicableElements(): Collection<String> = listOf(
        SdkConstants.TAG_SERVICE,
        SdkConstants.TAG_ACTIVITY
    )

    override fun beforeCheckProject(context: Context) {
        metadataInfos.clear()
        matchedPackages.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            SdkConstants.TAG_SERVICE -> checkService(context, element)
            SdkConstants.TAG_ACTIVITY -> checkActivity(context, element)
        }
    }

    override fun afterCheckProject(context: Context) {
        for (info in metadataInfos) {
            if (info.packageName !in matchedPackages) {
                context.report(
                    Incident(
                        ISSUE,
                        info.location,
                        "Watch face service declares wearableConfigurationAction=WATCH_FACE_EDITOR " +
                            "but there is no matching activity in the same package with an intent " +
                            "filter for action WATCH_FACE_EDITOR" +
                            (if (requiresWearableConfigurationCategory(context)) {
                                " and category $CONFIG_CATEGORY"
                            } else "") + "."
                    )
                )
            }
        }
        metadataInfos.clear()
        matchedPackages.clear()
    }

    private fun checkService(context: XmlContext, service: Element) {
        val packageName = currentPackage(context) ?: return
        val metaDataNodes = service.getElementsByTagName(SdkConstants.TAG_META_DATA)
        for (i in 0 until metaDataNodes.length) {
            val metaData = metaDataNodes.item(i) as Element
            val name = getAndroidAttribute(metaData, SdkConstants.ATTR_NAME)
            val value = getAndroidAttribute(metaData, SdkConstants.ATTR_VALUE)
            if (name == META_DATA_NAME && value == CONFIG_ACTION_VALUE) {
                metadataInfos.add(ConfigActionInfo(packageName, context.getLocation(metaData)))
            }
        }
    }

    private fun checkActivity(context: XmlContext, activity: Element) {
        val packageName = currentPackage(context) ?: return
        val filterNodes = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER)
        for (i in 0 until filterNodes.length) {
            val filter = filterNodes.item(i) as Element
            val hasAction = hasChildWithAndroidAttribute(
                filter,
                SdkConstants.TAG_ACTION,
                SdkConstants.ATTR_NAME,
                CONFIG_ACTION_VALUE
            )
            if (!hasAction) continue

            val hasCategory = if (requiresWearableConfigurationCategory(context)) {
                hasChildWithAndroidAttribute(
                    filter,
                    SdkConstants.TAG_CATEGORY,
                    SdkConstants.ATTR_NAME,
                    CONFIG_CATEGORY
                )
            } else {
                true
            }

            if (hasAction && hasCategory) {
                matchedPackages.add(packageName)
                return
            }
        }
    }

    private fun requiresWearableConfigurationCategory(context: XmlContext): Boolean {
        val minSdk = getMinSdkVersion(context)
        return minSdk in 1..29
    }

    private fun getMinSdkVersion(context: XmlContext): Int {
        var minSdk = context.project.minSdk
        if (minSdk <= 0) {
            val manifest = context.document.documentElement
            val usesSdkNodes = manifest.getElementsByTagName(SdkConstants.TAG_USES_SDK)
            if (usesSdkNodes.length > 0) {
                val version = getAndroidAttribute(
                    usesSdkNodes.item(0) as Element,
                    SdkConstants.ATTR_MIN_SDK_VERSION
                )
                if (version != null) {
                    minSdk = version.toIntOrNull() ?: -1
                }
            }
        }
        return minSdk
    }

    private fun currentPackage(context: XmlContext): String? {
        return context.document.documentElement
            .getAttribute(SdkConstants.ATTR_PACKAGE)
            .takeIf { it.isNotBlank() }
    }

    private fun hasChildWithAndroidAttribute(
        parent: Element,
        tag: String,
        attr: String,
        value: String
    ): Boolean {
        val nodes = parent.getElementsByTagName(tag)
        for (i in 0 until nodes.length) {
            val child = nodes.item(i) as Element
            if (getAndroidAttribute(child, attr) == value) {
                return true
            }
        }
        return false
    }

    private fun getAndroidAttribute(element: Element, name: String): String? {
        return element.getAttributeNS(SdkConstants.ANDROID_URI, name).takeIf { it.isNotBlank() }
            ?: element.getAttribute(name).takeIf { it.isNotBlank() }
    }

    private data class ConfigActionInfo(
        val packageName: String,
        val location: Location
    )

    companion object {
        private const val META_DATA_NAME =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val CONFIG_ACTION_VALUE = "WATCH_FACE_EDITOR"
        private const val CONFIG_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                When a watch face service declares a metadata element named
                `com.google.android.wearable.watchface.wearableConfigurationAction` with the value
                `WATCH_FACE_EDITOR`, an activity in the same package must provide a matching intent
                filter for `WATCH_FACE_EDITOR`. If `minSdkVersion` is less than 30, that intent filter
                must also include the category
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            ),
            moreInfo = "https://developer.android.com/training/wearables/watch-faces/configuration"
        )
    }
}