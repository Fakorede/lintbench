class InternalInsetResourceDetector : Detector(), XmlScanner, SourceCodeScanner {
    override fun getApplicableAttributes() = ALL
    override fun visitAttribute(context, attribute) { ... }
    override fun getApplicableReferenceNames() = INSETS
    override fun visitReference(context, reference, referenced) { ... }
    companion object { ... }
}