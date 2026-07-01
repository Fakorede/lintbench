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
            "uses-library",
            "queries",
            "profileable",
            "property",
            "instrumentation",
            "original-package",
            "protected-broadcast",
            "adopt-permissions",
            "uses-native-library",
            "sdk-library"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (VALID_TAGS.contains(tagName)) {
            return;
        }

        String closest = null;
        int minDistance = Integer.MAX_VALUE;
        for (String valid : VALID_TAGS) {
            int dist = getEditDistance(tagName, valid);
            if (dist < minDistance) {
                minDistance = dist;
                closest = valid;
            }
        }

        if (closest != null && minDistance <= 2 && minDistance < (tagName.length() + 1) / 2) {
            String message = String.format("Possible typo in element tag: \"%1$s\" (did you mean \"%2$s\"?)", tagName, closest);
            context.report(ISSUE, element, context.getNameLocation(element), message);
        }
    }

    private static int getEditDistance(String s, String t) {
        int m = s.length();
        int n = t.length();
        int[][] d = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            d[0][j] = j;
        }
        for (int j = 1; j <= n; j++) {
            for (int i = 1; i <= m; i++) {
                if (s.charAt(i - 1) == t.charAt(j - 1)) {
                    d[i][j] = d[i - 1][j - 1];
                } else {
                    d[i][j] = Math.min(Math.min(
                            d[i - 1][j] + 1,
                            d[i][j - 1] + 1),
                            d[i - 1][j - 1] + 1);
                }
            }
        }
        return d[m][n];
    }
}