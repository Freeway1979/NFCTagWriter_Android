package com.liu.andy.demo.nfctagwriter.nfc

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresPermission
import java.lang.reflect.Array
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Arrays
import java.util.Date
import java.util.Locale
import kotlin.math.min

class NTagUtils {
    private fun byteArrayLength3InversedToInt(data: ByteArray): Int {
        return (data[2].toInt() and 0xff) shl 16 or ((data[1].toInt() and 0xff) shl 8) or (data[0].toInt() and 0xff)
    }

    companion object {
        // this checks if a String sequence is a valid hext string
        fun isHexNumeric(hexStr: String): Boolean {
            if (hexStr.isEmpty() ||
                (hexStr[0] != '-' && (hexStr[0].digitToIntOrNull(16) ?: -1) == -1)
            ) return false
            if (hexStr.length == 1 && hexStr[0] == '-') return false

            for (i in 1..<hexStr.length) if ((hexStr[i]
                    .digitToIntOrNull(16) ?: -1) == -1
            ) return false
            return true
        }

        fun removeAllNonAlphaNumeric(s: String?): String? {
            if (s == null) {
                return null
            }
            return s.replace("[^A-Za-z0-9]".toRegex(), "")
        }

        // position is 0 based starting from right to left
        fun setBitInByte(input: Byte, pos: Int): Byte {
            return (input.toInt() or (1 shl pos)).toByte()
        }

        // position is 0 based starting from right to left
        fun unsetBitInByte(input: Byte, pos: Int): Byte {
            return (input.toInt() and (1 shl pos).inv()).toByte()
        }

        // https://stackoverflow.com/a/29396837/8166854
        fun testBit(b: Byte, n: Int): Boolean {
            val mask = 1 shl n // equivalent of 2 to the nth power
            return (b.toInt() and mask) != 0
        }

        // https://stackoverflow.com/a/29396837/8166854
        fun testBit(array: ByteArray, n: Int): Boolean {
            val index = n ushr 3 // divide by 8
            val mask = 1 shl (n and 7) // n modulo 8
            return (array[index].toInt() and mask) != 0
        }

        fun printData(dataName: String?, data: ByteArray?): String {
            val dataLength: Int
            var dataString = ""
            if (data == null) {
                dataLength = 0
                dataString = "IS NULL"
            } else {
                dataLength = data.size
                dataString = bytesToHex(data)
            }
            val sb = StringBuilder()
            sb
                .append(dataName)
                .append(" length: ")
                .append(dataLength)
                .append(" data: ")
                .append(dataString)
            return sb.toString()
        }

        fun bytesToHex(bytes: ByteArray?): String {
            if (bytes == null) return ""
            val result = StringBuffer()
            for (b in bytes) result.append(((b.toInt() and 0xff) + 0x100).toString(16).substring(1))
            return result.toString()
        }

        fun bytesToHexNpe(bytes: ByteArray?): String {
            if (bytes == null) return ""
            val result = StringBuffer()
            for (b in bytes) result.append(((b.toInt() and 0xff) + 0x100).toString(16).substring(1))
            return result.toString()
        }

        fun bytesToHexNpeUpperCase(bytes: ByteArray?): String {
            if (bytes == null) return ""
            val result = StringBuffer()
            for (b in bytes) result.append(((b.toInt() and 0xff) + 0x100).toString(16).substring(1))
            return result.toString().uppercase(Locale.getDefault())
        }

        fun bytesToHexNpeUpperCaseBlank(bytes: ByteArray?): String {
            if (bytes == null) return ""
            val result = StringBuffer()
            for (b in bytes) result.append(((b.toInt() and 0xff) + 0x100).toString(16).substring(1))
                .append(" ")
            return result.toString().uppercase(Locale.getDefault())
        }

        fun byteToHex(input: Byte?): String {
            return String.format("%02X", input)
            //return String.format("0x%02X", input);
        }

        fun hexToBytes(s: String): ByteArray =
            s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        fun ByteArray.toHex(): String =
            joinToString("") { "%02X".format(it) }


        fun hexStringToByteArray(s: String): ByteArray? {
            try {
                val len = s.length
                val data = ByteArray(len / 2)
                var i = 0
                while (i < len) {
                    data[i / 2] = ((s[i].digitToIntOrNull(16) ?: (-1 shl 4)) + s[i + 1]
                        .digitToIntOrNull(16)!!).toByte()
                    i += 2
                }
                return data
            } catch (e: Exception) {
                return null
            }
        }

        /**
         * will delete any '-' and " " characters between the hex chars before converting to a byte array
         * @param s
         * @return
         */
        fun hexStringToByteArrayMinus(s: String): ByteArray? {
            val newS = s.replace("-".toRegex(), "").replace(" ".toRegex(), "")
            return hexStringToByteArray(newS)
        }

        fun getDec(bytes: ByteArray): String {
            var result: Long = 0
            var factor: Long = 1
            for (i in bytes.indices) {
                val value = bytes[i].toLong() and 0xffL
                result += value * factor
                factor *= 256L
            }
            return result.toString() + ""
        }

        fun printByteBinary(bytes: Byte): String {
            val data = ByteArray(1)
            data[0] = bytes
            return printByteArrayBinary(data)
        }

        fun printByteArrayBinary(bytes: ByteArray): String {
            var output = ""
            for (b1 in bytes) {
                val s1 = String.format("%8s", Integer.toBinaryString(b1.toInt() and 0xFF))
                    .replace(' ', '0')
                //s1 += " " + Integer.toHexString(b1);
                //s1 += " " + b1;
                output = "$output $s1"
                //System.out.println(s1);
            }
            return output
        }

        /**
         * Reverse a byte Array (e.g. Little Endian -> Big Endian).
         * Hmpf! Java has no Array.reverse(). And I don't want to use
         * Commons.Lang (ArrayUtils) from Apache....
         *
         * @param array The array to reverse (in-place).
         */
        fun reverseByteArrayInPlace(array: ByteArray) {
            for (i in 0..<array.size / 2) {
                val temp = array[i]
                array[i] = array[array.size - i - 1]
                array[array.size - i - 1] = temp
            }
        }


        // converts an int to a 3 byte long array
        fun intTo3ByteArray(value: Int): ByteArray {
            return byteArrayOf(
                (value shr 16).toByte(),
                (value shr 8).toByte(),
                value.toByte()
            )
        }

        // converts an int to a 3 byte long array inversed
        fun intTo3ByteArrayInversed(value: Int): ByteArray {
            return byteArrayOf(
                value.toByte(),
                (value shr 8).toByte(),
                (value shr 16).toByte()
            )
        }

        fun intFrom3ByteArrayInversed(bytes: ByteArray): Int {
            return ((bytes[2].toInt() and 0xFF) shl 16) or
                    ((bytes[1].toInt() and 0xFF) shl 8) or
                    ((bytes[0].toInt() and 0xFF) shl 0)
        }

        fun intFrom3ByteArray(bytes: ByteArray): Int {
            return ((bytes[0].toInt() and 0xFF) shl 16) or
                    ((bytes[1].toInt() and 0xFF) shl 8) or
                    ((bytes[2].toInt() and 0xFF) shl 0)
        }

        // converts an int to a 2 byte long array inversed = LSB
        fun intTo2ByteArrayInversed(value: Int): ByteArray {
            return byteArrayOf(
                value.toByte(),
                (value shr 8).toByte()
            )
        }

        /**
         * Returns a byte array with length = 4
         * @param value
         * @return
         */
        fun intToByteArray4(value: Int): ByteArray {
            return byteArrayOf(
                (value ushr 24).toByte(),
                (value ushr 16).toByte(),
                (value ushr 8).toByte(),
                value.toByte()
            )
        }

        // Little Endian = LSB order
        fun intTo4ByteArrayInversed(myInteger: Int): ByteArray {
            return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(myInteger).array()
        }

        // packing an array of 4 bytes to an int, big endian, minimal parentheses
        // operator precedence: <<, &, |
        // when operators of equal precedence (here bitwise OR) appear in the same expression, they are evaluated from left to right
        fun intFromByteArray(bytes: ByteArray): Int {
            return bytes[0].toInt() shl 24 or ((bytes[1].toInt() and 0xFF) shl 16) or ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        }

        /** packing an array of 4 bytes to an int, big endian, clean code */
        fun intFromByteArrayV3(bytes: ByteArray): Int {
            return ((bytes[0].toInt() and 0xFF) shl 24) or
                    ((bytes[1].toInt() and 0xFF) shl 16) or
                    ((bytes[2].toInt() and 0xFF) shl 8) or
                    ((bytes[3].toInt() and 0xFF) shl 0)
        }

        fun byteArrayLength4NonInversedToInt(bytes: ByteArray): Int {
            return bytes[0].toInt() shl 24 or ((bytes[1].toInt() and 0xFF) shl 16) or ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        }

        //
        fun byteArrayLength4InversedToInt(bytes: ByteArray): Int {
            return bytes[3].toInt() shl 24 or ((bytes[2].toInt() and 0xFF) shl 16) or ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[0].toInt() and 0xFF)
        }

