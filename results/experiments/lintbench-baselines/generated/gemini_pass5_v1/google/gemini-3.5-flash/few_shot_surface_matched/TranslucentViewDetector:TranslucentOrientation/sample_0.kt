package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.*
import org.jetbrains.uast.*

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
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
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                java.util.EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE, Scope.RESOURCE_FILE)
            )
        )
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf("screenOrientation")
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("activity")
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT ||
                folderType == com.android.resources.ResourceFolderType.VALUES ||
                folderType == com.android.resources.ResourceFolderType.XML
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val value = attribute.value
        if (isFixedOrientation(value)) {
            val element = attribute.ownerElement
            if (hasTranslucentTheme(element)) {
                context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Should not specify screenOrientation with a translucent theme"
                )
            }
        }
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (element.tagName == "activity") {
            val orientationAttr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "screenOrientation")
            if (orientationAttr != null && isFixedOrientation(orientationAttr.value) && hasTranslucentTheme(element)) {
                context.report(
                    ISSUE,
                    orientationAttr,
                    context.getLocation(orientationAttr),
                    "Should not specify screenOrientation with a translucent theme"
                )
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return true
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("setRequestedOrientation")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInSubClassOf(method, "android.app.Activity")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Setting orientation programmatically on a potentially translucent Activity"
            )
        }
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        return when (orientation) {
            "portrait", "landscape", "reversePortrait", "reverseLandscape",
            "sensorPortrait", "sensorLandscape", "userPortrait", "userLandscape" -> true
            else -> false
        }
    }

    private fun hasTranslucentTheme(element: org.w3c.dom.Element): Boolean {
        val theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme")
        return theme.contains("Translucent") || theme.contains("Dialog")
    }
}