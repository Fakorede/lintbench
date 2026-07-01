package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.evaluateString

class PackageVisibilityDetector : Detector(), XmlScanner, SourceCodeScanner {

    companion object {
        private const val PERMISSION_QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ATTR_NAME = "name"
        private const val TAG_USES_PERMISSION = "uses-permission"

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = """
                If you need to query or interact with other installed apps, you should be using a \
                `<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES permission in \
                order to see all installed apps is rarely necessary, and most apps on Google Play are \
                not allowed to have this permission.
                See https://g.co/dev/packagevisibility for more information.
            """.trimIndent(),
            category = Category.COMPLIANCE,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                setOf(Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE)
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
        if (nameAttr.value == PERMISSION_QUERY_ALL_PACKAGES) {
            context.report(
                Incident(
                    ISSUE,
                    element,
                    context.getLocation(nameAttr),
                    "Use `<queries>` instead of the `QUERY_ALL_PACKAGES` permission"
                )
            )
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("checkSelfPermission", "requestPermissions", "shouldShowRequestPermissionRationale")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        for (arg in node.valueArguments) {
            if (arg.evaluateString() == PERMISSION_QUERY_ALL_PACKAGES) {
                context.report(
                    Incident(
                        ISSUE,
                        node,
                        context.getLocation(arg),
                        "Use `<queries>` instead of the `QUERY_ALL_PACKAGES` permission"
                    )
                )
                return
            }
        }
    }

    override fun filterIncident(incident: Incident): Boolean {
        return true
    }
}