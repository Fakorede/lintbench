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
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.w3c.dom.Attr
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

    private val translucentStyles = mutableSetOf<String>()
    private val activityThemes = mutableMapOf<String, String>()

    companion object {
        private val IMPLEMENTATION = Implementation(
            TranslucentViewDetector::class.java,
            EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE, Scope.JAVA_FILE),
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

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun getApplicableElements(): Collection<String> = listOf("style", "activity")

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {}

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == "style") {
            val name = element.getAttribute("name") ?: return
            val parent = element.getAttribute("parent")
            
            var isTranslucent = false
            val cleanName = cleanStyleName(name)
            if (cleanName.contains("Translucent", ignoreCase = true) ||
                cleanName.contains("Dialog", ignoreCase = true) ||
                cleanName.contains("Floating", ignoreCase = true) ||
                cleanName.contains("Transparent", ignoreCase = true)) {
                isTranslucent = true
            } else if (!parent.isNullOrEmpty()) {
                val cleanParent = cleanStyleName(parent)
                if (cleanParent.contains("Translucent", ignoreCase = true) ||
                    cleanParent.contains("Dialog", ignoreCase = true) ||
                    cleanParent.contains("Floating", ignoreCase = true) ||
                    cleanParent.contains("Transparent", ignoreCase = true) ||
                    translucentStyles.contains(cleanParent)) {
                    isTranslucent = true
                }
            }
            
            if (!isTranslucent) {
                val childNodes = element.childNodes
                for (i in 0 until childNodes.length) {
                    val node = childNodes.item(i)
                    if (node is Element && node.tagName == "item") {
                        val itemName = node.getAttribute("name")
                        val itemValue = node.textContent?.trim()
                        if ((itemName == "android:windowIsTranslucent" || 
                             itemName == "android:windowSwipeToDismiss" || 
                             itemName == "android:windowIsFloating") && 
                            itemValue == "true") {
                            isTranslucent = true
                            break
                        }
                    }
                }
            }
            
            if (isTranslucent) {
                translucentStyles.add(cleanName)
            }
        } else if (element.tagName == "activity") {
            val orientation = element.getAttribute("android:screenOrientation")
            val theme = element.getAttribute("android:theme").takeIf { it.isNotEmpty() }
                ?: (element.parentNode as? Element)?.getAttribute("android:theme")
            
            val packageName = element.ownerDocument.documentElement.getAttribute("package") ?: ""
            val activityName = element.getAttribute("android:name") ?: ""
            val fqName = if (activityName.startsWith(".")) {
                packageName + activityName
            } else if (!activityName.contains(".")) {
                "$packageName.$activityName"
            } else {
                activityName
            }
            
            if (!theme.isNullOrEmpty()) {
                activityThemes[fqName] = theme
                
                if (orientation.isNotEmpty() && isFixedOrientation(orientation)) {
                    val incident = Incident(context, ISSUE)
                        .at(element)
                        .message("Activity ($activityName) specifies a fixed orientation ($orientation) and has a translucent theme ($theme)")
                    
                    val map = LintMap()
                    map.put("theme", theme)
                    incident.metadata = map
                    context.report(incident)
                }
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        if (context.project.targetSdkVersion.apiLevel < 26) {
            return false
        }
        
        val theme = map.getString("theme")
        if (theme != null) {
            return isThemeTranslucent(theme)
        }
        
        val activityFqName = map.getString("activityFqName")
        if (activityFqName != null) {
            val activityTheme = activityThemes[activityFqName] ?: return false
            return isThemeTranslucent(activityTheme)
        }
        
        return false
    }

    override fun getApplicableMethodNames(): List<String> = listOf("setRequestedOrientation")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (method.name == "setRequestedOrientation") {
            val arg = node.valueArguments.firstOrNull() ?: return
            val argStr = arg.asSourceString()
            if (isFixedOrientationExpression(argStr)) {
                var current: UElement? = node
                var containingClass: UClass? = null
                while (current != null) {
                    if (current is UClass) {
                        containingClass = current
                        break
                    }
                    current = current.uastParent
                }
                val fqName = containingClass?.qualifiedName
                if (fqName != null) {
                    val incident = Incident(context, ISSUE)
                        .at(node)
                        .message("Activity ($fqName) specifies a fixed orientation programmatically and has a translucent theme")
                    
                    val map = LintMap()
                    map.put("activityFqName", fqName)
                    incident.metadata = map
                    context.report(incident)
                }
            }
        }
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        return when (orientation) {
            "portrait", "landscape",
            "reversePortrait", "reversePortait",
            "sensorPortrait", "sensorLandscape",
            "userPortrait", "userLandscape",
            "locked" -> true
            else -> false
        }
    }

    private fun isFixedOrientationExpression(expressionString: String): Boolean {
        val upper = expressionString.toUpperCase()
        return upper.contains("PORTRAIT") ||
               upper.contains("LANDSCAPE") ||
               upper.contains("LOCKED") ||
               expressionString == "0" ||
               expressionString == "1" ||
               expressionString == "8" ||
               expressionString == "9" ||
               expressionString == "14"
    }

    private fun isThemeTranslucent(theme: String): Boolean {
        val cleanName = cleanStyleName(theme)
        if (cleanName.contains("Translucent", ignoreCase = true) ||
            cleanName.contains("Dialog", ignoreCase = true) ||
            cleanName.contains("Floating", ignoreCase = true) ||
            cleanName.contains("Transparent", ignoreCase = true)) {
            return true
        }
        return translucentStyles.contains(cleanName)
    }

    private fun cleanStyleName(style: String): String {
        return style.substringAfterLast("/")
    }
}