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
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "This check looks through the manifest, and if it finds any tags "
                            + "that look like likely misspellings, they are flagged.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    new Implementation(
                            ManifestTypoDetector.class, Scope.MANIFEST_SCOPE));

    private static final String[] VALID_TAGS = {
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
        "instrumentation",
        "intent-filter",
        "action",
        "category",
        "data",
        "meta-data",
        "grant-uri-permission",
        "path-permission",
        "queries",
        "package",
        "profileable",
        "property",
        "uses-native-library",
        "uses-library"
    };

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        for (String validTag : VALID_TAGS) {
            if (validTag.equals(tagName)) {
                return;
            }
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

        int threshold = 2;
        if (tagName.length() <= 4) {
            threshold = 1;
        } else if (tagName.length() > 10) {
            threshold = 3;
        }

        if (minDistance > 0 && minDistance <= threshold && closest != null) {
            String message = String.format("Possible typo in element tag \"%1$s\"; did you mean \"%2$s\"?", tagName, closest);
            context.report(ISSUE, element, context.getNameLocation(element), message);
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
        char tj;
        int cost;
        for (i = 0; i <= n; i++) {
            p[i] = i;
        }
        for (j = 1; j <= m; j++) {
            tj = t.charAt(j - 1);
            d[0] = j;
            for (i = 1; i <= n; i++) {
                cost = s.charAt(i - 1) == tj ? 0 : 1;
                d[i] = Math.min(Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1] + cost);
            }
            _d = p;
            p = d;
            d = _d;
        }
        return p[n];
    }
}