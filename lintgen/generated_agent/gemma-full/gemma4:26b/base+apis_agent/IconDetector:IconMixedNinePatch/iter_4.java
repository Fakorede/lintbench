package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import java.io.File;
import java.util.Arrays;
import java.util.List;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = new Issue.Builder()
            .setId("ClashingPngAndNinePatch")
            .setShortDescription("Detects clashing PNG and 9-patch files.")
            .setHelpText("If you have both 'file.png' and 'file.9.png' in your drawable resources, they both resolve to the same resource ID (@drawable/file). This causes ambiguity and unexpected behavior.")
            .setCategory(Issue.Category.CORRECTNESS)
            .setSeverity(Severity.ERROR)
            .build();

    @Override
    public List<String> getApplicableAttributes() {
        return Arrays.asList(
                "android:src",
                "android:background",
                "app:srcCompat",
                "android:drawableTop",
                "android:drawableEnd",
                "android:drawableBottom",
                "android:drawableLeft",
                "android:drawableStart"
        );
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@drawable/")) {
            return;
        }

        String name = value.substring(9);
        // Handle dynamic references like @drawable/file?attr/state
        int questionMarkIndex = name.indexOf('?');
        if (questionMarkIndex != -1) {
            name = name.substring(0, questionMarkIndex);
        }

        if (checkClash(context, name)) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    String.format("Clashing PNG and 9-PNG files found for resource: %s. Both '%s.png' and '%s.9.png' exist in drawable resources.", name, name, name));
        }
    }

    private boolean checkClash(XmlContext context, String name) {
        try {
            File resDir = new File(context.getFiles().getRootDir(), "res");
            if (!res0Exists(resDir)) return false;

            File[] dirs = resDir.listFiles();
            if (dirs == null) return false;

            boolean pngFound = false;
            boolean ninePatchFound = false;

            for (File dir : dirs) {
                if (dir.isDirectory() && dir.getName().startsWith("drawable")) {
                    if (new File(dir, name + ".png").exists()) {
                        pngFound = true;
                    }
                    if (new File(dir, name + ".9.png").exists()) {
                        ninePatchFound = true;
                    }
                    if (pngFound && ninePatchFound) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors during scanning
        }
        return false;
    }

    private boolean res0Exists(File dir) {
        return dir != null && dir.exists() && dir.isDirectory();
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }
}