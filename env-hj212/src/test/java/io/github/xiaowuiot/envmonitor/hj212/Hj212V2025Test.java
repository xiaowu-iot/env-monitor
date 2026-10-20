package io.github.xiaowuiot.envmonitor.hj212;

import io.github.xiaowuiot.envmonitor.hj212.codec.Hj212Decoder;
import io.github.xiaowuiot.envmonitor.hj212.codec.Hj212Encoder;
import io.github.xiaowuiot.envmonitor.hj212.codec.Hj212Frame;
import io.github.xiaowuiot.envmonitor.hj212.codec.Hj212FrameDecoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HJ212-2025 的报文，用标准原文附录 A、附录 C 的示例做对照
 */
class Hj212V2025Test {

    /**
     * 附录 A.1 的 CRC 示例
     */
    static final String A1 = "##0087QN=20240601085857223;ST=32;CN=1011;PW=123456;MN=010000A8900016F000169DC0;Flag=9;CP=&&&&2200\r\n";

    /**
     * 附录 A.2 示例 1、示例 3 的明文报文
     */
    static final String A2_1 = "QN=20240520210700000;ST=31;CN=2051;PW=123456;MN=010000A8900016F000169DC0;Flag=9;CP=&&DataTime=20240520210600;"
            + "a01011-Min=1.35,a01011-Avg=1.35,a01011-Max=1.35,a01011-Flag=N;a01012-Min=9.3,a01012-Avg=9.3,a01012-Max=9.3,a01012-Flag=N;"
            + "a01014-Min=3.20,a01014-Avg=3.211,a01014-Max=3.212,a01014-Flag=N&&";
    static final String A2_3 = "QN=20240520210700000;ST=31;CN=2051;PW=123456;MN=010000A8900016F000169DC0;Flag=9;CP=&&DataTime=20240520210600;"
            + "a01011-Min=1.35,a01011-Avg=1.35,a01011-Max=1.35,a01011-Flag=N;a01014-Min=3.20,a01014-Avg=3.20,a01014-Max=3.20,a01014-Flag=N&&";

    /**
     * 附录 A.2 的示例密钥：16 个 0x30
     */
    static final byte[] KEY = Hj212Crypto.key("0000000000000000");

    @Test
    void crcAndLengthMatchAppendixA() {
        String data = A1.substring(6, A1.length() - 6);
        assertEquals(A1, Hj212Format.frame(data));
        assertTrue(Hj212Format.frame(A2_1).startsWith("##0295"));
        assertTrue(Hj212Format.frame(A2_1).endsWith("2200\r\n"));
        assertTrue(Hj212Format.frame(A2_3).startsWith("##0234"));
        assertTrue(Hj212Format.frame(A2_3).endsWith("B541\r\n"));
    }

    @Test
    void flagCarriesVersion2() {
        Hj212Packet p = Hj212Format.parse(A1.substring(6, A1.length() - 6));
        assertEquals(Hj212Packet.VERSION_2025, p.version());
        assertEquals(Hj212Versions.V2025, Hj212Versions.detect(p));
        assertTrue(p.needAck());
        // 应答也用 2025 版：Flag=8
        assertEquals(8, p.dataAck().getFlag());
        assertEquals(9, Hj212Packet.create(Hj212Packet.VERSION_2025, "32", "2011", "123456", "MN", true).getFlag());
    }

    @Test
    void encryptionFollowsAppendixA2() {
        byte[] plain = A2_3.getBytes(StandardCharsets.UTF_8);
        byte[] cipher = Hj212Crypto.encrypt(plain, KEY);
        // 密文和明文一样长，包头和结尾的 && 不变
        assertEquals(plain.length, cipher.length);
        int cp = A2_3.indexOf("CP=&&") + 5;
        assertEquals(A2_3.substring(0, cp), new String(cipher, 0, cp, StandardCharsets.US_ASCII));
        // CP 内容 147 字节 = 9 组密文 + 3 字节明文 "g=N"，和示例 4 一样
        assertEquals("g=N&&", new String(cipher, cipher.length - 5, 5, StandardCharsets.US_ASCII));
        // 示例 4 印出来的第 2 组密文（标准里有几组和印出来的明文对不上，取能对上的一组）
        assertEquals("a242577abfaed881be88b72d69fc55ed", HexFormat.of().formatHex(cipher, cp + 16, cp + 32));
        assertArrayEquals(plain, Hj212Crypto.decrypt(cipher, KEY));
    }

