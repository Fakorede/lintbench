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

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val CREATE_PASSWORD_REQUEST = "androidx.credentials.CreatePasswordRequest"
    private const val GET_PASSWORD_REQUEST = "androidx.credentials.GetPasswordRequest"
    private const val HAS_CREDENTIAL_MANAGER_USAGE = "hasCredentialManagerUsage"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "CredManMissingDal",
        briefDescription = "Missing Digital Asset Link declaration for Credential Manager",
        explanation =
          """
                When using password sign-in through Credential Manager, you must declare an asset
                statements string resource file in the manifest using a
                `<meta-data android:name="asset_statements" ...>` element. That resource should
                include the `assetlinks.json` files to load.

                Without this declaration Credential Manager may be unable to load the Digital
                Asset Links required for password sign-in.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation =
          Implementation(CredentialManagerDigitalAssetLinkDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableConstructorTypes(): List<String> =
    listOf(CREATE_PASSWORD_REQUEST, GET_PASSWORD_REQUEST)

  override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
    context.getPartialResults(ISSUE).setBoolean(HAS_CREDENTIAL_MANAGER_USAGE, true)
  }

  override fun checkPartialResults(context: Context, partialResult: PartialResult) {
    if (!partialResult.getBoolean(HAS_CREDENTIAL_MANAGER_USAGE)) {
      return
    }

    // Propagate usage to the root project so the project-wide manifest check can report it.
    val rootProject = context.mainProject
    context.driver.request
      .getPartialResults(rootProject, ISSUE)
      .setBoolean(HAS_CREDENTIAL_MANAGER_USAGE, true)
  }

  override fun afterCheckRootProject(context: Context) {
    val rootPartial = context.getPartialResults(ISSUE)
    if (!rootPartial.getBoolean(HAS_CREDENTIAL_MANAGER_USAGE)) {
      return
    }

    if (hasAssetStatementsMetaData(context)) {
      return
    }

    val project = context.mainProject
    val manifest = project.manifestFiles.firstOrNull() ?: project.dir
    val location = Location.create(manifest)
    val message =
      "Missing <meta-data android:name=\"asset_statements\" android:resource=\"@string/...\" /> " +
        "declaration required for Credential Manager password sign-in. See " +
        "https://developer.android.com/identity/sign-in/credential-manager#add-support-dal"
    context.report(Incident(ISSUE, location, message))
  }

  private fun hasAssetStatementsMetaData(context: Context): Boolean {
    for (manifest in context.mainProject.manifestFiles) {
      val contents = context.getContents(manifest) ?: continue
      if (contents.contains("asset_statements")) {
        return true
      }
    }
    return false
  }
}