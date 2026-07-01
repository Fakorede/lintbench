package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.Location
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
            explanation = "When using password sign-in through Credential Manager, " +
                    "an asset statements string resource file that includes the assetlinks.json " +
                    "files to load must be declared in the manifest using a <meta-data> element.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf(
            "androidx.credentials.CreatePasswordRequest",
            "androidx.credentials.GetPasswordOption"
        )
    }

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        val map = context.getPartialResults(ISSUE).map(context.project)
        val count = map.getInteger("count") ?: 0
        map.put("count", count + 1)
        map.put("location_$count", context.getLocation(node))
        map.put("hasPasswordSignIn", true)
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        val mainProject = context.mainProject
        val mergedManifest = mainProject.mergedManifest ?: return

        if (hasAssetStatements(mergedManifest)) {
            return
        }

        val projects = partialResults.projects()
        for (project in projects) {
            val map = partialResults.map(project)
            if (map.getBoolean("hasPasswordSignIn") == true) {
                val count = map.getInteger("count") ?: 0
                for (i in 0 until count) {
                    val location = map.getLocation("location_$i")
                    if (location != null) {
                        context.report(
                            ISSUE,
                            location,
                            "Missing Digital Asset Link declaration in AndroidManifest.xml for Credential Manager password sign-in"
                        )
                    }
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // No-op, handled in checkPartialResults
    }

    private fun hasAssetStatements(manifest: org.w3c.dom.Document): Boolean {
        val root = manifest.documentElement ?: return false
        val metaDatas = root.getElementsByTagName("meta-data")
        for (i in 0 until metaDatas.length) {
            val metaData = metaDatas.item(i) as? org.w3c.dom.Element ?: continue
            val name = metaData.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            val name2 = metaData.getAttribute("android:name")
            if (name == "asset_statements" || name2 == "asset_statements") {
                return true
            }
        }
        return false
    }
}