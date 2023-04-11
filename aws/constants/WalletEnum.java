package com.freecharge.smsprofilerservice.aws.constants;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.*;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
public enum WalletEnum {

    FREECHARGE("Freecharge"),
    AMAZONPAY("Amazonpay"),
    PAYTM("PayTm"),
    OXIGEN("OXIGEN");

    @Getter
    private String walletName;

    private static final Map<String, WalletEnum> BY_VALUE = new HashMap<>();

    public static final List<String> names = new LinkedList<>();

    static {
        for (WalletEnum e : values()) {
            BY_VALUE.put(e.walletName, e);
            names.add(e.walletName);
        }
    }

    public static Optional<WalletEnum> valueOfLabel(String code) {
        return Optional.ofNullable(BY_VALUE.getOrDefault(code, null));
    }

}
