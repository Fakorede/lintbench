package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.IssueRegistry;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
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
            .setShortDescription("Detects clashing PNG and 9-patch files.")
            .setHelpText("If you have both 'file.png' and 'file.9.png' in your drawable resources, they both resolve to the same resource ID (@drawable/file). This causes ambiguity and unexpected behavior.")
            .setCategory(Issue.Category.CORRECTNESS)
            .setSeverity(Issue.Severity.ERROR)
            .build();

    private final Map<String, Boolean> clashCache = new HashMap<>();

    @Override
    public List<String> getApplicableAttributes() {
        return Arrays.asList("android:src", "android:background", "app:srcCompat", "android:drawableTop", "android:drawableEnd", "android:drawableBottom", "android:drawableLeft");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@drawable/")) {
            return;
        }

        String name = value.substring(9);
        int questionMarkIndex = name.indexOf('?');
        if (questionMarkIndex != -1) {
            name = name.substring(0, questionMarkIndex);
        }

        if (clashCache.containsKey(name)) {
            if (clashCache.get(name)) {
                reportClash(context, attribute, name);
            }
            return;
        }

        boolean clashFound = checkClash(context, name);
        clashCache.put(name, clashFound);
        if (clashFound) {
            reportClash(context, attribute, name);
        }
    }

    private void reportClash(XmlContext context, Attr attribute, String name) {
        context.report(ISSUE, attribute, context.getLocation(attribute),
                String.format("Clashing PNG and 9-PNG files found for resource: %s. Both '%s.png' and '%s.9.png' exist.", name, name, name));
    }

    private boolean checkClash(XmlContext context, String name) {
        try {
            File root = context.getProject().getRootDir();
            return findClashInDir(root, name, new boolean[]{false, false});
        } catch (Exception e) {
            return false;
        }
    }

    private boolean findClashInDir(File dir, String name, boolean[] found) {
        if (dir == null || !dir.isDirectory()) return false;

        File pngFile = new File(dir, name + ".png");
        File ninePatchFile = new File(dir, name + ".9.png");

        if (pngFile.exists()) found[0] = true;
        if (ninePatchFile.exists()) found[1] = true;

        if (found[0] && found[1]) return true;

        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() && (child.getName().startsWith("drawable") || child.getName().equals("res"))) {
                    if (findClashInDir(child, name, found)) return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
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