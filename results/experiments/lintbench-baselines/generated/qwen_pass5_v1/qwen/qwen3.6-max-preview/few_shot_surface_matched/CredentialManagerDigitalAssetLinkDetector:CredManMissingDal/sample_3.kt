package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
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
    private const val META_DATA_NAME = "androidx.credentials.provider.asset-statements"
    private const val LEGACY_META_DATA_NAME = "android.credentials.asset-statements"

    @JvmField
    val ISSUE: Issue =
      Issue.create(
        id = "CredManMissingDal",
        briefDescription = "Missing Digital Asset Link for Credential Manager",
        explanation =
          """
            When using password sign-in through Credential Manager, an asset statements string \
            resource file that includes the `assetlinks.json` files to load must be declared in \
            the manifest using a `<meta-data>` element.
          """.trimIndent(),
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation =
          Implementation(
            CredentialManagerDigitalAssetLinkDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
          ),
      )
  }

  private val usages = mutableListOf<Pair<JavaContext, UCallExpression>>()

  override fun getApplicableConstructorTypes(): List<String> =
    listOf(
      "androidx.credentials.GetPasswordOption",
      "androidx.credentials.GetCredentialRequest",
    )

  override fun visitConstructor(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    usages.add(context to node)
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult<*>) {
    // Partial results are aggregated incrementally; final manifest validation occurs in
    // afterCheckRootProject.
  }

  override fun afterCheckRootProject(context: Context) {
    if (usages.isEmpty()) return

    val manifestFile = context.project.manifest
    if (manifestFile == null || !hasAssetStatementsMetaData(manifestFile)) {
      val message =
        "Missing Digital Asset Links meta-data in AndroidManifest.xml. " +
          "Add `<meta-data android:name=\"$META_DATA_NAME\" android:resource=\"@xml/asset_statements\" />` " +
          "to support password sign-in via Credential Manager."
      for ((ctx, node) in usages) {
        val location = ctx.getLocation(node)
        ctx.report(Incident(ISSUE, node, location, message))
      }
    }
    usages.clear()
  }

  private fun hasAssetStatementsMetaData(manifestFile: java.io.File): Boolean {
    return try {
      val content = manifestFile.readText()
      content.contains(META_DATA_NAME) || content.contains(LEGACY_META_DATA_NAME)
    } catch (e: Exception) {
      false
    }
  }
}