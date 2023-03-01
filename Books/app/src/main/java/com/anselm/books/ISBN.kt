package com.anselm.books

class ISBN {

    companion object {
        private fun digit(c: Char): Int {
            return c.digitToInt()
        }

        private fun checksum(s12: String): Char {
            check(s12.length == 12) { "Argument string should have exactly 12 characters." }
            val sum1 = arrayListOf(0, 2, 4, 6, 8, 10).sumOf { digit(s12[it]) }
            val sum2 = 3 * arrayListOf(1, 3, 5, 7, 9, 11).sumOf { digit(s12[it]) }
            val checksum = (sum1 + sum2) % 10
            return if (checksum == 0) '0' else ('0' + 10 - checksum)
        }

        fun isValidEAN13(isbn: String): Boolean {
            if (isbn.length != 13) {
                return false
            }
            // Verifies the checksum.
            return checksum(isbn.substring(0..11)) == isbn[12]
        }

        // https://isbn-information.com/convert-isbn-10-to-isbn-13.html
        fun toISBN13(isbn10: String): String? {
            if (isbn10.length != 10) {
                return null
            }
            val s = "978" + isbn10.substring(0..8)
            return s + checksum(s)
        }

        fun isValidEAN(s: String): Boolean {
            return isValidEAN13(s) || toISBN13(s) != null
        }
    }
}