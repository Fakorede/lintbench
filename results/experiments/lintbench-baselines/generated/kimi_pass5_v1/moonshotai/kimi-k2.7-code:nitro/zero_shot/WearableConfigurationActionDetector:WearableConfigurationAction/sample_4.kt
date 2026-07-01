package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PACKAGE
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class WearableConfigurationActionDetector : ResourceXmlDetector() {

    private val wearableConfigurationMetadata = mutableListOf<Element>()
    private val activities = mutableListOf<ActivityInfo>()

    override fun getApplicableElements(): Collection<String> = listOf(
        TAG_META_DATA,
        TAG_ACTIVITY
    )

    override fun beforeCheckFile(context: Context) {
        wearableConfigurationMetadata.clear()
        activities.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_META_DATA -> {
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name != METADATA_WEARABLE_CONFIG) {
                    return
                }
                val value = element.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (value != VAL_WATCH_FACE_EDITOR) {
                    return
                }
                val parent = element.parentNode as? Element ?: return
                if (parent.tagName != TAG_SERVICE || !parent.isWatchFaceService()) {
                    return
                }
                wearableConfigurationMetadata.add(element)
            }
            TAG_ACTIVITY -> {
                val root = context.document.documentElement
                val pkg = root.getAttribute(ATTR_PACKAGE)
                val activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (activityName.isBlank()) {
                    return
                }
                val resolvedName = resolveClassName(pkg, activityName)
                val actions = mutableSetOf<String>()
                val categories = mutableSetOf<String>()
                element.childNodes.forEach { child ->
                    if (child is Element && child.tagName == TAG_INTENT_FILTER) {
                        child.childNodes.forEach { grandchild ->
                            if (grandchild is Element) {
                                when (grandchild.tagName) {
                                    TAG_ACTION -> {
                                        grandchild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                                            .takeUnless { it.isBlank() }
                                            ?.let(actions::add)
                                    }
                                    TAG_CATEGORY -> {
                                        grandchild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                                            .takeUnless { it.isBlank() }
                                            ?.let(categories::add)
                                    }
                                }
                            }
                        }
                    }
                }
                activities.add(ActivityInfo(resolvedName, actions, categories))
            }
        }
    }

    override fun afterCheckFile(context: XmlContext) {
        if (wearableConfigurationMetadata.isEmpty()) {
            return
        }

        val root = context.document.documentElement
        val pkg = root.getAttribute(ATTR_PACKAGE)
        val minSdk = context.project.minSdkVersion.featureLevel

        for (metadata in wearableConfigurationMetadata) {
            val hasMatchingActivity = activities.any { activity ->
                activity.packageName == pkg &&
                    activity.actions.contains(VAL_WATCH_FACE_EDITOR) &&
                    (minSdk >= MIN_SDK_NO_CATEGORY || activity.categories.contains(CATEGORY_WEARABLE_CONFIGURATION))
            }
            if (!hasMatchingActivity) {
                val message = buildString {
                    append(
                        "The watch face service declares a wearableConfigurationAction metadata " +
                            "with value \"$VAL_WATCH_FACE_EDITOR\" but there is no activity in the same package " +
                            "with an intent filter action \"$VAL_WATCH_FACE_EDITOR\""
                    )
                    if (minSdk < MIN_SDK_NO_CATEGORY) {
                        append(" and category \"$CATEGORY_WEARABLE_CONFIGURATION\"")
                    }
                    append(".")
                }
                context.report(ISSUE, metadata, context.getLocation(metadata), message)
            }
        }
    }

    private fun Element.isWatchFaceService(): Boolean {
        childNodes.forEach { child ->
            if (child is Element && child.tagName == TAG_INTENT_FILTER) {
                child.childNodes.forEach { grandchild ->
                    if (grandchild is Element && grandchild.tagName == TAG_CATEGORY) {
                        if (grandchild.getAttributeNS(ANDROID_URI, ATTR_NAME) == CATEGORY_WATCH_FACE) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun resolveClassName(pkg: String, name: String): String {
        return when {
            name.startsWith(".") -> pkg + name
            name.contains(".") -> name
            else -> "$pkg.$name"
        }
    }

    private val ActivityInfo.packageName: String
        get() = name.substringBeforeLast('.')

    private data class ActivityInfo(
        val name: String,
        val actions: Set<String>,
        val categories: Set<String>
    )

    companion object {
        const val METADATA_WEARABLE_CONFIG =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        const val VAL_WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
        const val CATEGORY_WATCH_FACE = "com.google.android.wearable.watchface.category.WATCH_FACE"
        const val CATEGORY_WEARABLE_CONFIGURATION =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        const val MIN_SDK_NO_CATEGORY = 30

        private const val ID = "WearableConfigurationAction"
        private const val DESCRIPTION = "Wear configuration action metadata must match an activity"
        private const val EXPLANATION =
            "When a watch face service declares a wearableConfigurationAction metadata element " +
                "with the value WATCH_FACE_EDITOR, there must be a corresponding activity in the " +
                "same package with an intent filter that handles the WATCH_FACE_EDITOR action. " +
                "If the app's minSdkVersion is less than 30, the activity's intent filter must " +
                "also include the com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION category."

        @JvmField
        val ISSUE = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}