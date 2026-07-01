package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.*
import org.jetbrains.uast.*

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf("screenOrientation")
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("activity")
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.VALUES ||
               folderType == com.android.resources.ResourceFolderType.XML
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val value = attribute.value
        if (value == "portrait" || value == "landscape" || value == "reversePortrait" ||
            value == "reverseLandscape" || value == "sensorPortrait" || value == "sensorLandscape" ||
            value == "locked" || value == "userPortrait" || value == "userLandscape") {

            val mainProject = context.mainProject
            if (mainProject.targetSdkVersion.apiLevel >= 26) {
                val element = attribute.ownerElement
                val theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme")
                if (theme.isNotEmpty() && isTranslucentThemeName(theme)) {
                    context.report(
                        Incident(
                            ISSUE,
                            attribute,
                            context.getValueLocation(attribute),
                            "Should not specify screenOrientation with a translucent theme"
                        )
                    )
                }
            }
        }
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (element.tagName == "activity") {
            val orientationAttr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "screenOrientation")
            if (orientationAttr != null) {
                visitAttribute(context, orientationAttr)
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
        val evaluator = context.evaluator
        if (evaluator.isMemberInSubClassOf(method, "android.app.Activity")) {
            val project = context.mainProject
            if (project.targetSdkVersion.apiLevel >= 26) {
                context.report(
                    Incident(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Setting orientation dynamically on targetSdk >= 26 can cause a crash if the activity is translucent"
                    )
                )
            }
        }
    }

    private fun isTranslucentThemeName(theme: String): Boolean {
        val name = theme.substringAfter('/')
        return name.contains("Translucent", ignoreCase = true) ||
               name.contains("Floating", ignoreCase = true) ||
               name.contains("Dialog", ignoreCase = true)
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with `targetSdkVersion` O or greater since there can be an another activity \
                visible behind your activity with a conflicting request.
                
                Devices running platform version O or greater will throw an exception in your \
                app if this state is detected.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                java.util.EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST, Scope.RESOURCE_FILE)
            )
        )
    }
}