        fun intToUpperNibble(input: Int): Char {
            val hexArray = charArrayOf(
                '0',
                '1',
                '2',
                '3',
                '4',
                '5',
                '6',
                '7',
                '8',
                '9',
                'A',
                'B',
                'C',
                'D',
                'E',
                'F'
            )
            //int v = input & 0xFF; // Cast byte to int, treating as unsigned value
            val v = input
            return hexArray[v ushr 4] // Select hex character from upper nibble
        }

        fun byteToUpperNibble(input: Byte): Char {
            val hexArray = charArrayOf(
                '0',
                '1',
                '2',
                '3',
                '4',
                '5',
                '6',
                '7',
                '8',
                '9',
                'A',
                'B',
                'C',
                'D',
                'E',
                'F'
            )
            val v = input.toInt() and 0xFF // Cast byte to int, treating as unsigned value
            return hexArray[v ushr 4] // Select hex character from upper nibble
        }

        fun byteToLowerNibble(input: Byte): Char {
            val hexArray = charArrayOf(
                '0',
                '1',
                '2',
                '3',
                '4',
                '5',
                '6',
                '7',
                '8',
                '9',
                'A',
                'B',
                'C',
                'D',
                'E',
                'F'
            )
            val v = input.toInt() and 0xFF // Cast byte to int, treating as unsigned value
            return hexArray[v and 0x0F] // Select hex character from lower nibble
        }

