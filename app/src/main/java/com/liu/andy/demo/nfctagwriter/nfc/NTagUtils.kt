package com.liu.andy.demo.nfctagwriter.nfc

import android.os.Build
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.jcajce.provider.digest.MD5
import java.security.Signature
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.lang.reflect.Array
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.min

class NTagUtils {
    private fun byteArrayLength3InversedToInt(data: ByteArray): Int {
        return (data[2].toInt() and 0xff) shl 16 or ((data[1].toInt() and 0xff) shl 8) or (data[0].toInt() and 0xff)
    }

    companion object {
        init {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }

        // 根据uid生成默认的key pass Key:（0-5）
        fun generateDefaultKeyPass(uid: String, key: Int): String {
            val source = "${uid}_key${key}"
            return generateKeyPass(source)
        }

        fun generateKeyPass(secret: String): String {
            val md5 = MD5.Digest()
            val bytes = md5.digest(secret.uppercase(Locale.US).toByteArray(StandardCharsets.US_ASCII))
            return bytesToHexNpeUpperCase(bytes)
        }

        // f6cfb225-d69b-4fbb-9eda-624b0b20516e
        // 根据 Box License ID 生成 Master Key0 密码
        fun generateMasterKey0Pass(bLicense: String): String {
            return generateKeyPass(bLicense)
        }

        /**
         * 将 Hex 字符串转换为 ECC 公钥 (X.509 格式)
         */
        fun loadPublicKeyFromHex(hex: String): PublicKey {
            // 确保 BC Provider 已注册
            if (Security.getProvider("BC") == null) {
                Security.addProvider(BouncyCastleProvider())
            }
            val keyBytes = hexStringToByteArray(hex)
            val spec = X509EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance("ECDSA", "BC")
            return kf.generatePublic(spec)
        }

        /**
         * 将 Hex 字符串转换为 ECC 私钥 (PKCS#8 格式)
         */
        fun loadPrivateKeyFromHex(hex: String): PrivateKey {
            // 确保 BC Provider 已注册
            if (Security.getProvider("BC") == null) {
                Security.addProvider(BouncyCastleProvider())
            }
            val keyBytes = hexStringToByteArray(hex)
            val spec = PKCS8EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance("ECDSA", "BC")
            return kf.generatePrivate(spec)
        }
        /**
         * 1.算法选择：使用了 SHA256withECDSA。
         * 这是目前移动端和嵌入式安全（包括 NFC 验证）最通用的签名算法。
         * 2.Bouncy Castle 依赖：代码显式指定了 "BC" 作为 Provider。
         * 由于 Android 系统自带了一个精简版的 Bouncy Castle，可能会有兼容性问题，所以通过 Security.addProvider(BouncyCastleProvider()) 强制使用您依赖中定义的 1.70 版本。
         * 3.曲线名称：secp256r1 是标准曲线，具有极高的安全性且计算效率高。
         * 4.输入处理：签名函数接受 ByteArray。在处理 NTAG424 时，您通常会从 Tag 对象获取 7 字节的 UID，或者从读取到的 NDEF 文本中提取。
         */
        /**
         * 生成 ECC 密钥对 (secp256r1 / NIST P-256)
         */
        fun generateECCKeyPair(): KeyPair {
            // 确保安装了 BouncyCastle 提供者
            Security.removeProvider("BC")
            Security.addProvider(BouncyCastleProvider())

            val keyPairGenerator = KeyPairGenerator.getInstance("ECDSA", "BC")
            val ecSpec = ECGenParameterSpec("secp256r1")
            keyPairGenerator.initialize(ecSpec, SecureRandom())
            return keyPairGenerator.generateKeyPair()
        }

        /**
         * 使用私钥对数据（如 UID）进行签名
         * @param privateKey ECC 私钥
         * @param data 要签名的数据 (如 NTAG424 UID)
         * @return 签名后的字节数组(70/71/72字节)
         */
        fun signData(privateKey: PrivateKey, data: ByteArray): ByteArray {
            val signature = Signature.getInstance("SHA256withECDSA", "BC")
            signature.initSign(privateKey)
            signature.update(data)
            return signature.sign()
        }
        /**
         * 将 ECDSA DER 签名补齐至固定 72 字节
         * @param derSignature 原始签名字节数组 (通常 70-72 字节)
         * @return 长度固定为 72 的字节数组
         */
        fun padSignatureTo72Bytes(derSignature: ByteArray): ByteArray {
            val targetLength = 72

            // 1. 安全检查：如果签名超过 72 字节（理论上 P-256 不会发生）
            if (derSignature.size > targetLength) {
                throw IllegalArgumentException("签名长度(${derSignature.size})超过预设的72字节")
            }

            // 2. 如果正好是 72 字节，直接返回副本
            if (derSignature.size == targetLength) {
                return derSignature.copyOf()
            }

            // 3. 创建新的 72 字节数组（Java/Kotlin 默认初始化为 0x00）
            val padded = ByteArray(targetLength)

            // 4. 将原始签名拷贝到起始位置
            System.arraycopy(derSignature, 0, padded, 0, derSignature.size)

            return padded
        }
        /**
         * 根据 ASN.1/DER 格式（0x30 + Length + Data）截取有效数据
         * 自动去除尾部补足的字节
         */
        fun unpadByAsn1Length(data: ByteArray): ByteArray {
            // 安全检查：至少需要 2 个字节（Header + Length）
            if (data.size < 2) return data

            // 校验第一个字节是否为 0x30
            if (data[0] != 0x30.toByte()) {
                // 如果不是 0x30，可能不是预期的格式，可以根据业务需求抛出异常或返回原数据
                return data
            }

            // 第二个字节是数据的长度
            // 注意：Java/Kotlin 的 Byte 是有符号的，0x80 及以上会变成负数，需要 & 0xFF 转换为无符号整数
            val dataLength = data[1].toInt() and 0xFF

            // 总长度 = 头部(2字节) + 数据内容长度
            val totalLength = 2 + dataLength

            // 如果原始数组比声明的长度长，说明有补足字节，进行截取
            return if (data.size > totalLength) {
                data.copyOfRange(0, totalLength)
            } else {
                data
            }
        }
        /**
         * 使用公钥验证签名
         * @param publicKey ECC 公钥
         * @param data 原始数据 (如 NTAG424 UID)
         * @param signatureBytes 签名数据
         * @return 验证是否通过
         */
        fun verifySignature(publicKey: PublicKey, data: ByteArray, signatureBytes: ByteArray): Boolean {
            val signature = Signature.getInstance("SHA256withECDSA", "BC")
            signature.initVerify(publicKey)
            signature.update(data)
            return signature.verify(signatureBytes)
        }

        fun derToRaw(derSignature: ByteArray): ByteArray {
            // 1. 解析 ASN.1 DER 结构
            val seq = ASN1Sequence.getInstance(derSignature)
            val r = ASN1Integer.getInstance(seq.getObjectAt(0)).value
            val s = ASN1Integer.getInstance(seq.getObjectAt(1)).value

            val raw = ByteArray(64)

            // 2. 将 BigInteger 转换为 32 字节的数组
            // 注意：BigInteger.toByteArray() 可能会因为符号位多出一个 0x00，或者长度不足 32
            val rBytes = extractFixedBytes(r, 32)
            val sBytes = extractFixedBytes(s, 32)

            System.arraycopy(rBytes, 0, raw, 0, 32)
            System.arraycopy(sBytes, 0, raw, 32, 32)

            return raw
        }

        private fun extractFixedBytes(value: BigInteger, length: Int = 32): ByteArray {
            val result = ByteArray(length)
            val bytes = value.toByteArray()

            // 移除 BigInteger 可能存在的符号填充位 (0x00)
            val start = if (bytes[0].toInt() == 0 && bytes.size > 1) 1 else 0
            val count = bytes.size - start

            // 如果 count > length，说明该曲线不是 P-256，需要调整长度
            // 这里将数据拷贝至结果数组的末尾（右对齐），前面自动补零
            System.arraycopy(bytes, start, result, length - count, count)
            return result
        }

        /**
         * 测试函数：演示生成密钥、签名及验证的完整流程
         */
        fun testECCSignatureWithUid(uidHex: String = "0464171A282290"): Boolean {
            try {
                val uid = hexStringToByteArray(uidHex)

                // 1. 生成密钥对
                val keyPair = generateECCKeyPair()
                val privateKey = keyPair.private
                val publicKey = keyPair.public
                println("Public Key: ${bytesToHexNpeUpperCaseBlank(publicKey.encoded)}")
                println("Private Key: ${bytesToHexNpeUpperCaseBlank(privateKey.encoded)}")
                // 2. 签名
                val signature = signData(privateKey, uid)
                println("UID: $uidHex")
                println("Signature: ${bytesToHex(signature)}")

                // 3. 验证
                val isValid = verifySignature(publicKey, uid, signature)
                println("Signature Verification Result: $isValid")
                return isValid
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return false
        }
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

        fun hexStringToByteArray(hex: String): ByteArray {
            val cleanHex = hex.replace(" ", "").replace("-", "")
            val result = ByteArray(cleanHex.length / 2)
            for (i in cleanHex.indices step 2) {
                val str = cleanHex.substring(i, i + 2)
                result[i / 2] = str.toInt(16).toByte()
            }
            return result
        }

        /**
         * Convert string to hex string using ASCII mapping
         * Example: "915565AB915565AB" -> "39313535363541423931353536354142"
         */
        fun stringToHexString(input: String): String {
            return input.map { char ->
                String.format("%02X", char.code)
            }.joinToString("")
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
