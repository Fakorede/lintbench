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
    @JvmField
    val USABLE_SPACE: Issue =
      Issue.create(
        id = "UsableSpace",
        briefDescription = "Using getUsableSpace() instead of getAllocatableBytes(UUID)",
        explanation =
          """
                When you need to allocate disk space for large files, consider using the new `allocateBytes(FileDescriptor, long)` API, which will automatically clear cached files belonging to other apps (as needed) to meet your request.

                When deciding if the device has enough disk space to hold your new data, call `getAllocatableBytes(UUID)` instead of using `getUsableSpace()`, since the former will consider any cached data that the system is willing to clear on your behalf.

                Note that these methods require API level 26. If your app is running on older devices, you will probably need to use both APIs, conditionally switching on `Build.VERSION.SDK_INT`. Lint only looks in the same compilation unit to see if you are already using both APIs, so if it warns even though you are already using the new API, consider moving the calls to the same file or suppressing the warning.
            """,
        category = Category.PERFORMANCE,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(StorageDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  private val pendingReports = mutableMapOf<String, MutableList<Pair<JavaContext, UCallExpression>>>()
  private val usesAllocatableBytes = mutableSetOf<String>()

  override fun getApplicableMethodNames() = listOf("getUsableSpace", "getAllocatableBytes")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    when (method.name) {
      "getUsableSpace" -> {
        if (context.evaluator.isMemberInClass(method, "java.io.File")) {
          val path = context.file.path
          if (path !in usesAllocatableBytes) {
            pendingReports.getOrPut(path) { mutableListOf() }.add(context to node)
          }
        }
      }
      "getAllocatableBytes" -> {
        if (context.evaluator.isMemberInClass(method, "android.os.storage.StorageManager")) {
          usesAllocatableBytes.add(context.file.path)
        }
      }
    }
  }

  override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
    // Resolved calls are handled by visitMethodCall.
  }

  override fun afterCheckFile(context: Context) {
    val path = context.file.path
    val pending = pendingReports.remove(path)
    if (path !in usesAllocatableBytes && pending != null) {
      for ((ctx, node) in pending) {
        val message =
          "Using getUsableSpace() can be inaccurate; use StorageManager#getAllocatableBytes(UUID) instead."
        val location = ctx.getLocation(node)
        ctx.report(Incident(USABLE_SPACE, node, location, message))
      }
    }
    usesAllocatableBytes.remove(path)
  }
}