        fun nibblesToByte(upperNibble: Char, lowerNibble: Char): Byte {
            val data = upperNibble.toString() + lowerNibble.toString()
            val byteArray = hexStringToByteArray(data)
            return byteArray!![0]
        }

        fun byteToUpperNibbleInt(input: Byte): Int {
            return (input.toInt() and 0xF0) shr 4
        }

        fun byteToLowerNibbleInt(input: Byte): Int {
            return input.toInt() and 0x0F
        }

        fun getNibblesFromByteArray(data: ByteArray?): MutableList<Int?>? {
            if ((data == null) || (data.isEmpty())) {
                return null
            }
            val length = data.size
            val list: MutableList<Int?> = ArrayList<Int?>()
            for (i in 0..<length) {
                val dataByte = data[i]
                val upperNibbleInt = byteToUpperNibbleInt(dataByte)
                val loweNibbleInt = byteToLowerNibbleInt(dataByte)
                list.add(upperNibbleInt)
                list.add(loweNibbleInt)
            }
            return list
        }


        /**
         * splits a byte array in chunks
         *
         * @param source
         * @param chunksize
         * @return a List<byte></byte>[]> with sets of chunksize
         */
        fun divideArrayToList(source: ByteArray, chunksize: Int): MutableList<ByteArray?> {
            val result: MutableList<ByteArray?> = ArrayList<ByteArray?>()
            var start = 0
            while (start < source.size) {
                val end = min(source.size, start + chunksize)
                result.add(source.copyOfRange(start, end))
                start += chunksize
            }
            return result
        }

