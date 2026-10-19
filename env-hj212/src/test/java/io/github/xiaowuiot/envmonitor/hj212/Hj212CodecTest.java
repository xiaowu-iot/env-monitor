package io.github.xiaowuiot.envmonitor.hj212;

import io.github.xiaowuiot.envmonitor.hj212.codec.Hj212Decoder;
import io.github.xiaowuiot.envmonitor.hj212.codec.Hj212Encoder;
import io.github.xiaowuiot.envmonitor.hj212.codec.Hj212Frame;
import io.github.xiaowuiot.envmonitor.hj212.codec.Hj212FrameDecoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class Hj212CodecTest {

    /**
     * 标准附录里的示例报文
     */
    static final String SAMPLE = "##0101QN=20160801085857223;ST=32;CN=1062;PW=100000;MN=010000A8900016F000169DC0;Flag=5;CP=&&RtdInterval=30&&1C80\r\n";

    @Test
    void crcMatchesStandardSample() {
        String data = SAMPLE.substring(6, SAMPLE.length() - 6);
        assertEquals(101, data.length());
        assertEquals(0x1C80, Hj212Crc.crc16(data.getBytes(StandardCharsets.US_ASCII)));
        assertEquals(SAMPLE, Hj212Format.frame(data));
    }

    @Test
    void parseAndFormatRoundTrip() {
        String data = "QN=20240101120000000;ST=32;CN=2011;PW=123456;MN=88888880000001;Flag=5;"
                + "CP=&&DataTime=20240101120000;w01018-Rtd=101.5,w01018-Flag=N;w21003-Rtd=1.25,w21003-Flag=N&&";
        Hj212Packet p = Hj212Format.parse(data);
        assertEquals("2011", p.getCn());
        assertEquals("88888880000001", p.getMn());
        assertTrue(p.needAck());
        assertEquals(1, p.version());
        assertEquals("20240101120000", p.getParam("DataTime"));
        assertEquals("101.5", p.getFactors().get("w01018").get("Rtd"));
        assertEquals(data, Hj212Format.format(p));

        Hj212Packet ack = p.dataAck();
        assertEquals("9014", ack.getCn());
        assertEquals(p.getQn(), ack.getQn());
        assertEquals("91", ack.getSt());
    }

    @Test
    void stickyAndHalfPackets() {
        EmbeddedChannel ch = new EmbeddedChannel(new Hj212FrameDecoder(), new Hj212Decoder());
        String two = "garbage" + SAMPLE + SAMPLE;
        // 第一次只给一半，第二次给剩下的
        int cut = 50;
        ch.writeInbound(buf(two.substring(0, cut)));
        assertNull(ch.readInbound());
        ch.writeInbound(buf(two.substring(cut)));
        Hj212Frame f1 = ch.readInbound();
        Hj212Frame f2 = ch.readInbound();
        assertTrue(f1.ok());
        assertTrue(f2.ok());
        assertEquals("1062", f1.packet().getCn());
        assertEquals("30", f2.packet().getParam("RtdInterval"));
    }

    @Test
    void badCrcIsReportedNotDropped() {
        EmbeddedChannel ch = new EmbeddedChannel(new Hj212FrameDecoder(), new Hj212Decoder());
        ch.writeInbound(buf(SAMPLE.replace("1C80", "1C81") + SAMPLE));
        Hj212Frame bad = ch.readInbound();
        assertFalse(bad.ok());
        assertTrue(bad.error().contains("CRC"));
        // CRC 错也能拿到 QN 和 MN，用来回 9011
        assertEquals("010000A8900016F000169DC0", bad.packet().getMn());
        Hj212Frame good = ch.readInbound();
        assertTrue(good.ok());
    }

    @Test
    void wrongLengthResyncs() {
        EmbeddedChannel ch = new EmbeddedChannel(new Hj212FrameDecoder(), new Hj212Decoder());
        ch.writeInbound(buf("##0050QN=1;CP=&&&&ABCD\r\n" + SAMPLE));
        Hj212Frame f = ch.readInbound();
        assertTrue(f.ok());
        assertEquals("1062", f.packet().getCn());
    }

    @Test
    void encoderWritesFrame() {
        EmbeddedChannel ch = new EmbeddedChannel(Hj212Encoder.INSTANCE);
        Hj212Packet p = Hj212Format.parse(SAMPLE.substring(6, SAMPLE.length() - 6));
        ch.writeOutbound(p);
        ByteBuf out = ch.readOutbound();
        assertEquals(SAMPLE, out.toString(StandardCharsets.US_ASCII));
        out.release();
    }

    @Test
    void qnIsUnique() {
        String a = Hj212Time.nextQn();
        String b = Hj212Time.nextQn();
        assertEquals(17, a.length());
        assertNotEquals(a, b);
    }

    private static ByteBuf buf(String s) {
        return Unpooled.copiedBuffer(s, StandardCharsets.US_ASCII);
    }
}
