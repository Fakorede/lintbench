package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.lint.detector.api.ClassScanner
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.util.EnumSet

/**
 * Checks that Fragment subclasses can be instantiated via reflection:
 * - Must be public
 * - If an inner class, must be static
 * - Must have a public no-argument constructor
 *
 * Note: only applies to android.app.Fragment (and support library v4 Fragment),
 * NOT to androidx.fragment.app.Fragment (which supports FragmentFactory).
 */
class FragmentDetector : Detector(), ClassScanner {

    companion object {
        private const val FRAGMENT_CLASS = "android/app/Fragment"
        private const val SUPPORT_FRAGMENT_CLASS = "android/support/v4/app/Fragment"
        // androidx fragments support FragmentFactory, so we skip them
        private const val ANDROIDX_FRAGMENT_CLASS = "androidx/fragment/app/Fragment"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                From the Fragment documentation:
                **Every** fragment must have an empty constructor, so it can be instantiated \
                when restoring its activity's state. It is strongly recommended that subclasses \
                do not have other constructors with parameters, since these constructors will \
                not be called when the fragment is re-instantiated; instead, arguments can be \
                supplied by the caller with `setArguments(Bundle)` and later retrieved by the \
                Fragment with `getArguments()`.

                Note that this is no longer true when you are using \
                `androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply \
                any arguments you want (as of version androidx version 1.1).
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                FragmentDetector::class.java,
                EnumSet.of(Scope.CLASS_FILE)
            ),
            moreInfo = "https://developer.android.com/reference/android/app/Fragment.html#Fragment()"
        )
    }

    // ClassScanner

    override fun applicableSuperClasses(): List<String> {
        return listOf(FRAGMENT_CLASS, SUPPORT_FRAGMENT_CLASS)
    }

    override fun checkClass(context: ClassContext, classNode: ClassNode) {
        // Skip androidx fragments — FragmentFactory is supported there
        if (isAndroidXFragment(context, classNode)) {
            return
        }

        // 1. The fragment class itself must be public
        if (classNode.access and Opcodes.ACC_PUBLIC == 0) {
            context.report(
                ISSUE,
                context.getLocation(classNode),
                "This fragment class should be public (${classNode.name.replace('/', '.')})"
            )
            return
        }

        // 2. If it's an inner class it must be static
        if (classNode.name.contains('$')) {
            if (classNode.access and Opcodes.ACC_STATIC == 0) {
                context.report(
                    ISSUE,
                    context.getLocation(classNode),
                    "This fragment inner class should be static (${classNode.name.replace('/', '.')})"
                )
                return
            }
        }

        // 3. Must have a public no-argument constructor
        if (!hasPublicNoArgConstructor(classNode)) {
            context.report(
                ISSUE,
                context.getLocation(classNode),
                "The default constructor used when re-creating a `Fragment` must be present and be a public no-argument constructor"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Walk up the superclass chain (using what the class file declares) and
     * check whether this class ultimately extends the androidx Fragment class.
     * Because [applicableSuperClasses] only returns the framework/support
     * Fragment names, any class that reaches us here won't directly extend
     * the androidx class, but its super might.  We do a best-effort walk.
     */
    private fun isAndroidXFragment(context: ClassContext, classNode: ClassNode): Boolean {
        var superName: String? = classNode.superName
        while (superName != null) {
            if (superName == ANDROIDX_FRAGMENT_CLASS) return true
            if (superName == FRAGMENT_CLASS || superName == SUPPORT_FRAGMENT_CLASS) return false
            if (superName == "java/lang/Object") return false
            // Try to load the super class from the driver
            val superClass = context.driver.findClass(context, superName, 0) ?: break
            superName = superClass.superName
        }
        return false
    }

    private fun hasPublicNoArgConstructor(classNode: ClassNode): Boolean {
        var hasExplicitConstructor = false

        @Suppress("UNCHECKED_CAST")
        val methods = classNode.methods as List<MethodNode>

        for (method in methods) {
            if (method.name == "<init>") {
                hasExplicitConstructor = true
                // Check for no-arg (descriptor == "()V") and public
                if (method.desc == "()V" && method.access and Opcodes.ACC_PUBLIC != 0) {
                    return true
                }
            }
        }

        // If there are no explicit constructors the compiler inserts a public no-arg one
        return !hasExplicitConstructor
    }
}