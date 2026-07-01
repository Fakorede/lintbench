package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.util.UastExpressionUtils;
import org.w3c.dom.Element;

public class RequiredAttributeDetector extends LayoutDetector
        implements SourceCodeScanner, XmlScanner {

    private static final String LAYOUT_PREFIX = "layout/";
    private static final String DOT_XML = ".xml";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    RequiredAttributeDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE),
                    Scope.RESOURCE_FILE_SCOPE,
                    Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "RequiredSize",
                            "Missing layout_width or layout_height attributes",
                            "Most layout tags and widgets should specify an explicit `layout_width`"
                                    + " and `layout_height` attribute. These can also be supplied via"
                                    + " styles. If they are not specified, an exception is thrown at"
                                    + " runtime. GridLayout is a special case and does not require"
                                    + " these attributes.",
                            Category.CORRECTNESS,
                            8,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    private Map<String, List<MissingSize>> mMissing;
    private Map<String, List<JavaReference>> mReferences;

    public RequiredAttributeDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if ("include".equals(tagName)
                || "merge".equals(tagName)
                || "layout".equals(tagName)
                || "requestFocus".equals(tagName)
                || tagName.endsWith("GridLayout")) {
            return;
        }

        boolean missingWidth = !element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean missingHeight = !element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (!missingWidth && !missingHeight) {
            return;
        }

        String layoutName = getLayoutName(context);
        if (mMissing == null) {
            mMissing = new HashMap<>();
        }
        List<MissingSize> list = mMissing.get(layoutName);
        if (list == null) {
            list = new ArrayList<>();
            mMissing.put(layoutName, list);
        }
        boolean alreadyStored = false;
        for (MissingSize existing : list) {
            if (existing.element == element) {
                alreadyStored = true;
                break;
            }
        }
        if (!alreadyStored) {
            list.add(new MissingSize(context, element));
        }

        if (missingWidth) {
            reportMissing(context, element, ATTR_LAYOUT_WIDTH, tagName);
        }
        if (missingHeight) {
            reportMissing(context, element, ATTR_LAYOUT_HEIGHT, tagName);
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("inflate");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, "android.view.LayoutInflater")) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        int argCount = args.size();
        if (argCount < 2 || argCount > 3) {
            return;
        }

        UExpression first = args.get(0);
        String resource = ResourceEvaluator.getResource(first, true);
        if (resource == null) {
            return;
        }
        if (resource.startsWith("@")) {
            resource = resource.substring(1);
        }
        if (!resource.startsWith(LAYOUT_PREFIX)) {
            return;
        }
        String layoutName = resource.substring(LAYOUT_PREFIX.length());

        UExpression second = args.get(1);
        if (UastExpressionUtils.isNull(second)) {
            return;
        }

        if (mReferences == null) {
            mReferences = new HashMap<>();
        }
        List<JavaReference> list = mReferences.get(layoutName);
        if (list == null) {
            list = new ArrayList<>();
            mReferences.put(layoutName, list);
        }
        list.add(new JavaReference(node, context));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mMissing == null || mReferences == null) {
            return;
        }

        for (Map.Entry<String, List<MissingSize>> entry : mMissing.entrySet()) {
            List<JavaReference> references = mReferences.get(entry.getKey());
            if (references == null) {
                continue;
            }

            List<MissingSize> missing = entry.getValue();
            for (JavaReference ref : references) {
                Location location = ref.context.getLocation(ref.call);
                if (!missing.isEmpty()) {
                    MissingSize first = missing.get(0);
                    Location secondary = first.context.getLocation(first.element);
                    secondary.setMessage("This layout is missing required size attributes");
                    location.setSecondary(secondary);
                }

                ref.context.report(
                        ISSUE,
                        location,
                        "The inflated layout is missing required layout_width or layout_height"
                                + " attributes");
            }
        }
    }

    private static String getLayoutName(@NonNull XmlContext context) {
        String name = context.file.getName();
        if (name.endsWith(DOT_XML)) {
            name = name.substring(0, name.length() - DOT_XML.length());
        }
        return name;
    }

    private static void reportMissing(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull String attribute,
            @NonNull String tagName) {
        String message = tagName + " is missing the required " + attribute + " attribute";
        context.report(ISSUE, element, context.getLocation(element), message);
    }

    private static class MissingSize {
        final XmlContext context;
        final Element element;

        MissingSize(@NonNull XmlContext context, @NonNull Element element) {
            this.context = context;
            this.element = element;
        }
    }

    private static class JavaReference {
        final UCallExpression call;
        final JavaContext context;

        JavaReference(@NonNull UCallExpression call, @NonNull JavaContext context) {
            this.call = call;
            this.context = context;
        }
    }
}