package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconExpectedSize",
                    "Icon has incorrect size",
                    "There are predefined sizes (for each density) for launcher icons. "
                            + "You should follow these conventions to make sure your icons fit in "
                            + "with the overall look of the platform.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ICON = "icon";
    private static final String ATTR_ROUND_ICON = "roundIcon";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_ACTIVITY_ALIAS = "activity-alias";

    private static final Map<String, Integer> EXPECTED_SIZES = new HashMap<>();
    static {
        EXPECTED_SIZES.put("ldpi", 36);
        EXPECTED_SIZES.put("mdpi", 48);
        EXPECTED_SIZES.put("tvdpi", 64);
        EXPECTED_SIZES.put("hdpi", 72);
        EXPECTED_SIZES.put("xhdpi", 96);
        EXPECTED_SIZES.put("xxhdpi", 144);
        EXPECTED_SIZES.put("xxxhdpi", 192);
    }

    private final Set<String> mIconNames = new HashSet<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mIconNames.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getMainProject();
        List<File> resourceFolders = project.getResourceFolders();
        for (File resDir : resourceFolders) {
            File[] dirs = resDir.listFiles();
            if (dirs == null) {
                continue;
            }
            for (File dir : dirs) {
                String folderName = dir.getName();
                if (!folderName.startsWith("mipmap")) {
                    continue;
                }
                String density = getDensityQualifier(folderName);
                if (density == null || !EXPECTED_SIZES.containsKey(density)) {
                    continue;
                }
                int expected = EXPECTED_SIZES.get(density);
                File[] files = dir.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (!file.isFile() || !file.getName().endsWith(".png")) {
                        continue;
                    }
                    String baseName = file.getName().substring(0, file.getName().length() - 4);
                    if (!mIconNames.isEmpty() && !mIconNames.contains(baseName)) {
                        continue;
                    }
                    int[] size = getPngSize(file);
                    if (size == null) {
                        continue;
                    }
                    if (size[0] != expected || size[1] != expected) {
                        String message =
                                "The icon '"
                                        + file.getName()
                                        + "' in "
                                        + folderName
                                        + " is "
                                        + size[0]
                                        + "x"
                                        + size[1]
                                        + " pixels, but for a launcher icon it should be "
                                        + expected
                                        + "x"
                                        + expected
                                        + " pixels for "
                                        + density;
                        Location location = Location.create(file);
                        context.report(new Incident(ISSUE, location, message));
                    }
                }
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP || folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_ACTIVITY, TAG_ACTIVITY_ALIAS);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String icon = element.getAttribute(ANDROID_URI, ATTR_ICON);
        if (icon != null && !icon.isEmpty()) {
            addIconName(icon);
        }
        String roundIcon = element.getAttribute(ANDROID_URI, ATTR_ROUND_ICON);
        if (roundIcon != null && !roundIcon.isEmpty()) {
            addIconName(roundIcon);
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No class-level analysis needed for this check.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UMethod.class, UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // No per-method analysis needed.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                String methodName = node.getMethodName();
                if (methodName == null) {
                    return;
                }
                if ("setIcon".equals(methodName)
                        || "setLogo".equals(methodName)
                        || "setImageResource".equals(methodName)
                        || "setImageDrawable".equals(methodName)) {
                    List<UExpression> args = node.getValueArguments();
                    if (args != null) {
                        for (UExpression arg : args) {
                            if (arg instanceof USimpleNameReferenceExpression) {
                                addIconName(((USimpleNameReferenceExpression) arg).getIdentifier());
                            }
                        }
                    }
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                String name = node.getIdentifier();
                if (name != null && (name.contains("ic_launcher") || name.contains("launcher_icon"))) {
                    addIconName(name);
                }
            }
        };
    }

    private void addIconName(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        String name = value.substring(value.lastIndexOf('/') + 1);
        if (name.contains(":")) {
            name = name.substring(name.lastIndexOf(':') + 1);
        }
        if (!name.isEmpty()) {
            mIconNames.add(name);
        }
    }

    private static String getDensityQualifier(String folderName) {
        String[] parts = folderName.split("-");
        for (int i = 1; i < parts.length; i++) {
            if (EXPECTED_SIZES.containsKey(parts[i])) {
                return parts[i];
            }
        }
        return null;
    }

    private static int[] getPngSize(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] signature = new byte[8];
            if (in.read(signature) != 8) {
                return null;
            }
            if (!isPngSignature(signature)) {
                return null;
            }
            byte[] buffer = new byte[4];
            while (true) {
                if (in.read(buffer) != 4) {
                    return null;
                }
                int length = readInt(buffer, 0);
                if (in.read(buffer) != 4) {
                    return null;
                }
                String type = new String(buffer, StandardCharsets.US_ASCII);
                if ("IHDR".equals(type)) {
                    byte[] ihdr = new byte[8];
                    if (in.read(ihdr) != 8) {
                        return null;
                    }
                    int width = readInt(ihdr, 0);
                    int height = readInt(ihdr, 4);
                    return new int[] { width, height };
                } else {
                    long skipped = in.skip(length + 4);
                    if (skipped != length + 4) {
                        return null;
                    }
                }
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean isPngSignature(byte[] sig) {
        return (sig[0] & 0xFF) == 0x89
                && sig[1] == 'P'
                && sig[2] == 'N'
                && sig[3] == 'G'
                && sig[4] == 0x0D
                && sig[5] == 0x0A
                && sig[6] == 0x1A
                && sig[7] == 0x0A;
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }
}