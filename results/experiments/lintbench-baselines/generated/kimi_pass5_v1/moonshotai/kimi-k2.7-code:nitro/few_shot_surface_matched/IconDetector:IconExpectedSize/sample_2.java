package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

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
                                    Scope.BINARY_RESOURCE_FILE_SCOPE)));

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ICON = "icon";
    private static final String ATTR_LOGO = "logo";

    private static final int LAUNCHER_BASE_SIZE = 48;
    private static final int ACTION_BAR_BASE_SIZE = 32;
    private static final int NOTIFICATION_BASE_SIZE = 24;

    private static final Map<String, Double> DENSITY_SCALES;

    static {
        Map<String, Double> map = new HashMap<>();
        map.put("ldpi", 0.75);
        map.put("mdpi", 1.0);
        map.put("hdpi", 1.5);
        map.put("xhdpi", 2.0);
        map.put("xxhdpi", 3.0);
        map.put("xxxhdpi", 4.0);
        DENSITY_SCALES = Collections.unmodifiableMap(map);
    }

    private Set<String> mLauncherIconNames;
    private Set<String> mActionBarIconNames;
    private Set<String> mNotificationIconNames;

    @Override
    public void beforeCheckRootProject(Context context) {
        mLauncherIconNames = new HashSet<>();
        mActionBarIconNames = new HashSet<>();
        mNotificationIconNames = new HashSet<>();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        checkIconSizes(context);
        mLauncherIconNames.clear();
        mActionBarIconNames.clear();
        mNotificationIconNames.clear();
    }

    @Override
    public boolean filterIncident(Incident incident) {
        return incident.getIssue() == ISSUE;
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE
                || folderType == com.android.resources.ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.unmodifiableList(
                Arrays.asList(
                        "manifest",
                        "application",
                        "activity",
                        "activity-alias",
                        "service",
                        "receiver",
                        "provider"));
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String tag = element.getTagName();
        if (!"application".equals(tag)
                && !"activity".equals(tag)
                && !"activity-alias".equals(tag)
                && !"service".equals(tag)
                && !"receiver".equals(tag)
                && !"provider".equals(tag)) {
            return;
        }

        recordLauncherIcon(element.getAttributeNS(ANDROID_NS, ATTR_ICON));
        recordLauncherIcon(element.getAttributeNS(ANDROID_NS, ATTR_LOGO));
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.unmodifiableList(
                Arrays.asList(
                        UCallExpression.class,
                        USimpleNameReferenceExpression.class,
                        UMethod.class,
                        UClass.class));
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
                // No method-level analysis required for expected icon sizes.
            }

            @Override
            public void visitClass(UClass node) {
                // No class-level analysis required for expected icon sizes.
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                String methodName = node.getMethodName();
                if (methodName == null) {
                    return;
                }
                List<UExpression> args = node.getValueArguments();
                if (args == null || args.isEmpty()) {
                    return;
                }
                UExpression first = args.get(0);
                String text = first.asSourceString();
                String name = extractResourceName(text);
                if (name == null) {
                    return;
                }
                if ("setSmallIcon".equals(methodName) || "setLargeIcon".equals(methodName)) {
                    mNotificationIconNames.add(name);
                } else if ("setIcon".equals(methodName) || "setLogo".equals(methodName)) {
                    mActionBarIconNames.add(name);
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                String name = node.getIdentifier();
                if (name == null || name.isEmpty()) {
                    return;
                }
                UElement parent = node.getUastParent();
                if (parent instanceof UQualifiedReferenceExpression) {
                    String source = ((UQualifiedReferenceExpression) parent).asSourceString();
                    if (source.startsWith("R.mipmap.") || source.startsWith("R.drawable.")) {
                        mLauncherIconNames.add(name);
                    }
                }
            }
        };
    }

    private void recordLauncherIcon(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (value.startsWith("@mipmap/") || value.startsWith("@drawable/")) {
            String name = value.substring(value.indexOf('/') + 1);
            int colon = name.indexOf(':');
            if (colon != -1) {
                name = name.substring(colon + 1);
            }
            mLauncherIconNames.add(name);
        }
    }

    private String extractResourceName(String reference) {
        if (reference == null || reference.isEmpty()) {
            return null;
        }
        if (!reference.startsWith("R.mipmap.") && !reference.startsWith("R.drawable.")) {
            return null;
        }
        int dot = reference.lastIndexOf('.');
        return dot != -1 && dot + 1 < reference.length() ? reference.substring(dot + 1) : null;
    }

    private void checkIconSizes(Context context) {
        for (File resDir : context.getProject().getResourceDirectories()) {
            if (!resDir.isDirectory()) {
                continue;
            }
            File[] folders = resDir.listFiles();
            if (folders == null) {
                continue;
            }
            for (File folder : folders) {
                if (!folder.isDirectory()) {
                    continue;
                }
                String folderName = folder.getName();
                String typePrefix = null;
                if (folderName.startsWith("mipmap-")) {
                    typePrefix = "mipmap-";
                } else if (folderName.startsWith("drawable-")) {
                    typePrefix = "drawable-";
                }
                if (typePrefix == null) {
                    continue;
                }
                String density = folderName.substring(typePrefix.length());
                Double scale = DENSITY_SCALES.get(density);
                if (scale == null) {
                    continue;
                }

                File[] files = folder.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (!file.isFile()) {
                        continue;
                    }
                    String baseName = getBaseName(file);
                    int expectedSize = -1;
                    if (mLauncherIconNames.contains(baseName)) {
                        expectedSize = (int) Math.round(LAUNCHER_BASE_SIZE * scale);
                    } else if (mActionBarIconNames.contains(baseName)) {
                        expectedSize = (int) Math.round(ACTION_BAR_BASE_SIZE * scale);
                    } else if (mNotificationIconNames.contains(baseName)) {
                        expectedSize = (int) Math.round(NOTIFICATION_BASE_SIZE * scale);
                    }
                    if (expectedSize == -1) {
                        continue;
                    }
                    int[] size = readImageSize(file);
                    if (size == null) {
                        continue;
                    }
                    if (size[0] != expectedSize || size[1] != expectedSize) {
                        String message =
                                "The icon "
                                        + baseName
                                        + " in "
                                        + folderName
                                        + " is "
                                        + size[0]
                                        + "x"
                                        + size[1]
                                        + " px, but should be "
                                        + expectedSize
                                        + "x"
                                        + expectedSize
                                        + " px for this density";
                        context.report(ISSUE, Location.create(file), message);
                    }
                }
            }
        }
    }

    private String getBaseName(File file) {
        String name = file.getName();
        String[] known = {".9.png", ".png", ".webp", ".jpg", ".jpeg", ".gif"};
        for (String ext : known) {
            if (name.endsWith(ext)) {
                return name.substring(0, name.length() - ext.length());
            }
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private int[] readImageSize(File file) {
        String lower = file.getName().toLowerCase();
        if (lower.endsWith(".png") || lower.contains(".9.png")) {
            return readPngSize(file);
        }
        return null;
    }

    private int[] readPngSize(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] header = new byte[24];
            if (raf.read(header) < 24) {
                return null;
            }
            if (header[0] != (byte) 0x89 || header[1] != (byte) 0x50) {
                return null;
            }
            int width =
                    ((header[16] & 0xFF) << 24)
                            | ((header[17] & 0xFF) << 16)
                            | ((header[18] & 0xFF) << 8)
                            | (header[19] & 0xFF);
            int height =
                    ((header[20] & 0xFF) << 24)
                            | ((header[21] & 0xFF) << 16)
                            | ((header[22] & 0xFF) << 8)
                            | (header[23] & 0xFF);
            return new int[] {width, height};
        } catch (IOException e) {
            return null;
        }
    }
}