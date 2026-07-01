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
            explanation = "When using password sign-in through Credential Manager, an asset statements string resource file that includes the assetlinks.json files to load must be declared in the manifest using a <meta-data> element.",
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
        val partialResults = context.getPartialResults(ISSUE)
        val map = partialResults.map(context.project)
        val count = map.getInt("count", 0)
        map.put("file_$count", context.file.absolutePath)
        map.put("start_$count", context.getLocation(node).start?.offset ?: 0)
        map.put("end_$count", context.getLocation(node).end?.offset ?: 0)
        map.put("count", count + 1)
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        var hasAssetStatements = false
        for (project in partialResults.projects()) {
            if (hasAssetStatements(project)) {
                hasAssetStatements = true
                break
            }
        }

        if (!hasAssetStatements) {
            for (project in partialResults.projects()) {
                val map = partialResults.map(project)
                val count = map.getInt("count", 0)
                for (i in 0 until count) {
                    val filePath = map.getString("file_$i") ?: continue
                    val startOffset = map.getInt("start_$i") ?: 0
                    val endOffset = map.getInt("end_$i") ?: 0
                    val file = java.io.File(filePath)
                    val contents = if (file.exists()) file.readText() else null
                    val location = com.android.tools.lint.detector.api.Location.create(file, contents, startOffset, endOffset)
                    context.report(
                        ISSUE,
                        location,
                        "Missing Digital Asset Link for Credential Manager"
                    )
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // No-op, execution handled in checkPartialResults
    }

    private fun hasAssetStatements(project: com.android.tools.lint.detector.api.Project): Boolean {
        for (manifestFile in project.manifestFiles) {
            if (manifestFile.exists()) {
                val xml = manifestFile.readText()
                if (xml.contains("android:name=\"asset_statements\"") || xml.contains("android:name='asset_statements'")) {
                    return true
                }
            }
        }
        return false
    }
}