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
import com.intellij.psi.PsiClassType
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
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
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with `targetSdkVersion` O or greater since there can be another activity \
                visible behind your activity with a conflicting request.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    private val translucentStyles = mutableSetOf<String>()
    private val styleParents = mutableMapOf<String, String>()

    override fun beforeCheckEachProject(context: Context) {
        translucentStyles.clear()
        styleParents.clear()
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf("screenOrientation")
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("style", "item")
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (context.project.targetSdkVersion.apiLevel < 26) return

        val orientation = attribute.value
        if (!isFixedOrientation(orientation)) return

        val activity = attribute.ownerElement
        if (activity.tagName != "activity") return

        val theme = activity.getAttributeNS("http://schemas.android.com/apk/res/android", "theme")
            .ifEmpty { activity.getAttribute("android:theme") }
            .ifEmpty {
                val application = activity.parentNode as? Element
                if (application?.tagName == "application") {
                    application.getAttributeNS("http://schemas.android.com/apk/res/android", "theme")
                        .ifEmpty { application.getAttribute("android:theme") }
                } else ""
            }

        if (theme.isNullOrEmpty()) return

        val incident = Incident(context, ISSUE)
            .at(attribute)
            .message("An activity with a translucent theme cannot request a fixed orientation")
        val map = LintMap()
        map.put("theme", theme)
        context.report(incident, map)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == "style") {
            val name = element.getAttribute("name")
            if (name.isNotEmpty()) {
                val parent = element.getAttribute("parent")
                if (parent.isNotEmpty()) {
                    styleParents[name] = parent.substringAfter('/')
                } else if (name.contains('.')) {
                    styleParents[name] = name.substringBeforeLast('.')
                }
            }
        } else if (element.tagName == "item") {
            val name = element.getAttribute("name")
            if (name.endsWith("windowIsTranslucent") || 
                name.endsWith("windowIsFloating") || 
                name.endsWith("windowSwipeToDismiss")) {
                val value = element.textContent?.trim()
                if (value == "true") {
                    val parentStyle = element.parentNode as? Element
                    if (parentStyle != null && parentStyle.tagName == "style") {
                        val styleName = parentStyle.getAttribute("name")
                        if (styleName.isNotEmpty()) {
                            translucentStyles.add(styleName)
                        }
                    }
                }
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        if (context.project.targetSdkVersion.apiLevel < 26) return false

        val theme = map.getString("theme")
        if (theme != null) {
            return isTranslucent(theme)
        }

        val className = map.getString("className")
        if (className != null) {
            val manifest = context.client.getMergedManifest(context.project) ?: return false
            val activityTheme = getActivityTheme(manifest, className) ?: return false
            return isTranslucent(activityTheme)
        }

        return false
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("setRequestedOrientation")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (context.project.targetSdkVersion.apiLevel < 26) return

        if (!context.evaluator.isMemberInSubClassOf(method, "android.app.Activity")) {
            return
        }

        val className = if (node.receiver != null) {
            (node.receiverType as? PsiClassType)?.resolve()?.qualifiedName
        } else {
            val containingClass = node.uastParent?.let {
                var parent = it
                while (parent != null && parent !is org.jetbrains.uast.UClass) {
                    parent = parent.uastParent ?: break
                }
                parent as? org.jetbrains.uast.UClass
            }
            containingClass?.qualifiedName
        } ?: return

        val incident = Incident(context, ISSUE)
            .at(node)
            .message("An activity with a translucent theme cannot request a fixed orientation")
        val map = LintMap()
        map.put("className", className)
        context.report(incident, map)
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        return when (orientation) {
            "portrait", "landscape", "reversePortrait", "reverseLandscape",
            "sensorPortrait", "sensorLandscape", "userPortrait", "userLandscape", "locked" -> true
            else -> false
        }
    }

    private fun isTranslucent(themeName: String): Boolean {
        val styleName = themeName.substringAfter('/')
        var current: String? = styleName
        val visited = mutableSetOf<String>()
        while (current != null && visited.add(current)) {
            if (translucentStyles.contains(current) || isDefaultTranslucentTheme(current)) {
                return true
            }
            current = styleParents[current]
        }
        return false
    }

    private fun isDefaultTranslucentTheme(themeName: String): Boolean {
        val lower = themeName.lowercase()
        return lower.contains("translucent") || 
               lower.contains("dialog") || 
               lower.contains("floating") || 
               lower.contains("swipetodismiss")
    }

    private fun getActivityTheme(manifest: org.w3c.dom.Document, className: String): String? {
        val root = manifest.documentElement ?: return null
        val application = root.getElementsByTagName("application").item(0) as? Element
        val defaultTheme = application?.let {
            it.getAttributeNS("http://schemas.android.com/apk/res/android", "theme")
                .ifEmpty { it.getAttribute("android:theme") }
        } ?: ""

        val activities = root.getElementsByTagName("activity")
        val packageName = root.getAttribute("package") ?: ""
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            val name = activity.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                .ifEmpty { activity.getAttribute("android:name") }
            if (matchClassName(name, className, packageName)) {
                val theme = activity.getAttributeNS("http://schemas.android.com/apk/res/android", "theme")
                    .ifEmpty { activity.getAttribute("android:theme") }
                return if (theme.isNotEmpty()) theme else if (defaultTheme.isNotEmpty()) defaultTheme else null
            }
        }
        return null
    }

    private fun matchClassName(manifestName: String, fqName: String, packageName: String): Boolean {
        if (manifestName == fqName) return true
        if (manifestName.startsWith(".")) {
            return "$packageName$manifestName" == fqName
        }
        if (!manifestName.contains(".")) {
            return "$packageName.$manifestName" == fqName
        }
        return false
    }
}