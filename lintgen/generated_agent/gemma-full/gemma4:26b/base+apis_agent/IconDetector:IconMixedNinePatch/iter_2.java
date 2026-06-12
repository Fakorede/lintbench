package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.IssueRegistry;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private final Map<String, Boolean> clashCache = new HashMap<>();

    @Override
    public List<String> getApplicableAttributes() {
        return ATTRIBUTES_TO_CHECK;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@drawable/")) {
            return;
        }

        String name = value.substring(9);
        int separatorIndex = name.indexOf('?');
        if (separatorIndex != -1) {
            name = name.substring(0, separatorIndex);
        }

        if (clashCache.containsKey(name)) {
            if (clashCache.get(name)) {
                reportClash(context, attribute, name);
            }
            return;
        }

        File resDir = null;
        for (File f : context.getFiles()) {
            File current = f;
            while (current != null) {
                if (current.getName().equals("res")) {
                    resDir = current;
                    break;
                }
                current = current.getParentFile();
            }
            if (resDir != null) break;
        }

        if (resDir != null) {
            boolean clashFound = checkClashInResDir(resDir, name);
            clashCache.put(name, clashFound);
            if (clashFound) {
                reportClash(context, attribute, name);
            }
        }
    }

    private boolean checkClashInResDir(File resDir, String name) {
        boolean pngExists = false;
        boolean ninePatchExists = false;
        File[] subDirs = resDir.listFiles();
        if (subDirs != null) {
            for (File dir : subDirs) {
                if (dir.isDirectory() && dir.getName().startsWith("drawable")) {
                    if (new File(dir, name + ".png").exists()) {
                        pngExists = true;
                    }
                    if (new File(dir, name + ".9.png").exists()) {
                        ninePatchExists = true;
                    }
                }
                if (pngExists && ninePatchExists) {
                    return true;
                }
            }
        }
        return false;
    }

    private void reportClash(XmlContext context, Attr attribute, String name) {
        context.report(ISSUE, attribute, context.getLocation(attribute),
                String.format("Clashing PNG and 9-PNG files found for resource: %s. Both '%s.png' and '%s.9.png' exist in the drawable resources.", name, name, name));
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