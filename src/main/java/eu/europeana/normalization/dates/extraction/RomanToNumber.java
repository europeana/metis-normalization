package eu.europeana.normalization.dates.extraction;

import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Auxiliary class for converting roman numerals to decimal.
 */
public final class RomanToNumber {

  @Getter
  @AllArgsConstructor
  enum RomanCharacter {
    I(1),
    V(5),
    X(10),
    L(50),
    C(100),
    D(500),
    M(1000);

    private final int value;
  }

  private RomanToNumber() {
  }

  /**
   * Converts a previously validated Roman numeral to its decimal value.
   * <p>Conversion is case-insensitive. This method performs numeric conversion only; the caller must validate Roman numeral
   * syntax before invoking it.</p>
   *
   * @param value a non-empty, syntactically valid Roman numeral
   * @return the decimal value
   */
  public static int romanToDecimal(String value) {
    int result = 0;
    int previousValue = 0;
    final String upperCasedValue = value.toUpperCase(Locale.ROOT);
    for (int i = 0; i < upperCasedValue.length(); i++) {
      final int currentValue = RomanCharacter.valueOf(String.valueOf(upperCasedValue.charAt(i))).getValue();
      result += currentValue;
      if (currentValue > previousValue) {
        // The previous value was already added; subtract it twice to make it subtractive.
        result -= 2 * previousValue;
      }
      previousValue = currentValue;
    }

    return result;
  }
}
