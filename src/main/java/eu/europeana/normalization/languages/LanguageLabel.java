package eu.europeana.normalization.languages;

/**
 * This class represents a label attached to a language. A language label is a name that a language can have in a given (possibly
 * different) language and script. For instance, the English language could have a label "Engels" where the language is Dutch and
 * the script is Latin.
 *
 * @param label Label
 * @param language Language
 * @param script String script
 */
public record LanguageLabel(String label, String language, String script) {
}
