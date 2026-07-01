package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
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
      explanation = "When using password sign-in through Credential Manager, an asset statements string resource file that includes the `assetlinks.json` files to load must be declared in the manifest using a `<meta-data>` element.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(CredentialManagerDigitalAssetLinkDetector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = true,
    )
  }

  private val projectsNeedingDal = mutableSetOf<Project>()

  override fun getApplicableConstructorTypes(): List<String>? =
    listOf("androidx.credentials.GetPasswordOption")

  override fun visitConstructor(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    projectsNeedingDal.add(context.project)
  }

  override fun checkPartialResults(context: Context) {
    // No partial aggregation required for this detector
  }

  override fun afterCheckRootProject(context: Context) {
    for (project in projectsNeedingDal) {
      val manifest = context.client.findManifest(project) ?: continue
      val content = manifest.readText()
      if (!content.contains("android:name=\"asset_statements\"") &&
          !content.contains("android:name='asset_statements'")) {
        val location = Location.create(manifest)
        val message = "Missing Digital Asset Link meta-data. When using password sign-in through Credential Manager, declare <meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" /> in the manifest."
        context.report(Incident(ISSUE, location, message))
      }
    }
    projectsNeedingDal.clear()
  }
}