    @Test
    void encryptedFrameRoundTrip() {
        Hj212Packet p = Hj212Format.parse(A2_1);
        EmbeddedChannel out = new EmbeddedChannel(Hj212Encoder.INSTANCE);
        out.writeOutbound(p.encryptWith(KEY));
        ByteBuf frame = out.readOutbound();
        byte[] bytes = ByteBufUtil.getBytes(frame);
        frame.release();
        // 长度和 CRC 都是明文的
        assertEquals("##0295", new String(bytes, 0, 6, StandardCharsets.US_ASCII));
        assertEquals("2200\r\n", new String(bytes, bytes.length - 6, 6, StandardCharsets.US_ASCII));
        assertFalse(new String(bytes, StandardCharsets.ISO_8859_1).contains("DataTime"));

        // 平台有这台设备的密钥：解开，标记为加密报文
        Hj212Decoder.KeyLookup keys = mn -> List.of(Hj212Crypto.key("1111111111111111"), KEY);
        EmbeddedChannel in = new EmbeddedChannel(new Hj212FrameDecoder(), new Hj212Decoder(keys));
        in.writeInbound(Unpooled.wrappedBuffer(bytes));
        Hj212Frame f = in.readInbound();
        assertTrue(f.ok(), f.error());
        assertTrue(f.packet().isEncrypted());
        assertEquals("3.212", f.packet().getFactors().get("a01014").get("Max"));
        assertTrue(f.raw().contains("DataTime=20240520210600"));

        // 没有密钥：CRC 对不上，但包头能解析出 MN
        EmbeddedChannel noKey = new EmbeddedChannel(new Hj212FrameDecoder(), new Hj212Decoder());
        noKey.writeInbound(Unpooled.wrappedBuffer(bytes));
        Hj212Frame bad = noKey.readInbound();
        assertFalse(bad.ok());
        assertEquals("010000A8900016F000169DC0", bad.packet().getMn());
    }

    @Test
    void onlyDataKeyAndInfoCommandsAreEncrypted() {
        assertTrue(Hj212Crypto.mustEncrypt("2011"));
        assertTrue(Hj212Crypto.mustEncrypt("2062"));
        assertTrue(Hj212Crypto.mustEncrypt("1014"));
        assertTrue(Hj212Crypto.mustEncrypt("3020"));
        assertFalse(Hj212Crypto.mustEncrypt("9014"));
        assertFalse(Hj212Crypto.mustEncrypt("1011"));
        assertFalse(Hj212Crypto.mustEncrypt("3019"));
        String key = Hj212Crypto.newKey();
        assertEquals(16, key.length());
        assertNotEquals(key, Hj212Crypto.newKey());
    }

    @Test
    void activationWithEmptyMn() {
        // 表 C.2：没激活的数采仪 MN 为空，上传硬件序号
        Hj212Packet p = Hj212Format.parse("QN=20240601085857223;ST=32;CN=1001;PW=123456;MN=;Flag=9;"
                + "CP=&&i23005-Info=6970000000001ABCDEFG1234;i23006-Info=A800200A8C6D;i23007-Info=E86A643C7BBC;i23008-Info=A763742C1AE1&&");
        assertEquals("", p.getMn());
        assertEquals("6970000000001ABCDEFG1234", p.getFactors().get("i23005").get("Info"));
        assertEquals("QN=20240601085857223;ST=91;CN=9013;PW=123456;MN=;Flag=8;CP=&&&&",
                Hj212Format.format(p.reply(Hj212Commands.NOTIFY_ACK)));
    }

    @Test
    void keyCommandUsesCommaBetweenFields() {
        // 表 C.8：SKCreateTime 和 NewSK 之间是逗号
        Hj212Packet p = Hj212Format.parse("QN=20240601085857223;ST=32;CN=1014;PW=123456;MN=010000A8900016F000169DC0;Flag=9;"
                + "CP=&&SKCreateTime=20240601085857223,NewSK=0000000000000000&&");
        assertEquals("20240601085857223", p.getParam("SKCreateTime"));
        assertEquals("0000000000000000", p.getParam("NewSK"));
    }

    @Test
    void refillFlag() {
        Hj212Packet p = Hj212Packet.create(Hj212Packet.VERSION_2025, "32", "2061", "123456", "010000A8900016F000169DC0", true)
                .setRefill(true).putParam("DataTime", "20240601080000").putFactor("w01018", "Avg", "40.1");
        String data = Hj212Format.format(p);
        assertTrue(data.contains(";Flag=9;RF=1;CP=&&"));
        assertTrue(Hj212Format.parse(data).isRefill());
        assertFalse(Hj212Format.parse(A2_1).isRefill());
    }

