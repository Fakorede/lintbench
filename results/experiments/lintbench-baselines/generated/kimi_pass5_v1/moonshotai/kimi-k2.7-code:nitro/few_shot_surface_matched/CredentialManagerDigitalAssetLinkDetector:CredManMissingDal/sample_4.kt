package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResultMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val ASSET_STATEMENTS = "asset_statements"
    private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    private const val GET_PASSWORD_OPTION = "androidx.credentials.GetPasswordOption"
    private const val KEY_USAGE = "usage"

    @JvmField
    val ISSUE = Issue.create(
      id = "CredManMissingDal",
      briefDescription = "Missing Digital Asset Link for Credential Manager",
      explanation = """
                When using password sign-in through Credential Manager, you must declare a
                `<meta-data android:name="asset_statements" android:resource="@string/asset_statements" />`
                element inside the `<application>` block of your `AndroidManifest.xml`. The referenced
                string resource should list the `assetlinks.json` files to load.
            """,
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(CredentialManagerDigitalAssetLinkDetector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = true,
    )
  }

  override fun getApplicableConstructorTypes() = listOf(GET_PASSWORD_OPTION)

  override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
    val containingClass = constructor.containingClass ?: return
    if (containingClass.qualifiedName != GET_PASSWORD_OPTION) return
    context.getPartialResults(ISSUE).putBoolean(KEY_USAGE, true)
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResultMap) {
    val used = partialResults.values.any { it.getBoolean(KEY_USAGE, false) }
    if (used) {
      context.getPartialResults(ISSUE).putBoolean(KEY_USAGE, true)
    }
  }

  override fun afterCheckRootProject(context: Context) {
    if (!context.getPartialResults(ISSUE).getBoolean(KEY_USAGE, false)) return
    if (hasAssetStatementsMetaData(context)) return

    val manifest = context.mainProject.manifestFiles.firstOrNull() ?: return
    val location = context.getLocation(manifest)
    val message =
      "Missing Digital Asset Link for Credential Manager password sign-in. " +
        "Add a `<meta-data android:name=\"asset_statements\" android:resource=\"@string/asset_statements\" />` element to `<application>`."
    context.report(Incident(ISSUE, location, message))
  }

  private fun hasAssetStatementsMetaData(context: Context): Boolean {
    val document = context.mainProject.mergedManifest ?: return false
    val applications = document.getElementsByTagName("application")
    for (i in 0 until applications.length) {
      val application = applications.item(i) as? org.w3c.dom.Element ?: continue
      val metaDataElements = application.getElementsByTagName("meta-data")
      for (j in 0 until metaDataElements.length) {
        val metaData = metaDataElements.item(j) as? org.w3c.dom.Element ?: continue
        val name = metaData.getAttributeNS(ANDROID_NAMESPACE, "name")
        if (name == ASSET_STATEMENTS) {
          return true
        }
      }
    }
    return false
  }
}