package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
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

    private static final String TAG_VECTOR = "vector";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final int EXPECTED_DP = 48;
    private static final float TOLERANCE_DP = 1.0f;

    private static final Map<String, Float> DENSITY_SCALES = new HashMap<>();
    static {
        DENSITY_SCALES.put("ldpi", 0.75f);
        DENSITY_SCALES.put("mdpi", 1.0f);
        DENSITY_SCALES.put("tvdpi", 1.33f);
        DENSITY_SCALES.put("hdpi", 1.5f);
        DENSITY_SCALES.put("xhdpi", 2.0f);
        DENSITY_SCALES.put("xxhdpi", 3.0f);
        DENSITY_SCALES.put("xxxhdpi", 4.0f);
    }

    private static final Pattern DIMENSION_PATTERN =
            Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*(dp|dip|px|sp)?\\s*$");

    private final Set<String> mReported = new HashSet<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mReported.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No cross-project icon size reporting is performed in this detector.
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP
                || folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_VECTOR);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_VECTOR.equals(element.getTagName())) {
            return;
        }
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.MIPMAP
                && folderType != ResourceFolderType.DRAWABLE) {
            return;
        }

        String folderName = context.file.getParentFile().getName();
        String density = getDensity(folderName);
        if (density == null) {
            return;
        }
        float densityScale = DENSITY_SCALES.get(density);

        String width = element.getAttributeNS(ANDROID_URI, "width");
        String height = element.getAttributeNS(ANDROID_URI, "height");
        if (width.isEmpty() || height.isEmpty()) {
            return;
        }

        float widthDp = convertToDp(width, densityScale);
        float heightDp = convertToDp(height, densityScale);
        if (widthDp <= 0 || heightDp <= 0) {
            return;
        }

        if (Math.abs(widthDp - EXPECTED_DP) > TOLERANCE_DP
                || Math.abs(heightDp - EXPECTED_DP) > TOLERANCE_DP) {
            String message = String.format(
                    "Expected launcher icon size of %ddp for %s, but found %.1fdp x %.1fdp",
                    EXPECTED_DP, density, widthDp, heightDp);
            if (mReported.add(context.file.getAbsolutePath())) {
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for IconExpectedSize.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not used for IconExpectedSize.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used for IconExpectedSize.
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not used for IconExpectedSize.
            }
        };
    }

    private static String getDensity(@NonNull String folderName) {
        int dash = folderName.lastIndexOf('-');
        if (dash == -1 || dash == folderName.length() - 1) {
            return null;
        }
        String suffix = folderName.substring(dash + 1);
        if (DENSITY_SCALES.containsKey(suffix)) {
            return suffix;
        }
        return null;
    }

    private static float convertToDp(@NonNull String value, float densityScale) {
        Matcher matcher = DIMENSION_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return -1;
        }
        float number = Float.parseFloat(matcher.group(1));
        String unit = matcher.group(2);
        if (unit == null || "dp".equals(unit) || "dip".equals(unit) || "sp".equals(unit)) {
            return number;
        } else if ("px".equals(unit)) {
            return number / densityScale;
        }
        return -1;
    }
}