package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val META_DATA_NAME = "android.credentials.asset_statements"

    private val PASSWORD_CLASSES = listOf(
      "androidx.credentials.GetPasswordOption",
      "androidx.credentials.CreatePasswordRequest"
    )

    @JvmField
    val ISSUE = Issue.create(
      id = "CredManMissingDal",
      briefDescription = "Missing Digital Asset Link for Credential Manager",
      explanation = "When using password sign-in through Credential Manager, an asset statements string resource file that includes the assetlinks.json files to load must be declared in the manifest using a <meta-data> element.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(CredentialManagerDigitalAssetLinkDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  private val projectsUsingPasswordSignIn = mutableSetOf<String>()
  private val reportedProjects = mutableSetOf<String>()

  override fun getApplicableConstructorTypes(): List<String> = PASSWORD_CLASSES

  override fun visitConstructor(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    projectsUsingPasswordSignIn.add(context.project.name)
  }

  override fun checkPartialResults(context: Context) {
    val projectName = context.project.name
    if (projectsUsingPasswordSignIn.contains(projectName) && !reportedProjects.contains(projectName)) {
      checkAndReport(context)
    }
  }

  override fun afterCheckRootProject(context: Context) {
    val projectName = context.project.name
    if (projectsUsingPasswordSignIn.contains(projectName) && !reportedProjects.contains(projectName)) {
      checkAndReport(context)
    }
  }

  private fun checkAndReport(context: Context) {
    val manifest = context.project.getManifest() ?: return
    if (!manifest.exists()) return

    val content = manifest.readText()
    if (content.contains(META_DATA_NAME)) return

    reportedProjects.add(context.project.name)
    val location = Location.create(manifest)
    val message = "Missing Digital Asset Links meta-data. Add <meta-data android:name=\"$META_DATA_NAME\" android:resource=\"@string/asset_statements\" /> to your AndroidManifest.xml."
    context.report(Incident(ISSUE, location, message))
  }
}