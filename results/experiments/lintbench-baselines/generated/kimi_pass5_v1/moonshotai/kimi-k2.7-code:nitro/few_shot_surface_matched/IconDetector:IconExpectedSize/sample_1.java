package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintDriver;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconExpectedSize",
                    "Icon has incorrect size",
                    "There are predefined sizes (for each density) for launcher icons. You should"
                            + " follow these conventions to make sure your icons fit in with the"
                            + " overall look of the platform.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(
                                    Scope.MANIFEST_SCOPE,
                                    Scope.RESOURCE_FILE_SCOPE,
                                    Scope.JAVA_FILE_SCOPE)));

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private final Map<Project, Map<String, Location>> mProjectIcons = new HashMap<>();
    private final Map<Project, Set<String>> mReportedIcons = new HashMap<>();

    @Override
    public void beforeCheckRootProject(Context context) {
        mProjectIcons.clear();
        mReportedIcons.clear();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        Project project = context.getProject();
        Map<String, Location> icons = mProjectIcons.get(project);
        if (icons == null || icons.isEmpty()) {
            return;
        }
        Set<String> reported = mReportedIcons.computeIfAbsent(project, k -> new HashSet<>());
        for (Map.Entry<String, Location> entry : icons.entrySet()) {
            String iconName = entry.getKey();
            if (!reported.add(iconName)) {
                continue;
            }
            checkIconSizes(context, project, iconName, entry.getValue());
        }
    }

    @Override
    public boolean filterIncident(
            LintDriver driver,
            Issue issue,
            Severity severity,
            Location location,
            String message,
            Object startNode,
            Object endNode,
            TextFormat textFormat) {
        return true;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UClass.class,
                UMethod.class,
                UCallExpression.class,
                USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new IconUastHandler();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "application",
                "activity",
                "activity-alias",
                "service",
                "receiver",
                "provider");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        org.w3c.dom.Attr iconAttr = element.getAttributeNodeNS(ANDROID_NS, "icon");
        if (iconAttr == null) {
            return;
        }
        String value = iconAttr.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }
        int slash = value.indexOf('/');
        String iconName = slash >= 0 ? value.substring(slash + 1) : value;
        if (iconName.isEmpty()) {
            return;
        }
        Project project = context.getProject();
        Map<String, Location> icons =
                mProjectIcons.computeIfAbsent(project, k -> new HashMap<>());
        icons.put(iconName, context.getLocation(iconAttr));
    }

    private void checkIconSizes(
            Context context, Project project, String iconName, Location reference) {
        List<File> resourceDirs = project.getResourceDirectories();
        if (resourceDirs == null) {
            return;
        }
        for (File resDir : resourceDirs) {
            File[] folders = resDir.listFiles();
            if (folders == null) {
                continue;
            }
            for (File folder : folders) {
                String folderName = folder.getName();
                if (!folderName.startsWith("mipmap-") && !folderName.startsWith("drawable-")) {
                    continue;
                }
                String density = getDensityQualifier(folderName);
                if (density == null) {
                    continue;
                }
                int expectedSize = getExpectedLauncherSize(density);
                if (expectedSize < 0) {
                    continue;
                }
                File iconFile = findIconFile(folder, iconName);
                if (iconFile == null) {
                    continue;
                }
                BufferedImage image = context.getClient().readImage(iconFile);
                if (image == null) {
                    continue;
                }
                int width = image.getWidth();
                int height = image.getHeight();
                if (width != expectedSize || height != expectedSize) {
                    String msg =
                            "Launcher icon '"
                                    + iconName
                                    + "' in "
                                    + folderName
                                    + " is "
                                    + width
                                    + "x"
                                    + height
                                    + " px; expected "
                                    + expectedSize
                                    + "x"
                                    + expectedSize
                                    + " px for "
                                    + density;
                    context.report(ISSUE, reference, msg);
                }
            }
        }
    }

    private static String getDensityQualifier(String folderName) {
        String[] parts = folderName.split("-");
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (getExpectedLauncherSize(part) >= 0) {
                return part;
            }
        }
        return null;
    }

    private static int getExpectedLauncherSize(String density) {
        switch (density) {
            case "ldpi":
                return 36;
            case "mdpi":
                return 48;
            case "hdpi":
                return 72;
            case "xhdpi":
                return 96;
            case "xxhdpi":
                return 144;
            case "xxxhdpi":
                return 192;
            case "tvdpi":
                return 64;
            default:
                return -1;
        }
    }

    private static File findIconFile(File folder, String iconName) {
        String[] extensions = {".png", ".webp", ".jpg", ".9.png"};
        for (String extension : extensions) {
            File candidate = new File(folder, iconName + extension);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    private static class IconUastHandler extends UElementHandler {
        @Override
        public void visitClass(UClass node) {}

        @Override
        public void visitMethod(UMethod node) {}

        @Override
        public void visitCallExpression(UCallExpression node) {}

        @Override
        public void visitSimpleNameReferenceExpression(
                USimpleNameReferenceExpression node) {}
    }
}