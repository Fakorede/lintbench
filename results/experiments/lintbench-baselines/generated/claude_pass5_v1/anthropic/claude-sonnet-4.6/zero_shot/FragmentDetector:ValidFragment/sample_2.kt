package com.android.tools.lint.checks

import com.android.SdkConstants.CLASS_FRAGMENT
import com.android.SdkConstants.CLASS_V4_FRAGMENT
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
 * Checks that Fragment subclasses can be instantiated via reflection,
 * which requires a public no-argument constructor.
 */
class FragmentDetector : Detector(), ClassScanner {

    companion object {
        /** The main issue discovered by this detector */
        @JvmField
        val ISSUE = Issue.create(
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
            moreInfo = "https://developer.android.com/reference/android/app/Fragment.html#Fragment()",
            implementation = Implementation(
                FragmentDetector::class.java,
                EnumSet.of(Scope.CLASS_FILE)
            )
        )

        private const val ANDROIDX_FRAGMENT_CLASS = "androidx/fragment/app/Fragment"
    }

    // ---- Implements ClassScanner ----

    override fun checkClass(context: ClassContext, classNode: ClassNode) {
        if (!isFragmentSubclass(context, classNode)) {
            return
        }

        // If this is an androidx Fragment subclass, skip the check
        // (FragmentFactory allows non-default constructors since androidx fragment 1.1)
        if (isAndroidxFragment(context, classNode)) {
            return
        }

        // Abstract classes don't need to be instantiated directly
        if (classNode.access and Opcodes.ACC_ABSTRACT != 0) {
            return
        }

        // Inner classes must be static
        if (classNode.name.contains('$')) {
            if (classNode.access and Opcodes.ACC_STATIC == 0) {
                val location = context.getLocation(classNode)
                context.report(
                    ISSUE,
                    location,
                    "This fragment inner class should be static (${classNode.name.replace('/', '.').replace('$', '.')})"
                )
                return
            }
        }

        // Check for a public no-argument constructor
        val methods = classNode.methods
        if (methods == null || methods.isEmpty()) {
            // No methods at all means no explicit constructors, which is fine
            // (the compiler will generate a default no-arg constructor)
            return
        }

        var hasExplicitConstructor = false
        var hasPublicNoArgConstructor = false

        for (methodObj in methods) {
            val method = methodObj as MethodNode
            if (method.name == "<init>") {
                hasExplicitConstructor = true
                if (method.desc == "()V") {
                    // This is a no-argument constructor
                    if (method.access and Opcodes.ACC_PUBLIC != 0) {
                        hasPublicNoArgConstructor = true
                    }
                }
            }
        }

        if (!hasExplicitConstructor) {
            // No explicit constructors: the compiler generates a public no-arg constructor
            return
        }

        if (!hasPublicNoArgConstructor) {
            val location = context.getLocation(classNode)
            context.report(
                ISSUE,
                location,
                "This fragment should provide a default constructor (a public constructor with no arguments) (`${classNode.name.replace('/', '.').replace('$', '.')}`)"
            )
        }
    }

    /**
     * Returns true if the given class is a subclass of Fragment
     * (either android.app.Fragment or android.support.v4.app.Fragment).
     */
    private fun isFragmentSubclass(context: ClassContext, classNode: ClassNode): Boolean {
        val fragmentClass = CLASS_FRAGMENT.replace('.', '/')
        val v4FragmentClass = CLASS_V4_FRAGMENT.replace('.', '/')
        val androidxFragmentClass = ANDROIDX_FRAGMENT_CLASS

        return context.driver.isSubclassOf(classNode, fragmentClass)
            || context.driver.isSubclassOf(classNode, v4FragmentClass)
            || context.driver.isSubclassOf(classNode, androidxFragmentClass)
    }

    /**
     * Returns true if the given class is a subclass of the AndroidX Fragment
     * (androidx.fragment.app.Fragment), which supports FragmentFactory.
     */
    private fun isAndroidxFragment(context: ClassContext, classNode: ClassNode): Boolean {
        return context.driver.isSubclassOf(classNode, ANDROIDX_FRAGMENT_CLASS)
    }
}