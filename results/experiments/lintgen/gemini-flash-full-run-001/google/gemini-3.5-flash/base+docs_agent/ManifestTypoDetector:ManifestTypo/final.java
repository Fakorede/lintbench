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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Typos in manifest tags",
            "This check looks through the manifest, and if it finds any tags " +
            "that look like likely misspellings, they are flagged.",
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
            "application",
            "activity",
            "service",
            "receiver",
            "provider",
            "activity-alias",
            "uses-library",
            "uses-permission",
            "uses-permission-sdk-23",
            "permission",
            "permission-group",
            "permission-tree",
            "uses-sdk",
            "instrumentation",
            "uses-configuration",
            "uses-feature",
            "supports-screens",
            "compatible-screens",
            "supports-gl-texture",
            "meta-data",
            "intent-filter",
            "action",
            "category",
            "data",
            "grant-uri-permission",
            "path-permission",
            "queries",
            "profileable",
            "property",
            "uses-native-library",
            "sdk-library"
    ));

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root != null) {
            checkElement(context, root);
        }
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (!VALID_TAGS.contains(tag)) {
            String closest = null;
            int minDistance = Integer.MAX_VALUE;
            for (String valid : VALID_TAGS) {
                int distance = getLevenshteinDistance(tag, valid);
                if (distance < minDistance) {
                    minDistance = distance;
                    closest = valid;
                }
            }
            if (closest != null && minDistance > 0 && minDistance <= 2) {
                String message = String.format("Suspicious tag name `%1$s`; did you mean `%2$s`?", tag, closest);
                context.report(ISSUE, element, context.getNameLocation(element), message);
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child);
            }
        }
    }

    private static int getLevenshteinDistance(String s, String t) {
        if (s == null || t == null) {
            return Integer.MAX_VALUE;
        }
        int n = s.length();
        int m = t.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }
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