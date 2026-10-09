package eu.europeana.normalization.dates.edtf;


import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Enum that indicates what type of date is in an interval.
 * <p>
 * A date in an interval can be:
 *   <ul>
 *     <li>{@link #DECLARED}. Indicates whether there is a value representing an actual date. Not meant to be (de)serialized</li>
 *     <li>{@link #OPEN}. Indicates whether the date is open, represented by {@code ..} (e.g., if the input EDTF-compliant date interval string was equal to
 *         {@code 1900/..}).</li>
 *     <li>{@link #UNKNOWN} Indicates whether the date is unknown, represented by an empty string ''(deserialization) and {@code ..}(serialization
 *          (e.g., if the input EDTF-compliant date interval string was equal to {@code 1900/}).</li>
 *   </ul>
 * </p>
 */
@Getter
@AllArgsConstructor
public enum DateBoundaryType {
  DECLARED(null, null),
  OPEN(DateBoundaryType.DEFAULT_OPEN_STRING, DateBoundaryType.DEFAULT_OPEN_STRING),
  UNKNOWN("", DateBoundaryType.DEFAULT_OPEN_STRING);

  public static final String DEFAULT_OPEN_STRING = "..";
  private final String deserializedRepresentation;
  private final String serializedRepresentation;
}
