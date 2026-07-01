package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.ClassScanner;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * Checks that classes implementing Parcelable also provide a CREATOR field.
 */
public class ParcelDetector extends Detector implements ClassScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable `CREATOR` field",
            "According to the `Parcelable` interface documentation, " +
            "\"Classes implementing the Parcelable interface must also have a static " +
            "field called `CREATOR`, which is an object implementing the " +
            "`Parcelable.Creator` interface.\"",
            Category.USABILITY,
            3,
            Severity.ERROR,
            new Implementation(
                    ParcelDetector.class,
                    Scope.CLASS_FILE_SCOPE))
            .addMoreInfo("https://developer.android.com/reference/android/os/Parcelable.html");

    private static final String PARCELABLE_OWNER = "android/os/Parcelable";
    private static final String CREATOR_NAME = "CREATOR";

    /** Constructs a new {@link ParcelDetector} */
    public ParcelDetector() {
    }

    // ---- Implements ClassScanner ----

    @Override
    public void checkClass(ClassContext context, ClassNode classNode) {
        // Check if this class implements Parcelable
        if (classNode.interfaces == null || !classNode.interfaces.contains(PARCELABLE_OWNER)) {
            return;
        }

        // Abstract classes don't need to provide CREATOR
        if ((classNode.access & Opcodes.ACC_ABSTRACT) != 0) {
            return;
        }

        // Interface definitions don't need CREATOR
        if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
            return;
        }

        // Look for a static CREATOR field
        if (classNode.fields != null) {
            for (Object fieldObject : classNode.fields) {
                FieldNode field = (FieldNode) fieldObject;
                if (CREATOR_NAME.equals(field.name)
                        && (field.access & Opcodes.ACC_STATIC) != 0) {
                    // Found the CREATOR field
                    return;
                }
            }
        }

        // No CREATOR field found - report the issue
        context.report(
                ISSUE,
                context.getLocation(classNode),
                "This class implements `Parcelable` but does not provide a `CREATOR` field");
    }
}