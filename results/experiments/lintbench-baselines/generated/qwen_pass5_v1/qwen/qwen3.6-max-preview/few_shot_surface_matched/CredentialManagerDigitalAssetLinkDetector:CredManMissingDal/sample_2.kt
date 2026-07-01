package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialAnalysisContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val CRED_MAN_MISSING_DAL = Issue.create(
      id = "CredManMissingDal",
      briefDescription = "Missing Digital Asset Link for Credential Manager",
      explanation = """
        When using password sign-in through Credential Manager, an asset statements string resource file that includes the `assetlinks.json` files to load must be declared in the manifest using a `<meta-data>` element.
        See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(
        CredentialManagerDigitalAssetLinkDetector::class.java,
        Scope.JAVA_FILE_SCOPE
      ),
      androidSpecific = true
    )
  }

  private val usages = mutableListOf<Pair<JavaContext, UCallExpression>>()

  override fun getApplicableConstructorTypes(): List<String> =
    listOf("androidx.credentials.GetPasswordOption")

  override fun visitConstructor(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    usages.add(context to node)
  }

  override fun checkPartialResults(context: Context, partialContext: PartialAnalysisContext) {
    // Defer reporting to afterCheckRootProject to ensure full manifest analysis.
  }

  override fun afterCheckRootProject(context: Context) {
    if (usages.isEmpty()) return

    val hasDal = context.client.getManifestFiles(context.project).any { file ->
      file.exists() && file.readText().contains("asset_statements")
    }

    if (!hasDal) {
      for ((javaContext, node) in usages) {
        javaContext.report(
          CRED_MAN_MISSING_DAL,
          node,
          javaContext.getLocation(node),
          "Missing Digital Asset Links meta-data in AndroidManifest.xml. " +
            "Add <meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" /> " +
            "to your manifest when using GetPasswordOption."
        )
      }
    }
    usages.clear()
  }
}