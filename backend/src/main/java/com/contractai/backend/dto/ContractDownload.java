package com.contractai.backend.dto;

import java.io.InputStream;

/** A contract's file ready to stream to the caller. The receiver must close {@code content}. */
public record ContractDownload(String filename, long sizeBytes, InputStream content) {
}
