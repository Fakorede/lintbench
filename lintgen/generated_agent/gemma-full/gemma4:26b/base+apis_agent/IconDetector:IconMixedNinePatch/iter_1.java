package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.registry.IssueRegistry;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = new Issue.Builder()
            .setId("ClashingPngAndNinePatch")
            .setShortDescriptionDescription("Detects clashing PNG and 9-patch files.")
            .setHelpText("If you have both 'file.png' and 'file.9.png' in your drawable resources, they both resolve to the same resource ID (@drawable/file). This causes ambiguity and unexpected behavior.")
            .setCategory(Issue.Category.CORRECTNESS)
            .setSeverity(Issue.Severity.ERROR)
            .build();

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
            int separatorIndex = name.indexOf('?');
            if (separatorIndex != -1) {
                name = name.substring(0, separatorIndex);
            }

            List<File> files = context.getFiles();
            if (files.isEmpty()) {
                return;
            }

            File resDir = null;
            for (File f : files) {
                File parent = f.getParentFile();
                if (parent != null && parent.getParentFile() != null) {
                    // Assuming the structure is res/layout/ -> res/
                    resDir = parent.getParentFile();
                    break;
                }
            }

            if (resDir == null) {
                return;
            }

            File[] subDirs = resDir.listFiles();
            if (subDirs == null) {
                return;
            }

            for (File dir : subDirs) {
                if (dir.isDirectory() && dir.getName().startsWith("drawable")) {
                    File pngFile = new File(dir, name + ".png");
                    File ninePatchFile = new
                            new File(dir, name + ".9.png");

                    if (pngFile.exists() && ninePatchFile.exists()) {
                        context.report(ISSUE, attribute, context.getLocation(attribute),
                                String.format("Clashing PNG and 9-PNG files found for resource: %s. Both '%s.png' and '%s.9.png' exist in the drawable resources.", name, name, name));
                        return;
                    }
                }
            }
        }
    }

    public static final IssueRegistry REGISTRY = new IssueRegistry() {
        @Override
        public final List<Issue> getIssues() {
            return Collections.singletonList(ISSUE);
        }

        @Override
        public final String getProblemDomain() {
            return "Resources";
        }
    };
}