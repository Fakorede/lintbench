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
                When using password sign-in through Credential Manager, you must declare \
                an asset statements string resource file that includes the `assetlinks.json` \
                files to load in your manifest using a `<meta-data>` element.
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
        val file = context.file.absolutePath
        val range = context.getLocation(node)
        val start = range.start?.offset ?: 0
        val end = range.end?.offset ?: 0
        val locationStr = "$file:$start:$end"

        val partialResults = context.getPartialResults(ISSUE)
        val existing = partialResults.getString("calls")
        val newCalls = if (existing.isNullOrEmpty()) locationStr else "$existing;$locationStr"
        partialResults.put("calls", newCalls)
    }

    override fun beforeCheckProject(context: Context) {
        if (hasDigitalAssetLink(context)) {
            val partialResults = context.getPartialResults(ISSUE)
            partialResults.put("hasDal", true)
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        var hasDalAnywhere = false
        val calls = mutableListOf<String>()

        for (project in partialResults.projects()) {
            val map = partialResults.map(project)
            if (map.getBoolean("hasDal") == true) {
                hasDalAnywhere = true
            }
            val projectCalls = map.getString("calls")
            if (!projectCalls.isNullOrEmpty()) {
                calls.addAll(projectCalls.split(";"))
            }
        }

        if (calls.isNotEmpty() && !hasDalAnywhere) {
            for (call in calls) {
                val parts = call.split(":")
                if (parts.size == 3) {
                    val filePath = parts[0]
                    val startOffset = parts[1].toIntOrNull() ?: 0
                    val endOffset = parts[2].toIntOrNull() ?: 0
                    val file = java.io.File(filePath)
                    if (file.exists()) {
                        val contents = context.getContents(file) ?: file.readText()
                        val location = Location.create(file, contents, startOffset, endOffset)
                        context.report(
                            ISSUE,
                            location,
                            "Missing Digital Asset Link for Credential Manager"
                        )
                    }
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // Handled in checkPartialResults
    }

    private fun hasDigitalAssetLink(context: Context): Boolean {
        val manifestFiles = context.project.manifestFiles
        for (file in manifestFiles) {
            if (file.exists()) {
                val text = file.readText()
                if (text.contains("android:name=\"asset_statements\"") ||
                    text.contains("android:name='asset_statements'")
                ) {
                    return true
                }
            }
        }
        return false
    }
}