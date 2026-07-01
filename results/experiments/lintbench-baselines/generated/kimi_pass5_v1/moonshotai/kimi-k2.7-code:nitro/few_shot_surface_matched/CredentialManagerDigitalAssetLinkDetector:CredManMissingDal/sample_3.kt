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
    private const val USES_PASSWORD_OPTION = "uses_password_option"
    private const val HAS_ASSET_STATEMENTS = "has_asset_statements"
    private const val ASSET_STATEMENTS_NAME = "asset_statements"

    @JvmField
    val ISSUE = Issue.create(
      id = "CredManMissingDal",
      briefDescription = "Missing Digital Asset Link for Credential Manager",
      explanation = """
        When using password sign-in through Credential Manager, an asset statements string
        resource file that includes the assetlinks.json files to load must be declared in the
        manifest using a `<meta-data>` element.
        """.trimIndent(),
      moreInfo = "https://developer.android.com/identity/sign-in/credential-manager#add-support-dal",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(CredentialManagerDigitalAssetLinkDetector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = true,
    )
  }

  override fun getApplicableConstructorTypes() = listOf("androidx.credentials.GetPasswordOption")

  override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
    context.getPartialResults(context.project).map()[USES_PASSWORD_OPTION] = true
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    val manifest = context.evaluator.getManifest(context.project) ?: return
    val application = manifest.getElementsByTagName("application").item(0) ?: return
    val children = application.childNodes
    var found = false
    for (i in 0 until children.length) {
      val child = children.item(i) ?: continue
      if (child.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue
      if (child.nodeName != "meta-data") continue
      val name = child.attributes?.getNamedItem("android:name")?.nodeValue
      if (name == ASSET_STATEMENTS_NAME) {
        found = true
        break
      }
    }
    partialResults.map()[HAS_ASSET_STATEMENTS] = found
  }

  override fun afterCheckRootProject(context: Context) {
    for (project in context.driver.projects) {
      val map = context.getPartialResults(project).map()
      val usesPasswordOption = map[USES_PASSWORD_OPTION] == true
      val hasAssetStatements = map[HAS_ASSET_STATEMENTS] == true
      if (usesPasswordOption && !hasAssetStatements) {
        val location = Location.create(project.manifestFiles.firstOrNull() ?: project.dir)
        val message =
          "Password sign-in with Credential Manager requires a Digital Asset Link. " +
            "Add a <meta-data android:name=\"$ASSET_STATEMENTS_NAME\" " +
            "android:resource=\"@string/...\" /> element to the <application> tag."
        context.report(Incident(ISSUE, location, message))
      }
    }
  }
}