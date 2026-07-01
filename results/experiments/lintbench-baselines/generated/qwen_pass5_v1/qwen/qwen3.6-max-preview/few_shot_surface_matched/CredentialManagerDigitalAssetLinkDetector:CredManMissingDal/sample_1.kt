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
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "CredManMissingDal",
        briefDescription = "Missing Digital Asset Link for Credential Manager",
        explanation =
          "When using password sign-in through Credential Manager, an asset statements string " +
            "resource file that includes the `assetlinks.json` files to load must be declared " +
            "in the manifest using a `<meta-data>` element.",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation =
          Implementation(
            CredentialManagerDigitalAssetLinkDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
          ),
        androidSpecific = true,
      )
  }

  private val usesCredentialManager = AtomicBoolean(false)

  override fun getApplicableConstructorTypes(): List<String> =
    listOf("androidx.credentials.CredentialManager", "android.credentials.CredentialManager")

  override fun visitConstructor(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    usesCredentialManager.set(true)
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    if (partialResults.getData() == true) {
      usesCredentialManager.set(true)
    }
  }

  override fun afterCheckRootProject(context: Context) {
    if (!usesCredentialManager.get()) return

    val manifests = context.mainProject.manifestFiles
    var hasAssetStatements = false
    var mainManifest: File? = null

    for (manifest in manifests) {
      if (manifest.name == "AndroidManifest.xml") {
        if (mainManifest == null) mainManifest = manifest
        val content = manifest.readText()
        if (
          content.contains("android:name=\"asset_statements\"") ||
            content.contains("android:name='asset_statements'")
        ) {
          hasAssetStatements = true
          break
        }
      }
    }

    if (!hasAssetStatements) {
      val location =
        if (mainManifest != null) Location.create(mainManifest)
        else Location.create(context.mainProject.dir)
      context.report(
        Incident(
          ISSUE,
          location,
          "Missing Digital Asset Links meta-data. Add " +
            "`<meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" />` " +
            "to your AndroidManifest.xml when using Credential Manager for password sign-in.",
        )
      )
    }
  }
}