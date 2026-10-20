package io.github.xiaowuiot.envmonitor.device;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.xiaowuiot.envmonitor.common.ApiException;
import io.github.xiaowuiot.envmonitor.config.EnvProperties;
import io.github.xiaowuiot.envmonitor.hj212.Hj212Crypto;
import io.github.xiaowuiot.envmonitor.hj212.Sm4;
import io.github.xiaowuiot.envmonitor.hj212.codec.Hj212Decoder;

/**
 * HJ212-2025 的通信密钥（标准 6.4）。开源版支持登记密钥、按密钥解密数采仪上报的加密报文；
 * 数采仪激活（1001 / 1002）、密钥自动更新（1014）在完整版里。
 * <p>
 * 密钥不明文存储：库里存的是用平台主密钥（env.hj212.key-secret，或 data/hj212-master.key）SM4 加密后的十六进制，
 * 页面只显示有没有密钥和创建时间，登记时随机生成的密钥只显示这一次。
 * 同时是解码器的密钥来源（{@link Hj212Decoder.KeyLookup}），内存里按 MN 缓存一份。
 */
@Service
public class SecretKeyService implements Hj212Decoder.KeyLookup {

    private static final Logger log = LoggerFactory.getLogger(SecretKeyService.class);
    private static final DateTimeFormatter CREATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final DeviceRepository devices;
    private final byte[] master;
    private final Map<String, List<byte[]>> cache = new ConcurrentHashMap<>();

    public SecretKeyService(DeviceRepository devices, EnvProperties props) {
        this.devices = devices;
        this.master = loadMaster(props);
    }

    private static byte[] loadMaster(EnvProperties props) {
        String text = props.hj212().keySecret();
        if (text != null && !text.isBlank()) {
            return Hj212Crypto.key(text.trim());
        }
        Path file = Path.of(props.dataDir(), "hj212-master.key");
        try {
            if (Files.exists(file)) {
                return Hj212Crypto.key(Files.readString(file, StandardCharsets.US_ASCII).trim());
            }
            Files.createDirectories(file.getParent());
            String generated = Hj212Crypto.newKey();
            Files.writeString(file, generated, StandardCharsets.US_ASCII);
            log.info("已生成 HJ212 通信密钥的主密钥 {}，请妥善备份（丢了就解不开库里存的设备密钥）", file.toAbsolutePath());
            return Hj212Crypto.key(generated);
        } catch (IOException e) {
            throw new IllegalStateException("读写主密钥文件失败：" + file, e);
        }
    }

    public String seal(String keyText) {
        return HexFormat.of().formatHex(Sm4.encryptEcb(master, Hj212Crypto.key(keyText)));
    }

    public String open(String sealed) {
        return new String(Sm4.decryptEcb(master, HexFormat.of().parseHex(sealed)), StandardCharsets.US_ASCII);
    }

    @Override
    public List<byte[]> keys(String mn) {
        return cache.computeIfAbsent(mn, k -> devices.findByMn(k).map(d -> {
            List<byte[]> list = new ArrayList<>(1);
            try {
                if (d.getSecretKey() != null) {
                    list.add(Hj212Crypto.key(open(d.getSecretKey())));
                }
            } catch (RuntimeException e) {
                log.warn("设备 {} 的密钥解不开（主密钥换过？）：{}", d.getMn(), e.getMessage());
            }
            return list;
        }).orElse(List.of()));
    }

    public void evict(String mn) {
        if (mn != null) {
            cache.remove(mn);
        }
    }

    /**
     * 登记（或重置）密钥。keyText 为空时随机生成
     *
     * @return 明文密钥，只在这里返回一次，用来写进数采仪
     */
    @Transactional
    public String register(Device d, String keyText) {
        String key = keyText == null || keyText.isBlank() ? Hj212Crypto.newKey() : keyText.trim();
        if (!key.matches("[0-9A-Za-z]{16}")) {
            throw ApiException.badRequest("密钥要 16 个字母或数字");
        }
        d.setSecretKey(seal(key));
        d.setSecretKeyCreateTime(LocalDateTime.now().format(CREATE_TIME));
        devices.save(d);
        evict(d.getMn());
        log.info("设备 {} 登记了通信密钥", d.getMn());
        return key;
    }
}
