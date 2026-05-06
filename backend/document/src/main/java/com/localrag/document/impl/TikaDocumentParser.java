/** Apache Tika 实现，自动检测文件格式并提取纯文本，支持 1000+ 文件类型。 */
package com.localrag.document.impl;

import com.localrag.document.contract.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Slf4j
@Component
public class TikaDocumentParser implements DocumentParser {

    private final Tika tika = new Tika();

    @Override
    public String parse(InputStream stream) {
        try {
            return tika.parseToString(stream);
        } catch (Exception e) {
            log.error("Tika parse failed", e);
            return "";
        }
    }
}
