package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OverdrawDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private static final String EXPLANATION =
            "If you set a background drawable on a root view, then you should use a custom theme "
                    + "where the theme background is null. Otherwise, the theme background will be "
                    + "painted first, only to have your custom background completely cover it; this "
                    + "is called \"overdraw\".\n\n"
                    + "Note: This detector relies on figuring out which layouts are associated with "
                    + "which activities based on scanning the Java code, and it currently does that "
                    + "using an inexact pattern matching algorithm. Therefore, it can incorrectly "
                    + "conclude which activity the layout is associated with and then wrongly "
                    + "complain that a background theme is hidden.\n\n"
                    + "If you want your custom background on multiple pages, then you should consider "
                    + "making a custom theme with your custom background and just using that theme "
                    + "instead of a root element background.\n\n"
                    + "Of course it's possible that your custom drawable is translucent and you want "
                    + "it to be mixed with the background. However, you will get better performance "
                    + "if you pre-mix the background with your drawable and use that resulting image "
                    + "or color as a custom theme background instead.";

    public static final Issue ISSUE = Issue.create(
            "Overdraw",
            "Overdraw: Painting regions more than once",
            EXPLANATION,
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(OverdrawDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    private final Map<String, String> mLayoutToActivity = new HashMap<>();
    private final Map<String, String> mActivityToTheme = new HashMap<>();
    private final Map<String, Boolean> mThemeHasBackground = new HashMap<>();
    private String mApplicationTheme;

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mLayoutToActivity.clear();
        mActivityToTheme.clear();
        mThemeHasBackground.clear();
        mApplicationTheme = null;

        Project project = context.getProject();
        Document manifest = project.getManifestDom();
        if (manifest == null) {
            return;
        }

        NodeList applications = manifest.getElementsByTagName(SdkConstants.TAG_APPLICATION);
        if (applications.getLength() > 0) {
            Element application = (Element) applications.item(0);
            String theme = application.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME);
            if (!theme.isEmpty()) {
                mApplicationTheme = theme;
            }
        }

        NodeList activities = manifest.getElementsByTagName(SdkConstants.TAG_ACTIVITY);
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            String name = getActivityName(activity, project);
            if (name == null) {
                continue;
            }
            String theme = activity.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME);
            if (!theme.isEmpty()) {
                mActivityToTheme.put(name, theme);
            }
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(SdkConstants.ATTR_BACKGROUND, SdkConstants.ATTR_WINDOW_BACKGROUND);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (SdkConstants.ATTR_BACKGROUND.equals(name)) {
            Node parent = element.getParentNode();
            if (parent != null && parent.getNodeType() == Node.DOCUMENT_NODE) {
                checkRootBackground(context, attribute, value);
            }
        } else if (SdkConstants.ATTR_WINDOW_BACKGROUND.equals(name)
                && SdkConstants.TAG_STYLE.equals(element.getTagName())) {
            String styleName = element.getAttribute(SdkConstants.ATTR_NAME);
            if (!styleName.isEmpty()) {
                mThemeHasBackground.put(styleName, !isNullOrTransparent(value));
            }
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setContentView");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        UClass cls = UastUtils.getParentOfType(call, UClass.class);
        while (cls != null) {
            if (context.getEvaluator().extendsClass(cls, "android.app.Activity", false)) {
                String qualifiedName = cls.getQualifiedName();
                if (qualifiedName != null) {
                    List<UExpression> args = call.getValueArguments();
                    if (!args.isEmpty()) {
                        String layout = getLayoutName(args.get(0));
                        if (layout != null) {
                            mLayoutToActivity.put(layout, qualifiedName);
                        }
                    }
                    return;
                }
            }
            cls = UastUtils.getParentOfType(cls, UClass.class);
        }
    }

    private void checkRootBackground(
            @NonNull XmlContext context,
            @NonNull Attr attribute,
            @NonNull String value) {
        if (isNullOrTransparent(value)) {
            return;
        }

        String layout = LintUtils.getLayoutName(context.file);
        String activity = mLayoutToActivity.get(layout);
        if (activity == null) {
            return;
        }

        String theme = mActivityToTheme.get(activity);
        if (theme == null) {
            theme = mApplicationTheme;
        }

        boolean hasWindowBackground = true;
        if (theme != null) {
            String themeName = normalizeTheme(theme);
            Boolean known = mThemeHasBackground.get(themeName);
            if (known != null) {
                hasWindowBackground = known;
            }
        }

        if (hasWindowBackground) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Possible overdraw: Root element paints background `"
                            + value
                            + "` with a theme that also paints a background (theme expected to be "
                            + "@null or @android:color/transparent)");
        }
    }

    @Nullable
    private String getActivityName(@NonNull Element element, @NonNull Project project) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name.isEmpty()) {
            return null;
        }

        String pkg = project.getPackage();
        if (pkg == null) {
            return name;
        }

        if (name.startsWith(".")) {
            return pkg + name;
        } else if (!name.contains(".")) {
            return pkg + "." + name;
        }
        return name;
    }

    @Nullable
    private static String getLayoutName(@NonNull UExpression expression) {
        String source = expression.asSourceString();
        int index = source.indexOf(".R.layout.");
        if (index != -1) {
            return source.substring(index + ".R.layout.".length());
        }
        if (source.startsWith("R.layout.")) {
            return source.substring("R.layout.".length());
        }
        return null;
    }

    @NonNull
    private static String normalizeTheme(@NonNull String theme) {
        if (theme.startsWith("@style/")) {
            return theme.substring("@style/".length());
        }
        if (theme.startsWith("@android:style/")) {
            return theme.substring("@android:style/".length());
        }
        return theme;
    }

    private static boolean isNullOrTransparent(@NonNull String value) {
        if ("@null".equals(value) || "@android:color/transparent".equals(value)) {
            return true;
        }
        if (value.startsWith("#")) {
            String hex = value.substring(1);
            if (hex.length() == 8) {
                try {
                    int alpha = Integer.parseInt(hex.substring(0, 2), 16);
                    return alpha == 0;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return false;
    }
}