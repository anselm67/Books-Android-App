package com.anselm.books

import java.math.BigInteger
import java.security.MessageDigest

class MD5 {

    companion object {
        private fun dash(md5: String): String {
            return "${md5.substring(0, 8)}-${md5.substring(8, 12)}" +
                    "-${md5.substring(12, 16)}-${md5.substring(16, 20)}" +
                    "-${md5.substring(20)}"
        }

        // Produces a 32 letters md5 formatted as 009076B2-D8AC-9686-5741-481F57ED8422
        fun from(input: String): String {
            val md = MessageDigest.getInstance("MD5")
            val digest = BigInteger(1, md.digest(input.toByteArray()))
                .toString(16).padStart(32, '0').uppercase()
            return dash(digest)
        }
    }
}