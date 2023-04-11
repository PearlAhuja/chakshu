package com.freecharge.smsprofilerservice.aws.constants;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
public enum SenderEnum {

    EPFOHO("EPFOHO"),
    ADHDFCBK("ADHDFCBK"),
    BPIPAYTM("BP-iPaytm"),
    AXICICB("AX-ICICB"),
    TMICICB("TM-ICICB");

    @Getter
    private String senderShortCode;

    private static final Map<String, SenderEnum> BY_VALUE = new HashMap<>();

    static {
        for (SenderEnum e : values()) {
            BY_VALUE.put(e.getSenderShortCode(), e);
        }
    }

    public static Optional<SenderEnum> valueOfLabel(String code) {
        return Optional.ofNullable(BY_VALUE.getOrDefault(code, null));
    }

}
