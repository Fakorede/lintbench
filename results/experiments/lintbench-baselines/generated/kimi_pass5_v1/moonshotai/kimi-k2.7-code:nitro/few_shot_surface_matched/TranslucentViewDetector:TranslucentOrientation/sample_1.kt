package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val TARGET_SDK = 26

        private const val WINDOW_IS_TRANSLUCENT = "android:windowIsTranslucent"
        private const val WINDOW_IS_TRANSLUCENT_NO_PREFIX = "windowIsTranslucent"

        private val FIXED_ORIENTATIONS = setOf(
            "landscape",
            "portrait",
            "reverseLandscape",
            "reversePortrait",
            "sensorLandscape",
            "sensorPortrait",
            "userLandscape",
            "userPortrait",
            "fullSensor",
            "fullUser",
            "locked"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screen orientation and translucency",
            explanation = """
                Specifying a fixed screenOrientation with a translucent theme isn't supported
                on apps with targetSdkVersion O or greater, because another activity may be
                visible behind your activity with a conflicting orientation request.
                Devices running Android O or later will throw an exception if this state is
                detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
                Scope.MANIFEST_SCOPE,
                Scope.RESOURCE_FILE_SCOPE
            ),
            androidSpecific = true
        )
    }

    private val translucentStyles = mutableSetOf<String>()
    private var currentStyle: String? = null
    private var currentStyleTranslucent = false

    private val manifestFixedOrientation = mutableMapOf<String, MutableList<Location>>()
    private val manifestTranslucentTheme = mutableMapOf<String, MutableList<Location>>()
    private val codeFixedOrientation = mutableMapOf<String, MutableList<Location>>()
    private val codeTranslucentTheme = mutableMapOf<String, MutableList<Location>>()

    override fun getApplicableElements(): Collection<String> = listOf("style", "item", "activity")

    override fun getApplicableAttributes(): Collection<String> = listOf("screenOrientation", "theme")

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean = true

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        when (element.tagName) {
            "style" -> {
                currentStyle = element.getAttribute("name").nullIfBlank()
                currentStyleTranslucent = false
                val parent = element.getAttribute("parent")
                if (parent.contains("Translucent", ignoreCase = true)) {
                    currentStyleTranslucent = true
                    currentStyle?.let { translucentStyles.add(it) }
                }
            }
            "item" -> {
                val name = element.getAttribute("name")
                if ((name == WINDOW_IS_TRANSLUCENT || name == WINDOW_IS_TRANSLUCENT_NO_PREFIX)
                    && element.textContent?.trim() == "true"
                ) {
                    currentStyleTranslucent = true
                    currentStyle?.let { translucentStyles.add(it) }
                }
            }
            "activity" -> {
                // Activity attributes are handled in visitAttribute.
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val element = attribute.ownerElement ?: return
        if (element.tagName != "activity") return
        val localName = attribute.localName ?: return
        val cls = resolveActivityClass(context, element) ?: return

        when (localName) {
            "screenOrientation" -> {
                val value = attribute.value
                if (value in FIXED_ORIENTATIONS) {
                    manifestFixedOrientation.getOrPut(cls) { mutableListOf() }
                        .add(context.getLocation(attribute))
                }
            }
            "theme" -> {
                val styleName = stripThemePrefix(attribute.value)
                if (translucentStyles.contains(styleName)
                    || styleName.contains("Translucent", ignoreCase = true)
                ) {
                    manifestTranslucentTheme.getOrPut(cls) { mutableListOf() }
                        .add(context.getLocation(attribute))
                }
            }
        }
    }

    override fun getApplicableMethodNames(): List<String> = listOf("setTheme", "setRequestedOrientation")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = node.getParentOfType(
            org.jetbrains.uast.UClass::class.java,
            true
        ) ?: return
        if (!context.evaluator.extendsClass(containingClass, "android.app.Activity", false)) return
        val className = containingClass.qualifiedName ?: return

        when (method.name) {
            "setTheme" -> {
                val arg = node.valueArguments.firstOrNull() ?: return
                val source = arg.asSourceString()
                if (source.contains("Translucent", ignoreCase = true)
                    || isTranslucentStyleReference(source)
                ) {
                    codeTranslucentTheme.getOrPut(className) { mutableListOf() }
                        .add(context.getLocation(node))
                }
            }
            "setRequestedOrientation" -> {
                val arg = node.valueArguments.firstOrNull() ?: return
                val source = arg.asSourceString()
                if (!source.contains("UNSPECIFIED", ignoreCase = true)
                    && !source.contains("BEHIND", ignoreCase = true)
                    && (source.contains("LANDSCAPE", ignoreCase = true)
                        || source.contains("PORTRAIT", ignoreCase = true)
                        || source.contains("LOCKED", ignoreCase = true))
                ) {
                    codeFixedOrientation.getOrPut(className) { mutableListOf() }
                        .add(context.getLocation(node))
                }
            }
        }
    }

    override fun beforeCheckProject(context: Context) {
        translucentStyles.clear()
        manifestFixedOrientation.clear()
        manifestTranslucentTheme.clear()
        codeFixedOrientation.clear()
        codeTranslucentTheme.clear()
        currentStyle = null
        currentStyleTranslucent = false
    }

    override fun afterCheckProject(context: Context) {
        val fixedClasses = manifestFixedOrientation.keys + codeFixedOrientation.keys
        val translucentClasses = manifestTranslucentTheme.keys + codeTranslucentTheme.keys
        val conflicting = fixedClasses.intersect(translucentClasses)

        for (cls in conflicting) {
            val message =
                "Activity $cls has a fixed screenOrientation and a translucent theme, " +
                    "which is not supported on Android O or later."
            val locations = mutableListOf<Location>()
            manifestFixedOrientation[cls]?.let { locations.addAll(it) }
            codeFixedOrientation[cls]?.let { locations.addAll(it) }
            manifestTranslucentTheme[cls]?.let { locations.addAll(it) }
            codeTranslucentTheme[cls]?.let { locations.addAll(it) }

            for (location in locations) {
                context.report(Incident(ISSUE, location, message))
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident): Boolean {
        if (incident.issue != ISSUE) return true
        val targetSdk = context.mainProject?.buildTargetSdk
            ?: context.project.buildTargetSdk
        return targetSdk == null || targetSdk >= TARGET_SDK
    }

    private fun resolveActivityClass(context: XmlContext, element: org.w3c.dom.Element): String? {
        val raw = element.getAttributeNS(ANDROID_NS, "name").nullIfBlank() ?: return null
        val pkg = context.mainProject?.manifestPackageName
            ?: context.project.manifestPackageName
            ?: return null
        return when {
            raw.startsWith(".") -> pkg + raw
            raw.contains(".") -> raw
            else -> "$pkg.$raw"
        }
    }

    private fun stripThemePrefix(themeAttr: String): String = when {
        themeAttr.startsWith("@style/") -> themeAttr.substring(7)
        themeAttr.startsWith("@android:style/") -> themeAttr.substring(15)
        themeAttr.startsWith("@*android:style/") -> themeAttr.substring(16)
        themeAttr.startsWith("?") -> themeAttr.substringAfterLast('/', themeAttr)
        else -> themeAttr
    }

    private fun isTranslucentStyleReference(source: String): Boolean {
        val candidate = source.substringAfterLast('.', "").replace('_', '.')
        return translucentStyles.any { it.equals(candidate, ignoreCase = true) }
    }

    private fun String.nullIfBlank(): String? = if (isBlank()) null else this
}