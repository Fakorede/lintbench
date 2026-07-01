package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Attr
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

    private val translucentStyles = mutableSetOf<String>()
    private val activityThemes = mutableMapOf<String, String>()
    private var applicationTheme: String? = null

    companion object {
        private val FIXED_ORIENTATIONS = setOf(
            0,  // ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            1,  // ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            6,  // ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            7,  // ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            8,  // ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            9,  // ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            11, // ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            12, // ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            14  // ActivityInfo.SCREEN_ORIENTATION_LOCKED
        )

        private val IMPLEMENTATION = Implementation(
            TranslucentViewDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE, Scope.MANIFEST),
        )

        @JvmField
        val ISSUE = Issue.create(
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
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun getApplicableElements(): Collection<String>? {
        return listOf("style", "application", "activity")
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // No-op: handled in visitElement
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        if (tagName == "style") {
            val name = element.getAttribute("name")
            if (name.isNotEmpty()) {
                val parent = element.getAttribute("parent")
                var isTranslucent = false
                val childNodes = element.childNodes
                for (i in 0 until childNodes.length) {
                    val node = childNodes.item(i)
                    if (node is Element && node.tagName == "item") {
                        val itemName = node.getAttribute("name")
                        if (itemName == "android:windowIsTranslucent" ||
                            itemName == "android:windowSwipeToDismiss" ||
                            itemName == "android:windowIsFloating" ||
                            itemName == "windowIsTranslucent" ||
                            itemName == "windowSwipeToDismiss" ||
                            itemName == "windowIsFloating"
                        ) {
                            val text = node.textContent?.trim()
                            if (text == "true") {
                                isTranslucent = true
                            }
                        }
                    }
                }
                if (isTranslucent) {
                    translucentStyles.add(name)
                } else if (parent.isNotEmpty()) {
                    if (translucentStyles.contains(parent) || isKnownTranslucentTheme(parent)) {
                        translucentStyles.add(name)
                    }
                }
            }
        } else if (tagName == "application") {
            val theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme")
            if (theme.isNotEmpty()) {
                applicationTheme = theme
            }
        } else if (tagName == "activity") {
            val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            val theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme")
            val orientation = element.getAttributeNS("http://schemas.android.com/apk/res/android", "screenOrientation")

            val actualTheme = if (theme.isNotEmpty()) theme else null
            val fullClassName = resolveClassName(context, name)
            if (actualTheme != null) {
                activityThemes[fullClassName] = actualTheme
            }

            if (orientation.isNotEmpty() && isFixedOrientationString(orientation)) {
                val map = LintMap()
                if (actualTheme != null) {
                    map.put("theme", actualTheme)
                }
                val incident = Incident(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Only fullscreen activities can request orientation"
                )
                context.report(incident, map)
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        if (context.project.targetSdkVersion.apiLevel < 26) {
            return false
        }
        val theme = map.getString("theme") ?: applicationTheme ?: return false
        return isTranslucent(theme)
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("setRequestedOrientation")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (context.project.targetSdkVersion.apiLevel < 26) {
            return
        }

        val containingClass = method.containingClass ?: return
        if (!context.evaluator.inheritsFrom(containingClass, "android.app.Activity", false)) {
            return
        }

        val argument = node.valueArguments.firstOrNull() ?: return
        val evaluated = argument.evaluate()
        val isFixed = if (evaluated is Int) {
            FIXED_ORIENTATIONS.contains(evaluated)
        } else {
            val src = argument.asSourceString()
            src.contains("SCREEN_ORIENTATION_PORTRAIT") ||
                    src.contains("SCREEN_ORIENTATION_LANDSCAPE") ||
                    src.contains("SCREEN_ORIENTATION_LOCKED") ||
                    src.contains("SCREEN_ORIENTATION_SENSOR_PORTRAIT") ||
                    src.contains("SCREEN_ORIENTATION_SENSOR_LANDSCAPE") ||
                    src.contains("SCREEN_ORIENTATION_REVERSE_PORTRAIT") ||
                    src.contains("SCREEN_ORIENTATION_REVERSE_LANDSCAPE") ||
                    src.contains("SCREEN_ORIENTATION_USER_PORTRAIT") ||
                    src.contains("SCREEN_ORIENTATION_USER_LANDSCAPE")
        }

        if (!isFixed) return

        val containingClassType = context.evaluator.getContainingClass(node) ?: return
        val className = containingClassType.qualifiedName ?: return
        val theme = activityThemes[className] ?: applicationTheme ?: return

        val map = LintMap()
        map.put("theme", theme)
        val incident = Incident(
            ISSUE,
            node,
            context.getLocation(node),
            "Only fullscreen activities can request orientation"
        )
        context.report(incident, map)
    }

    private fun resolveClassName(context: XmlContext, name: String): String {
        if (name.startsWith(".")) {
            val pkg = context.document.documentElement?.getAttribute("package")?.takeIf { it.isNotEmpty() }
                ?: context.project.packageName
                ?: ""
            return pkg + name
        }
        if (!name.contains(".")) {
            val pkg = context.document.documentElement?.getAttribute("package")?.takeIf { it.isNotEmpty() }
                ?: context.project.packageName
                ?: ""
            return "$pkg.$name"
        }
        return name
    }

    private fun isFixedOrientationString(orientation: String): Boolean {
        return when (orientation) {
            "portrait", "landscape", "reversePortrait", "reverseLandscape",
            "sensorPortrait", "sensorLandscape", "userPortrait", "userLandscape", "locked" -> true
            else -> false
        }
    }

    private fun isTranslucent(theme: String): Boolean {
        val cleanTheme = theme.replace("@style/", "").replace("@android:style/", "")
        return translucentStyles.contains(cleanTheme) || isKnownTranslucentTheme(cleanTheme)
    }

    private fun isKnownTranslucentTheme(theme: String): Boolean {
        val clean = theme.replace("@style/", "").replace("@android:style/", "")
        return clean.contains("Translucent", ignoreCase = true) ||
                clean.contains("Dialog", ignoreCase = true) ||
                clean.contains("Floating", ignoreCase = true) ||
                clean.contains("SwipeToDismiss", ignoreCase = true)
    }
}