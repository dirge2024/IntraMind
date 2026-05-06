/** 文档解析接口：InputStream → 纯文本 String。由 TikaDocumentParser 实现。 */
package com.localrag.document.contract;

import java.io.InputStream;

public interface DocumentParser {
    String parse(InputStream stream);
}
