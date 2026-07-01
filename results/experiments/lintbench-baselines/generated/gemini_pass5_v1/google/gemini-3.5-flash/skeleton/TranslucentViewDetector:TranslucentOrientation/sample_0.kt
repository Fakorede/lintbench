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

    private var packageName: String? = null
    private var applicationTheme: String? = null
    private val translucentStyles = mutableSetOf<String>()
    private val parentStyles = mutableMapOf<String, String>()
    private val activityThemes = mutableMapOf<String, String>()

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
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with `targetSdkVersion` O or greater since there can be another activity \
                visible behind your activity with a conflicting request.
                
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
        return listOf("style", "activity", "application", "manifest")
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // Unused
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "manifest" -> {
                packageName = element.getAttribute("package")
            }
            "application" -> {
                applicationTheme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme")
            }
            "style" -> {
                val styleName = element.getAttribute("name") ?: return
                val parent = element.getAttribute("parent")
                if (parent.isNotEmpty()) {
                    parentStyles[styleName] = parent
                } else if (styleName.contains('.')) {
                    val parentName = styleName.substringBeforeLast('.')
                    parentStyles[styleName] = parentName
                }

                val childNodes = element.childNodes
                for (i in 0 until childNodes.length) {
                    val child = childNodes.item(i)
                    if (child is Element && child.tagName == "item") {
                        val itemName = child.getAttribute("name")
                        val itemValue = child.textContent?.trim()
                        if (itemValue == "true") {
                            if (itemName == "android:windowIsTranslucent" ||
                                itemName == "android:windowIsFloating" ||
                                itemName == "android:windowSwipeToDismiss" ||
                                itemName == "windowIsTranslucent" ||
                                itemName == "windowIsFloating" ||
                                itemName == "windowSwipeToDismiss") {
                                translucentStyles.add(styleName)
                            }
                        }
                    }
                }
            }
            "activity" -> {
                val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name") ?: return
                val screenOrientation = element.getAttributeNS("http://schemas.android.com/apk/res/android", "screenOrientation")
                val theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme")
                    .takeIf { it.isNotEmpty() } ?: applicationTheme

                val fqcn = resolveFqcn(name, packageName)
                if (theme != null) {
                    activityThemes[fqcn] = theme
                }

                if (screenOrientation.isNotEmpty() && isFixedOrientation(screenOrientation)) {
                    if (theme != null) {
                        val incident = Incident(ISSUE, element, context.getNameLocation(element), "Activity is using a fixed orientation while being translucent")
                        val map = LintMap()
                        map.put("theme", theme)
                        incident.metadata = map
                        context.report(incident)
                    }
                }
            }
        }
    }

    private fun resolveFqcn(className: String, packageName: String?): String {
        val normalized = className.replace('$', '.')
        if (normalized.startsWith(".")) {
            return (packageName ?: "") + normalized
        }
        if (!normalized.contains(".")) {
            return if (packageName != null) "$packageName.$normalized" else normalized
        }
        return normalized
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

    private fun isTranslucentThemeName(themeName: String): Boolean {
        val name = themeName.substringAfterLast('/')
        return name.contains("Translucent", ignoreCase = true) ||
               name.contains("Dialog", ignoreCase = true) ||
               name.contains("Floating", ignoreCase = true) ||
               name.contains("SwipeToDismiss", ignoreCase = true)
    }

    private fun isThemeTranslucent(themeName: String): Boolean {
        val cleanName = themeName.substringAfterLast('/')
        if (translucentStyles.contains(cleanName)) {
            return true
        }
        if (isTranslucentThemeName(cleanName)) {
            return true
        }
        val visited = mutableSetOf<String>()
        var current: String? = cleanName
        while (current != null && visited.add(current)) {
            if (translucentStyles.contains(current)) {
                return true
            }
            if (isTranslucentThemeName(current)) {
                return true
            }
            current = parentStyles[current]?.substringAfterLast('/')
        }
        return false
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val targetSdk = context.project.targetSdkVersion.apiLevel
        if (targetSdk < 26) {
            return false
        }

        val theme = map.getString("theme")
        if (theme != null) {
            return isThemeTranslucent(theme)
        }

        val className = map.getString("class")
        if (className != null) {
            val activityTheme = activityThemes[className] ?: return false
            return isThemeTranslucent(activityTheme)
        }

        return false
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("setRequestedOrientation")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, "android.app.Activity", false)) {
            return
        }

        val arg = node.valueArguments.firstOrNull() ?: return
        val argText = arg.asSourceString()
        val isFixed = isFixedOrientationArg(argText) || (arg.evaluate() as? Int)?.let {
            it in setOf(0, 1, 8, 9, 11, 12, 14)
        } ?: false

        if (!isFixed) return

        val containingClass = node.uastParent?.let { parent ->
            var curr = parent
            while (curr != null && curr !is org.jetbrains.uast.UClass) {
                curr = curr.uastParent ?: break
            }
            curr as? org.jetbrains.uast.UClass
        } ?: return

        val fqcn = containingClass.qualifiedName ?: return
        val normalizedFqcn = fqcn.replace('$', '.')

        val incident = Incident(ISSUE, node, context.getNameLocation(node), "Activity is setting a fixed orientation while being translucent")
        val map = LintMap()
        map.put("class", normalizedFqcn)
        incident.metadata = map
        context.report(incident)
    }

    private fun isFixedOrientationArg(argumentText: String): Boolean {
        val upper = argumentText.uppercase()
        return upper.contains("PORTRAIT") || upper.contains("LANDSCAPE") || upper.contains("LOCKED")
    }
}