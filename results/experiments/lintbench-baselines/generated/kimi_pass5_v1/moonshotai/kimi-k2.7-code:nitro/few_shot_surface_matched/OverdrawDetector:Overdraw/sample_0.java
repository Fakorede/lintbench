package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
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
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a custom"
                            + " theme where the theme background is null. Otherwise, the theme"
                            + " background will be painted first, only to have your custom background"
                            + " completely cover it; this is called overdraw. Consider using a custom"
                            + " theme with your custom background instead of a root element"
                            + " background.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            OverdrawDetector.class,
                            EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE)));

    private static final String ATTR_BACKGROUND = "background";
    private static final Pattern LAYOUT_RESOURCE_PATTERN =
            Pattern.compile("R\\s*\\.\\s*layout\\s*\\.\\s*(\\w+)");

    private final Map<String, Boolean> mLayoutHasBackground = new HashMap<>();
    private final Map<String, Location> mLayoutBackgroundLocations = new HashMap<>();
    private final Set<String> mLayoutsUsedByActivities = new HashSet<>();
    private String mCurrentActivityClass;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        String fileName = context.file.getName();
        mLayoutHasBackground.put(fileName, Boolean.FALSE);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_BACKGROUND);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element owner = attribute.getOwnerElement();
        Node parent = owner.getParentNode();
        if (parent == null || parent.getNodeType() != Node.DOCUMENT_NODE) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty() || value.equals("@null") || value.equals("null")) {
            return;
        }

        String fileName = context.file.getName();
        mLayoutHasBackground.put(fileName, Boolean.TRUE);
        mLayoutBackgroundLocations.put(fileName, context.getLocation(attribute));
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Background detection is handled in visitAttribute.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UClass.class, UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList("android.app.Activity", "android.support.v7.app.AppCompatActivity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String name = declaration.getQualifiedName();
        if (name != null) {
            mCurrentActivityClass = name;
        }
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        if (mCurrentActivityClass == null) {
            return;
        }
        String methodName = node.getMethodName();
        if ("setContentView".equals(methodName) || "inflate".equals(methodName)) {
            List<UExpression> args = node.getValueArguments();
            if (!args.isEmpty()) {
                String layout = findLayoutName(args.get(0));
                if (layout != null) {
                    mLayoutsUsedByActivities.add(layout);
                }
            }
        }
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // Reserved for future variable-flow tracking.
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Boolean> entry : mLayoutHasBackground.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) {
                continue;
            }
            String layoutName = entry.getKey();
            if (!mLayoutsUsedByActivities.contains(layoutName)) {
                continue;
            }
            Location location = mLayoutBackgroundLocations.get(layoutName);
            if (location == null) {
                continue;
            }
            context.report(
                    ISSUE,
                    location,
                    "Possible overdraw: this root view has a background drawable. If the activity"
                            + " theme also paints a window background, that background will be"
                            + " covered and the region will be drawn twice. Consider using a custom"
                            + " theme with @null android:windowBackground.");
        }
    }

    private String findLayoutName(UExpression expression) {
        String source = expression.asSourceString();
        Matcher matcher = LAYOUT_RESOURCE_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group(1) + ".xml";
        }
        return null;
    }
}