package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_MANIFEST;
import static com.android.SdkConstants.TAG_USES_FEATURE;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue MISSING_LEANBACK_SUPPORT = Issue.create(
            "MissingLeanbackSupport",
            "Missing Leanback Support",
            "The manifest should declare the use of the Leanback user interface required by Android TV.\n"
                    + "To fix this, add `<uses-feature android:name=\"android.software.leanback\" "
                    + "android:required=\"false\" />` to your manifest.\n\n"
                    + "Reference documentation:\n"
                    + "https://developer.android.com/training/tv/start/start.html#leanback-req",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String LEANBACK_LAUNCHER_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String CATEGORY_TAG = "category";

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_MANIFEST);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (!hasLeanbackLauncher(element)) {
            return;
        }

        if (hasLeanbackFeature(element)) {
            return;
        }

        LintFix fix = LintFix.create()
                .name("Add leanback uses-feature")
                .replace()
                .text(">")
                .with(">\n    <uses-feature android:name=\"android.software.leanback\"\n"
                        + "        android:required=\"false\" />")
                .build();

        context.report(
                MISSING_LEANBACK_SUPPORT,
                element,
                context.getLocation(element),
                "Missing `android.software.leanback` uses-feature declaration required by Android TV",
                fix);
    }

    private static boolean hasLeanbackLauncher(@NotNull Element manifest) {
        Element application = getFirstChildByTag(manifest, TAG_APPLICATION);
        if (application == null) {
            return false;
        }
        for (Element activity : getChildrenByTag(application, TAG_ACTIVITY)) {
            for (Element intentFilter : getChildrenByTag(activity, TAG_INTENT_FILTER)) {
                for (Element category : getChildrenByTag(intentFilter, CATEGORY_TAG)) {
                    String name = category.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasLeanbackFeature(@NotNull Element manifest) {
        for (Element usesFeature : getChildrenByTag(manifest, TAG_USES_FEATURE)) {
            String name = usesFeature.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (LEANBACK_FEATURE.equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Element getFirstChildByTag(@NotNull Element parent, @NotNull String tagName) {
        List<Element> children = getChildrenByTag(parent, tagName);
        return children.isEmpty() ? null : children.get(0);
    }

    @NotNull
    private static List<Element> getChildrenByTag(@NotNull Element parent, @NotNull String tagName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && tagName.equals(child.getNodeName())) {
                result.add((Element) child);
            }
        }
        return result;
    }
}