package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
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
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OverdrawDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a "
                            + "custom theme where the theme background is null. Otherwise, the theme "
                            + "background will be painted first, only to have your custom background "
                            + "completely cover it; this is called \"overdraw\".\n\n"
                            + "NOTE: This detector relies on figuring out which layouts are associated "
                            + "with which activities based on scanning the Java code, and it's currently "
                            + "doing that using an inexact pattern matching algorithm. Therefore, "
                            + "it can incorrectly conclude which activity the layout is associated with "
                            + "and then wrongly complain that a background-theme is hidden.\n\n"
                            + "If you want your custom background on multiple pages, then you should consider "
                            + "making a custom theme with your custom background and just using that theme "
                            + "instead of a root element background.\n\n"
                            + "Of course it's possible that your custom drawable is translucent and you want "
                            + "it to be mixed with the background. However, you will get better performance "
                            + "if you pre-mix the background with your drawable and use that resulting image or "
                            + "color as a custom theme background instead.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, List<RootBackground>> mLayoutsWithBackgrounds = new HashMap<>();
    private final Map<String, Set<String>> mActivityToLayouts = new HashMap<>();
    private final Map<String, String> mActivityToTheme = new HashMap<>();
    private final Set<String> mNullBackgroundThemes = new HashSet<>();
    private String mApplicationTheme = null;
    private String mManifestPackage = "";
    private String mCurrentActivity = null;

    private static class RootBackground {
        final XmlContext context;
        final Attr attribute;

        RootBackground(XmlContext context, Attr attribute) {
            this.context = context;
            this.attribute = attribute;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mCurrentActivity = null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if ("background".equals(name) || "android:background".equals(name)) {
            Element element = attribute.getOwnerElement();
            if (element.getParentNode() != null && element.getParentNode().getNodeType() == org.w3c.dom.Node.DOCUMENT_NODE) {
                String layoutName = context.file.getName();
                if (layoutName.endsWith(".xml")) {
                    layoutName = layoutName.substring(0, layoutName.length() - 4);
                }
                registerRootBackground(layoutName, context, attribute);
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("background", "android:background");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("style", "item", "activity", "application", "manifest");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("manifest".equals(tagName)) {
            mManifestPackage = element.getAttribute("package");
        } else if ("activity".equals(tagName)) {
            String activityName = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (activityName.isEmpty()) {
                activityName = element.getAttribute("android:name");
            }
            String theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme");
            if (theme.isEmpty()) {
                theme = element.getAttribute("android:theme");
            }
            if (!activityName.isEmpty() && !theme.isEmpty()) {
                registerActivityTheme(activityName, theme);
            }
        } else if ("application".equals(tagName)) {
            String theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme");
            if (theme.isEmpty()) {
                theme = element.getAttribute("android:theme");
            }
            if (!theme.isEmpty()) {
                mApplicationTheme = theme;
            }
        } else if ("item".equals(tagName)) {
            String name = element.getAttribute("name");
            if ("android:windowBackground".equals(name) || "windowBackground".equals(name)) {
                String value = element.getTextContent().trim();
                if ("@null".equals(value) || "@android:color/transparent".equals(value) || "@color/transparent".equals(value) || value.isEmpty()) {
                    org.w3c.dom.Node parentNode = element.getParentNode();
                    if (parentNode instanceof Element) {
                        Element parentStyle = (Element) parentNode;
                        if ("style".equals(parentStyle.getTagName())) {
                            String themeName = parentStyle.getAttribute("name");
                            if (themeName != null && !themeName.isEmpty()) {
                                registerNullBackgroundTheme(themeName);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList("android.app.Activity", "androidx.appcompat.app.AppCompatActivity", "support.v7.app.AppCompatActivity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mCurrentActivity = declaration.getQualifiedName();
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(USimpleNameReferenceExpression.class, UCallExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                OverdrawDetector.this.visitSimpleNameReferenceExpression(context, node);
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                OverdrawDetector.this.visitCallExpression(context, node);
            }
        };
    }

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        PsiElement resolved = node.resolve();
        if (resolved instanceof PsiField) {
            PsiField field = (PsiField) resolved;
            PsiClass containment = field.getContainingClass();
            if (containment != null && "layout".equals(containment.getName())) {
                PsiClass rClass = containment.getContainingClass();
                if (rClass != null && "R".equals(rClass.getName())) {
                    String layoutName = field.getName();
                    String activityName = mCurrentActivity;
                    if (activityName == null) {
                        activityName = getContainingActivityOf(node);
                    }
                    if (activityName != null && layoutName != null) {
                        registerActivityLayout(activityName, layoutName);
                    }
                }
            }
        }
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        // Handled via simple name references to R.layout
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<RootBackground>> entry : mLayoutsWithBackgrounds.entrySet()) {
            String layoutName = entry.getKey();
            List<RootBackground> backgrounds = entry.getValue();

            boolean hasOverdraw = false;
            boolean hasAssociatedActivity = false;
            for (Map.Entry<String, Set<String>> activityEntry : mActivityToLayouts.entrySet()) {
                String activityName = activityEntry.getKey();
                Set<String> layouts = activityEntry.getValue();
                if (layouts.contains(layoutName)) {
                    hasAssociatedActivity = true;
                    String theme = mActivityToTheme.get(activityName);
                    if (theme == null) {
                        theme = mApplicationTheme;
                    }
                    if (theme == null || !mNullBackgroundThemes.contains(cleanThemeName(theme))) {
                        hasOverdraw = true;
                        break;
                    }
                }
            }

            if (hasAssociatedActivity && hasOverdraw) {
                for (RootBackground rb : backgrounds) {
                    rb.context.report(
                            ISSUE,
                            rb.attribute,
                            rb.context.getLocation(rb.attribute),
                            "Possible overdraw: Root element paints background "
                                    + rb.attribute.getValue()
                                    + " with a theme that also paints a background");
                }
            }
        }
    }

    private void registerRootBackground(String layoutName, XmlContext context, Attr attribute) {
        List<RootBackground> backgrounds = mLayoutsWithBackgrounds.get(layoutName);
        if (backgrounds == null) {
            backgrounds = new ArrayList<>();
            mLayoutsWithBackgrounds.put(layoutName, backgrounds);
        }
        backgrounds.add(new RootBackground(context, attribute));
    }

    private void registerActivityLayout(String activityName, String layoutName) {
        Set<String> layouts = mActivityToLayouts.get(activityName);
        if (layouts == null) {
            layouts = new HashSet<>();
            mActivityToLayouts.put(activityName, layouts);
        }
        layouts.add(layoutName);
    }

    private void registerActivityTheme(String activityName, String theme) {
        String resolvedName = resolveActivityName(mManifestPackage, activityName);
        mActivityToTheme.put(resolvedName, theme);
    }

    private void registerNullBackgroundTheme(String themeName) {
        mNullBackgroundThemes.add(cleanThemeName(themeName));
    }

    private String resolveActivityName(String manifestPackage, String activityName) {
        if (activityName.startsWith(".")) {
            return manifestPackage + activityName;
        } else if (!activityName.contains(".")) {
            return manifestPackage + "." + activityName;
        }
        return activityName;
    }

    private String cleanThemeName(String theme) {
        if (theme == null) {
            return null;
        }
        int index = theme.lastIndexOf('/');
        if (index != -1) {
            theme = theme.substring(index + 1);
        }
        if (theme.startsWith("@")) {
            theme = theme.substring(1);
        }
        return theme;
    }

    private static String getContainingActivityOf(UElement node) {
        UElement current = node;
        while (current != null) {
            if (current instanceof UClass) {
                UClass uClass = (UClass) current;
                return uClass.getQualifiedName();
            }
            current = current.getUastParent();
        }
        return null;
    }
}