    @Test
    void logsKeepSeparatorsAndChinese() {
        // 表 C.47：日志写在一对 // 之间，可以有中文；这里故意带上分号和逗号
        String data = "QN=20240601085857223;ST=32;CN=3020;PW=123456;MN=010000A8900016F000169DC0;Flag=9;"
                + "CP=&&DataTime=20240601085857;PolId=w01018;i11001-Info=//清洗管路;更换试剂,完成//&&";
        Hj212Packet p = Hj212Format.parse(data);
        assertEquals("//清洗管路;更换试剂,完成//", p.getFactors().get("i11001").get("Info"));
        assertEquals("w01018", p.getParam("PolId"));
        // 值里的网址不是日志
        Hj212Packet url = Hj212Format.parse("QN=1;ST=91;CN=8001;PW=1;MN=M;Flag=5;CP=&&FwVer=1.2.0;FwUrl=http://127.0.0.1/a/b;FwMd5=ab&&");
        assertEquals("http://127.0.0.1/a/b", url.getParam("FwUrl"));
        assertEquals("ab", url.getParam("FwMd5"));
        // 长度按 UTF-8 字节算，解码后中文不乱
        EmbeddedChannel ch = new EmbeddedChannel(new Hj212FrameDecoder(), new Hj212Decoder());
        ch.writeInbound(Unpooled.copiedBuffer(Hj212Format.frame(data), StandardCharsets.UTF_8));
        Hj212Frame f = ch.readInbound();
        assertTrue(f.ok(), f.error());
        assertEquals("//清洗管路;更换试剂,完成//", f.packet().getFactors().get("i11001").get("Info"));
    }

    @Test
    void toleratesInconsistenciesInAppendixC() {
        // 表 C.29：BeginTime、EndTime 之间是逗号；表 C.31：字段名前多一个空格
        Hj212Packet a = Hj212Format.parse("QN=20240601085857223;ST=32;CN=2041;PW=123456;MN=010000A8900016F000169DC0;Flag=9;"
                + "CP=&&BeginTime=20240601000000,EndTime=20240601000000&&");
        assertEquals("20240601000000", a.getParam("EndTime"));
        Hj212Packet b = Hj212Format.parse("QN=20240601085857223;ST=31;CN=2111;PW=123456;MN=010000A8900016F000169DC0;Flag=9;"
                + "CP=&&DataTime=20240601011000; a01901-Avg=868&&");
        assertEquals("868", b.getFactors().get("a01901").get("Avg"));
    }

    @Test
    void splitPartsHaveTheirOwnQn() {
        // 表 C.58：Flag=11，每个分包 QN 不同、都带 DataTime，每一包单独就能解析入库
        String p1 = "QN=20240601085857534;ST=32;CN=2061;PW=123456;MN=010000A8900016F000169DC0;Flag=11;PNUM=2;PNO=1;"
                + "CP=&&DataTime=20240601080000;w00000-Cou=63.0,w00000-Avg=17.5,w00000-Flag=N;w01001-Min=7.1,w01001-Max=7.8,w01001-Flag=N&&";
        Hj212Packet first = Hj212Format.parse(p1);
        assertTrue(first.isSplit());
        assertTrue(first.needAck());
        assertEquals(2, first.getPnum());
        assertEquals("20240601080000", first.getParam("DataTime"));
        // 每包单独应答，应答的 QN 是这一包的 QN
        assertEquals("20240601085857534", first.dataAck().getQn());
        assertEquals(8, first.dataAck().getFlag());
    }

    @Test
    void newParameterCodes() {
        assertTrue(Hj212Factors.isFactorCode("d20105"));
        assertTrue(Hj212Factors.isFactorCode("p99101"));
        assertTrue(Hj212Factors.isFactorCode("p11a01"));
        assertTrue(Hj212Factors.isFactorCode("LA"));
        assertTrue(Hj212Factors.isFactorCode("Leq"));
        assertTrue(Hj212Factors.isFactorCode("L5"));
        assertTrue(Hj212Factors.isFactorCode("w01018"));
        assertFalse(Hj212Factors.isFactorCode("i23005"));
        assertEquals("闷炉", Hj212Commands.flagName("B", Hj212Packet.VERSION_2025));
        assertEquals("通讯异常", Hj212Commands.flagName("B", Hj212Packet.VERSION_2017));
        assertFalse(Hj212Commands.isValidFlag("Td"));
        assertTrue(Hj212Commands.isValidFlag("Sr"));
    }
}
