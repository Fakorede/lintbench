package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_STYLE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.utils.XmlUtils;
import com.intellij.psi.PsiElement;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OverdrawDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String STYLE_PREFIX = "@style/";
    private static final String ANDROID_STYLE_PREFIX = "@android:style/";
    private static final String WINDOW_BACKGROUND = "windowBackground";

    private static final Pattern R_LAYOUT_PATTERN =
            Pattern.compile("\\b(?:\\w+\\.)*R\\.layout\\.([A-Za-z_]\\w*)");

    private static final Implementation IMPLEMENTATION = new Implementation(
            OverdrawDetector.class,
            EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Painting regions more than once",
            "If you set a background drawable on a root view, then you should use a custom theme where the theme background is null. Otherwise, the theme background will be painted first, only to have your custom background completely cover it; this is called \"overdraw\".\n\n"
                    + "NOTE: This detector relies on figuring out which layouts are associated with which activities based on scanning the Java code, and it's currently doing that using an inexact pattern matching algorithm. Therefore, it can incorrectly conclude which activity the layout is associated with and then wrongly complain that a background-theme is hidden.\n\n"
                    + "If you want your custom background on multiple pages, then you should consider making a custom theme with your custom background and just using that theme instead of a root element background.\n\n"
                    + "Of course it's possible that your custom drawable is translucent and you want it to be mixed with the background. However, you will get better performance if you pre-mix the background with your drawable and use that resulting image or color as a custom theme background instead.",
            Category.PERFORMANCE,
            2,
            Severity.WARNING,
            IMPLEMENTATION);

    private final Set<String> mOverdrawLayouts = new HashSet<>();
    private final Map<String, Location> mLayoutLocations = new HashMap<>();
    private final Map<String, Set<String>> mLayoutToActivities = new HashMap<>();
    private final Map<String, String> mActivityThemes = new HashMap<>();
    private final Map<String, StyleInfo> mStyleInfos = new HashMap<>();
    @Nullable private String mApplicationTheme;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_BACKGROUND);
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_ACTIVITY, TAG_STYLE);
    }

    @Override
    @NonNull
    public Collection<String> applicableCallNames() {
        return Collections.singletonList(SET_CONTENT_VIEW);
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mOverdrawLayouts.clear();
        mLayoutLocations.clear();
        mLayoutToActivities.clear();
        mActivityThemes.clear();
        mStyleInfos.clear();
        mApplicationTheme = null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        if (!ATTR_BACKGROUND.equals(attribute.getLocalName())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty() || value.startsWith("?") || "@null".equals(value)) {
            return;
        }
        if (!value.startsWith("@") && !value.startsWith("#")) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        if (!isRoot(owner)) {
            return;
        }

        String fileName = context.file.getName();
        int dot = fileName.lastIndexOf('.');
        String layoutName = dot > 0 ? fileName.substring(0, dot) : fileName;

        mOverdrawLayouts.add(layoutName);
        mLayoutLocations.put(layoutName, context.getLocation(attribute));
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        ResourceFolderType folderType = context.getResourceFolderType();

        if (folderType == null) {
            if (TAG_APPLICATION.equals(tag)) {
                mApplicationTheme = normalizeStyleName(element.getAttribute(ATTR_THEME));
            } else if (TAG_ACTIVITY.equals(tag)) {
                String name = element.getAttribute(ATTR_NAME);
                String activity = resolveActivityName(context, name);
                String theme = normalizeStyleName(element.getAttribute(ATTR_THEME));
                if (theme == null || theme.isEmpty()) {
                    theme = mApplicationTheme;
                }
                if (activity != null) {
                    mActivityThemes.put(activity, theme != null ? theme : "");
                }
            }
        } else if (folderType == ResourceFolderType.VALUES) {
            if (TAG_STYLE.equals(tag)) {
                String styleName = element.getAttribute(ATTR_NAME);
                if (styleName == null || styleName.isEmpty()) {
                    return;
                }

                String parent = normalizeStyleName(element.getAttribute(ATTR_PARENT));
                Boolean hasBackground = null;
                for (Element item : XmlUtils.getSubTags(element)) {
                    if (TAG_ITEM.equals(item.getTagName())) {
                        String itemName = item.getAttribute(ATTR_NAME);
                        if (itemName != null && itemName.endsWith(WINDOW_BACKGROUND)) {
                            String itemValue = item.getTextContent().trim();
                            if (!itemValue.isEmpty()) {
                                hasBackground = isNullOrTransparent(itemValue)
                                        ? Boolean.FALSE
                                        : Boolean.TRUE;
                            }
                        }
                    }
                }

                mStyleInfos.put(styleName, new StyleInfo(parent, hasBackground));
            }
        }
    }

    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression node,
                                @NonNull PsiElement method) {
        String methodName = node.getMethodName();
        if (methodName == null || !SET_CONTENT_VIEW.equals(methodName)) {
            return;
        }

        if (node.getValueArguments().isEmpty()) {
            return;
        }

        UExpression argument = node.getValueArguments().get(0);
        String layoutName = getLayoutName(argument);
        if (layoutName == null) {
            return;
        }

        UClass containingClass = UastUtils.getParentOfType(node, UClass.class, false);
        if (containingClass == null) {
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.extendsClass(containingClass, "android.app.Activity", false)
                && !evaluator.extendsClass(containingClass,
                "androidx.appcompat.app.AppCompatActivity", false)) {
            return;
        }

        String activityName = containingClass.getQualifiedName();
        if (activityName == null) {
            return;
        }

        mLayoutToActivities
                .computeIfAbsent(layoutName, k -> new HashSet<>())
                .add(activityName);
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (String layout : mOverdrawLayouts) {
            Set<String> activities = mLayoutToActivities.get(layout);
            if (activities == null || activities.isEmpty()) {
                continue;
            }

            boolean possibleOverdraw = false;
            for (String activity : activities) {
                String theme = mActivityThemes.get(activity);
                if (theme == null || theme.isEmpty()) {
                    theme = mApplicationTheme;
                }
                if (hasWindowBackground(theme)) {
                    possibleOverdraw = true;
                    break;
                }
            }

            if (possibleOverdraw) {
                Location location = mLayoutLocations.get(layout);
                if (location != null) {
                    context.report(ISSUE, location,
                            "Possible overdraw: the root view of layout \"" + layout
                                    + "\" has a custom background, and this layout is used by an "
                                    + "Activity whose theme also has a window background. Consider "
                                    + "using a theme with a null android:windowBackground.");
                }
            }
        }
    }

    private boolean isRoot(@NonNull Element element) {
        Node parent = element.getParentNode();
        return parent != null && parent.getNodeType() == Node.DOCUMENT_NODE;
    }

    @Nullable
    private String getLayoutName(@NonNull UExpression argument) {
        PsiElement sourcePsi = argument.getSourcePsi();
        if (sourcePsi == null) {
            return null;
        }
        String text = sourcePsi.getText();
        if (text == null) {
            return null;
        }
        Matcher matcher = R_LAYOUT_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    @Nullable
    private String resolveActivityName(@NonNull XmlContext context, @NonNull String name) {
        String packageName = context.getMainProject().getPackage();
        if (packageName == null) {
            packageName = "";
        }
        if (name.startsWith(".")) {
            return packageName + name;
        }
        if (name.contains(".")) {
            return name;
        }
        return packageName + "." + name;
    }

    @Nullable
    private static String normalizeStyleName(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.startsWith(STYLE_PREFIX)) {
            return value.substring(STYLE_PREFIX.length());
        }
        if (value.startsWith(ANDROID_STYLE_PREFIX)) {
            return value.substring(ANDROID_STYLE_PREFIX.length());
        }
        if (value.startsWith("@")) {
            return value.substring(1);
        }
        return value;
    }

    private boolean hasWindowBackground(@Nullable String theme) {
        if (theme == null || theme.isEmpty()) {
            return true;
        }
        return resolveWindowBackground(theme);
    }

    private boolean resolveWindowBackground(@NonNull String styleName) {
        StyleInfo info = mStyleInfos.get(styleName);
        if (info == null) {
            return true;
        }
        if (info.hasBackground != null) {
            return info.hasBackground;
        }
        if (info.parent == null || info.parent.isEmpty()) {
            return true;
        }
        return resolveWindowBackground(info.parent);
    }

    private static boolean isNullOrTransparent(@NonNull String value) {
        return "@null".equals(value)
                || "@android:color/transparent".equals(value)
                || "#00000000".equalsIgnoreCase(value)
                || value.toLowerCase().endsWith("/transparent");
    }

    private static final class StyleInfo {
        @Nullable final String parent;