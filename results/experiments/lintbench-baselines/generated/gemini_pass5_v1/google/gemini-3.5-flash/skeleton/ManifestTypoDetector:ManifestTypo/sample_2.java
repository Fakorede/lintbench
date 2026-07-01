package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    private static final java.util.Set<String> VALID_TAGS = new java.util.HashSet<>(java.util.Arrays.asList(
            "manifest",
            "application",
            "activity",
            "activity-alias",
            "service",
            "receiver",
            "provider",
            "uses-permission",
            "uses-permission-sdk-23",
            "permission",
            "permission-group",
            "permission-tree",
            "uses-sdk",
            "uses-configuration",
            "uses-feature",
            "supports-screens",
            "compatible-screens",
            "supports-gl-texture",
            "meta-data",
            "grant-uri-permission",
            "path-permission",
            "intent-filter",
            "action",
            "category",
            "data",
            "instrumentation",
            "uses-library",
            "queries",
            "profileable",
            "property",
            "sdk-library",
            "uses-native-library",
            "processes",
            "deny-permission",
            "original-package",
            "protected-broadcast",
            "adopt-permissions",
            "uses-split"
    ));

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "This check looks through the manifest, and if it finds any tags that look like likely misspellings, they are flagged.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (VALID_TAGS.contains(tagName)) {
            return;
        }

        String closest = null;
        int minDistance = Integer.MAX_VALUE;

        for (String validTag : VALID_TAGS) {
            int distance = getLevenshteinDistance(tagName, validTag);
            if (distance < minDistance) {
                minDistance = distance;
                closest = validTag;
            }
        }

        boolean isTypo = false;
        if (minDistance == 1 && tagName.length() >= 3) {
            isTypo = true;
        } else if (minDistance == 2 && tagName.length() >= 5) {
            isTypo = true;
        }

        if (isTypo && closest != null) {
            String message = String.format("Possible typo in manifest tag: \"%s\". Did you mean \"%s\"?", tagName, closest);
            context.report(ISSUE, element, context.getNameLocation(element), message);
        }
    }

    private static int getLevenshteinDistance(String s, String t) {
        if (s == null || t == null) {
            return Integer.MAX_VALUE;
        }
        int n = s.length();
        int m = t.length();
        if (n == 0) return m;
        if (m == 0) return n;

        int[] p = new int[n + 1];
        int[] d = new int[n + 1];
        int[] _d;

        for (int i = 0; i <= n; i++) {
            p[i] = i;
        }

        for (int j = 1; j <= m; j++) {
            char tj = t.charAt(j - 1);
            d[0] = j;
            for (int i = 1; i <= n; i++) {
                int cost = s.charAt(i - 1) == tj ? 0 : 1;
                d[i] = Math.min(Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1] + cost);
            }
            _d = p;
            p = d;
            d = _d;
        }
        return p[n];
    }
}