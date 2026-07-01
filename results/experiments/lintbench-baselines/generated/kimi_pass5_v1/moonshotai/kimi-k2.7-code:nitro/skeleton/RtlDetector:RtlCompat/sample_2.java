package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_TEXT_ALIGNMENT = "textAlignment";
    private static final String ATTR_GRAVITY = "gravity";
    private static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";
    private static final String GRAVITY_CLASS = "android.view.Gravity";
    private static final String API_KEY = "api";

    private static final Implementation IMPLEMENTATION =
            new Implementation(RtlDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "RtlCompat",
                    "Right-to-left text compatibility issues",
                    "API 17 adds a `textAlignment` attribute to specify text alignment. However, "
                            + "if you are supporting older versions than API 17, you must also "
                            + "specify a `gravity` or `layout_gravity` attribute, since older "
                            + "platforms will ignore the `textAlignment` attribute. Similarly, "
                            + "`Gravity#START` and `Gravity#END` are only available on API 16 "
                            + "and higher.",
                    Category.RTL,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        int minSdk = context.getMainProject().getMinSdkVersion();
        int requiredApi = map.getInt(API_KEY, 17);
        if (minSdk >= requiredApi) {
            return false;
        }
        Location location = incident.getLocation();
        if (location != null && location.getFile() != null) {
            int folderVersion = getFolderVersion(location.getFile().getPath());
            if (folderVersion >= requiredApi) {
                return false;
            }
        }
        return true;
    }

    private int getFolderVersion(String path) {
        String marker = File.separator + "res" + File.separator;
        int index = path.lastIndexOf(marker);
        if (index == -1) {
            return 0;
        }
        String after = path.substring(index + marker.length());
        int slash = after.indexOf(File.separatorChar);
        String folder = slash == -1 ? after : after.substring(0, slash);
        int v = folder.indexOf("-v");
        if (v == -1) {
            return 0;
        }
        int start = v + 2;
        int end = start;
        while (end < folder.length() && Character.isDigit(folder.charAt(end))) {
            end++;
        }
        if (end > start) {
            try {
                return Integer.parseInt(folder.substring(start, end));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // No project-wide aggregation is required for this check.
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ATTR_TEXT_ALIGNMENT.equals(attribute.getLocalName())) {
            return;
        }
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }
        if (hasAndroidAttribute(element, ATTR_GRAVITY)
                || hasAndroidAttribute(element, ATTR_LAYOUT_GRAVITY)) {
            return;
        }
        String message =
                "When using `textAlignment` for RTL text alignment on devices older than API 17, "
                        + "you must also specify a `gravity` or `layout_gravity` attribute.";
        LintMap data = LintMap.builder().put(API_KEY, 17).build();
        context.report(ISSUE, attribute, context.getLocation(attribute), message, data);
    }

    private boolean hasAndroidAttribute(@NonNull Element element, @NonNull String localName) {
        return element.hasAttributeNS(ANDROID_URI, localName);
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                String name = node.getIdentifier();
                if (!"START".equals(name) && !"END".equals(name)) {
                    return;
                }
                PsiElement resolved = node.resolve();
                if (!(resolved instanceof PsiField)) {
                    return;
                }
                PsiField field = (PsiField) resolved;
                PsiClass containingClass = field.getContainingClass();
                if (containingClass == null
                        || !GRAVITY_CLASS.equals(containingClass.getQualifiedName())) {
                    return;
                }
                int minSdk = context.getMainProject().getMinSdkVersion();
                String message =
                        "Using `Gravity."
                                + name
                                + "` is only supported on API 16 and higher "
                                + "(current minSdkVersion is "
                                + minSdk
                                + ").";
                LintMap data = LintMap.builder().put(API_KEY, 16).build();
                context.report(ISSUE, node, context.getLocation(node), message, data);
            }
        };
    }
}