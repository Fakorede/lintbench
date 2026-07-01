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
import java.io.File
import java.util.Collections

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

    private val usages = Collections.synchronizedList(mutableListOf<Pair<JavaContext, UCallExpression>>())

    companion object {
        private val IMPLEMENTATION = Implementation(
            CredentialManagerDigitalAssetLinkDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = "When using password sign-in through Credential Manager, an asset statements string resource file that includes the assetlinks.json files to load must be declared in the manifest using a <meta-data> element with android:name=\"asset_statements\".",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? = listOf("androidx.credentials.GetPasswordRequest")

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        usages.add(context to node)
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // Incremental partial results are not utilized for this cross-file manifest verification.
    }

    override fun afterCheckRootProject(context: Context) {
        if (usages.isEmpty()) return

        val manifestFile = context.project.manifest
        if (manifestFile == null || !manifestFile.exists()) return

        if (!hasAssetStatementsMetaData(manifestFile)) {
            for ((ctx, node) in usages) {
                ctx.report(
                    ISSUE,
                    node,
                    ctx.getLocation(node),
                    "Missing Digital Asset Link configuration. When using password sign-in through Credential Manager, you must declare a `<meta-data>` element with `android:name=\"asset_statements\"` in your AndroidManifest.xml."
                )
            }
        }
    }

    private fun hasAssetStatementsMetaData(manifestFile: File): Boolean {
        return try {
            val content = manifestFile.readText()
            """android:name\s*=\s*["']asset_statements["']""".toRegex().containsMatchIn(content)
        } catch (e: Exception) {
            false
        }
    }
}