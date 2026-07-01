package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements string \
                resource file that includes the `assetlinks.json` files to load must be declared \
                in the manifest using a `<meta-data>` element.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            "androidx.credentials.CreatePasswordRequest",
            "androidx.credentials.GetPasswordOption"
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        candidate: PsiMethod
    ) {
        val map = context.getPartialResults(ISSUE).map(context.project)
        map.put("uses_cred_man", true)
        if (!map.containsKey("location")) {
            map.put("location", context.getLocation(node))
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        var usesCredMan = false
        var location: Location? = null

        for (project in partialResults.projects()) {
            val map = partialResults.map(project)
            if (map.getBoolean("uses_cred_man") == true) {
                usesCredMan = true
                if (location == null) {
                    location = map.getLocation("location")
                }
            }
        }

        if (usesCredMan) {
            if (!hasAssetStatements(context)) {
                val finalLocation = location ?: context.getLocation(context.project)
                val incident = Incident(
                    ISSUE,
                    finalLocation,
                    "Missing Digital Asset Link for Credential Manager. An asset statements string resource file must be declared in the manifest using a `<meta-data>` element."
                )
                context.report(incident)
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // Required override, no-op as the check is performed in checkPartialResults
    }

    private fun hasAssetStatements(context: Context): Boolean {
        val project = context.mainProject
        val mergedManifest = project.mergedManifest ?: return false
        val metaDatas = mergedManifest.getElementsByTagName("meta-data") ?: return false
        for (i in 0 until metaDatas.length) {
            val element = metaDatas.item(i) as? org.w3c.dom.Element ?: continue
            val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            if (name == "asset_statements") {
                return true
            }
            if (element.getAttribute("android:name") == "asset_statements") {
                return true
            }
        }
        return false
    }
}