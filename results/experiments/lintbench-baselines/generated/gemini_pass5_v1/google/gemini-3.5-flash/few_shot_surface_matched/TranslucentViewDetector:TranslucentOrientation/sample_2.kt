package com.android.tools.lint.checks

import com.android.tools.lint.client.api.*
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class TranslucentViewDetector : Detector(), SourceCodeScanner, XmlScanner {

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf("screenOrientation")
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val element = attribute.ownerElement
        if (element.tagName == "activity") {
            checkElement(context, element)
        }
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("activity")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        checkElement(context, element)
    }

    private fun checkElement(context: XmlContext, element: org.w3c.dom.Element) {
        val orientation = element.getAttributeNS("http://schemas.android.com/apk/res/android", "screenOrientation")
        val theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme")
        if (orientation.isNotEmpty() && theme.isNotEmpty() && orientation != "unspecified") {
            if (theme.contains("Translucent") || theme.contains("Dialog")) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Mixing screenOrientation and translucency is not supported on Android O or greater"
                )
            }
        }
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return true
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return true
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("setRequestedOrientation")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Mixing screenOrientation and translucency is not supported on Android O or greater"
        )
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
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                java.util.EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
        )
    }
}