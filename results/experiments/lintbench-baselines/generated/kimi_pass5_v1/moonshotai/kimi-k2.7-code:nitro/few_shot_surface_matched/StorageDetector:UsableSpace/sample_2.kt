package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class StorageDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val METHOD_GET_USABLE_SPACE = "getUsableSpace"
    private const val METHOD_GET_ALLOCATABLE_BYTES = "getAllocatableBytes"
    private const val METHOD_ALLOCATE_BYTES = "allocateBytes"
    private const val CLASS_FILE = "java.io.File"
    private const val CLASS_STORAGE_MANAGER = "android.os.storage.StorageManager"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "UsableSpace",
        briefDescription = "Consider using getAllocatableBytes(UUID) instead of getUsableSpace()",
        explanation =
          """
                When you need to allocate disk space for large files, consider using the new `allocateBytes(FileDescriptor, long)` API, which will automatically clear cached files belonging to other apps (as needed) to meet your request.

                When deciding if the device has enough disk space to hold your new data, call `getAllocatableBytes(UUID)` instead of using `getUsableSpace()`, since the former will consider any cached data that the system is willing to clear on your behalf.

                Note that these methods require API level 26. If your app is running on older devices, you will probably need to use both APIs, conditionally switching on `Build.VERSION.SDK_INT`. Lint only looks in the same compilation unit to see if you are already using both APIs, so if it warns even though you are already using the new API, consider moving the calls to the same file or suppressing the warning.
            """
            .trimIndent(),
        category = Category.PERFORMANCE,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(StorageDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  private val pendingReports = mutableListOf<Pair<JavaContext, UCallExpression>>()
  private var hasNewApi = false

  override fun beforeCheckFile(context: Context) {
    pendingReports.clear()
    hasNewApi = false
  }

  override fun getApplicableMethodNames() = listOf(METHOD_GET_USABLE_SPACE)

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, CLASS_FILE)) {
      return
    }
    pendingReports.add(context to node)
  }

  override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
    val method = node.resolve() ?: return
    val name = method.name
    if (
      (name == METHOD_GET_ALLOCATABLE_BYTES || name == METHOD_ALLOCATE_BYTES) &&
      context.evaluator.isMemberInClass(method, CLASS_STORAGE_MANAGER)
    ) {
      hasNewApi = true
    }
  }

  override fun afterCheckFile(context: Context) {
    if (!hasNewApi) {
      for ((javaContext, node) in pendingReports) {
        val message =
          "Consider using StorageManager.getAllocatableBytes(UUID) instead of File.getUsableSpace(); " +
            "the former will consider cached data that the system is willing to clear on your behalf."
        javaContext.report(Incident(ISSUE, node, javaContext.getLocation(node), message))
      }
    }
    pendingReports.clear()
    hasNewApi = false
  }
}