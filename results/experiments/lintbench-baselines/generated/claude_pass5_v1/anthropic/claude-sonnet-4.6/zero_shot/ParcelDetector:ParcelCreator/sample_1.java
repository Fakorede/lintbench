package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

import java.util.EnumSet;
import java.util.List;

/**
 * Checks that classes implementing Parcelable also have a static CREATOR field.
 */
public class ParcelDetector extends Detector implements ClassScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable `CREATOR` field",
            "According to the `Parcelable` interface documentation, \"Classes implementing " +
            "the Parcelable interface must also have a static field called `CREATOR`, which " +
            "is an object implementing the `Parcelable.Creator` interface.\"",
            Category.USABILITY,
            3,
            Severity.ERROR,
            new Implementation(
                    ParcelDetector.class,
                    Scope.CLASS_FILE_SCOPE
            ))
            .addMoreInfo("https://developer.android.com/reference/android/os/Parcelable.html");

    private static final String PARCELABLE_OWNER = "android/os/Parcelable";
    private static final String CREATOR_FIELD = "CREATOR";

    /** Constructs a new {@link ParcelDetector} */
    public ParcelDetector() {
    }

    // ---- Implements ClassScanner ----

    @Nullable
    @Override
    public List<String> getApplicableCallNames() {
        return null;
    }

    @Nullable
    @Override
    public List<String> getApplicableSuperClasses() {
        return null;
    }

    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        // Only check concrete classes (not interfaces or abstract classes)
        if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
            return;
        }
        if ((classNode.access & Opcodes.ACC_ABSTRACT) != 0) {
            return;
        }

        // Check if this class implements Parcelable
        List interfaces = classNode.interfaces;
        if (interfaces == null || !interfaces.contains(PARCELABLE_OWNER)) {
            return;
        }

        // Check for the CREATOR field
        List fields = classNode.fields;
        if (fields != null) {
            for (Object fieldObj : fields) {
                FieldNode field = (FieldNode) fieldObj;
                if (CREATOR_FIELD.equals(field.name)
                        && (field.access & Opcodes.ACC_STATIC) != 0) {
                    // Found the required CREATOR field
                    return;
                }
            }
        }

        // Report the missing CREATOR field
        context.report(
                ISSUE,
                context.getLocation(classNode),
                "This class implements `Parcelable` but does not provide a `CREATOR` field");
    }
}