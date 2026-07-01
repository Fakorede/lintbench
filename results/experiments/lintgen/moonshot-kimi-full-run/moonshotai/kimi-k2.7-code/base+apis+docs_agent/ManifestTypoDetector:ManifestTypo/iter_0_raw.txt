package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Likely misspelled manifest tag",
                    "This tag looks like a misspelling of a valid Android manifest tag. "
                            + "Misspelled tags are ignored by the framework and can lead to "
                            + "missing functionality.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE));

    private static final List<String> KNOWN_TAGS =
            Arrays.asList(
                    "action",
                    "activity",
                    "activity-alias",
                    "application",
                    "attribution",
                    "category",
                    "compatible-screens",
                    "component",
                    "data",
                    "eat-comment",
                    "feature-group",
                    "grant-uri-permission",
                    "instrumentation",
                    "intent",
                    "intent-filter",
                    "layout",
                    "manifest",
                    "meta-data",
                    "overlay",
                    "package",
                    "path-permission",
                    "permission",
                    "permission-group",
                    "permission-tree",
                    "profileable",
                    "property",
                    "provider",
                    "queries",
                    "receiver",
                    "restrict-update",
                    "service",
                    "supports-gl-texture",
                    "supports-screens",
                    "uses-configuration",
                    "uses-feature",
                    "uses-library",
                    "uses-native-library",
                    "uses-package",
                    "uses-permission",
                    "uses-permission-sdk-23",
                    "uses-sdk",
                    "uses-split");

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (!SdkConstants.ANDROID_MANIFEST_XML.equals(context.file.getName())) {
            return;
        }

        String tag = element.getTagName();
        if (tag.contains(":")) {
            return;
        }

        String lowerTag = tag.toLowerCase(Locale.US);
        if (KNOWN_TAGS.contains(lowerTag)) {
            return;
        }

        String closest = null;
        int minDistance = Integer.MAX_VALUE;
        for (String known : KNOWN_TAGS) {
            int distance = editDistance(lowerTag, known);
            if (distance < minDistance) {
                minDistance = distance;
                closest = known;
            }
        }

        if (closest != null && minDistance <= 1 && lowerTag.length() > 2) {
            String message =
                    String.format("Suspicious tag `%1$s`; did you mean `%2$s`?", tag, closest);
            LintFix fix =
                    LintFix.create()
                            .name("Change to " + closest)
                            .replace()
                            .text(tag)
                            .with(closest)
                            .range(context.getNameLocation(element))
                            .build();
            context.report(ISSUE, element, context.getNameLocation(element), message, fix);
        }
    }

    private static int editDistance(String s, String t) {
        int n = s.length();
        int m = t.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }

        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char sc = s.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = sc == t.charAt(j - 1) ? 0 : 1;
                curr[j] =
                        Math.min(
                                Math.min(curr[j - 1] + 1, prev[j] + 1),
                                prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[m];
    }
}