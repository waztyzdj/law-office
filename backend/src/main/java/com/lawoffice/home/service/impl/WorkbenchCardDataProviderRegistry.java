package com.lawoffice.home.service.impl;

import com.lawoffice.home.service.IWorkbenchCardDataProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WorkbenchCardDataProviderRegistry {

    private final List<IWorkbenchCardDataProvider> providers;

    public WorkbenchCardDataProviderRegistry(List<IWorkbenchCardDataProvider> providers) {
        this.providers = providers;
    }

    public IWorkbenchCardDataProvider requireProvider(String cardCode) {
        return providers.stream()
                .filter(provider -> provider.supports(cardCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("卡片数据Provider尚未接入"));
    }
}
