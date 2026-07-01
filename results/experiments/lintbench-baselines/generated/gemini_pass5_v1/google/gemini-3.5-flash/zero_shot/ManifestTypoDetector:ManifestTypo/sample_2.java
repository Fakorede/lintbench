package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
        "manifest",
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
        "application",
        "activity",
        "activity-alias",
        "service",
        "receiver",
        "provider",
        "intent-filter",
        "action",
        "category",
        "data",
        "meta-data",
        "grant-uri-permission",
        "path-permission",
        "instrumentation",
        "uses-library",
        "queries",
        "package",
        "profileable",
        "property",
        "attribution",
        "sdk-library"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if (VALID_TAGS.contains(tagName)) {
            return;
        }

        String suggestion = null;
        int minDistance = Integer.MAX_VALUE;
        for (String validTag : VALID_TAGS) {
            int dist = getLevenshteinDistance(tagName, validTag);
            if (dist < minDistance) {
                minDistance = dist;
                suggestion = validTag;
            }
        }

        int maxAllowedDistance = tagName.length() <= 4 ? 1 : 2;
        if (minDistance <= maxAllowedDistance && suggestion != null) {
            String message = String.format("Possible typo in tag \"%1$s\"; did you mean \"%2$s\"?", tagName, suggestion);
            LintFix fix = fix().replace().text(tagName).with(suggestion).build();
            context.report(ISSUE, element, context.getNameLocation(element), message, fix);
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
            int[] placeholder = p;
            p = d;
            d = placeholder;
        }
        return p[n];
    }
}