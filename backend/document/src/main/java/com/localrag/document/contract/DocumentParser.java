package com.localrag.document.contract;

import java.io.InputStream;

public interface DocumentParser {
    String parse(InputStream stream);
}
