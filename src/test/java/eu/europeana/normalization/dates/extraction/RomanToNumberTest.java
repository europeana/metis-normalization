package eu.europeana.normalization.dates.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.of;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit test for {@link RomanToNumber} class
 */
class RomanToNumberTest {

  private static Stream<Arguments> numberData() {
    return Stream.of(
        of("I", 1),
        of("II", 2),
        of("III", 3),
        of("IV", 4),
        of("V", 5),
        of("VI", 6),
        of("VII", 7),
        of("VIII", 8),
        of("IX", 9),
        of("X", 10),
        of("XI", 11),
        of("XII", 12),
        of("XIII", 13),
        of("XIV", 14),
        of("XV", 15),
        of("XVI", 16),
        of("XVII", 17),
        of("XVIII", 18),
        of("XIX", 19),
        of("XX", 20),
        of("XXI", 21),
        of("XXII", 22),
        of("XXIII", 23),
        of("XXIV", 24),
        of("XXV", 25),
        of("XXVI", 26),
        of("XXVII", 27),
        of("XXVIII", 28),
        of("XXIX", 29),
        of("XXX", 30),
        of("XXXI", 31),
        of("XXXIV", 34),
        of("XXXV", 35),
        of("XL", 40),
        of("XLI", 41),
        of("XLII", 42),
        of("XLIII", 43),
        of("XLIV", 44),
        of("XLV", 45),
        of("XLVI", 46),
        of("XLVII", 47),
        of("XLVIII", 48),
        of("XLIX", 49),
        of("L", 50),
        of("LI", 51),
        of("LII", 52),
        of("XC", 90),
        of("C", 100),
        of("CD", 400),
        of("D", 500),
        of("CM", 900),
        of("M", 1000),
        of("MCMLXXVI", 1976),
        of("MCMXCVIII", 1998),
        of("MMXXII", 2022),
        of("MMMDCCCLXXXVIII", 3888),
        of("MMMCMXCIX", 3999),
        of("i", 1),
        of("iv", 4),
        of("xIv", 14),
        of("mCmXcIx", 1999)
    );
  }

  @ParameterizedTest
  @MethodSource("numberData")
  void romanToDecimal(String romanNumber, int expectedNumber) {
    assertEquals(expectedNumber, RomanToNumber.romanToDecimal(romanNumber));
  }
}
