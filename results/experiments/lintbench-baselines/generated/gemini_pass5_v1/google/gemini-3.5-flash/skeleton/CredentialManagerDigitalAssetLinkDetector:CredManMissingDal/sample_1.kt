package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
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
        private val IMPLEMENTATION = Implementation(
            CredentialManagerDigitalAssetLinkDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, you must associate your \
                app with a website by declaring the asset statements in your AndroidManifest.xml \
                using a <meta-data> element pointing to your assetlinks.json.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            "androidx.credentials.CreatePasswordRequest",
            "androidx.credentials.GetPasswordOption"
        )
    }

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        context.getPartialResults(ISSUE).map().put("uses_credential_manager", true)
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        val mainProject = context.mainProject
        var usesCredentialManager = false
        for (project in partialResults.projects()) {
            val map = partialResults.map(project)
            if (map.get("uses_credential_manager") as? Boolean == true) {
                usesCredentialManager = true
                break
            }
        }
        if (usesCredentialManager) {
            val manifest = context.client.getMergedManifest(mainProject)
            if (!hasAssetStatements(manifest)) {
                val location = Location.create(mainProject.dir)
                context.report(
                    ISSUE,
                    location,
                    "Missing Digital Asset Link for Credential Manager"
                )
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // No-op, logic handled in checkPartialResults
    }

    private fun hasAssetStatements(manifest: org.w3c.dom.Document?): Boolean {
        if (manifest == null) return false
        val root = manifest.documentElement ?: return false
        val application = root.getElementsByTagName("application").item(0) as? org.w3c.dom.Element ?: return false
        val metaDatas = application.getElementsByTagName("meta-data")
        for (i in 0 until metaDatas.length) {
            val item = metaDatas.item(i) as? org.w3c.dom.Element ?: continue
            val name = item.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                .ifEmpty { item.getAttribute("android:name") }
            if (name == "asset_statements") {
                return true
            }
        }
        return false
    }
}