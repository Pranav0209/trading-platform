package com.zkuba.trading.domain.model;

import com.zkuba.trading.domain.enums.AssetClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;
public class Instrument {

    @NotBlank
    private final String symbol;
    @NotBlank
    private final String displayName;
    @NotNull
    private final AssetClass assetClass;
    @NotBlank
    private final String currency;
    private boolean delisted;

    public Instrument(
            String symbol,
            String displayName,
            AssetClass assetClass,
            String currency,
            boolean delisted) {

        this.symbol = Objects.requireNonNull(symbol);
        this.displayName = Objects.requireNonNull(displayName);
        this.assetClass = Objects.requireNonNull(assetClass);
        this.currency = Objects.requireNonNull(currency);
        this.delisted = delisted;
    }

    // rule 3.2 flag to check if instrument is delisted or not tradeable anymore
    public boolean isTradable() {
        return !delisted;
    }

    public void delist() {
        this.delisted = true;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AssetClass getAssetClass() {
        return assetClass;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isDelisted() {
        return delisted;
    }
}


