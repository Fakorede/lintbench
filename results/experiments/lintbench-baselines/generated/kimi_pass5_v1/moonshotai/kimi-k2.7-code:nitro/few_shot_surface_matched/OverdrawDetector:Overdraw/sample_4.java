package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UIdentifier;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OverdrawDetector extends LayoutDetector
        implements SourceCodeScanner, XmlScanner {

    private static final Pattern LAYOUT_PATTERN =
            Pattern.compile("R\\.layout\\.([\\w$]+)");
    private static final Pattern NO_BG_THEME_PATTERN =
            Pattern.compile(".*(?:NoBackground|NoDisplay|Translucent|Transparent).*");

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a "
                            + "custom theme where the theme background is null. Otherwise, the "
                            + "theme background will be painted first, only to have your custom "
                            + "background completely cover it (overdraw).\n\n"
                            + "If you want your custom background on multiple pages, consider "
                            + "making a custom theme with your custom background and just using "
                            + "that theme instead of a root element background.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            OverdrawDetector.class,
                            Scope.JAVA_FILE_SCOPE,
                            Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, Location> mLayoutBackgrounds = new HashMap<>();
    private final Map<String, String> mActivityToLayout = new HashMap<>();
    private final Set<String> mActivities = new HashSet<>();
    private final Set<String> mNoWindowBackgroundActivities = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull Project project) {
        return true;
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }
    }

    @NonNull
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("background");
    }

    @NonNull
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!"background".equals(attribute.getLocalName())) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        if (owner == null
                || owner.getOwnerDocument().getDocumentElement() != owner) {
            return;
        }

        String value = attribute.getValue();
        if (value == null
                || value.startsWith("@null")
                || value.startsWith("@android:color/transparent")) {
            return;
        }

        String layoutName = getBaseName(context.file.getName());
        mLayoutBackgrounds.put(layoutName, context.getLocation(attribute));
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.ListActivity",
                "android.preference.PreferenceActivity",
                "android.support.v7.app.AppCompatActivity",
                "androidx.appcompat.app.AppCompatActivity",
                "androidx.fragment.app.FragmentActivity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String name = declaration.getQualifiedName();
        if (name != null) {
            mActivities.add(name);
        }
    }

    @NonNull
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @NonNull
    @Override
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                String methodName = node.getMethodName();
                if (methodName == null) {
                    UIdentifier id = node.getMethodIdentifier();
                    if (id != null) {
                        methodName = id.getName();
                    }
                }
                if (methodName == null) {
                    return;
                }

                List<UExpression> args = node.getValueArguments();
                if (args.isEmpty()) {
                    return;
                }

                UExpression firstArg = args.get(0);

                if ("setContentView".equals(methodName)) {
                    String src = firstArg.asSourceString();
                    Matcher matcher = LAYOUT_PATTERN.matcher(src);
                    if (matcher.find()) {
                        String layoutName = matcher.group(1);
                        String activityName = getEnclosingActivityName(node);
                        if (activityName != null) {
                            mActivityToLayout.put(activityName, layoutName);
                        }
                    }
                } else if ("setTheme".equals(methodName)) {
                    String src = firstArg.asSourceString();
                    if (NO_BG_THEME_PATTERN.matcher(src).matches()) {
                        String activityName = getEnclosingActivityName(node);
                        if (activityName != null) {
                            mNoWindowBackgroundActivities.add(activityName);
                        }
                    }
                } else if (methodName.startsWith("setBackground")) {
                    if (isNullOrTransparent(firstArg)) {
                        String activityName = getEnclosingActivityName(node);
                        if (activityName != null) {
                            mNoWindowBackgroundActivities.add(activityName);
                        }
                    }
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // No simple-name heuristic is currently used; call expressions provide
                // the layout and background information needed for this check.
            }
        };
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Location> layoutEntry : mLayoutBackgrounds.entrySet()) {
            String layoutName = layoutEntry.getKey();
            Location location = layoutEntry.getValue();

            List<String> offendingActivities = new ArrayList<>();
            for (Map.Entry<String, String> mapping : mActivityToLayout.entrySet()) {
                if (layoutName.equals(mapping.getValue())) {
                    String activityName = mapping.getKey();
                    if (!mNoWindowBackgroundActivities.contains(activityName)) {
                        offendingActivities.add(activityName);
                    }
                }
            }

            if (!offendingActivities.isEmpty()) {
                String message =
                        "Possible overdraw: the root view has a custom background, but the "
                                + "associated activity may be painting a theme background first. "
                                + "Consider using a theme with a null windowBackground. "
                                + "Associated activities: "
                                + offendingActivities;
                context.report(ISSUE, location, message);
            }
        }
    }

    private String getEnclosingActivityName(UElement node) {
        UElement current = node;
        while (current != null) {
            if (current instanceof UClass) {
                String name = ((UClass) current).getQualifiedName();
                if (name != null && mActivities.contains(name)) {
                    return name;
                }
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean isNullOrTransparent(UExpression expression) {
        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            if (value == null) {
                return true;
            }
            if (value instanceof Number && ((Number) value).intValue() == 0) {
                return true;
            }
        }
        return false;
    }

    private static String getBaseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}