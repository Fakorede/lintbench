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
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.getParentOfType
import org.w3c.dom.Attr
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            TranslucentViewDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE, Scope.MANIFEST),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported 
                on apps with `targetSdkVersion` O or greater since there can be another activity 
                visible behind your activity with a conflicting request.

                Devices running platform version O or greater will throw an exception in your 
                app if this state is detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    private val translucentStyles = mutableSetOf<String>()
    private val styleParents = mutableMapOf<String, String>()
    private val activityThemes = mutableMapOf<String, String>()

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun getApplicableElements(): Collection<String>? = listOf("style", "activity")

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {}

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == "style") {
            val styleName = element.getAttribute("name") ?: return
            val parent = if (element.hasAttribute("parent")) {
                element.getAttribute("parent")
            } else {
                if (styleName.contains('.')) {
                    styleName.substringBeforeLast('.')
                } else {
                    null
                }
            }
            if (!parent.isNullOrEmpty()) {
                styleParents[styleName] = parent
            }
            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i) as? Element ?: continue
                if (child.tagName == "item") {
                    val name = child.getAttribute("name")
                    val text = child.textContent?.trim()
                    if (name == "android:windowIsTranslucent" && text == "true") {
                        translucentStyles.add(styleName)
                    } else if (name == "android:windowSwipeToDismiss" && text == "true") {
                        translucentStyles.add(styleName)
                    } else if (name == "android:windowIsFloating" && text == "true") {
                        translucentStyles.add(styleName)
                    }
                }
            }
        } else if (element.tagName == "activity") {
            var theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme")
            if (theme.isEmpty()) {
                val application = element.parentNode as? Element
                if (application != null && application.tagName == "application") {
                    theme = application.getAttributeNS("http://schemas.android.com/apk/res/android", "theme")
                }
            }
            val nameAttr = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            if (nameAttr.isNotEmpty()) {
                val fqName = resolveActivityName(context, nameAttr)
                if (theme.isNotEmpty()) {
                    activityThemes[fqName] = theme
                }
            }
            val orientation = element.getAttributeNS("http://schemas.android.com/apk/res/android", "screenOrientation")
            if (orientation.isNotEmpty() && isFixedOrientation(orientation)) {
                val incident = Incident(ISSUE, element, context.getNameLocation(element), "Activity should not request both a fixed orientation and translucency")
                val map = LintMap()
                map.put("theme", theme)
                map.put("orientation", orientation)
                incident.setData(map)
                context.report(incident)
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val targetSdk = context.project.targetSdkVersion.apiLevel
        if (targetSdk < 26) {
            return false
        }
        val theme = map.getString("theme") ?: run {
            val activityClass = map.getString("activityClass") ?: return false
            activityThemes[activityClass] ?: ""
        }
        if (theme.isNotEmpty() && isThemeTranslucent(theme)) {
            return true
        }
        return false
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("setRequestedOrientation")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (context.evaluator.isMemberInSubClassOf(method, "android.app.Activity", false)) {
            val argument = node.valueArguments.firstOrNull() ?: return
            val evaluated = argument.evaluate()
            val isFixed = when (evaluated) {
                is Int -> isFixedOrientation(evaluated)
                else -> {
                    val src = argument.asSourceString()
                    src.contains("SCREEN_ORIENTATION_PORTRAIT") ||
                    src.contains("SCREEN_ORIENTATION_LANDSCAPE") ||
                    src.contains("SCREEN_ORIENTATION_SENSOR_PORTRAIT") ||
                    src.contains("SCREEN_ORIENTATION_SENSOR_LANDSCAPE") ||
                    src.contains("SCREEN_ORIENTATION_REVERSE_PORTRAIT") ||
                    src.contains("SCREEN_ORIENTATION_REVERSE_LANDSCAPE") ||
                    src.contains("SCREEN_ORIENTATION_USER_PORTRAIT") ||
                    src.contains("SCREEN_ORIENTATION_USER_LANDSCAPE") ||
                    src.contains("SCREEN_ORIENTATION_LOCKED")
                }
            }
            if (isFixed) {
                val containingClass = node.getParentOfType<UClass>(UClass::class.java)
                val fqName = containingClass?.qualifiedName ?: return
                val incident = Incident(ISSUE, node, context.getLocation(node), "Activity should not request both a fixed orientation and translucency")
                val map = LintMap()
                map.put("activityClass", fqName)
                incident.setData(map)
                context.report(incident)
            }
        }
    }

    private fun resolveActivityName(context: XmlContext, name: String): String {
        if (name.startsWith(".")) {
            val pkg = context.project.packageName ?: ""
            return pkg + name
        } else if (!name.contains(".")) {
            val pkg = context.project.packageName ?: ""
            return "$pkg.$name"
        }
        return name
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        return when (orientation) {
            "portrait", "landscape",
            "reversePortrait", "reverseLandscape",
            "sensorPortrait", "sensorLandscape",
            "userPortrait", "userLandscape",
            "locked" -> true
            else -> false
        }
    }

    private fun isFixedOrientation(orientation: Int): Boolean {
        return orientation == 0 || // LANDSCAPE
               orientation == 1 || // PORTRAIT
               orientation == 6 || // SENSOR_LANDSCAPE
               orientation == 7 || // SENSOR_PORTRAIT
               orientation == 8 || // REVERSE_LANDSCAPE
               orientation == 9 || // REVERSE_PORTRAIT
               orientation == 11 || // USER_LANDSCAPE
               orientation == 12 || // USER_PORTRAIT
               orientation == 14   // LOCKED
    }

    private fun isSystemTranslucentTheme(themeName: String): Boolean {
        val name = themeName.substringAfterLast('.')
        return name.contains("Translucent", ignoreCase = true) ||
               name.contains("Dialog", ignoreCase = true) ||
               name.contains("Floating", ignoreCase = true) ||
               name.contains("BottomSheet", ignoreCase = true)
    }

    private fun isThemeTranslucent(themeName: String): Boolean {
        val cleanName = themeName.removePrefix("@style/").removePrefix("@android:style/")
        if (isSystemTranslucentTheme(cleanName)) {
            return true
        }
        if (translucentStyles.contains(cleanName)) {
            return true
        }
        var current = cleanName
        val visited = mutableSetOf<String>()
        while (current in styleParents) {
            if (!visited.add(current)) break
            val parent = styleParents[current] ?: break
            val cleanParent = parent.removePrefix("@style/").removePrefix("@android:style/")
            if (isSystemTranslucentTheme(cleanParent) || translucentStyles.contains(cleanParent)) {
                return true
            }
            current = cleanParent
        }
        return false
    }
}