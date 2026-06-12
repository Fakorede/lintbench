package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.IssueRegistry;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;
import org.jetbrains.uast.UElement;
import org.w3cap.dom.Attr;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IconDetector extends com.android.tools.lint.detector.api.XmlScanner {

    private static final String ISSU_ID = "ClashingPngAndNinePatch";
    private static final String MESSAGE_TEMPLATE = "Clashing PNG and 9-PNG files found for resource: %s. Both ' %s.png' and '%s.9.png' exist in the drawable resources, which causes a collision.";

    private final Set<String> checkedNames = new HashSet<>();
    private final Set<String> clashingNames = new HashSet<>();

    private static final List<String> ATTRIBUTES_TO_CHECK = Arrays.asList(
            "android:src",
            "android:background",
            "app:srcCompat",
            "android:drawableTop",
            "android:drawableEnd",
            "android:drawableBottom",
            "android:drawableLeft",
            "android:backgroundTint"
    );

    @Override
    public List<String> getApplicableAttributes() {
        return ATTRIBUTES_TO_CHECK;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value != null && value.startsWith("@drawable/")) {
            String name = value.substring(9);
            // Remove any potential suffixes like ?color=... or other parameters if present
            int separatorIndex = name.indexOf('?');
            if (separatorIndex != -1) {
                name = name.substring(0, separatorIndex);
            }

            if (!checkedNames.contains(name)) {
                checkClash(context, name);
                checkedNames.add(name);
            }

            if (clashingNames.contains(name)) {
                context.report(ISSUE, attribute, context.getLocation(),
                        String.format(MESSAGE_TEMPLATE, name, name, name));
            }
        }
    }

    private void checkClash(XmlContext context, String name) {
        File drawableDir = context.getFolder(ResourceFolderType.DRAWABLE);
        if (drawableDir == null) {
            return;
        }

        File resDir = drawableDir.getParentFile();
        if (resDir == null || !resDir.exists()) {
            return;
        }

        boolean pngExists = false;
        boolean ninePatchExists = false;

        File[] folders = resDir.listFiles();
        if (folders == null) {
            return;
        }

        for (File dir : folders) {
            if (dir.isDirectory() && (dir.getName().startsWith("drawable") || dir.getName().equals("drawable"))) {
                File pngFile = new File(dir, name + ".png");
                File ninePatchFile = new File(dir, name + ".9.png");

                if (pngFile.exists()) {
                    pngExists = true;
                }
                if (ninePatchFile.exists()) {
                    ninePatchExists = true;
                }

                // If we found both types in the project's drawable folders, it's a clash
                if (pngExists && ninePatchExists) {
                    clashingNames.add(name);
                    return;
                }
            }
        }
    }

    public static final Issue ISSUE = new Issue.Builder()
            .setId(ISSU_ID)
            .setShortDescriptionDescription("Detects clashing PNG and 9-patch files.")
            .setHelpText("If you have both 'file.png' and 'file.9.png' in your drawable resources, they both resolve to the same resource ID (@drawable/file). This causes ambiguity and unexpected behavior.")
            .setCategory(com.android.tools.lint.detector.api.Issue.Category.CORRECTNESS)
            .setSeverity(com.android.tools.lint.detector.api.Severity.ERROR)
            .addBugPattern()
            .build();

    public static final IssueRegistry REGISTRY = new IssueRegistry() {
        @Override
        public final List<Issue> getIssues() {
            return Arrays.asList(ISSUE);
        }

        @Override
        public final String getProblemDomain() {
            return "Resources";
        }
    };
}