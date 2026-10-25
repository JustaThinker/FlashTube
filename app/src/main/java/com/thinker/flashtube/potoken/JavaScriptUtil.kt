package com.thinker.flashtube.potoken

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses the raw challenge data obtained from the Create endpoint and returns an object that can be
 * embedded in a JavaScript snippet.
 */
fun parseChallengeData(rawChallengeData: String): String {
    val scrambled = JSONArray(rawChallengeData)

    val challengeData = if (scrambled.length() > 1 && scrambled.optString(1).isNotEmpty()) {
        val descrambled = descramble(scrambled.getString(1))
        JSONArray(descrambled)
    } else {
        scrambled.getJSONArray(0)
    }

    val messageId = challengeData.getString(0)
    val interpreterHash = challengeData.getString(3)
    val program = challengeData.getString(4)
    val globalName = challengeData.getString(5)
    val clientExperimentsStateBlob = challengeData.getString(7)

    var privateDoNotAccessOrElseSafeScriptWrappedValue: Any? = JSONObject.NULL
    var privateDoNotAccessOrElseTrustedResourceUrlWrappedValue: Any? = JSONObject.NULL

    if (!challengeData.isNull(1)) {
        val arr = challengeData.optJSONArray(1)
        if (arr != null) {
            for (i in 0 until arr.length()) {
                if (arr.get(i) is String) {
                    privateDoNotAccessOrElseSafeScriptWrappedValue = arr.getString(i)
                    break
                }
            }
        }
    }
    if (!challengeData.isNull(2)) {
        val arr = challengeData.optJSONArray(2)
        if (arr != null) {
            for (i in 0 until arr.length()) {
                if (arr.get(i) is String) {
                    privateDoNotAccessOrElseTrustedResourceUrlWrappedValue = arr.getString(i)
                    break
                }
            }
        }
    }

    val interp = JSONObject()
    interp.put("privateDoNotAccessOrElseSafeScriptWrappedValue", privateDoNotAccessOrElseSafeScriptWrappedValue)
    interp.put("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", privateDoNotAccessOrElseTrustedResourceUrlWrappedValue)

    val result = JSONObject()
    result.put("messageId", messageId)
    result.put("interpreterJavascript", interp)
    result.put("interpreterHash", interpreterHash)
    result.put("program", program)
    result.put("globalName", globalName)
    result.put("clientExperimentsStateBlob", clientExperimentsStateBlob)

    return result.toString()
}

/**
 * Parses the raw integrity token data obtained from the GenerateIT endpoint to a JavaScript
 * `Uint8Array` that can be embedded directly in JavaScript code, and a [Long] representing the
 * duration of this token in seconds.
 */
fun parseIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
    val integrityTokenData = JSONArray(rawIntegrityTokenData)
    return base64ToU8(integrityTokenData.getString(0)) to integrityTokenData.getLong(1)
}

/**
 * Converts a string (usually the identifier used as input to `obtainPoToken`) to a JavaScript
 * `Uint8Array` that can be embedded directly in JavaScript code.
 */
fun stringToU8(identifier: String): String {
    return newUint8Array(identifier.toByteArray(Charsets.UTF_8))
}

/**
 * Takes a poToken encoded as a sequence of bytes represented as integers separated by commas (e.g.
 * "97,98,99" would be "abc"), which is the output of `Uint8Array::toString()` in JavaScript, and
 * converts it to the specific base64 representation for poTokens.
 */
fun u8ToBase64(poToken: String): String {
    val bytes = poToken.split(",")
        .filter { it.isNotBlank() }
        .map { it.trim().toInt().toByte() }
        .toByteArray()
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

/** Takes the scrambled challenge, decodes it from base64, adds 97 to each byte. */
private fun descramble(scrambledChallenge: String): String {
    val bytes = base64ToByteArray(scrambledChallenge)
    val descrambled = ByteArray(bytes.size) { (bytes[it] + 97).toByte() }
    return String(descrambled, Charsets.UTF_8)
}

/**
 * Decodes a base64 string encoded in the specific base64 representation used by YouTube, and
 * returns a JavaScript `Uint8Array` that can be embedded directly in JavaScript code.
 */
private fun base64ToU8(base64: String): String {
    return newUint8Array(base64ToByteArray(base64))
}

private fun newUint8Array(contents: ByteArray): String {
    return "new Uint8Array([" + contents.joinToString(separator = ",") { (it.toInt() and 0xFF).toString() } + "])"
}

/** Decodes a base64 string encoded in the specific base64 representation used by YouTube. */
private fun base64ToByteArray(base64: String): ByteArray {
    val base64Mod = base64.replace('-', '+').replace('_', '/').replace('.', '=')
    return Base64.decode(base64Mod, Base64.DEFAULT)
}
