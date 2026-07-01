package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_SCREEN_ORIENTATION
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Element
import java.util.EnumSet

/**
 * Detector that flags activities that combine a fixed screen orientation
 * with a translucent theme, which causes a crash on API 26+.
 */
class TranslucentViewDetector : ResourceXmlDetector() {

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with `targetSdkVersion` O or greater since there can be another activity \
                visible behind your activity with a conflicting request.

                For example, your activity requests landscape and the visible activity behind \
                your translucent activity requests portrait. In this case the system can only \
                honor one of the requests and currently prefers to honor the request from \
                non-translucent activities since there is nothing visible behind them.

                Devices running platform version O or greater will throw an exception in your \
                app if this state is detected.
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.ALL_RESOURCE_FILES)
            )
        )

        // Fixed orientation values that are not "unspecified" / sensor-based
        private val FIXED_ORIENTATIONS = setOf(
            "landscape",
            "portrait",
            "reverseLandscape",
            "reversePortrait",
            "sensorLandscape",
            "sensorPortrait",
            "userLandscape",
            "userPortrait",
            "locked"
        )

        // Style attributes that indicate translucency
        private val TRANSLUCENT_THEME_PATTERNS = listOf(
            "Translucent",
            "translucent",
            "FloatingWindow",
            "Dialog",
            "Transparent",
            "transparent"
        )

        private const val KEY_ACTIVITY_NAME = "activityName"
        private const val KEY_ORIENTATION = "orientation"
        private const val KEY_THEME = "theme"
        private const val KEY_LOCATION = "location"

        // Map from activity name -> (orientation, theme, location)
        // Populated during manifest pass, checked during resource pass
        private val pendingActivities =
            mutableMapOf<String, Triple<String, String, Location>>()

        // Themes known to be translucent (collected from styles.xml passes)
        private val translucentThemes = mutableSetOf<String>()
    }

    // Per-lint-run state
    private val manifestActivities =
        mutableMapOf<String, Triple<String, String, Location>>() // name -> (orientation, theme, location)

    private val resolvedTranslucentThemes = mutableSetOf<String>()

    // Deferred reports: (location, activityName, orientation, theme)
    private val deferredReports =
        mutableListOf<Triple<Location, String, String>>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> {
        return XmlScannerConstants.ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when {
            context.document.documentElement?.tagName == "manifest" ||
                    isManifestFile(context) -> {
                if (element.tagName == TAG_ACTIVITY) {
                    handleActivityElement(context, element)
                }
            }

            isValuesFile(context) -> {
                handleStyleElement(context, element)
            }
        }
    }

    private fun isManifestFile(context: XmlContext): Boolean {
        return context.file.name == "AndroidManifest.xml"
    }

    private fun isValuesFile(context: XmlContext): Boolean {
        return context.resourceFolderType == ResourceFolderType.VALUES
    }

    private fun handleActivityElement(context: XmlContext, element: Element) {
        val orientationAttr =
            element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION) ?: return
        val orientation = orientationAttr.value
        if (orientation !in FIXED_ORIENTATIONS) return

        val themeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_THEME) ?: return
        val theme = themeAttr.value

        val activityName =
            element.getAttributeNS(ANDROID_URI, ATTR_NAME).ifEmpty { "<unknown>" }

        val location = context.getLocation(element)

        // Check if theme name itself looks translucent
        if (looksTranslucent(theme)) {
            reportIssue(context, location, activityName, orientation, theme)
            return
        }

        // Otherwise store for deferred check after styles are parsed
        manifestActivities[activityName] =
            Triple(orientation, theme, location)
    }

    private fun handleStyleElement(context: XmlContext, element: Element) {
        if (element.tagName != "style") return

        val nameAttr = element.getAttribute("name").ifEmpty { return }
        val parentAttr = element.getAttribute("parent")

        // Check if the style name or parent looks translucent
        if (looksTranslucent(nameAttr) || looksTranslucent(parentAttr)) {
            resolvedTranslucentThemes.add(nameAttr)
            resolvedTranslucentThemes.add("@style/$nameAttr")
            resolvedTranslucentThemes.add("@android:style/$nameAttr")
        }

        // Check child items for windowIsTranslucent or windowIsFloating
        val items = element.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i) as? Element ?: continue
            val itemName = item.getAttribute("name")
            if (itemName == "android:windowIsTranslucent" ||
                itemName == "android:windowIsFloating" ||
                itemName == "windowIsTranslucent" ||
                itemName == "windowIsFloating"
            ) {
                val value = item.textContent?.trim()
                if (value == "true") {
                    resolvedTranslucentThemes.add(nameAttr)
                    resolvedTranslucentThemes.add("@style/$nameAttr")
                    resolvedTranslucentThemes.add("@android:style/$nameAttr")
                }
            }
        }
    }

    private fun looksTranslucent(value: String): Boolean {
        return TRANSLUCENT_THEME_PATTERNS.any { pattern -> value.contains(pattern) }
    }

    private fun reportIssue(
        context: XmlContext,
        location: Location,
        activityName: String,
        orientation: String,
        theme: String
    ) {
        context.report(
            ISSUE,
            location,
            buildMessage(activityName, orientation, theme)
        )
    }

    private fun buildMessage(
        activityName: String,
        orientation: String,
        theme: String
    ): String {
        return "The activity `$activityName` requests a fixed orientation " +
                "(`$orientation`) with a translucent theme (`$theme`). This combination " +
                "is not allowed on API 26+ and will cause a crash."
    }

    override fun afterCheckFile(context: Context) {
        // After processing the manifest, check deferred activities against already-known themes
        if (context.file.name == "AndroidManifest.xml") {
            val toRemove = mutableListOf<String>()
            for ((activityName, triple) in manifestActivities) {
                val (orientation, theme, location) = triple
                if (resolvedTranslucentThemes.contains(theme) ||
                    resolvedTranslucentThemes.contains(stripRef(theme))
                ) {
                    deferredReports.add(Triple(location, activityName, orientation))
                    toRemove.add(activityName)
                }
            }
            toRemove.forEach { manifestActivities.remove(it) }
        }
    }

    override fun afterCheckProject(context: Context) {
        // Final pass: check any remaining manifest activities against all collected themes
        for ((activityName, triple) in manifestActivities) {
            val (orientation, theme, location) = triple
            val bare = stripRef(theme)
            if (resolvedTranslucentThemes.contains(theme) ||
                resolvedTranslucentThemes.contains(bare)
            ) {
                context.report(
                    ISSUE,
                    location,
                    buildMessage(activityName, orientation, theme)
                )
            }
        }
    }

    private fun stripRef(ref: String): String {
        // @style/Foo -> Foo, @android:style/Foo -> Foo
        return ref.substringAfterLast("/")
    }
}