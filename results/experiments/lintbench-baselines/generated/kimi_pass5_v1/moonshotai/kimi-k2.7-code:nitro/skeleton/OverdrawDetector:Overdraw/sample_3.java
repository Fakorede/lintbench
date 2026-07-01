package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
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
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OverdrawDetector extends LayoutDetector implements Detector.UastScanner {

    private static final String CLASS_ACTIVITY = "android.app.Activity";
    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String SET_THEME = "setTheme";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_BACKGROUND = "background";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_PARENT = "parent";
    private static final String STYLE_TAG = "style";
    private static final String ITEM_TAG = "item";
    private static final String WINDOW_BACKGROUND = "windowBackground";
    private static final String NULL_VALUE = "@null";

    private static final Implementation IMPLEMENTATION =
            new Implementation(OverdrawDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "Setting a background drawable on a root view is wasteful when the activity's theme also paints a window background, because the theme background is drawn first and then completely covered by the view background. To avoid this \"overdraw\", consider using a custom theme with android:windowBackground set to @null, or pre-mix the theme background with your drawable and use the result as the theme background.\n\n"
                            + "NOTE: This detector uses an inexact pattern match to associate layouts with activities, so it may occasionally associate a layout with the wrong activity or miss an activity that sets a null window background theme.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, String> mActivityToLayout = new HashMap<>();
    private final Map<String, String> mActivityToTheme = new HashMap<>();
    private final Map<String, List<String>> mLayoutToActivities = new HashMap<>();
    private final Map<String, List<Location>> mLayoutToBackgroundLocations = new HashMap<>();
    private final Set<String> mNullWindowBackgroundThemes = new HashSet<>();

    private String mStyleName;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            XmlContext xml = (XmlContext) context;
            if (xml.getResourceFolderType() == ResourceFolderType.VALUES) {
                mStyleName = null;
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_BACKGROUND);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(STYLE_TAG, ITEM_TAG);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element owner = attribute.getOwnerElement();
        if (owner != owner.getOwnerDocument().getDocumentElement()) {
            return;
        }

        String ns = attribute.getNamespaceURI();
        if (ns != null && !ANDROID_URI.equals(ns)) {
            return;
        }

        if (!ATTR_BACKGROUND.equals(attribute.getLocalName())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || NULL_VALUE.equals(value) || "null".equals(value)) {
            return;
        }

        String layout = getBaseName(context.file.getName());
        Location location = context.getLocation(attribute);
        mLayoutToBackgroundLocations.computeIfAbsent(layout, k -> new ArrayList<>()).add(location);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getLocalName();
        if (STYLE_TAG.equals(tag)) {
            String name = element.getAttribute(ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                mStyleName = name;
                String parent = element.getAttribute(ATTR_PARENT);
                parent = stripStyleReference(parent);
                if (parent != null && mNullWindowBackgroundThemes.contains(parent)) {
                    mNullWindowBackgroundThemes.add(name);
                }
            }
        } else if (ITEM_TAG.equals(tag)) {
            String itemName = element.getAttribute(ATTR_NAME);
            if (itemName != null && itemName.endsWith(WINDOW_BACKGROUND)) {
                String value = element.getTextContent().trim();
                if (NULL_VALUE.equals(value) && mStyleName != null) {
                    mNullWindowBackgroundThemes.add(mStyleName);
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<Location>> entry : mLayoutToBackgroundLocations.entrySet()) {
            String layout = entry.getKey();
            List<String> activities = mLayoutToActivities.get(layout);

            boolean hasActivity = activities != null && !activities.isEmpty();
            boolean allNullBackground = true;
            if (hasActivity) {
                for (String activity : activities) {
                    String theme = mActivityToTheme.get(activity);
                    if (theme != null && mNullWindowBackgroundThemes.contains(theme)) {
                        continue;
                    }
                    allNullBackground = false;
                    break;
                }
            }

            if (hasActivity && allNullBackground) {
                continue;
            }

            String message = "Possible overdraw: root view has a background drawable. "
                    + "If the activity's theme also provides a window background, it will be painted first and then covered by this view, wasting GPU fill rate. "
                    + "Consider using a theme with android:windowBackground set to @null or moving the background to the theme.";

            for (Location location : entry.getValue()) {
                context.report(ISSUE, location, message);
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(CLASS_ACTIVITY);
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                OverdrawDetector.this.visitClass(context, node);
            }
        };
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (!context.getEvaluator().extendsClass(declaration.getPsi(), CLASS_ACTIVITY, false)) {
            return;
        }

        declaration.accept(new AbstractUastVisitor(true) {
            private final Deque<String> mActivityStack = new ArrayDeque<>();

            @Override
            public boolean visitClass(UClass node) {
                if (context.getEvaluator().extendsClass(node.getPsi(), CLASS_ACTIVITY, false)) {
                    String name = node.getName();
                    if (name != null) {
                        mActivityStack.push(name);
                    }
                }
                return super.visitClass(node);
            }

            @Override
            public void afterVisitClass(UClass node) {
                if (context.getEvaluator().extendsClass(node.getPsi(), CLASS_ACTIVITY, false)) {
                    if (!mActivityStack.isEmpty()) {
                        mActivityStack.pop();
                    }
                }
                super.afterVisitClass(node);
            }

            @Override
            public boolean visitCallExpression(UCallExpression node) {
                if (!mActivityStack.isEmpty()) {
                    OverdrawDetector.this.visitCallExpression(context, mActivityStack.peek(), node);
                }
                return super.visitCallExpression(node);
            }
        });
    }

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression expression) {
        // Not used directly; resource references are inspected as call-expression arguments.
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull String activity,
            @NonNull UCallExpression node) {
        String methodName = node.getMethodName();
        if (methodName == null) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        ResourceRef ref = getResourceRef(args.get(0));
        if (ref == null) {
            return;
        }

        if (SET_CONTENT_VIEW.equals(methodName) && "layout".equals(ref.type)) {
            mActivityToLayout.put(activity, ref.name);
            mLayoutToActivities.computeIfAbsent(ref.name, k -> new ArrayList<>()).add(activity);
        } else if (SET_THEME.equals(methodName) && "style".equals(ref.type)) {
            mActivityToTheme.put(activity, ref.name);
        }
    }

    private ResourceRef getResourceRef(UExpression expression) {
        String text = expression == null ? null : expression.getText();
        if (text == null) {
            return null;
        }
        int r = text.lastIndexOf("R.");
        if (r == -1) {
            return null;
        }
        int dot = text.indexOf('.', r + 2);
        if (dot == -1) {
            return null;
        }
        String type = text.substring(r + 2, dot);
        String name = text.substring(dot + 1);
        if (name.contains("(") || name.contains(" ") || name.contains("/")) {
            return null;
        }
        return new ResourceRef(type, name);
    }

    private String stripStyleReference(String reference) {
        if (reference == null || reference.isEmpty()) {
            return null;
        }
        if (reference.startsWith("@style/")) {
            return reference.substring("@style/".length());
        }
        if (reference.startsWith("@android:style/")) {
            return reference.substring("@android:style/".length());
        }
        return reference;
    }

    private static String getBaseName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot == -1 ? name : name.substring(0, dot);
    }

    private static class ResourceRef {
        final String type;
        final String name;

        ResourceRef(String type, String name) {
            this.type = type;
            this.name = name;
        }
    }
}