SOURCE = '''
// EXAMPLE: MissingResourcesPropertiesDetector (Kotlin, GradleScanner)
// Issue: MissingResourcesProperties
// Explanation: When generateLocaleConfig is turned on, the default locale must
// be specified in a resources.properties file.

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import java.io.File

class MissingResourcesPropertiesDetector : Detector(), GradleScanner {

    override fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        propertyCookie: Any,
        valueCookie: Any,
        statementCookie: Any,
    ) {
        if (context.project.isLibrary) return
        if (property == "generateLocaleConfig" && value == "true") {
            if (context.project.resourceFolders.none { File(it, "resources.properties").exists() }) {
                val incident = Incident(
                    ISSUE, propertyCookie,
                    context.getLocation(propertyCookie),
                    "Missing resources.properties file"
                )
                context.client.report(context, incident)
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "MissingResourcesProperties",
            briefDescription = "Missing resources.properties file",
            explanation = "When `generateLocaleConfig` is turned on, the default locale must be " +
                "specified in a resources.properties file.",
            category = Category.CORRECTNESS,
            priority = 2,
            severity = Severity.WARNING,
            implementation = Implementation(
                MissingResourcesPropertiesDetector::class.java, Scope.GRADLE_SCOPE
            ),
            androidSpecific = true,
        )
    }
}
'''.strip()
