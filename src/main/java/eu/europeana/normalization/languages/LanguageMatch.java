package eu.europeana.normalization.languages;

/**
 * Instances of this class denote a match result of language matching. It contains the source text, the found matches (language
 * codes) and the type of match that was made. The language code depends on the {@link LanguagesVocabulary} that was configured
 * for the {@link LanguageMatcher}.
 */
public record LanguageMatch(String input, String match, Type type) {

  /**
   * This enum lists the possible match types.
   */
  public enum Type {

    /**
     * Indicates that the match was made by finding a code in the input.
     **/
    CODE_MATCH,

    /**
     * Indicates that the match was made by finding a label in the input.
     **/
    LABEL_MATCH,

    /**
     * Indicates that no match could be made and the match result will be null.
     **/
    NO_MATCH
  }
}