        val timestamp: String
            // gives an 19 byte long timestamp yyyy.MM.dd HH:mm:ss
            get() {
                // gives a 19 character long string
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    return ZonedDateTime
                        .now(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("uuuu.MM.dd HH:mm:ss"))
                } else {
                    return SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(Date())
                }
            }

        val timestampLog: String
            // gives an 19 byte long timestamp dd.MM.yyyy HH:mm:ss
            get() {
                // gives a 19 character long string
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ZonedDateTime
                        .now(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss"))
                } else {
                    SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(Date())
                }
            }

        fun generateTestData(length: Int): ByteArray {
            /**
             * this method will generate a byte array of size 'length' and will hold a byte sequence
             * 00 01 .. FE FF 00 01 ..
             */
            // first generate a basis array
            val basis = ByteArray(256)
            for (i in 0..255) {
                basis[i] = (i and 0xFF).toByte()
            }
            // second copying the basis array to the target array
            var target = ByteArray(length)
            if (length < 256) {
                target = basis.copyOfRange(0, length)
                return target
            }
            // now length is > 256 so we do need multiple copies
            val numberOfChunks = length / 256
            var dataLoop = 0
            for (i in 0..<numberOfChunks) {
                System.arraycopy(basis, 0, target, dataLoop, 256)
                dataLoop += 256
            }
            // if some bytes are missing we are copying now
            if (dataLoop < length) {
                System.arraycopy(basis, 0, target, dataLoop, length - dataLoop)
            }
            return target
        }

        fun generateRandomTestData(length: Int): ByteArray {
            val secureRandom = SecureRandom()
            val data = ByteArray(length)
            secureRandom.nextBytes(data)
            return data
        }

        /**
         * NFC Forum "URI Record Type Definition"
         *
         *
         * This is a mapping of "URI Identifier Codes" to URI string prefixes,
         * per section 3.2.2 of the NFC Forum URI Record Type Definition document.
         */
        // source: https://github.com/skjolber/ndef-tools-for-android
        private val URI_PREFIX_MAP = arrayOf<String>(
            "",  // 0x00
            "http://www.",  // 0x01
            "https://www.",  // 0x02
            "http://",  // 0x03
            "https://",  // 0x04
            "tel:",  // 0x05
            "mailto:",  // 0x06
            "ftp://anonymous:anonymous@",  // 0x07
            "ftp://ftp.",  // 0x08
            "ftps://",  // 0x09
            "sftp://",  // 0x0A
            "smb://",  // 0x0B
            "nfs://",  // 0x0C
            "ftp://",  // 0x0D
            "dav://",  // 0x0E
            "news:",  // 0x0F
            "telnet://",  // 0x10
            "imap:",  // 0x11
            "rtsp://",  // 0x12
            "urn:",  // 0x13
            "pop:",  // 0x14
            "sip:",  // 0x15
            "sips:",  // 0x16
            "tftp:",  // 0x17
            "btspp://",  // 0x18
            "btl2cap://",  // 0x19
            "btgoep://",  // 0x1A
            "tcpobex://",  // 0x1B
            "irdaobex://",  // 0x1C
            "file://",  // 0x1D
            "urn:epc:id:",  // 0x1E
            "urn:epc:tag:",  // 0x1F
            "urn:epc:pat:",  // 0x20
            "urn:epc:raw:",  // 0x21
            "urn:epc:",  // 0x22
        )

        fun parseUriRecordPayload(ndefPayload: ByteArray): String {
            val uriPrefix = Array.getByte(ndefPayload, 0).toInt()
            val ndefPayloadLength = ndefPayload.size
            val message = ByteArray(ndefPayloadLength - 1)
            System.arraycopy(ndefPayload, 1, message, 0, ndefPayloadLength - 1)
            return URI_PREFIX_MAP[uriPrefix] + String(message, StandardCharsets.UTF_8)
        }
    }
}
