package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "ManifestTypo",
        "Typos in manifest tags",
        "This check looks through the manifest, and if it finds any tags that look " +
        "like likely misspellings, they are flagged.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(
            ManifestTypoDetector.class,
            Scope.MANIFEST_SCOPE
        )
    );

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
        "manifest", "application", "activity", "activity-alias", "service", "receiver",
        "provider", "uses-permission", "uses-permission-sdk-23", "permission",
        "permission-group", "permission-tree", "uses-sdk", "instrumentation",
        "uses-library", "library", "meta-data", "intent-filter", "action",
        "category", "data", "grant-uri-permission", "path-permission", "queries",
        "package", "profileable", "property", "uses-native-library",
        "compatible-screens", "supports-screens", "supports-gl-texture",
        "uses-configuration", "uses-feature", "sdk-library", "attribution",
        "original-package", "protected-broadcast", "adopt-permissions"
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

        String bestMatch = null;
        int minDistance = Integer.MAX_VALUE;

        for (String validTag : VALID_TAGS) {
            int dist = getLevenshteinDistance(tagName, validTag);
            if (dist < minDistance) {
                minDistance = dist;
                bestMatch = validTag;
            }
        }

        int threshold = tagName.length() <= 4 ? 1 : 2;
        if (bestMatch != null && minDistance <= threshold) {
            String message = String.format("Has typo in manifest tag: \"%s\" is likely a typo of \"%s\"", tagName, bestMatch);
            LintFix fix = fix().replace().text(tagName).with(bestMatch).build();
            context.report(ISSUE, element, context.getNameLocation(element), message, fix);
        }
    }

    private static int getLevenshteinDistance(String s, String t) {
        if (s == null || t == null) {
            throw new IllegalArgumentException("Strings must not be null");
        }
        int n = s.length();
        int m = t.length();
        if (n == 0) {
            return m;
        } else if (m == 0) {
            return n;
        }
        if (n > m) {
            String tmp = s;
            s = t;
            t = tmp;
            n = m;
            m = t.length();
        }
        int[] p = new int[n + 1];
        int[] d = new int[n + 1];
        int[] _d;
        int i;
        int j;
        char t_j;
        int cost;
        for (i = 0; i <= n; i++) {
            p[i] = i;
        }
        for (j = 1; j <= m; j++) {
            t_j = t.charAt(j - 1);
            d[0] = j;
            for (i = 1; i <= n; i++) {
                cost = s.charAt(i - 1) == t_j ? 0 : 1;
                d[i] = Math.min(Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1] + cost);
            }
            _d = p;
            p = d;
            d = _d;
        }
        return p[n];
    }
}